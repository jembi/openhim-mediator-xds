/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.SimpleMediatorResponse;

public class OrchestrateProvideAndRegisterRequestResponse extends SimpleMediatorResponse<String> {
    public OrchestrateProvideAndRegisterRequestResponse(MediatorRequestMessage originalRequest, String responseObject) {
        super(originalRequest, responseObject);
    }
}
