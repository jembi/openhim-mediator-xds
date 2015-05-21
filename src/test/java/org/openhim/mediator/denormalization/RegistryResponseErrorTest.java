/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.denormalization;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 */
public class RegistryResponseErrorTest {
    private static final String EXPECTED =
        "------OPENHIM\n" +
        "Content-Type: application/xop+xml; charset=utf-8; type=\"application/soap+xml\"\n" +
        "\n" +
        "\n" +
        "\n" +
        "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
        "  <env:Header xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\n" +
        "    <wsa:To env:mustUnderstand=\"true\">http://www.w3.org/2005/08/addressing/anonymous</wsa:To>\n" +
        "    <wsa:Action>urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-bResponse</wsa:Action>\n" +
        "    <wsa:MessageID>urn:uuid:9876-9876-9876</wsa:MessageID>\n" +
        "    <wsa:RelatesTo>urn:uuid:1234-1234-1234</wsa:RelatesTo>\n" +
        "  </env:Header>\n" +
        "  <env:Body>\n" +
        "    <ns3:RegistryResponse\n" +
        "      xmlns:ns3=\"urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0\"\n" +
        "      xmlns:ns2=\"urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0\"\n" +
        "      xmlns:ns4=\"urn:ihe:iti:xds-b:2007\"\n" +
        "      xmlns:ns5=\"urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0\"\n" +
        "      xmlns:ns6=\"urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0\" status=\"urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure\">\n" +
        "      <ns3:RegistryErrorList highestSeverity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\">\n" +
        "        <ns3:RegistryError errorCode=\"XDSRepositoryError\" codeContext=\"This is an error!\" severity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\"/>\n" +
        "      </ns3:RegistryErrorList>\n" +
        "    </ns3:RegistryResponse>\n" +
        "  </env:Body>\n" +
        "</env:Envelope>\n" +
        "------OPENHIM--";

    private static final String EXPECTED_XML_TAGS_IN_CODECONTEXT =
        "------OPENHIM\n" +
        "Content-Type: application/xop+xml; charset=utf-8; type=\"application/soap+xml\"\n" +
        "\n" +
        "\n" +
        "\n" +
        "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
        "  <env:Header xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\n" +
        "    <wsa:To env:mustUnderstand=\"true\">http://www.w3.org/2005/08/addressing/anonymous</wsa:To>\n" +
        "    <wsa:Action>urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-bResponse</wsa:Action>\n" +
        "    <wsa:MessageID>urn:uuid:9876-9876-9876</wsa:MessageID>\n" +
        "    <wsa:RelatesTo>urn:uuid:1234-1234-1234</wsa:RelatesTo>\n" +
        "  </env:Header>\n" +
        "  <env:Body>\n" +
        "    <ns3:RegistryResponse\n" +
        "      xmlns:ns3=\"urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0\"\n" +
        "      xmlns:ns2=\"urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0\"\n" +
        "      xmlns:ns4=\"urn:ihe:iti:xds-b:2007\"\n" +
        "      xmlns:ns5=\"urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0\"\n" +
        "      xmlns:ns6=\"urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0\" status=\"urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure\">\n" +
        "      <ns3:RegistryErrorList highestSeverity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\">\n" +
        "        <ns3:RegistryError errorCode=\"XDSRepositoryError\" codeContext=\"&lt;error&gt;This is an error!&lt;/error&gt;\" severity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\"/>\n" +
        "      </ns3:RegistryErrorList>\n" +
        "    </ns3:RegistryResponse>\n" +
        "  </env:Body>\n" +
        "</env:Envelope>\n" +
        "------OPENHIM--";

    private static final String EXPECTED_MULTIPLE_ERRORS =
        "------OPENHIM\n" +
        "Content-Type: application/xop+xml; charset=utf-8; type=\"application/soap+xml\"\n" +
        "\n" +
        "\n" +
        "\n" +
        "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
        "  <env:Header xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\n" +
        "    <wsa:To env:mustUnderstand=\"true\">http://www.w3.org/2005/08/addressing/anonymous</wsa:To>\n" +
        "    <wsa:Action>urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-bResponse</wsa:Action>\n" +
        "    <wsa:MessageID>urn:uuid:9876-9876-9876</wsa:MessageID>\n" +
        "    <wsa:RelatesTo>urn:uuid:1234-1234-1234</wsa:RelatesTo>\n" +
        "  </env:Header>\n" +
        "  <env:Body>\n" +
        "    <ns3:RegistryResponse\n" +
        "      xmlns:ns3=\"urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0\"\n" +
        "      xmlns:ns2=\"urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0\"\n" +
        "      xmlns:ns4=\"urn:ihe:iti:xds-b:2007\"\n" +
        "      xmlns:ns5=\"urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0\"\n" +
        "      xmlns:ns6=\"urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0\" status=\"urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure\">\n" +
        "      <ns3:RegistryErrorList highestSeverity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\">\n" +
        "        <ns3:RegistryError errorCode=\"XDSRepositoryError\" codeContext=\"This is an error!\" severity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\"/>\n" +
        "        <ns3:RegistryError errorCode=\"XDSRepositoryError\" codeContext=\"This is another error!\" severity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\"/>\n" +
        "        <ns3:RegistryError errorCode=\"XDSUnknownPatientId\" codeContext=\"The patient is unknown!\" severity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\"/>\n" +
        "      </ns3:RegistryErrorList>\n" +
        "    </ns3:RegistryResponse>\n" +
        "  </env:Body>\n" +
        "</env:Envelope>\n" +
        "------OPENHIM--";

    @Test
    public void testToXML() throws Exception {
        RegistryResponseError error = new RegistryResponseError(RegistryResponseError.PNR_RESPONSE_ACTION, "urn:uuid:9876-9876-9876", "urn:uuid:1234-1234-1234");
        error.addRegistryError(new RegistryResponseError.RegistryError(RegistryResponseError.XDS_REPOSITORY_ERROR, "This is an error!"));
        assertEquals(EXPECTED, error.toXML());
    }

    @Test
    public void testToXML_shouldEscapeXMLTags() throws Exception {
        RegistryResponseError error = new RegistryResponseError(RegistryResponseError.PNR_RESPONSE_ACTION, "urn:uuid:9876-9876-9876", "urn:uuid:1234-1234-1234");
        error.addRegistryError(new RegistryResponseError.RegistryError(RegistryResponseError.XDS_REPOSITORY_ERROR, "<error>This is an error!</error>"));
        assertEquals(EXPECTED_XML_TAGS_IN_CODECONTEXT, error.toXML());
    }

    @Test
    public void testToXML_shouldHandleMultipleErrors() throws Exception {
        RegistryResponseError error = new RegistryResponseError(RegistryResponseError.PNR_RESPONSE_ACTION, "urn:uuid:9876-9876-9876", "urn:uuid:1234-1234-1234");
        error.addRegistryError(new RegistryResponseError.RegistryError(RegistryResponseError.XDS_REPOSITORY_ERROR, "This is an error!"));
        error.addRegistryError(new RegistryResponseError.RegistryError(RegistryResponseError.XDS_REPOSITORY_ERROR, "This is another error!"));
        error.addRegistryError(new RegistryResponseError.RegistryError(RegistryResponseError.XDS_UNKNOWN_PATIENTID, "The patient is unknown!"));
        assertEquals(EXPECTED_MULTIPLE_ERRORS, error.toXML());
    }
}