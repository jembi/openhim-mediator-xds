package org.openhim.mediator.dummies;

import akka.actor.UntypedActor;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.messages.ResolvePatientIdentifier;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DummyResolveIdentifierActor extends UntypedActor {
    public static class ExpectedRequest {
        private Identifier identifier;
        private boolean seen = false;

        public ExpectedRequest(Identifier identifier) {
            this.identifier = identifier;
        }

        public boolean wasSeen() {
            return seen;
        }
    }
    private ExpectedRequest expectedRequest;
    private List<ExpectedRequest> expectedRequests;
    private Class expectedMessageClass;
    private Object response;

    public DummyResolveIdentifierActor(Class expectedMessageClass, Object response, ExpectedRequest expectedRequest) {
        this(expectedMessageClass, response);
        this.expectedRequest = expectedRequest;
    }

    public DummyResolveIdentifierActor(Class expectedMessageClass, Object response, List<ExpectedRequest> expectedRequests) {
        this(expectedMessageClass, response);
        this.expectedRequests = expectedRequests;
    }

    public DummyResolveIdentifierActor(Class expectedMessageClass, Object response) {
        this.expectedMessageClass = expectedMessageClass;
        this.response = response;
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (expectedMessageClass.isInstance(msg)) {
            if (expectedRequest!=null) {
                assertEquals(expectedRequest, msg);
                expectedRequest.seen = true;
            } else if (expectedRequests!=null) {
                for (ExpectedRequest er : expectedRequests) {
                    if (er.identifier.equals(((ResolvePatientIdentifier) msg).getIdentifier())) {
                        er.seen = true;
                    }
                }
            }

            getSender().tell(response, getSelf());
        } else {
            fail("Unexpected message received: " + msg.getClass());
        }
    }
}
