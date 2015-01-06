package org.openhim.mediator.dummies;

import akka.actor.UntypedActor;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.messages.ResolvePatientIdentifier;
import org.openhim.mediator.messages.ResolvePatientIdentifierResponse;

import static org.junit.Assert.*;

/**
 * Responds with ECID1 in the domain ECID&ECID
 */
public class DummyResolvePatientIDActor extends UntypedActor {
    private ResolvePatientIdentifier expectedRequest;

    public DummyResolvePatientIDActor(ResolvePatientIdentifier expectedRequest) {
        this.expectedRequest = expectedRequest;
    }

    public DummyResolvePatientIDActor() {
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ResolvePatientIdentifier) {
            if (expectedRequest!=null) {
                assertEquals(expectedRequest, msg);
            }

            Identifier id = new Identifier("ECID1", new AssigningAuthority("ECID", "ECID"));
            ResolvePatientIdentifierResponse response = new ResolvePatientIdentifierResponse((MediatorRequestMessage) msg, id);
            getSender().tell(response, getSelf());
        } else {
            fail("Unexpected message received: " + msg.getClass());
        }
    }
}
