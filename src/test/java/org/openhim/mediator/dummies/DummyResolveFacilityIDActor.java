package org.openhim.mediator.dummies;

import akka.actor.UntypedActor;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.messages.ResolveFacilityIdentifier;
import org.openhim.mediator.messages.ResolveFacilityIdentifierResponse;

import static org.junit.Assert.*;

/**
 * Responds with ELID1 in the domain ELID&ELID
 */
public class DummyResolveFacilityIDActor extends UntypedActor {
    private ResolveFacilityIdentifier expectedRequest;

    public DummyResolveFacilityIDActor(ResolveFacilityIdentifier expectedRequest) {
        this.expectedRequest = expectedRequest;
    }

    public DummyResolveFacilityIDActor() {
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ResolveFacilityIdentifier) {
            if (expectedRequest!=null) {
                assertEquals(expectedRequest, msg);
            }

            Identifier id = new Identifier("ELID1", new AssigningAuthority("ELID", "ELID"));
            ResolveFacilityIdentifierResponse response = new ResolveFacilityIdentifierResponse((MediatorRequestMessage) msg, id);
            getSender().tell(response, getSelf());
        } else {
            fail("Unexpected message received: " + msg.getClass());
        }
    }
}
