/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.datatypes;

import org.openhim.mediator.exceptions.CXParseException;

public class Identifier {
    private String identifier;
    private AssigningAuthority assigningAuthority;

    public Identifier(String identifier, AssigningAuthority assigningAuthority) {
        this.assigningAuthority = assigningAuthority;
        this.identifier = identifier;
    }

    public Identifier(String CX) throws CXParseException {
        try {
            identifier = CX.substring(0, CX.indexOf('^'));
            String _assigningAuthority = CX.substring(CX.lastIndexOf('^') + 1, CX.indexOf('&'));
            String _assigningAuthorityId = CX.substring(CX.indexOf('&') + 1, CX.lastIndexOf('&'));
            assigningAuthority = new AssigningAuthority(_assigningAuthority, _assigningAuthorityId);
        } catch (ArrayIndexOutOfBoundsException | StringIndexOutOfBoundsException ex) {
            throw new CXParseException("Failed to parse CX string: " + CX, ex);
        }
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
        return toCX();
    }

    public String toCX() {
        if (assigningAuthority!=null) {
            String auth = assigningAuthority.getAssigningAuthority()!=null ? assigningAuthority.getAssigningAuthority() : "";
            String authId = assigningAuthority.getAssigningAuthorityId()!=null ? assigningAuthority.getAssigningAuthorityId() : "";
            return String.format("%s^^^%s&%s&ISO", identifier, auth, authId);
        }
        return identifier + "^^^&&ISO";
    }

    public String toXCN() {
        String authId = assigningAuthority!=null && assigningAuthority.getAssigningAuthorityId()!=null ?
                assigningAuthority.getAssigningAuthorityId() :
                "";
        return identifier + "^^^^^^^^&" + authId + "&ISO";
    }

    public String toXON(String organisationName) {
        String authId = assigningAuthority!=null && assigningAuthority.getAssigningAuthorityId()!=null ?
                assigningAuthority.getAssigningAuthorityId() :
                "";
        return organisationName + "^^^^^&" + authId + "&ISO" + "^^^^" + identifier;
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
