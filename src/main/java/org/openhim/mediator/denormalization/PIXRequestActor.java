/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.denormalization;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v25.message.ACK;
import ca.uhn.hl7v2.model.v25.message.ADT_A01;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.model.v25.message.RSP_K23;
import ca.uhn.hl7v2.model.v25.segment.MSH;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.util.Terser;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.*;
import org.openhim.mediator.messages.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Actor for processing PIX messages.
 * <br/><br/>
 * Supports identifier cross-referencing requests (QBP_Q21) and Patient Identity Feed (ADT_A04).
 * <br/><br/>
 * Messages supported:
 * <ul>
 * <li>ResolvePatientIdentifier - responds with ResolvePatientIdentifierResponse. The identifier returned will be null if the id could not be resolved.</li>
 * <li>RegisterNewPatient - responds with RegisterNewPatientResponse</li>
 * </ul>
 */
public class PIXRequestActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private MediatorConfig config;

    private Map<String, MediatorRequestMessage> originalRequests = new HashMap<>();
    private Map<String, String> controlIds = new HashMap<>();

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssZ");
    private static final SimpleDateFormat dateFormatDay = new SimpleDateFormat("yyyyMMdd");


    public PIXRequestActor(MediatorConfig config) {
        this.config = config;
    }


    public String constructQBP_Q21(String correlationId, ResolvePatientIdentifier msg) throws HL7Exception {

        QBP_Q21 qbp_q21 = new QBP_Q21();
        Terser t = new Terser(qbp_q21);

        MSH msh = (MSH) t.getSegment("MSH");
        t.set("MSH-1", "|");
        t.set("MSH-2", "^~\\&");
        t.set("MSH-3-1", config.getProperty("pix.sendingApplication"));
        t.set("MSH-4-1", config.getProperty("pix.sendingFacility"));
        t.set("MSH-5-1", config.getProperty("pix.receivingApplication"));
        t.set("MSH-6-1", config.getProperty("pix.receivingFacility"));
        msh.getDateTimeOfMessage().getTime().setValue(dateFormat.format(new Date()));
        t.set("MSH-9-1", "QBP");
        t.set("MSH-9-2", "Q23");
        t.set("MSH-9-3", "QBP_Q21");
        //MSH-10 message control id
        String _msh10 = UUID.randomUUID().toString();
        controlIds.put(correlationId, _msh10);
        t.set("MSH-10", _msh10);
        t.set("MSH-11-1", "P");
        t.set("MSH-12-1-1", "2.5");

        t.set("QPD-1-1", "IHE PIX Query");
        t.set("QPD-2", UUID.randomUUID().toString());
        t.set("QPD-3-1", msg.getIdentifier().getIdentifier());
        t.set("QPD-3-4", msg.getIdentifier().getAssigningAuthority().getAssigningAuthority());
        t.set("QPD-3-4-2", msg.getIdentifier().getAssigningAuthority().getAssigningAuthorityId());
        t.set("QPD-3-4-3", "ISO");
        t.set("QPD-3-5", "PI");

        t.set("QPD-4-4", msg.getTargetAssigningAuthority().getAssigningAuthority());
        t.set("QPD-4-4-2", msg.getTargetAssigningAuthority().getAssigningAuthorityId());
        t.set("QPD-4-4-3", "ISO");
        t.set("QPD-4-5", "PI");

        t.set("RCP-1", "I");

        Parser p = new GenericParser();
        return p.encode(qbp_q21);
    }

    public String constructADT_A04(String correlationId, RegisterNewPatient msg) throws HL7Exception {

        ADT_A01 adt_a04 = new ADT_A01();
        Terser t = new Terser(adt_a04);

        MSH msh = (MSH) t.getSegment("MSH");
        t.set("MSH-1", "|");
        t.set("MSH-2", "^~\\&");
        t.set("MSH-3-1", config.getProperty("pix.sendingApplication"));
        t.set("MSH-4-1", config.getProperty("pix.sendingFacility"));
        t.set("MSH-5-1", config.getProperty("pix.receivingApplication"));
        t.set("MSH-6-1", config.getProperty("pix.receivingFacility"));
        msh.getDateTimeOfMessage().getTime().setValue(dateFormat.format(new Date()));
        t.set("MSH-9-1", "ADT");
        t.set("MSH-9-2", "A04");
        t.set("MSH-9-3", "ADT_A01");
        //MSH-10 message control id
        String _msh10 = UUID.randomUUID().toString();
        controlIds.put(correlationId, _msh10);
        t.set("MSH-10", _msh10);
        t.set("MSH-11-1", "P");
        t.set("MSH-12-1-1", "2.5");

        t.set("EVN-2", dateFormatDay.format(new Date()));

        for (int i=0; i<msg.getPatientIdentifiers().size(); i++) {
            t.set("PID-3(" + i + ")-1", msg.getPatientIdentifiers().get(i).getIdentifier());
            t.set("PID-3(" + i + ")-4", msg.getPatientIdentifiers().get(i).getAssigningAuthority().getAssigningAuthority());
            t.set("PID-3(" + i + ")-4-2", msg.getPatientIdentifiers().get(i).getAssigningAuthority().getAssigningAuthorityId());
            t.set("PID-3(" + i + ")-4-3", "ISO");
        }
        t.set("PID-5-1", msg.getFamilyName());
        t.set("PID-5-2", msg.getGivenName());
        t.set("PID-7", msg.getBirthDate());
        t.set("PID-8", msg.getGender());
        t.set("PID-13", msg.getTelecom());
        t.set("PID-15", msg.getLanguageCommunicationCode());

        t.set("PV1-2", "O");

        Parser p = new GenericParser();
        return p.encode(adt_a04);
    }

    private void sendPIXRequest(ActorRef requestHandler, String orchestration, String correlationId, String pixRequest) {
        boolean secure = config.getProperty("pix.secure").equalsIgnoreCase("true");

        int port;
        if (secure) {
            port = Integer.parseInt(config.getProperty("pix.manager.securePort"));
        } else {
            port = Integer.parseInt(config.getProperty("pix.manager.port"));
        }

        ActorSelection connector = getContext().actorSelection(config.userPathFor("mllp-connector"));
        MediatorSocketRequest request = new MediatorSocketRequest(
                requestHandler, getSelf(), orchestration, correlationId,
                config.getProperty("pix.manager.host"), port, pixRequest, secure
        );
        connector.tell(request, getSelf());
    }

    private void sendPIXRequest(ResolvePatientIdentifier msg) {
        try {
            String correlationId = UUID.randomUUID().toString();
            String pixQuery = constructQBP_Q21(correlationId, msg);
            originalRequests.put(correlationId, msg);
            sendPIXRequest(msg.getRequestHandler(), "PIX Resolve Enterprise Identifier", correlationId, pixQuery);
        } catch (HL7Exception ex) {
            msg.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    private void sendPIXRequest(RegisterNewPatient msg) {
        try {
            String correlationId = UUID.randomUUID().toString();
            String pixRequest = constructADT_A04(correlationId, msg);
            originalRequests.put(correlationId, msg);
            sendPIXRequest(msg.getRequestHandler(), "PIX Create Patient Demographic Record", correlationId, pixRequest);
        } catch (HL7Exception ex) {
            msg.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    private Identifier parseRSP_K23(String response) throws HL7Exception {
        Parser parser = new GenericParser();
        Object parsedMsg = parser.parse(response);
        if (!(parsedMsg instanceof RSP_K23)) {
            return null;
        }

        RSP_K23 msg = (RSP_K23)parsedMsg;

        int numIds = msg.getQUERY_RESPONSE().getPID().getPid3_PatientIdentifierListReps();
        if (numIds < 1) {
            return null;
        }

        String id = msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getCx1_IDNumber().getValue();
        String assigningAuthority = msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getAssigningAuthority().getNamespaceID().getValue();
        String assigningAuthorityId = msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getAssigningAuthority().getUniversalID().getValue();

        return new Identifier(id, new AssigningAuthority(assigningAuthority, assigningAuthorityId));
    }

    private void processQBP_Q21Response(MediatorSocketResponse msg, ResolvePatientIdentifier originalRequest) {
        Identifier result = null;
        try {
            result = parseRSP_K23(msg.getBody());
            originalRequest.getRespondTo().tell(new ResolvePatientIdentifierResponse(originalRequest, result), getSelf());
        } catch (HL7Exception ex) {
            msg.getOriginalRequest().getRequestHandler().tell(new ExceptError(ex), getSelf());
        } finally {
            sendAuditMessage(ATNAAudit.TYPE.PIX_REQUEST, result, msg, result!=null);
        }
    }

    private String parseACKError(String response) throws HL7Exception {
        Parser parser = new GenericParser();
        Object parsedMsg = parser.parse(response);
        if (!(parsedMsg instanceof ACK)) {
            return null;
        }

        ACK msg = (ACK)parsedMsg;
        if (msg.getMSA()!=null && msg.getMSA().getAcknowledgmentCode()!=null &&
                "AA".equalsIgnoreCase(msg.getMSA().getAcknowledgmentCode().getValue())) {
            return null;
        }

        String err = "Failed to register new patient:\n";

        if (msg.getERR()!=null && msg.getERR().getErr3_HL7ErrorCode()!=null) {
            if (msg.getERR().getErr3_HL7ErrorCode().getCwe1_Identifier()!=null) {
                err += msg.getERR().getErr3_HL7ErrorCode().getCwe1_Identifier().getValue() + "\n";
            }
            if (msg.getERR().getErr3_HL7ErrorCode().getCwe2_Text()!=null) {
                err += msg.getERR().getErr3_HL7ErrorCode().getCwe2_Text().getValue() + "\n";
            }
        }

        return err;
    }

    private void processADT_A04Response(MediatorSocketResponse msg, RegisterNewPatient originalRequest) {
        String err = null;
        try {
            err = parseACKError(msg.getBody());
            originalRequest.getRespondTo().tell(new RegisterNewPatientResponse(originalRequest, err == null, err), getSelf());
        } catch (HL7Exception ex) {
            msg.getOriginalRequest().getRequestHandler().tell(new ExceptError(ex), getSelf());
        } finally {
            Identifier pid = originalRequest.getPatientIdentifiers().get(0);
            sendAuditMessage(ATNAAudit.TYPE.PIX_IDENTITY_FEED, pid, msg, err==null);
        }
    }

    private void processResponse(MediatorSocketResponse msg) {
        MediatorRequestMessage originalRequest = originalRequests.remove(msg.getOriginalRequest().getCorrelationId());

        if (originalRequest instanceof ResolvePatientIdentifier) {
            processQBP_Q21Response(msg, (ResolvePatientIdentifier) originalRequest);
        } else if (originalRequest instanceof RegisterNewPatient) {
            processADT_A04Response(msg, (RegisterNewPatient) originalRequest);
        }
    }

    private void sendAuditMessage(ATNAAudit.TYPE type, Identifier patientID, MediatorSocketResponse msg, boolean outcome) {
        try {
            ATNAAudit audit = new ATNAAudit(type);
            audit.setMessage(((MediatorSocketRequest) msg.getOriginalRequest()).getBody());
            audit.setParticipantIdentifiers(Collections.singletonList(patientID));
            audit.setUniqueId(controlIds.remove(msg.getOriginalRequest().getCorrelationId()));
            audit.setOutcome(outcome);

            getContext().actorSelection(config.userPathFor("atna-auditing")).tell(audit, getSelf());
        } catch (Exception ex) {
            //quiet you!
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ResolvePatientIdentifier) {
            log.info("Received request to resolve patient identifier in the '" + ((ResolvePatientIdentifier) msg).getTargetAssigningAuthority() + "' domain");
            if (log.isDebugEnabled()) {
                log.debug("Patient ID: " + ((ResolvePatientIdentifier) msg).getIdentifier());
            }
            sendPIXRequest((ResolvePatientIdentifier) msg);
        } else if (msg instanceof RegisterNewPatient) {
            log.info("Received request to register new patient demographic record");
            sendPIXRequest((RegisterNewPatient) msg);
        } else if (msg instanceof MediatorSocketResponse) {
            processResponse((MediatorSocketResponse) msg);
        } else {
            unhandled(msg);
        }
    }
}
