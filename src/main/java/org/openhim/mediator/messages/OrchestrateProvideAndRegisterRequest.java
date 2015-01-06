package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;

public class OrchestrateProvideAndRegisterRequest extends SimpleMediatorRequest<String> {
    public OrchestrateProvideAndRegisterRequest(ActorRef requestHandler, ActorRef respondTo, String requestObject) {
        super(requestHandler, respondTo, requestObject);
    }
}
