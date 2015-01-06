package org.openhim.mediator.messages;

import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.MediatorResponseMessage;

public class BaseResolveIdentifierResponse extends MediatorResponseMessage {
    private final Identifier identifier;

    public BaseResolveIdentifierResponse(MediatorRequestMessage originalRequest, Identifier identifier) {
        super(originalRequest);
        this.identifier = identifier;
    }

    public Identifier getIdentifier() {
        return identifier;
    }
}
