package org.openhim.mediator.denormalization;

import akka.actor.UntypedActor;
import org.openhim.mediator.messages.EnrichProvideAndRegisterDocument;
import org.openhim.mediator.messages.EnrichProvideAndRegisterDocumentResponse;

public class EnrichProvideAndRegisterDocumentActor extends UntypedActor {
    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof EnrichProvideAndRegisterDocument) {
            EnrichProvideAndRegisterDocumentResponse response = new EnrichProvideAndRegisterDocumentResponse(
                    (EnrichProvideAndRegisterDocument)msg, "TODO"
            );
            ((EnrichProvideAndRegisterDocument) msg).getRespondTo().tell(response, getSelf());
        } else {
            unhandled(msg);
        }
    }
}
