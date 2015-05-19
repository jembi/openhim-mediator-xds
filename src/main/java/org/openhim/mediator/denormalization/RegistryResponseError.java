/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.denormalization;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.messages.FinishRequest;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class RegistryResponseError {
    public static final String PNR_RESPONSE_ACTION = "urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-bResponse";

    public static final String XDS_REPOSITORY_ERROR = "XDSRepositoryError";
    public static final String XDS_UNKNOWN_PATIENTID = "XDSUnknownPatientId";
    public static final String XDS_REPOSITORY_METADATA_ERROR = "XDSRepositoryMetadataError";


    private static final String TEMPLATE =
        "------OPENHIM\n" +
        "Content-Type: application/xop+xml; charset=utf-8; type=\"application/soap+xml\"\n" +
        "\n" +
        "\n" +
        "\n" +
        "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">\n" +
        "  <env:Header xmlns:wsa=\"http://www.w3.org/2005/08/addressing\">\n" +
        "    <wsa:To env:mustUnderstand=\"true\">http://www.w3.org/2005/08/addressing/anonymous</wsa:To>\n" +
        "    <wsa:Action>%s</wsa:Action>\n" +
        "    <wsa:MessageID>%s</wsa:MessageID>\n" +
        "    <wsa:RelatesTo>%s</wsa:RelatesTo>\n" +
        "  </env:Header>\n" +
        "  <env:Body>\n" +
        "    <ns3:RegistryResponse\n" +
        "      xmlns:ns3=\"urn:oasis:names:tc:ebxml-regrep:xsd:rs:3.0\"\n" +
        "      xmlns:ns2=\"urn:oasis:names:tc:ebxml-regrep:xsd:rim:3.0\"\n" +
        "      xmlns:ns4=\"urn:ihe:iti:xds-b:2007\"\n" +
        "      xmlns:ns5=\"urn:oasis:names:tc:ebxml-regrep:xsd:lcm:3.0\"\n" +
        "      xmlns:ns6=\"urn:oasis:names:tc:ebxml-regrep:xsd:query:3.0\" status=\"urn:oasis:names:tc:ebxml-regrep:ResponseStatusType:Failure\">\n" +
        "      <ns3:RegistryErrorList highestSeverity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\">\n" +
        "%s" +
        "      </ns3:RegistryErrorList>\n" +
        "    </ns3:RegistryResponse>\n" +
        "  </env:Body>\n" +
        "</env:Envelope>\n" +
        "------OPENHIM--";


    public static class RegistryError {
        private static final String REGISTRY_ERROR_TEMPLATE = "<ns3:RegistryError errorCode=\"%s\" codeContext=\"%s\" severity=\"urn:oasis:names:tc:ebxml-regrep:ErrorSeverityType:Error\"/>";

        private String errorCode;
        private String codeContext;

        public RegistryError(String errorCode, String codeContext) {
            this.errorCode = errorCode;
            this.codeContext = codeContext;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getCodeContext() {
            return codeContext;
        }

        protected String toXML() {
            return String.format(REGISTRY_ERROR_TEMPLATE, StringEscapeUtils.escapeXml10(errorCode), StringEscapeUtils.escapeXml10(codeContext));
        }
    }

    private String action;
    private String messageID;
    private String relatesTo;
    private List<RegistryError> errorList = new LinkedList<>();

    public RegistryResponseError(String action, String messageID, String relatesTo) {
        this.action = action;
        this.messageID = messageID;
        this.relatesTo = relatesTo;
    }

    public RegistryResponseError(String action, String relatesTo) {
        this(action, "urn:uuid:" + UUID.randomUUID().toString(), relatesTo);
    }

    public void addRegistryError(RegistryError error) {
        errorList.add(error);
    }

    public void addRegistryErrors(List<RegistryError> errors) {
        errorList.addAll(errors);
    }

    protected String toXML() {
        StringBuilder errorListXML = new StringBuilder();

        for (RegistryError error : errorList) {
            errorListXML.append("        " + error.toXML() + "\n");
        }

        return String.format(TEMPLATE, action, messageID, relatesTo, errorListXML.toString());
    }

    public FinishRequest toFinishRequest() {
        String contentType = "Multipart/Related; start-info=\"application/soap+xml\"; type=\"application/xop+xml\"; boundary=\"------OPENHIM\";charset=UTF-8";
        return new FinishRequest(toXML(), contentType, HttpStatus.SC_OK);
    }
}
