package org.openhim.mediator.normalization;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SOAPWrapper {
    protected String soapBegin;
    protected String soapBody;
    protected String soapEnd;

    public SOAPWrapper(String soapMessage) throws SOAPParseException {
        Pattern beginPattern = Pattern.compile("<(\\w+:)?Body>");
        Matcher beginMatcher = beginPattern.matcher(soapMessage);
        String _rest;
        if (beginMatcher.find()) {
            int i = beginMatcher.end();
            soapBegin = soapMessage.substring(0, i);
            _rest = soapMessage.substring(i);
        } else {
            throw new SOAPParseException();
        }

        Pattern endPattern = Pattern.compile("</(\\w+:)?Body>");
        Matcher endMatcher = endPattern.matcher(_rest);
        int lastI = -1;
        while (endMatcher.find()) {
            lastI = endMatcher.start();
        }
        if (lastI<0) {
            throw new SOAPParseException();
        }

        soapBody = _rest.substring(0, lastI);
        soapEnd = _rest.substring(lastI);
    }

    public String getSoapBody() {
        return soapBody;
    }

    public void setSoapBody(String soapBody) {
        this.soapBody = soapBody;
    }

    public String getFullDocument() {
        return soapBegin + soapBody + soapEnd;
    }

    public String toString() {
        return getFullDocument();
    }

    public static class SOAPParseException extends Exception {
        public SOAPParseException() {
            super("Failed to parse SOAP contents");
        }
    }
}
