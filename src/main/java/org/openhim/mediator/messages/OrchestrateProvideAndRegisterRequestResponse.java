package org.openhim.mediator.messages;

import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.SimpleMediatorResponse;

public class OrchestrateProvideAndRegisterRequestResponse extends SimpleMediatorResponse<String> {
    public OrchestrateProvideAndRegisterRequestResponse(MediatorRequestMessage originalRequest, String responseObject) {
        super(originalRequest, responseObject);
    }
}
