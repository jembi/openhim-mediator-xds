/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.datatypes;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdentifierTest {

    @Test
    public void testParseCX() throws Exception {
        String cx = "112244^^^&1.2.840.113619.6.197&ISO";
        Identifier id = new Identifier(cx);
        assertEquals("112244", id.getIdentifier());
        assertNotNull(id.getAssigningAuthority());
        assertEquals("1.2.840.113619.6.197", id.getAssigningAuthority().getAssigningAuthorityId());
        assertEquals("ISO", id.getAssigningAuthority().getAssigningAuthorityIdType());
    }

    @Test
    public void testParseCX_withTypeCode() throws Exception {
        String cx = "1234567^^^ZAF^NI";
        Identifier id = new Identifier(cx);
        assertEquals("1234567", id.getIdentifier());
        assertNotNull(id.getAssigningAuthority());
        assertEquals("ZAF", id.getAssigningAuthority().getAssigningAuthority());
        assertEquals("NI", id.getTypeCode());
    }

    @Test
    public void testToCX() throws Exception {
        String cx = "112244^^^&1.2.840.113619.6.197&ISO";
        Identifier id = new Identifier("112244", new AssigningAuthority("", "1.2.840.113619.6.197", "ISO"));
        assertEquals(cx, id.toCX());
    }

    @Test
    public void testToCX_withTypeCode() throws Exception {
        String cx = "1234567^^^ZAF^NI";
        Identifier id = new Identifier("1234567", new AssigningAuthority("ZAF", ""), "NI");
        assertEquals(cx, id.toCX());
    }

    @Test
    public void testToCX_noAssigningAuthority() throws Exception {
        String cx = "1234567";
        Identifier id = new Identifier("1234567", null);
        assertEquals(cx, id.toCX());
    }

    @Test
    public void testToXCN() throws Exception {
        String xcn = "11375^^^^^^^^&1.2.840.113619.6.197&ISO";
        Identifier id = new Identifier("11375", new AssigningAuthority("", "1.2.840.113619.6.197", "ISO"));
        assertEquals(xcn, id.toXCN());
    }

    @Test
    public void testToXON() throws Exception {
        String xon = "Some Hospital^^^^^&1.2.3.4.5.6.7.8.9.1789&ISO^^^^45";
        Identifier id = new Identifier("45", new AssigningAuthority("", "1.2.3.4.5.6.7.8.9.1789", "ISO"));
        assertEquals(xon, id.toXON("Some Hospital"));
    }
}