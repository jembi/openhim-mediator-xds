/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.MediatorResponseMessage;

/**
 */
public class RegisterNewPatientResponse extends MediatorResponseMessage {
    private Boolean successful;
    private String err;

    public RegisterNewPatientResponse(MediatorRequestMessage originalRequest, Boolean successful, String err) {
        super(originalRequest);
        this.successful = successful;
        this.err = err;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public String getErr() {
        return err;
    }
}
