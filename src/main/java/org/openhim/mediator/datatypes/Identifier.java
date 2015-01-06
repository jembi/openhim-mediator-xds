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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Identifier that = (Identifier) o;

        if (assigningAuthority != null ? !assigningAuthority.equals(that.assigningAuthority) : that.assigningAuthority != null)
            return false;
        if (identifier != null ? !identifier.equals(that.identifier) : that.identifier != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = identifier != null ? identifier.hashCode() : 0;
        result = 31 * result + (assigningAuthority != null ? assigningAuthority.hashCode() : 0);
        return result;
    }
}
