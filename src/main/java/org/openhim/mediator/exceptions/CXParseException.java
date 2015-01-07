package org.openhim.mediator.exceptions;

public class CXParseException extends ValidationException {
    public CXParseException(Throwable cause) {
        super(cause);
    }

    public CXParseException() {
    }

    public CXParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public CXParseException(String message) {
        super(message);
    }
}
