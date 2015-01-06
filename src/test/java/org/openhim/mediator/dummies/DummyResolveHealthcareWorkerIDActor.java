package org.openhim.mediator.dummies;

import akka.actor.UntypedActor;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.messages.ResolveHealthcareWorkerIdentifier;
import org.openhim.mediator.messages.ResolveHealthcareWorkerIdentifierResponse;

import static org.junit.Assert.*;

/**
 * Responds with EPID1 in the domain EPID&EPID
 */
public class DummyResolveHealthcareWorkerIDActor extends UntypedActor {
    private ResolveHealthcareWorkerIdentifier expectedRequest;

    public DummyResolveHealthcareWorkerIDActor(ResolveHealthcareWorkerIdentifier expectedRequest) {
        this.expectedRequest = expectedRequest;
    }

    public DummyResolveHealthcareWorkerIDActor() {
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ResolveHealthcareWorkerIdentifier) {
            if (expectedRequest!=null) {
                assertEquals(expectedRequest, msg);
            }

            Identifier id = new Identifier("EPID1", new AssigningAuthority("EPID", "EPID"));
            ResolveHealthcareWorkerIdentifierResponse response = new ResolveHealthcareWorkerIdentifierResponse((MediatorRequestMessage) msg, id);
            getSender().tell(response, getSelf());
        } else {
            fail("Unexpected message received: " + msg.getClass());
        }
    }
}
