/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.normalization;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SOAPWrapperTest {

    private static final String TEST_MSG_START = "<S:Envelope xmlns:S=\"http://www.w3.org/2003/05/soap-envelope\"><S:Header/><S:Body>";
    private static final String TEST_MSG_BODY = "test message";
    private static final String TEST_MSG_END = "</S:Body>";

    @Test
    public void testSOAPWrapper() throws Exception {
        SOAPWrapper wrapper = new SOAPWrapper(TEST_MSG_START + TEST_MSG_BODY + TEST_MSG_END);
        assertEquals(TEST_MSG_START, wrapper.soapBegin);
        assertEquals(TEST_MSG_BODY, wrapper.soapBody);
        assertEquals(TEST_MSG_END, wrapper.soapEnd);
        assertEquals(wrapper.getFullDocument(), TEST_MSG_START + TEST_MSG_BODY + TEST_MSG_END);
    }
}