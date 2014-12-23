package org.openhim.mediator.messages;

import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.MediatorResponseMessage;

public class EnrichRegistryStoredQueryResponse extends MediatorResponseMessage {
    private final String enrichedMessage;

    public EnrichRegistryStoredQueryResponse(MediatorRequestMessage originalRequest, String enrichedMessage) {
        super(originalRequest);
        this.enrichedMessage = enrichedMessage;
    }

    public String getEnrichedMessage() {
        return enrichedMessage;
    }
}
