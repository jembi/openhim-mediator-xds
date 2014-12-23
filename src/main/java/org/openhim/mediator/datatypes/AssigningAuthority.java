package org.openhim.mediator.datatypes;

public class AssigningAuthority {
    private String assigningAuthority;
    private String assigningAuthorityId;

    public AssigningAuthority() {
    }

    public AssigningAuthority(String assigningAuthority, String assigningAuthorityId) {
        this.assigningAuthority = assigningAuthority;
        this.assigningAuthorityId = assigningAuthorityId;
    }

    public String getAssigningAuthority() {
        return assigningAuthority;
    }

    public void setAssigningAuthority(String assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
    }

    public String getAssigningAuthorityId() {
        return assigningAuthorityId;
    }

    public void setAssigningAuthorityId(String assigningAuthorityId) {
        this.assigningAuthorityId = assigningAuthorityId;
    }
}
