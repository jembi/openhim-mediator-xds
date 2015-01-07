package org.openhim.mediator.datatypes;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdentifierTest {

    @Test
    public void testToCX() throws Exception {
        String xcn = "112244^^^&1.2.840.113619.6.197&ISO";
        Identifier id = new Identifier("112244", new AssigningAuthority("", "1.2.840.113619.6.197"));
        assertEquals(xcn, id.toCX());
    }

    @Test
    public void testToXCN() throws Exception {
        String xcn = "11375^^^^^^^^&1.2.840.113619.6.197&ISO";
        Identifier id = new Identifier("11375", new AssigningAuthority("", "1.2.840.113619.6.197"));
        assertEquals(xcn, id.toXCN());
    }

    @Test
    public void testToXON() throws Exception {
        String xon = "Some Hospital^^^^^&1.2.3.4.5.6.7.8.9.1789&ISO^^^^45";
        Identifier id = new Identifier("45", new AssigningAuthority("", "1.2.3.4.5.6.7.8.9.1789"));
        assertEquals(xon, id.toXON("Some Hospital"));
    }
}