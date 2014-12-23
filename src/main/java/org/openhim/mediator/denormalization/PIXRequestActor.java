package org.openhim.mediator.denormalization;

import akka.actor.ActorRef;
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
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import org.openhim.mediator.messages.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Messages supported:
 * <ul>
 * <li>ResolvePatientIdentifierMessage</li>
 * <li>MediatorHTTPResponse</li>
 * </ul>
 */
public class PIXRequestActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);
    private ActorRef respondTo;

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssZ");


    public String constructPIXQuery(ResolvePatientIdentifier msg) throws HL7Exception {

        QBP_Q21 qbp_q21 = new QBP_Q21();
        Terser t = new Terser(qbp_q21);

        MSH msh = (MSH) t.getSegment("MSH");
        t.set("MSH-1", "|");
        t.set("MSH-2", "^~\\&");
        //TODO config for sending/receiving apps
        t.set("MSH-3-1", "mediator-xds");
        t.set("MSH-4-1", "openhim");
        t.set("MSH-5-1", "pix");
        t.set("MSH-6-1", "pix");
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
        t.set("QPD-3-4-2", msg.getIdentifier().getAssigningAuthority());
        t.set("QPD-3-4-3", "ISO");
        t.set("QPD-3-5", "PI");

        t.set("QPD-4-4-2", msg.getTargetAssigningAuthority());
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
        String assigningAuthority = msg.getQUERY_RESPONSE().getPID().getPatientIdentifierList(0).getAssigningAuthority().getUniversalID().getValue();

        return new Identifier(id, assigningAuthority);
    }

    private void sendPIXRequest(ResolvePatientIdentifier msg) {
        try {
            String pixQuery = constructPIXQuery(msg);

            //TODO Sending via http to dummy server for now
            ActorSelection connector = getContext().actorSelection("/user/xds-mediator/http-connector");
            MediatorHTTPRequest httpRequest = new MediatorHTTPRequest(msg.getRequestHandler(), getSelf(), "PIX Query", "POST", "http", "localhost", 3005, "/dummypix", pixQuery, null, null);
            connector.tell(httpRequest, getSelf());
        } catch (HL7Exception ex) {
            msg.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    private void processResponse(MediatorHTTPResponse msg) {
        try {
            Identifier result = parseResponse(msg.getBody());
            respondTo.tell(new ResolvePatientIdentifierResponse(result), getSelf());
        } catch (HL7Exception ex) {
            msg.getOriginalRequest().getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ResolvePatientIdentifier) {
            respondTo = ((ResolvePatientIdentifier) msg).getRespondTo();
            sendPIXRequest((ResolvePatientIdentifier) msg);
        } else if (msg instanceof MediatorHTTPResponse) {
            processResponse((MediatorHTTPResponse) msg);
        } else {
            unhandled(msg);
        }
    }
}
