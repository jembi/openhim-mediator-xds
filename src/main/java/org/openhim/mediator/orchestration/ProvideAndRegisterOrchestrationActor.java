package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.denormalization.EnrichProvideAndRegisterDocumentActor;
import org.openhim.mediator.denormalization.ResolveEnterpriseIdentifierActor;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.messages.*;
import org.openhim.mediator.normalization.ParseProvideAndRegisterRequestActor;

import java.util.ArrayList;
import java.util.List;

/**
 * An orchestrator for enriching XDS.b Provide and Register Document Set requests.
 * <br/><br/>
 * Messages supported:
 * <ul>
 *     <li>EnrichPnRRequestMessage: responds with EnrichPnRRequestMessageResponse</li>
 * </ul>
 */
public class ProvideAndRegisterOrchestrationActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private static class IdentifierMapping {
        Identifier fromId;
        Identifier resolvedId;

        public IdentifierMapping(Identifier fromId) {
            this.fromId = fromId;
        }
    }

    private final MediatorConfig config;
    private final ActorRef resolvePatientIdHandler;
    private final ActorRef resolveHealthcareWorkerIdHandler;
    private final ActorRef resolveFacilityIdHandler;
    private OrchestrateProvideAndRegisterRequest originalRequest;

    private String request;
    private ParsedProvideAndRegisterRequest parsedRequest;
    private List<IdentifierMapping> enterprisePatientIds = new ArrayList<>();
    private List<IdentifierMapping> enterpriseHealthcareWorkerIds = new ArrayList<>();
    private List<IdentifierMapping> enterpriseFacilityIds = new ArrayList<>();


    public ProvideAndRegisterOrchestrationActor(MediatorConfig config, ActorRef resolvePatientIdHandler, ActorRef resolveHealthcareWorkerIdHandler, ActorRef resolveFacilityIdHandler) {
        this.config = config;
        this.resolvePatientIdHandler = resolvePatientIdHandler;
        this.resolveHealthcareWorkerIdHandler = resolveHealthcareWorkerIdHandler;
        this.resolveFacilityIdHandler = resolveFacilityIdHandler;
    }

    public ProvideAndRegisterOrchestrationActor(MediatorConfig config) {
        this.config = config;
        this.resolvePatientIdHandler = getContext().actorOf(Props.create(ResolveEnterpriseIdentifierActor.class, config));
        this.resolveHealthcareWorkerIdHandler = null;
        this.resolveFacilityIdHandler = null;
    }

    private void parseRequest(OrchestrateProvideAndRegisterRequest msg) {
        log.info("Parsing Xds.b Provide and Register request");
        request = msg.getRequestObject();
        ActorRef parseHandler = getContext().actorOf(Props.create(ParseProvideAndRegisterRequestActor.class));
        parseHandler.tell(new SimpleMediatorRequest<String>(msg.getRequestHandler(), getSelf(), request), getSelf());
    }

    private void initIdentifiersToBeResolvedMappings() {
        //TODO

    }

    private void resolveEnterpriseIdentifiers() {
        log.info("Resolving identifiers");
        resolvePatientIdentifiers();
        resolveHealthcareWorkerIdentifiers();
        resolveFacilityIdentifiers();
    }

    private void resolvePatientIdentifiers() {
        AssigningAuthority targetPatientIdAuthority = new AssigningAuthority();
        targetPatientIdAuthority.setAssigningAuthority(config.getProperties().getProperty("pix.requestedAssigningAuthority"));
        targetPatientIdAuthority.setAssigningAuthorityId(config.getProperties().getProperty("pix.requestedAssigningAuthorityId"));

        for (IdentifierMapping mapping : enterprisePatientIds) {
            ResolvePatientIdentifier msg = new ResolvePatientIdentifier(
                    originalRequest.getRequestHandler(), getSelf(), mapping.fromId.toString(), mapping.fromId, targetPatientIdAuthority
            );
            resolvePatientIdHandler.tell(msg, getSelf());
        }
    }

    private void resolveHealthcareWorkerIdentifiers() {
        AssigningAuthority targetHealthcareWorkerIdAuthority = new AssigningAuthority();
        targetHealthcareWorkerIdAuthority.setAssigningAuthority("EPID");
        targetHealthcareWorkerIdAuthority.setAssigningAuthorityId("EPID");

        for (IdentifierMapping mapping : enterpriseHealthcareWorkerIds) {
            ResolveHealthcareWorkerIdentifier msg = new ResolveHealthcareWorkerIdentifier(
                    originalRequest.getRequestHandler(), getSelf(), mapping.fromId.toString(), mapping.fromId, targetHealthcareWorkerIdAuthority
            );
            resolveHealthcareWorkerIdHandler.tell(msg, getSelf());
        }
    }

    private void resolveFacilityIdentifiers() {
        AssigningAuthority targetFacilityIdAuthority = new AssigningAuthority();
        targetFacilityIdAuthority.setAssigningAuthority("ELID");
        targetFacilityIdAuthority.setAssigningAuthorityId("ELID");

        for (IdentifierMapping mapping : enterpriseFacilityIds) {
            ResolveFacilityIdentifier msg = new ResolveFacilityIdentifier(
                    originalRequest.getRequestHandler(), getSelf(), mapping.fromId.toString(), mapping.fromId, targetFacilityIdAuthority
            );
            resolveFacilityIdHandler.tell(msg, getSelf());
        }
    }

    private void processResolvedPatientId(ResolvePatientIdentifierResponse response) {
        processResolvedId(response, enterprisePatientIds);
    }

    private void processResolvedHealthcareWorkerId(ResolveHealthcareWorkerIdentifierResponse response) {
        processResolvedId(response, enterpriseHealthcareWorkerIds);
    }

    private void processResolvedFacilityId(ResolveFacilityIdentifierResponse response) {
        processResolvedId(response, enterpriseFacilityIds);
    }

    private void processResolvedId(BaseResolveIdentifierResponse response, List<IdentifierMapping> lst) {
        for (IdentifierMapping mapping : lst) {
            if (mapping.fromId.toString().equals(response.getOriginalRequest().getCorrelationId())) {
                mapping.resolvedId = response.getIdentifier();
            }
        }

    }

    private boolean checkAndProceedWithEnrichmentIfAllResolved() {
        if (areAllIdentifiersResolved()) {
            log.info("All identifiers resolved. Proceeding with message enrichment");
            enrichProviderAndRegisterDocument();
            return true;
        }
        return false;
    }

    private boolean areAllIdentifiersResolved() {
        return areAllIdentifiersResolvedForList(enterprisePatientIds) &&
                areAllIdentifiersResolvedForList(enterpriseHealthcareWorkerIds) &&
                areAllIdentifiersResolvedForList(enterpriseFacilityIds);
    }

    private boolean areAllIdentifiersResolvedForList(List<IdentifierMapping> lst) {
        for (IdentifierMapping mapping : lst) {
            if (mapping.resolvedId == null) {
                return false;
            }
        }
        return true;
    }

    private void enrichProviderAndRegisterDocument() {
        EnrichProvideAndRegisterDocument request = new EnrichProvideAndRegisterDocument(originalRequest.getRequestHandler(), getSelf());
        ActorRef enricher = getContext().actorOf(Props.create(EnrichProvideAndRegisterDocumentActor.class));
        enricher.tell(request, getSelf());
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof OrchestrateProvideAndRegisterRequest) {
            log.info("Orchestrating XDS.b Provide and Register request");
            originalRequest = (OrchestrateProvideAndRegisterRequest) msg;
            parseRequest((OrchestrateProvideAndRegisterRequest) msg);
        } else if (msg instanceof ParsedProvideAndRegisterRequest) {
            parsedRequest = (ParsedProvideAndRegisterRequest) msg;
            initIdentifiersToBeResolvedMappings();
            if (!checkAndProceedWithEnrichmentIfAllResolved()) {
                resolveEnterpriseIdentifiers();
            }
        } else if (msg instanceof ResolvePatientIdentifierResponse) {
            processResolvedPatientId((ResolvePatientIdentifierResponse) msg);
            checkAndProceedWithEnrichmentIfAllResolved();
        } else if (msg instanceof ResolveHealthcareWorkerIdentifierResponse) {
            processResolvedHealthcareWorkerId((ResolveHealthcareWorkerIdentifierResponse) msg);
            checkAndProceedWithEnrichmentIfAllResolved();
        } else if (msg instanceof ResolveFacilityIdentifierResponse) {
            processResolvedFacilityId((ResolveFacilityIdentifierResponse) msg);
            checkAndProceedWithEnrichmentIfAllResolved();
        } else if (msg instanceof EnrichProvideAndRegisterDocumentResponse) {
            OrchestrateProvideAndRegisterRequestResponse response = new OrchestrateProvideAndRegisterRequestResponse(
                    originalRequest, ((EnrichProvideAndRegisterDocumentResponse) msg).getResponseObject()
            );
            originalRequest.getRespondTo().tell(response, getSelf());
        } else {
            unhandled(msg);
        }
    }
}
