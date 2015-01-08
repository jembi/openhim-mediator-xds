package org.openhim.mediator.denormalization;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ca.uhn.hl7v2.HL7Exception;
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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Messages supported:
 * <ul>
 * <li>ResolvePatientIdentifier - responds with ResolvePatientIdentifierResponse</li>
 * </ul>
 */
public class PIXRequestActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private Map<String, ResolvePatientIdentifier> originalRequests = new HashMap<>();
    private MediatorConfig config;

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssZ");

    public PIXRequestActor(MediatorConfig config) {
        this.config = config;
    }

    public String constructPIXQuery(ResolvePatientIdentifier msg) throws HL7Exception {

        QBP_Q21 qbp_q21 = new QBP_Q21();
        Terser t = new Terser(qbp_q21);

        MSH msh = (MSH) t.getSegment("MSH");
        t.set("MSH-1", "|");
        t.set("MSH-2", "^~\\&");
        t.set("MSH-3-1", config.getProperties().getProperty("pix.sendingApplication"));
        t.set("MSH-4-1", config.getProperties().getProperty("pix.sendingFacility"));
        t.set("MSH-5-1", config.getProperties().getProperty("pix.receivingApplication"));
        t.set("MSH-6-1", config.getProperties().getProperty("pix.receivingFacility"));
        msh.getDateTimeOfMessage().getTime().setValue(dateFormat.format(new Date()));
        t.set("MSH-9-1", "QBP");
        t.set("MSH-9-2", "Q23");
        t.set("MSH-9-3", "QBP_Q21");
        //MSH-10 message control id
        String _msh10 = UUID.randomUUID().toString();
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

    private Identifier parseResponse(String response) throws HL7Exception {
        Parser parser = new GenericParser();
        Object parsedMsg = parser.parse(response);
        if (!(parsedMsg instanceof RSP_K23))
            return null;

        RSP_K23 msg = (RSP_K23)parsedMsg;

        int numIds = msg.getQUERY_RESPONSE().getPID().getPid3_PatientIdentifierListReps();
        if (numIds < 1)
            return null;

        String id = msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getCx1_IDNumber().getValue();
        String assigningAuthority = msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getAssigningAuthority().getNamespaceID().getValue();
        String assigningAuthorityId = msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getAssigningAuthority().getUniversalID().getValue();

        return new Identifier(id, new AssigningAuthority(assigningAuthority, assigningAuthorityId));
    }

    private void sendPIXRequest(ResolvePatientIdentifier msg) {
        try {
            String pixQuery = constructPIXQuery(msg);
            String correlationId = UUID.randomUUID().toString();
            originalRequests.put(correlationId, msg);

            ActorSelection connector = getContext().actorSelection("/user/" + config.getName() + "/mllp-connector");
            MediatorSocketRequest request = new MediatorSocketRequest(
                    msg.getRequestHandler(), getSelf(), "PIX Resolve Enterprise Identifier", correlationId,
                    config.getProperties().getProperty("pix.manager.host"),
                    Integer.parseInt(config.getProperties().getProperty("pix.manager.port")),
                    pixQuery
            );
            connector.tell(request, getSelf());
        } catch (HL7Exception ex) {
            msg.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    private void processResponse(MediatorSocketResponse msg) {
        try {
            Identifier result = parseResponse(msg.getBody());
            ResolvePatientIdentifier originalRequest = originalRequests.remove(msg.getOriginalRequest().getCorrelationId());
            originalRequest.getRespondTo().tell(new ResolvePatientIdentifierResponse(originalRequest, result), getSelf());
        } catch (HL7Exception ex) {
            msg.getOriginalRequest().getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ResolvePatientIdentifier) {
            sendPIXRequest((ResolvePatientIdentifier) msg);
        } else if (msg instanceof MediatorSocketResponse) {
            processResponse((MediatorSocketResponse) msg);
        } else {
            unhandled(msg);
        }
    }
}
