package org.openhim.mediator.messages;

import org.openhim.mediator.datatypes.Identifier;

public class ParsedRegistryStoredQuery {
    private Identifier patientId;

    public ParsedRegistryStoredQuery(Identifier patientId) {
        this.patientId = patientId;
    }

    public Identifier getPatientId() {
        return patientId;
    }
}
