package org.openhim.mediator.messages;

import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

public class ResolveHealthcareWorkerIdentifierResponse extends BaseResolveIdentifierResponse {
    public ResolveHealthcareWorkerIdentifierResponse(MediatorRequestMessage originalRequest, Identifier identifier) {
        super(originalRequest, identifier);
    }
}
