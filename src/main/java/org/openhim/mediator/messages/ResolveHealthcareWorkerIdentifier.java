package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

/**
 * Lookup a healthcare worker identifier in a target domain.
 */
public class ResolveHealthcareWorkerIdentifier extends BaseResolveIdentifier {

    public ResolveHealthcareWorkerIdentifier(ActorRef requestHandler, ActorRef respondTo, Identifier identifier, AssigningAuthority targetAssigningAuthority) {
        this(requestHandler, respondTo, null, identifier, targetAssigningAuthority);
    }

    public ResolveHealthcareWorkerIdentifier(ActorRef requestHandler, ActorRef respondTo, String correlationId, Identifier identifier, AssigningAuthority targetAssigningAuthority) {
        super(requestHandler, respondTo, "resolve-healthcare-worker-identifier", correlationId, identifier, targetAssigningAuthority);
    }
}
