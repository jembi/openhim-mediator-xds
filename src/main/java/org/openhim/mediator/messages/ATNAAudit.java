package org.openhim.mediator.messages;

import org.openhim.mediator.datatypes.Identifier;

public class ATNAAudit {
    public static enum TYPE {
        PIX_REQUEST
    }

    private final TYPE type;
    private String message;
    private Identifier patientIdentifier;
    private String msh10;


    public ATNAAudit(TYPE type) {
        this.type = type;
    }


    public TYPE getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Identifier getPatientIdentifier() {
        return patientIdentifier;
    }

    public void setPatientIdentifier(Identifier patientIdentifier) {
        this.patientIdentifier = patientIdentifier;
    }

    public String getMsh10() {
        return msh10;
    }

    public void setMsh10(String msh10) {
        this.msh10 = msh10;
    }
}
