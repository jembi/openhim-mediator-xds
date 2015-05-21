/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.openhim.mediator.denormalization.CSDRequestActor;
import org.openhim.mediator.denormalization.PIXRequestActor;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import org.openhim.mediator.messages.OrchestrateProvideAndRegisterRequest;
import org.openhim.mediator.messages.OrchestrateProvideAndRegisterRequestResponse;
import org.openhim.mediator.normalization.SOAPWrapper;
import org.openhim.mediator.normalization.XDSbMimeProcessorActor;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RepositoryActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private MediatorConfig config;
    private ActorRef mtomProcessor;

    private MediatorHTTPRequest originalRequest;

    private String action;
    private String messageID;
    private String xForwardedFor;
    private String mimeDocument;
    private String contentType;
    private boolean messageIsMTOM;

    private String messageBuffer;
    private SOAPWrapper soapWrapper;


    public RepositoryActor(MediatorConfig config) {
        this.config = config;
        mtomProcessor = getContext().actorOf(Props.create(XDSbMimeProcessorActor.class), "xds-multipart-normalization");
    }


    private void readMessage() {
        contentType = originalRequest.getHeaders().get("Content-Type");

        if (contentType!=null && (contentType.contains("multipart/related") || contentType.contains("multipart/form-data"))) {
            log.info("Message is multipart. Parsing contents...");
            XDSbMimeProcessorActor.MimeMessage mimeMsg = new XDSbMimeProcessorActor.MimeMessage(originalRequest.getRequestHandler(), getSelf(), originalRequest.getBody(), contentType);
            mtomProcessor.tell(mimeMsg, getSelf());
            messageIsMTOM = true;
        } else {
            messageBuffer = originalRequest.getBody();
            messageIsMTOM = false;
            triggerRepositoryAction();
        }
    }

    private void processMtomProcessorResponse(XDSbMimeProcessorActor.XDSbMimeProcessorResponse msg) {
        if (msg.getOriginalRequest() instanceof XDSbMimeProcessorActor.MimeMessage) {
            log.info("Successfully parsed multipart contents");
            messageBuffer = msg.getResponseObject();

            if (msg.getDocuments()!=null && msg.getDocuments().size()>0) {
                //TODO atm only a single document is handled
                //this is just used for 'autoRegister' and really only so that there is _some_ support for mtom.
                mimeDocument = msg.getDocuments().get(0);
            }

            triggerRepositoryAction();
        } else if (msg.getOriginalRequest() instanceof XDSbMimeProcessorActor.EnrichedMessage) {
            messageBuffer = msg.getResponseObject();
            forwardRequestToRepository();
        } else {
            unhandled(msg);
        }
    }

    private boolean determineSOAPAction() {
        try {
            readSOAPHeader();
            if (action==null || action.isEmpty()) {
                //not in soap header. maybe it's in the content-type?
                action = getSOAPActionFromContentType();

                if (action==null || action.isEmpty()) {
                    FinishRequest fr = new FinishRequest("Could not determine SOAP Action. Is the correct WS-Adressing header set?", "text/plain", HttpStatus.SC_BAD_REQUEST);
                    originalRequest.getRespondTo().tell(fr, getSelf());
                    return false;
                }
            }

            action = action.trim();
            log.info("Action: " + action);
            return true;
        } catch (ParserConfigurationException | SAXException | XPathExpressionException | IOException ex) {
            originalRequest.getRequestHandler().tell(new ExceptError(ex), getSelf());
            return false;
        }
    }

    private void readSOAPHeader() throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(IOUtils.toInputStream(messageBuffer));
        XPath xpath = XPathFactory.newInstance().newXPath();
        action = xpath.compile("//Envelope/Header/Action").evaluate(doc);
        messageID = xpath.compile("//Envelope/Header/MessageID").evaluate(doc);
    }

    private String getSOAPActionFromContentType() {
        if (contentType==null) {
            return null;
        }

        int startI = contentType.indexOf("action=")+"action=\"".length();
        if (startI<0) {
            return null;
        }

        String subStr = contentType.substring(startI);
        int endI = subStr.indexOf("\"");
        if (endI>-1) {
            return subStr.substring(0, endI);
        }
        return subStr;
    }

    private void processProviderAndRegisterAction() {
        ActorRef resolvePatientIDHandler = getContext().actorOf(Props.create(PIXRequestActor.class, config), "pix-denormalization");
        ActorRef resolveHealthcareWorkerIDHandler = getContext().actorOf(Props.create(CSDRequestActor.class, config), "csd-denormalization");
        ActorRef resolveFacilityIDHandler = resolveHealthcareWorkerIDHandler;
        ActorRef pnrOrchestrator = getContext().actorOf(
                Props.create(
                        ProvideAndRegisterOrchestrationActor.class, config,
                        resolvePatientIDHandler, resolveHealthcareWorkerIDHandler, resolveFacilityIDHandler
                ),
                "xds-pnr-orchestrator"
        );

        try {
            soapWrapper = new SOAPWrapper(messageBuffer);
            OrchestrateProvideAndRegisterRequest msg = new OrchestrateProvideAndRegisterRequest(
                    originalRequest.getRequestHandler(), getSelf(), soapWrapper.getSoapBody(), xForwardedFor, mimeDocument, messageID
            );
            pnrOrchestrator.tell(msg, getSelf());
        } catch (SOAPWrapper.SOAPParseException ex) {
            FinishRequest fr = new FinishRequest(ex.getMessage(), "text/plain", HttpStatus.SC_BAD_REQUEST);
            originalRequest.getRequestHandler().tell(fr, getSelf());
        }
    }

    private void processProvideAndRegisterResponse(OrchestrateProvideAndRegisterRequestResponse msg) {
        soapWrapper.setSoapBody(msg.getResponseObject());
        messageBuffer = soapWrapper.getFullDocument();

        if (messageIsMTOM) {
            XDSbMimeProcessorActor.EnrichedMessage mimeMsg = new XDSbMimeProcessorActor.EnrichedMessage(
                    originalRequest.getRequestHandler(), getSelf(), messageBuffer
            );
            mtomProcessor.tell(mimeMsg, getSelf());
        } else {
            forwardRequestToRepository();
        }
    }

    private void triggerRepositoryAction() {
        if (determineSOAPAction()) {
            if ("urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b".equals(action)) {
                processProviderAndRegisterAction();
            } else {
                messageBuffer = originalRequest.getBody();
                forwardRequestToRepository();
            }
        }
    }

    private void forwardRequestToRepository() {
        log.info("Forwarding request to repository");
        ActorSelection httpConnector = getContext().actorSelection(config.userPathFor("http-connector"));

        // Copy original content type
        String contentType = originalRequest.getHeaders().get("Content-Type");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);

        String scheme;
        Integer port;
        if (config.getProperty("xds.repository.secure").equals("true")) {
            scheme = "https";
            port = Integer.parseInt(config.getProperty("xds.repository.securePort"));
        } else {
            scheme = "http";
            port = Integer.parseInt(config.getProperty("xds.repository.port"));
        }

        MediatorHTTPRequest request = new MediatorHTTPRequest(
                originalRequest.getRespondTo(), getSelf(), "XDS.b Repository", "POST", scheme,
                config.getProperty("xds.repository.host"), port, config.getProperty("xds.repository.path"),
                messageBuffer, headers, null
        );
        httpConnector.tell(request, getSelf());
    }

    private void finalizeResponse(MediatorHTTPResponse response) {
        originalRequest.getRespondTo().tell(response.toFinishRequest(), getSelf());
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            originalRequest = (MediatorHTTPRequest) msg;
            xForwardedFor = ((MediatorHTTPRequest)msg).getHeaders().get("X-Forwarded-For");
            readMessage();
        } else if (msg instanceof XDSbMimeProcessorActor.XDSbMimeProcessorResponse) {
            processMtomProcessorResponse((XDSbMimeProcessorActor.XDSbMimeProcessorResponse) msg);
        } else if (msg instanceof OrchestrateProvideAndRegisterRequestResponse) {
            processProvideAndRegisterResponse((OrchestrateProvideAndRegisterRequestResponse) msg);
        } else if (msg instanceof MediatorHTTPResponse) {
            finalizeResponse((MediatorHTTPResponse) msg);
        } else {
            unhandled(msg);
        }
    }
}
