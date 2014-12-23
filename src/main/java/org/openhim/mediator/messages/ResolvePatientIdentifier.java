package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

/**
 * Lookup a patient identifier in a target domain.
 */
public class ResolvePatientIdentifier extends MediatorRequestMessage {
    private final Identifier identifier;
    private final String targetAssigningAuthority;

    public ResolvePatientIdentifier(ActorRef requestHandler, ActorRef respondTo, Identifier identifier, String targetAssigningAuthority) {
        super(requestHandler, respondTo, "resolve-patient-identifier", null);
        this.identifier = identifier;
        this.targetAssigningAuthority = targetAssigningAuthority;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public String getTargetAssigningAuthority() {
        return targetAssigningAuthority;
    }
}
