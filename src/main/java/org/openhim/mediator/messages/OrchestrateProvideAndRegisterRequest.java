package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;

public class OrchestrateProvideAndRegisterRequest extends SimpleMediatorRequest<String> {
    private final String xForwardedFor; //needed for auditing

    public OrchestrateProvideAndRegisterRequest(ActorRef requestHandler, ActorRef respondTo, String requestObject, String xForwardedFor) {
        super(requestHandler, respondTo, requestObject);
        this.xForwardedFor = xForwardedFor;
    }

    public String getXForwardedFor() {
        return xForwardedFor;
    }
}
