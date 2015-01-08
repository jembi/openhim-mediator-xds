package org.openhim.mediator.denormalization;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ihe.iti.atna.AuditMessage;
import ihe.iti.atna.EventIdentificationType;
import org.openhim.mediator.ATNAUtil;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.MediatorSocketRequest;
import org.openhim.mediator.messages.ATNAAudit;

import javax.xml.bind.JAXBException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An actor for sending out audit messages to an audit repository.
 * <br/><br/>
 * Messages supported:
 * <ul>
 *     <li>ATNAAudit - fire-and-forget</li>
 * </ul>
 */
public class ATNAAuditingActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private MediatorConfig config;


    public ATNAAuditingActor(MediatorConfig config) {
        this.config = config;
    }

    protected String generateForPIXRequest(ATNAAudit audit) throws JAXBException {
        AuditMessage res = new AuditMessage();

        EventIdentificationType eid = new EventIdentificationType();
        eid.setEventID( ATNAUtil.buildCodedValueType("DCM", "110112", "Query") );
        eid.setEventActionCode("E");
        eid.setEventDateTime( ATNAUtil.newXMLGregorianCalendar() );
        eid.getEventTypeCode().add( ATNAUtil.buildCodedValueType("IHE Transactions", "ITI-9", "PIX Query") );
        eid.setEventOutcomeIndicator(audit.getParticipantIdentifiers()!=null ? BigInteger.ZERO : new BigInteger("4"));
        res.setEventIdentification(eid);

        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(
                config.getProperties().getProperty("pix.sendingFacility") + "|" + config.getProperties().getProperty("pix.sendingApplication"),
                ATNAUtil.getProcessID(), true, ATNAUtil.getHostIP(), (short)2, "DCM", "110153", "Source"));
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(
                config.getProperties().getProperty("pix.receivingFacility") + "|" + config.getProperties().getProperty("pix.receivingApplication"),
                "2100", false, config.getProperties().getProperty("pix.manager.host"), (short)1, "DCM", "110152", "Destination"));

        res.getAuditSourceIdentification().add(ATNAUtil.buildAuditSource("openhim"));

        for (Identifier id : audit.getParticipantIdentifiers()) {
            res.getParticipantObjectIdentification().add(
                    ATNAUtil.buildParticipantObjectIdentificationType(id.toCX(), (short) 1, (short) 1, "RFC-3881", "2", "PatientNumber", null)
            );
        }
        res.getParticipantObjectIdentification().add(
                ATNAUtil.buildParticipantObjectIdentificationType(
                        UUID.randomUUID().toString(), (short)2, (short)24, "IHE Transactions", "ITI-9", "PIX Query",
                        audit.getMessage(), new ATNAUtil.ParticipantObjectDetail("MSH-10", audit.getUniqueId().getBytes())
                )
        );

        return ATNAUtil.marshallATNAObject(res);
    }

    protected String generateForRegistryQueryReceived(ATNAAudit audit) throws JAXBException {
        AuditMessage res = new AuditMessage();

        EventIdentificationType eid = new EventIdentificationType();
        eid.setEventID( ATNAUtil.buildCodedValueType("DCM", "110112", "Query") );
        eid.setEventActionCode("E");
        eid.setEventDateTime( ATNAUtil.newXMLGregorianCalendar() );
        eid.getEventTypeCode().add( ATNAUtil.buildCodedValueType("IHE Transactions", "ITI-18", "Registry Stored Query") );
        eid.setEventOutcomeIndicator(audit.getOutcome() ? BigInteger.ONE : BigInteger.ZERO);
        res.setEventIdentification(eid);

        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(ATNAUtil.WSA_REPLYTO_ANON, "client", true, audit.getSourceIP(), (short)2, "DCM", "110153", "Source"));
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(ATNAUtil.WSA_REPLYTO_ANON, ATNAUtil.getProcessID(), false, ATNAUtil.getHostIP(), (short)2, "DCM", "110152", "Destination"));

        res.getAuditSourceIdentification().add(ATNAUtil.buildAuditSource("openhim"));

        for (Identifier id : audit.getParticipantIdentifiers()) {
            res.getParticipantObjectIdentification().add(
                    ATNAUtil.buildParticipantObjectIdentificationType(id.toCX(), (short) 1, (short) 1, "RFC-3881", "2", "PatientNumber", null)
            );
        }

        List<ATNAUtil.ParticipantObjectDetail> pod = new ArrayList<>();
        pod.add(new ATNAUtil.ParticipantObjectDetail("QueryEncoding", "UTF-8".getBytes()));
        if (audit.getHomeCommunityId()!=null) pod.add(new ATNAUtil.ParticipantObjectDetail("urn:ihe:iti:xca:2010:homeCommunityId", audit.getHomeCommunityId().getBytes()));

        res.getParticipantObjectIdentification().add(
                ATNAUtil.buildParticipantObjectIdentificationType(
                        audit.getUniqueId(), (short)2, (short)24, "IHE Transactions", "ITI-18", "Registry Stored Query", audit.getMessage(), pod
                )
        );

        return ATNAUtil.marshallATNAObject(res);
    }

    protected String generateForRegistryQueryResponse(ATNAAudit audit) throws JAXBException {
        AuditMessage res = new AuditMessage();

        EventIdentificationType eid = new EventIdentificationType();
        eid.setEventID( ATNAUtil.buildCodedValueType("DCM", "110112", "Query") );
        eid.setEventActionCode("E");
        eid.setEventDateTime( ATNAUtil.newXMLGregorianCalendar() );
        eid.getEventTypeCode().add( ATNAUtil.buildCodedValueType("IHE Transactions", "ITI-18", "Registry Stored Query") );
        eid.setEventOutcomeIndicator(audit.getOutcome() ? BigInteger.ONE : BigInteger.ZERO);
        res.setEventIdentification(eid);

        String xdsRegistryHost = config.getProperties().getProperty("xds.registry.host");
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(ATNAUtil.WSA_REPLYTO_ANON, ATNAUtil.getProcessID(), true, ATNAUtil.getHostIP(), (short)2, "DCM", "110153", "Source"));
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(buildRegistryPath(), xdsRegistryHost, false, xdsRegistryHost, (short)1, "DCM", "110152", "Destination"));

        res.getAuditSourceIdentification().add(ATNAUtil.buildAuditSource("openhim"));

        for (Identifier id : audit.getParticipantIdentifiers()) {
            res.getParticipantObjectIdentification().add(
                    ATNAUtil.buildParticipantObjectIdentificationType(id.toCX(), (short) 1, (short) 1, "RFC-3881", "2", "PatientNumber", null)
            );
        }

        List<ATNAUtil.ParticipantObjectDetail> pod = new ArrayList<>();
        pod.add(new ATNAUtil.ParticipantObjectDetail("QueryEncoding", "UTF-8".getBytes()));
        if (audit.getHomeCommunityId()!=null) pod.add(new ATNAUtil.ParticipantObjectDetail("urn:ihe:iti:xca:2010:homeCommunityId", audit.getHomeCommunityId().getBytes()));

        res.getParticipantObjectIdentification().add(
                ATNAUtil.buildParticipantObjectIdentificationType(
                        audit.getUniqueId(), (short)2, (short)24, "IHE Transactions", "ITI-18", "Registry Stored Query", audit.getMessage(), pod
                )
        );

        return ATNAUtil.marshallATNAObject(res);
    }

    private String buildRegistryPath() {
        return String.format(
                "%s:%s/%s", config.getProperties().getProperty("xds.registry.host"),
                ((config.getProperties().getProperty("ihe.secure").equalsIgnoreCase("true")) ?
                        config.getProperties().getProperty("xds.registry.securePort") : config.getProperties().getProperty("xds.registry.port")),
                config.getProperties().getProperty("xds.registry.path")
        );
    }

    protected String generateForPNRReceived(ATNAAudit audit) throws JAXBException {
        AuditMessage res = new AuditMessage();

        EventIdentificationType eid = new EventIdentificationType();
        eid.setEventID( ATNAUtil.buildCodedValueType("DCM", "110107", "Import") );
        eid.setEventActionCode("C");
        eid.setEventDateTime( ATNAUtil.newXMLGregorianCalendar() );
        eid.getEventTypeCode().add( ATNAUtil.buildCodedValueType("IHE Transactions", "ITI-41", "Provide and Register Document Set-b") );
        eid.setEventOutcomeIndicator(audit.getOutcome() ? BigInteger.ONE : BigInteger.ZERO);
        res.setEventIdentification(eid);

        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(ATNAUtil.WSA_REPLYTO_ANON, "client", true, audit.getSourceIP(), (short)2, "DCM", "110153", "Source"));
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(ATNAUtil.WSA_REPLYTO_ANON, ATNAUtil.getProcessID(), false, ATNAUtil.getHostIP(), (short)2, "DCM", "110152", "Destination"));

        res.getAuditSourceIdentification().add(ATNAUtil.buildAuditSource("openhim"));

        for (Identifier id : audit.getParticipantIdentifiers()) {
            res.getParticipantObjectIdentification().add(
                    ATNAUtil.buildParticipantObjectIdentificationType(id.toCX(), (short) 1, (short) 1, "RFC-3881", "2", "PatientNumber", null)
            );
        }

        List<ATNAUtil.ParticipantObjectDetail> pod = new ArrayList<>();
        pod.add(new ATNAUtil.ParticipantObjectDetail("QueryEncoding", "UTF-8".getBytes()));
        if (audit.getHomeCommunityId()!=null) pod.add(new ATNAUtil.ParticipantObjectDetail("urn:ihe:iti:xca:2010:homeCommunityId", audit.getHomeCommunityId().getBytes()));

        res.getParticipantObjectIdentification().add(
                ATNAUtil.buildParticipantObjectIdentificationType(
                        audit.getUniqueId(), (short)2, (short)20, "IHE XDS Metadata", "urn:uuid:a54d6aa5-d40d-43f9-88c5-b4633d873bdd", "submission set classificationNode", audit.getMessage(), pod
                )
        );

        return ATNAUtil.marshallATNAObject(res);
    }

    protected String generateForPNRResponse(ATNAAudit audit) throws JAXBException {
        AuditMessage res = new AuditMessage();

        EventIdentificationType eid = new EventIdentificationType();
        eid.setEventID( ATNAUtil.buildCodedValueType("DCM", "110106", "Export") );
        eid.setEventActionCode("R");
        eid.setEventDateTime( ATNAUtil.newXMLGregorianCalendar() );
        eid.getEventTypeCode().add( ATNAUtil.buildCodedValueType("IHE Transactions", "ITI-41", "Provide and Register Document Set-b") );
        eid.setEventOutcomeIndicator(audit.getOutcome() ? BigInteger.ZERO : new BigInteger("4"));
        res.setEventIdentification(eid);

        String xdsRepositoryHost = config.getProperties().getProperty("xds.repository.host");
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(ATNAUtil.WSA_REPLYTO_ANON, ATNAUtil.getProcessID(), true, ATNAUtil.getHostIP(), (short)2, "DCM", "110153", "Source"));
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(xdsRepositoryHost, false, xdsRepositoryHost, (short)1, "DCM", "110152", "Destination"));

        res.getAuditSourceIdentification().add(ATNAUtil.buildAuditSource("openhim"));

        for (Identifier id : audit.getParticipantIdentifiers()) {
            res.getParticipantObjectIdentification().add(
                    ATNAUtil.buildParticipantObjectIdentificationType(id.toCX(), (short) 1, (short) 1, "RFC-3881", "2", "PatientNumber", null)
            );
        }
        res.getParticipantObjectIdentification().add(
                ATNAUtil.buildParticipantObjectIdentificationType(
                        audit.getUniqueId(), (short)2, (short)20, "IHE XDS Metadata",
                        "urn:uuid:a54d6aa5-d40d-43f9-88c5-b4633d873bdd", "submission set classificationNode",
                        audit.getMessage()
                )
        );

        return ATNAUtil.marshallATNAObject(res);
    }

    private void sendAuditMessage(ATNAAudit audit)
            throws Exception { //Just die if something goes wrong, akka will restart

        log.info("Sending ATNA " + audit.getType() + " audit message using UDP");

        ActorSelection udpConnector = getContext().actorSelection("/user/" + config.getName() + "/udp-fire-forget-connector");
        String message = null;

        switch (audit.getType()) {
            case PIX_REQUEST:
                message = generateForPIXRequest(audit);
                break;
            case REGISTRY_QUERY_RECEIVED:
                message = generateForRegistryQueryReceived(audit);
                break;
            case REGISTRY_QUERY_RESPONSE:
                message = generateForRegistryQueryResponse(audit);
                break;
            case PROVIDE_AND_REGISTER_RECEIVED:
                message = generateForPNRReceived(audit);
                break;
            case PROVIDE_AND_REGISTER_RESPONSE:
                message = generateForPNRResponse(audit);
                break;
        }

        message = ATNAUtil.build_TCP_Msg_header() + message;
        message = message.length() + " " + message + "\r\n";

        MediatorSocketRequest request = new MediatorSocketRequest(
                ActorRef.noSender(), getSelf(), "ATNA Audit",
                config.getProperties().getProperty("atna.host"),
                Integer.parseInt(config.getProperties().getProperty("atna.udpPort")),
                message
        );

        udpConnector.tell(request, getSelf());
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ATNAAudit) {
            sendAuditMessage((ATNAAudit) msg);
        } else {
            unhandled(msg);
        }
    }
}
