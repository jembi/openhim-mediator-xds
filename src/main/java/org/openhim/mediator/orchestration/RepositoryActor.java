package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
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

public class RepositoryActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private MediatorConfig config;

    private MediatorHTTPRequest originalRequest;
    private ActorRef mtomProcessor;
    private boolean messageIsMTOM;
    private String contentType;
    private String message;
    private SOAPWrapper soapWrapper;
    private String action;


    public RepositoryActor(MediatorConfig config) {
        this.config = config;
        mtomProcessor = getContext().actorOf(Props.create(XDSbMimeProcessorActor.class));
    }


    private void readMessage() {
        contentType = originalRequest.getHeaders().get("Content-Type");

        if (contentType!=null && contentType.contains("multipart/related")) {
            XDSbMimeProcessorActor.MimeMessage mimeMsg = new XDSbMimeProcessorActor.MimeMessage(originalRequest.getRequestHandler(), getSelf(), originalRequest.getBody(), contentType);
            mtomProcessor.tell(mimeMsg, getSelf());
            messageIsMTOM = true;
        } else {
            message = originalRequest.getBody();
            messageIsMTOM = false;
            triggerRepositoryAction();
        }
    }

    private boolean determineSOAPAction() {
        try {
            action = getSOAPActionFromHeader();
            if (action==null) {
                //not in soap header. maybe it's in the content-type?
                action = getSOAPActionFromContentType();

                if (action==null) {
                    FinishRequest fr = new FinishRequest("Could not determine SOAP Action. Is the correct WS-Adressing header set?", "text/plain", HttpStatus.SC_BAD_REQUEST);
                    originalRequest.getRespondTo().tell(fr, getSelf());
                    return false;
                }
            }
            return true;
        } catch (ParserConfigurationException | SAXException | XPathExpressionException | IOException ex) {
            originalRequest.getRequestHandler().tell(new ExceptError(ex), getSelf());
            return false;
        }
    }

    private String getSOAPActionFromHeader() throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(IOUtils.toInputStream(message));
        XPath xpath = XPathFactory.newInstance().newXPath();
        return xpath.compile("//Envelope/Header/Action").evaluate(doc);
    }

    private String getSOAPActionFromContentType() {
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
        ActorRef resolvePatientIDHandler = getContext().actorOf(Props.create(PIXRequestActor.class, config));
        ActorRef resolveProviderIDHandler = null;
        ActorRef resolveFacilityIDHandler = null;
        ActorRef pnrOrchestrator = getContext().actorOf(
                Props.create(
                        ProvideAndRegisterOrchestrationActor.class, config,
                        resolvePatientIDHandler, resolveProviderIDHandler, resolveFacilityIDHandler
                )
        );

        try {
            soapWrapper = new SOAPWrapper(message);
            OrchestrateProvideAndRegisterRequest msg = new OrchestrateProvideAndRegisterRequest(
                    originalRequest.getRequestHandler(), getSelf(), soapWrapper.getSoapBody()
            );
            pnrOrchestrator.tell(msg, getSelf());
        } catch (SOAPWrapper.SOAPParseException ex) {
            FinishRequest fr = new FinishRequest(ex.getMessage(), "text/plain", HttpStatus.SC_BAD_REQUEST);
            originalRequest.getRequestHandler().tell(fr, getSelf());
        }
    }

    private void triggerRepositoryAction() {
        if (determineSOAPAction()) {
            if ("urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b".equals(action)) {
                processProviderAndRegisterAction();
            } else {
                message = originalRequest.getBody();
                forwardRequestToRepository();
            }
        }
    }

    private void forwardRequestToRepository() {
        ActorSelection httpConnector = getContext().actorSelection("/user/" + config.getName() + "/http-connector");
        MediatorHTTPRequest request = new MediatorHTTPRequest(
                originalRequest.getRespondTo(), getSelf(), "xds-repository", "POST", "http",
                config.getProperties().getProperty("xds.repository.host"),
                Integer.parseInt(config.getProperties().getProperty("xds.repository.port")),
                config.getProperties().getProperty("xds.repository.path"),
                message, originalRequest.getHeaders(), null
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
            readMessage();
        } else if (msg instanceof XDSbMimeProcessorActor.XDSbMimeProcessorResponse) {
            if (((XDSbMimeProcessorActor.XDSbMimeProcessorResponse) msg).getOriginalRequest() instanceof XDSbMimeProcessorActor.MimeMessage) {
                message = ((XDSbMimeProcessorActor.XDSbMimeProcessorResponse) msg).getResponseObject();
                triggerRepositoryAction();
            } else if (((XDSbMimeProcessorActor.XDSbMimeProcessorResponse) msg).getOriginalRequest() instanceof XDSbMimeProcessorActor.EnrichedMessage) {
                message = ((XDSbMimeProcessorActor.XDSbMimeProcessorResponse) msg).getResponseObject();
                forwardRequestToRepository();
            } else {
                unhandled(msg);
            }
        } else if (msg instanceof OrchestrateProvideAndRegisterRequestResponse) {
            soapWrapper.setSoapBody(((OrchestrateProvideAndRegisterRequestResponse) msg).getResponseObject());
            message = soapWrapper.getFullDocument();
            forwardRequestToRepository();
        } else if (msg instanceof MediatorHTTPResponse) {
            finalizeResponse((MediatorHTTPResponse) msg);
        } else {
            unhandled(msg);
        }
    }
}
