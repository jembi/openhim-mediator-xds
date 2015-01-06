package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

public class EnrichProvideAndRegisterDocument extends MediatorRequestMessage {
    public EnrichProvideAndRegisterDocument(ActorRef requestHandler, ActorRef respondTo) {
        super(requestHandler, respondTo);
    }
}
