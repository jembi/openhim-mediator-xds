/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

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
