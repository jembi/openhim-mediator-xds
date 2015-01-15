/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import org.openhim.mediator.datatypes.Identifier;

public class ParsedRegistryStoredQuery {
    private Identifier patientId;

    public ParsedRegistryStoredQuery(Identifier patientId) {
        this.patientId = patientId;
    }

    public Identifier getPatientId() {
        return patientId;
    }
}
