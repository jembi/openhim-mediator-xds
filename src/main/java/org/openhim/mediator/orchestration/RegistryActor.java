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
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.denormalization.PIXRequestActor;
import org.openhim.mediator.denormalization.RegistryResponseError;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.*;
import org.openhim.mediator.messages.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RegistryActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private MediatorConfig config;

    protected ActorRef resolvePatientIDActor;

    private ActorRef requestHandler;
    private String xForwardedFor;
    private String messageBuffer;
    private Identifier patientId;
    private Identifier resolvedPatientId;
    private String messageID;
    private boolean isStoredQuery;


    public RegistryActor(MediatorConfig config) {
        this.config = config;

        resolvePatientIDActor = getContext().actorOf(Props.create(PIXRequestActor.class, config), "pix-denormalization");
    }


    protected boolean isAdhocQuery(String msg) throws ParserConfigurationException, IOException, XPathExpressionException {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(IOUtils.toInputStream(msg));
            XPath xpath = XPathFactory.newInstance().newXPath();
            String pathResult = xpath.compile("//AdhocQueryRequest[1]").evaluate(doc);
            return pathResult != null && !pathResult.isEmpty();
        } catch (SAXException ex) {
            return false;
        }
    }

    private void parseMessage(MediatorHTTPRequest request) {
        try {
            requestHandler = request.getRequestHandler();
            xForwardedFor = request.getHeaders().get("X-Forwarded-For");

            //get request body
            messageBuffer = request.getBody();

            isStoredQuery = isAdhocQuery(request.getBody());
            if (isStoredQuery) {
                log.info("Parsing registry stored query request...");
                ActorSelection parseActor = getContext().actorSelection(config.userPathFor("parse-registry-stored-query"));
                parseActor.tell(new SimpleMediatorRequest<>(request.getRequestHandler(), getSelf(), messageBuffer), getSelf());
            } else {
                log.info("Forwarding request to registry...");
                forwardToRegistry();
            }
        } catch (ParserConfigurationException | IOException | XPathExpressionException ex) {
            request.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    private void lookupEnterpriseIdentifier() {
        String enterpriseIdentifierAuthority = config.getProperty("client.requestedAssigningAuthority");
        String enterpriseIdentifierAuthorityId = config.getProperty("client.requestedAssigningAuthorityId");
        AssigningAuthority authority = new AssigningAuthority(enterpriseIdentifierAuthority, enterpriseIdentifierAuthorityId);
        ResolvePatientIdentifier msg = new ResolvePatientIdentifier(requestHandler, getSelf(), patientId, authority);
        resolvePatientIDActor.tell(msg, getSelf());
    }

    private void enrichEnterpriseIdentifier(ResolvePatientIdentifierResponse msg) {
        resolvedPatientId = msg.getIdentifier();

        if (resolvedPatientId != null) {
            log.info("Resolved patient enterprise identifier. Enriching message...");
            ActorSelection enrichActor = getContext().actorSelection(config.userPathFor("enrich-registry-stored-query"));
            EnrichRegistryStoredQuery enrichMsg = new EnrichRegistryStoredQuery(requestHandler, getSelf(), messageBuffer, resolvedPatientId);
            enrichActor.tell(enrichMsg, getSelf());
        } else {
            String err = "Could not resolve patient identifier " + patientId;
            log.error(err);

            RegistryResponseError registryResponseError = new RegistryResponseError(RegistryResponseError.STORED_QUERY_RESPONSE_ACTION, messageID);
            registryResponseError.addRegistryError(new RegistryResponseError.RegistryError(RegistryResponseError.XDS_UNKNOWN_PATIENTID, err));
            requestHandler.tell(registryResponseError.toFinishRequest(), getSelf());
        }
    }

    private boolean isAdhocQuerySuccessful(MediatorHTTPResponse response) {
        return response.getStatusCode()>200 && response.getStatusCode()<=299 &&
                response.getBody().contains("status=\"urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Success\"");
    }

    private void forwardToRegistry() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/soap+xml");

        String scheme;
        Integer port;
        if (config.getProperty("xds.registry.secure").equals("true")) {
            scheme = "https";
            port = Integer.parseInt(config.getProperty("xds.registry.securePort"));
        } else {
            scheme = "http";
            port = Integer.parseInt(config.getProperty("xds.registry.port"));
        }

        MediatorHTTPRequest request = new MediatorHTTPRequest(
                requestHandler, getSelf(), "XDS.b Registry", "POST", scheme,
                config.getProperty("xds.registry.host"), port, config.getProperty("xds.registry.path"),
                messageBuffer, headers, Collections.<String, String>emptyMap()
        );

        ActorSelection httpConnector = getContext().actorSelection(config.userPathFor("http-connector"));
        httpConnector.tell(request, getSelf());
    }

    private void finalizeResponse(MediatorHTTPResponse response) {
        requestHandler.tell(response.toFinishRequest(), getSelf());
    }

    private void sendAuditMessage(ATNAAudit.TYPE type, boolean outcome) {
        try {
            ATNAAudit audit = new ATNAAudit(type);
            audit.setMessage(messageBuffer);

            audit.setParticipantIdentifiers(Collections.singletonList(patientId));
            audit.setUniqueId("NotParsed");
            audit.setOutcome(outcome);
            audit.setSourceIP(xForwardedFor);

            getContext().actorSelection(config.userPathFor("atna-auditing")).tell(audit, getSelf());
        } catch (Exception ex) {
            //quiet you!
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) { //parse request
            log.info("Parsing registry request...");
            parseMessage((MediatorHTTPRequest) msg);

        } else if (msg instanceof ParsedRegistryStoredQuery) { //resolve patient id
            log.info("Parsed contents. Resolving patient enterprise identifier...");

            messageID = ((ParsedRegistryStoredQuery) msg).getMessageId();
            patientId = ((ParsedRegistryStoredQuery) msg).getPatientId();
            lookupEnterpriseIdentifier();

            sendAuditMessage(ATNAAudit.TYPE.REGISTRY_QUERY_RECEIVED, true); //audit

        } else if (msg instanceof ResolvePatientIdentifierResponse) { //enrich message
            enrichEnterpriseIdentifier((ResolvePatientIdentifierResponse) msg);

        } else if (msg instanceof EnrichRegistryStoredQueryResponse) { //forward to registry
            log.info("Sending enriched request to XDS.b Registry");
            messageBuffer = ((EnrichRegistryStoredQueryResponse) msg).getEnrichedMessage();
            forwardToRegistry();

        } else if (msg instanceof MediatorHTTPResponse) { //respond
            log.info("Received response from XDS.b Registry");
            finalizeResponse((MediatorHTTPResponse) msg);
            if (isStoredQuery) {
                boolean outcome = isAdhocQuerySuccessful((MediatorHTTPResponse) msg);
                sendAuditMessage(ATNAAudit.TYPE.REGISTRY_QUERY_ENRICHED, outcome); //audit
            }

        } else {
            unhandled(msg);
        }
    }
}
