package org.openhim.mediator.messages;

import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.SimpleMediatorResponse;

public class EnrichProvideAndRegisterDocumentResponse extends SimpleMediatorResponse<String> {
    public EnrichProvideAndRegisterDocumentResponse(MediatorRequestMessage originalRequest, String responseObject) {
        super(originalRequest, responseObject);
    }
}
