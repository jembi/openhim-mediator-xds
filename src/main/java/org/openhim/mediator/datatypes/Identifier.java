package org.openhim.mediator.datatypes;

public class Identifier {
    private String identifier;
    private AssigningAuthority assigningAuthority;

    public Identifier(String identifier, AssigningAuthority assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
        this.identifier = identifier;
    }

    public AssigningAuthority getAssigningAuthority() {
        return assigningAuthority;
    }

    public void setAssigningAuthority(AssigningAuthority assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String toString() {
        if (assigningAuthority!=null) {
            return String.format("%s^^^%s&%s&ISO", identifier, assigningAuthority.getAssigningAuthority(), assigningAuthority.getAssigningAuthorityId());
        }
        return identifier + "^^^&&ISO";
    }
}
