package org.openhim.mediator.messages;

import org.openhim.mediator.datatypes.Identifier;

public class ResolvePatientIdentifierResponse {
    private final Identifier identifier;

    public ResolvePatientIdentifierResponse(Identifier identifier) {
        this.identifier = identifier;
    }

    public Identifier getIdentifier() {
        return identifier;
    }
}
