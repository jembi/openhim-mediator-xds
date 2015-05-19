/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;

/**
 * Orchestrate an XDS.b Provider and Register request
 */
public class OrchestrateProvideAndRegisterRequest extends SimpleMediatorRequest<String> {
    private final String xForwardedFor; //needed for auditing
    private final String messageID; //message id from the SOAP header

    //the actual document contained in the request, if available
    //the mime handler will place it here, however if not available
    //it will likely need to be retrieved from the XDS.b contents (document element)
    private final String document;

    public OrchestrateProvideAndRegisterRequest(ActorRef requestHandler, ActorRef respondTo, String requestObject, String xForwardedFor, String document, String messageID) {
        super(requestHandler, respondTo, requestObject);
        this.xForwardedFor = xForwardedFor;
        this.document = document;
        this.messageID = messageID;
    }

    public String getXForwardedFor() {
        return xForwardedFor;
    }

    public String getDocument() {
        return document;
    }

    public String getMessageID() {
        return messageID;
    }
}
