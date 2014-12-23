package org.openhim.mediator.orchestration;

import akka.actor.UntypedActor;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;

public class RepositoryActor extends UntypedActor {

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof MediatorHTTPRequest) {
            FinishRequest response = new FinishRequest("I don't exist yet!", "text/plain", HttpStatus.SC_OK);
            ((MediatorHTTPRequest) msg).getRequestHandler().tell(response, getSelf());
        } else {
            unhandled(msg);
        }
    }
}
