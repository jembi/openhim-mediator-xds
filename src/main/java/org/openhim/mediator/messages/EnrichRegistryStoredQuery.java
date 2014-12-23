package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

public class EnrichRegistryStoredQuery extends MediatorRequestMessage {
    private final String originalRequest;
    private final Identifier patientID;

    public EnrichRegistryStoredQuery(ActorRef requestHandler, ActorRef respondTo, String originalRequest, Identifier patientID) {
        super(requestHandler, respondTo, "enrich-registry-stored-query", null);
        this.originalRequest = originalRequest;
        this.patientID = patientID;
    }

    public String getOriginalRequest() {
        return originalRequest;
    }

    public Identifier getPatientID() {
        return patientID;
    }
}
