/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

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
