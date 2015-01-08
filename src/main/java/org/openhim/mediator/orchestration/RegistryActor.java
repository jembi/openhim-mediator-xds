package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.http.HttpStatus;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.denormalization.PIXRequestActor;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.messages.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RegistryActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private MediatorConfig config;

    private ActorRef requestHandler;
    private ActorRef respondTo;
    private String xForwardedFor;
    private String messageBuffer;
    private Identifier patientIdBuffer;


    public RegistryActor(MediatorConfig config) {
        this.config = config;
    }


    private void parseMessage(MediatorHTTPRequest request) {
        requestHandler = request.getRequestHandler();
        respondTo = request.getRespondTo();
        xForwardedFor = request.getHeaders().get("X-Forwarded-For");

        //get request body
        messageBuffer = request.getBody();

        //parse message...
        ActorSelection parseActor = getContext().actorSelection("/user/" + config.getName() + "/parse-registry-stored-query");
        parseActor.tell(new SimpleMediatorRequest<String>(request.getRequestHandler(), getSelf(), messageBuffer), getSelf());
    }

    private void lookupEnterpriseIdentifier() {
        ActorRef resolvePatientIDActor = getContext().actorOf(Props.create(PIXRequestActor.class, config), "pix-denormalization");
        String enterpriseIdentifierAuthority = config.getProperties().getProperty("pix.requestedAssigningAuthority");
        String enterpriseIdentifierAuthorityId = config.getProperties().getProperty("pix.requestedAssigningAuthorityId");
        AssigningAuthority authority = new AssigningAuthority(enterpriseIdentifierAuthority, enterpriseIdentifierAuthorityId);
        ResolvePatientIdentifier msg = new ResolvePatientIdentifier(requestHandler, getSelf(), patientIdBuffer, authority);
        resolvePatientIDActor.tell(msg, getSelf());
    }

    private void enrichEnterpriseIdentifier(ResolvePatientIdentifierResponse msg) {
        patientIdBuffer = msg.getIdentifier();

        if (patientIdBuffer !=null) {
            log.info("Resolved patient enterprise identifier. Enriching message...");
            ActorSelection enrichActor = getContext().actorSelection("/user/" + config.getName() + "/enrich-registry-stored-query");
            EnrichRegistryStoredQuery enrichMsg = new EnrichRegistryStoredQuery(requestHandler, getSelf(), messageBuffer, patientIdBuffer);
            enrichActor.tell(enrichMsg, getSelf());
        } else {
            log.info("Could not resolve patient identifier");
            FinishRequest response = new FinishRequest("Unknown patient identifier", "text/plain", HttpStatus.SC_NOT_FOUND);
            respondTo.tell(response, getSelf());
        }
    }

    private void forwardEnrichedMessage(EnrichRegistryStoredQueryResponse msg) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/soap+xml");

        messageBuffer = msg.getEnrichedMessage();

        MediatorHTTPRequest request = new MediatorHTTPRequest(
                requestHandler, getSelf(), "XDS.b Registry", "POST", "http",
                config.getProperties().getProperty("xds.registry.host"),
                Integer.parseInt(config.getProperties().getProperty("xds.registry.port")),
                config.getProperties().getProperty("xds.registry.path"),
                messageBuffer,
                headers,
                Collections.<String, String>emptyMap()
        );

        ActorSelection httpConnector = getContext().actorSelection("/user/" + config.getName() + "/http-connector");
        httpConnector.tell(request, getSelf());
    }

    private void finalizeResponse(MediatorHTTPResponse response) {
        respondTo.tell(response.toFinishRequest(), getSelf());
    }

    private void sendAuditMessage(ATNAAudit.TYPE type) {
        try {
            ATNAAudit audit = new ATNAAudit(type);
            audit.setMessage(messageBuffer);

            audit.setParticipantIdentifiers(Collections.singletonList(patientIdBuffer));
            audit.setUniqueId("NotParsed");
            //TODO failed outcome
            audit.setOutcome(true);
            audit.setSourceIP(xForwardedFor);

            getContext().actorSelection("/user/" + config.getName() + "/atna-auditing").tell(audit, getSelf());
        } catch (Exception ex) {
            //quiet you!
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) { //parse request
            log.info("Parsing registry stored query request...");
            parseMessage((MediatorHTTPRequest) msg);

        } else if (msg instanceof ParsedRegistryStoredQuery) { //resolve patient id
            log.info("Parsed contents. Resolving patient enterprise identifier...");
            patientIdBuffer = ((ParsedRegistryStoredQuery) msg).getPatientId();
            lookupEnterpriseIdentifier();

            sendAuditMessage(ATNAAudit.TYPE.REGISTRY_QUERY_RECEIVED); //audit

        } else if (msg instanceof ResolvePatientIdentifierResponse) { //enrich message
            enrichEnterpriseIdentifier((ResolvePatientIdentifierResponse) msg);

        } else if (msg instanceof EnrichRegistryStoredQueryResponse) { //forward to registry
            log.info("Sending enriched request to XDS.b Registry");
            forwardEnrichedMessage((EnrichRegistryStoredQueryResponse) msg);

        } else if (msg instanceof MediatorHTTPResponse) { //respond
            log.info("Received response from XDS.b Registry");
            finalizeResponse((MediatorHTTPResponse) msg);
            sendAuditMessage(ATNAAudit.TYPE.REGISTRY_QUERY_RESPONSE); //audit

        } else {
            unhandled(msg);
        }
    }
}
