package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

/**
 * Lookup a patient identifier in a target domain.
 */
public class ResolvePatientIdentifier extends BaseResolveIdentifier {
    public ResolvePatientIdentifier(ActorRef requestHandler, ActorRef respondTo, Identifier identifier, AssigningAuthority targetAssigningAuthority) {
        this(requestHandler, respondTo, null, identifier, targetAssigningAuthority);
    }

    public ResolvePatientIdentifier(ActorRef requestHandler, ActorRef respondTo, String correlationId, Identifier identifier, AssigningAuthority targetAssigningAuthority) {
        super(requestHandler, respondTo, "resolve-patient-identifier", correlationId, identifier, targetAssigningAuthority);
    }
}
