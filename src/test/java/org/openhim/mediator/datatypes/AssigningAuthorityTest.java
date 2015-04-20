/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.datatypes;

import org.junit.Test;

import static org.junit.Assert.*;

public class AssigningAuthorityTest {

    @Test
    public void testToHL7() throws Exception {
        assertEquals("NID", new AssigningAuthority("NID").toHL7());
        assertEquals("NID&1.2.3.4", new AssigningAuthority("NID", "1.2.3.4").toHL7());
        assertEquals("NID&1.2.3.4&ISO", new AssigningAuthority("NID", "1.2.3.4", "ISO").toHL7());
        assertEquals("&1.2.3.4", new AssigningAuthority(null, "1.2.3.4").toHL7());
        assertEquals("&1.2.3.4&ISO", new AssigningAuthority(null, "1.2.3.4", "ISO").toHL7());
        assertEquals("&&ISO", new AssigningAuthority(null, null, "ISO").toHL7());
    }
}