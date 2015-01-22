/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.denormalization;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.dispatch.OnComplete;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ihe.iti.atna.AuditMessage;
import ihe.iti.atna.EventIdentificationType;
import org.apache.commons.io.IOUtils;
import org.openhim.mediator.ATNAUtil;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.MediatorSocketRequest;
import org.openhim.mediator.messages.ATNAAudit;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import javax.net.ssl.SSLSocketFactory;
import javax.xml.bind.JAXBException;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static akka.dispatch.Futures.future;

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
                config.getProperty("pix.sendingFacility") + "|" + config.getProperty("pix.sendingApplication"),
                ATNAUtil.getProcessID(), true, ATNAUtil.getHostIP(), (short)2, "DCM", "110153", "Source"));
        res.getActiveParticipant().add( ATNAUtil.buildActiveParticipant(
                config.getProperty("pix.receivingFacility") + "|" + config.getProperty("pix.receivingApplication"),
                "2100", false, config.getProperty("pix.manager.host"), (short)1, "DCM", "110152", "Destination"));

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

        String xdsRegistryHost = config.getProperty("xds.registry.host");
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
                "%s:%s/%s", config.getProperty("xds.registry.host"),
                ((config.getProperty("xds.registry.secure").equalsIgnoreCase("true")) ?
                        config.getProperty("xds.registry.securePort") : config.getProperty("xds.registry.port")),
                config.getProperty("xds.registry.path")
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

        String xdsRepositoryHost = config.getProperty("xds.repository.host");
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


    private void sendUsingUDP(MediatorSocketRequest request) {
        ActorSelection udpConnector = getContext().actorSelection(config.userPathFor("udp-fire-forget-connector"));
        udpConnector.tell(request, getSelf());
    }

    private Socket getSocket(final MediatorSocketRequest req) throws IOException {
        if (req.isSecure()) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            return factory.createSocket(req.getHost(), req.getPort());
        } else {
            return new Socket(req.getHost(), req.getPort());
        }
    }

    private void sendUsingTCP(final MediatorSocketRequest request) throws IOException {
        final Socket socket = getSocket(request);

        ExecutionContext ec = getContext().dispatcher();
        Future<Boolean> f = future(new Callable<Boolean>() {
            public Boolean call() throws IOException {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                out.writeBytes(request.getBody());
                return Boolean.TRUE;
            }
        }, ec);
        f.onComplete(new OnComplete<Boolean>() {
            @Override
            public void onComplete(Throwable ex, Boolean result) throws Throwable {
                IOUtils.closeQuietly(socket);

                if (ex!=null) {
                    log.error(ex, "Exception during TCP send");
                }
            }
        }, ec);
    }

    private String generateMesage(ATNAAudit audit) throws JAXBException {
        switch (audit.getType()) {
            case PIX_REQUEST:
                return generateForPIXRequest(audit);
            case REGISTRY_QUERY_RECEIVED:
                return generateForRegistryQueryReceived(audit);
            case REGISTRY_QUERY_ENRICHED:
                return generateForRegistryQueryResponse(audit);
            case PROVIDE_AND_REGISTER_RECEIVED:
                return generateForPNRReceived(audit);
            case PROVIDE_AND_REGISTER_ENRICHED:
                return generateForPNRResponse(audit);
        }

        //shouldn't happen as we cover all the enum cases
        return "";
    }

    private void sendAuditMessage(ATNAAudit audit)
            throws Exception { //Just die if something goes wrong, akka will restart

        String message = generateMesage(audit);

        message = ATNAUtil.build_TCP_Msg_header() + message;
        message = message.length() + " " + message + "\r\n";
        boolean useTCP;
        int port;

        if (config.getProperty("atna.useTcp").equalsIgnoreCase("true")) {
            port = Integer.parseInt(config.getProperty("atna.tcpPort"));
            useTCP = true;
        } else {
            port = Integer.parseInt(config.getProperty("atna.udpPort"));
            useTCP = false;
        }

        MediatorSocketRequest request = new MediatorSocketRequest(
                ActorRef.noSender(),
                getSelf(),
                "ATNA Audit",
                null,
                config.getProperty("atna.host"),
                port,
                message,
                config.getProperty("atna.secure").equalsIgnoreCase("true")
        );

        if (useTCP) {
            log.info("Sending ATNA " + audit.getType() + " audit message using TCP");
            sendUsingTCP(request);
        } else {
            log.info("Sending ATNA " + audit.getType() + " audit message using UDP");
            sendUsingUDP(request);
        }
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
