package org.openhim.mediator.datatypes;

public class Identifier {
    private String assigningAuthority;
    private String identifier;

    public Identifier(String identifier, String assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
        this.identifier = identifier;
    }

    public String getAssigningAuthority() {
        return assigningAuthority;
    }

    public void setAssigningAuthority(String assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String toString() {
        return String.format("%s^^^&%s&ISO", identifier, assigningAuthority);
    }
}
