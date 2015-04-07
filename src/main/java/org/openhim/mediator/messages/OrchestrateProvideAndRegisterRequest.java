/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;

public class OrchestrateProvideAndRegisterRequest extends SimpleMediatorRequest<String> {
    private final String xForwardedFor; //needed for auditing
    private final String document; //the actual document contained in the request, if available (e.g. from mime)

    public OrchestrateProvideAndRegisterRequest(ActorRef requestHandler, ActorRef respondTo, String requestObject, String xForwardedFor, String document) {
        super(requestHandler, respondTo, requestObject);
        this.xForwardedFor = xForwardedFor;
        this.document = document;
    }

    public String getXForwardedFor() {
        return xForwardedFor;
    }

    public String getDocument() {
        return document;
    }
}
