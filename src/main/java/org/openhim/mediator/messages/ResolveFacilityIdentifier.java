package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;

/**
 * Lookup a facility identifier in a target domain.
 */
public class ResolveFacilityIdentifier extends BaseResolveIdentifier {
    public ResolveFacilityIdentifier(ActorRef requestHandler, ActorRef respondTo, Identifier identifier, AssigningAuthority targetAssigningAuthority) {
        this(requestHandler, respondTo, null, identifier, targetAssigningAuthority);
    }

    public ResolveFacilityIdentifier(ActorRef requestHandler, ActorRef respondTo, String correlationId, Identifier identifier, AssigningAuthority targetAssigningAuthority) {
        super(requestHandler, respondTo, "resolve-facility-identifier", correlationId, identifier, targetAssigningAuthority);
    }
}
