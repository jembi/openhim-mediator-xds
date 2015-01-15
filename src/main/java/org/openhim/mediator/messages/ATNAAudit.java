/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import org.openhim.mediator.datatypes.Identifier;

import java.util.List;

public class ATNAAudit {
    public static enum TYPE {
        PIX_REQUEST, REGISTRY_QUERY_RECEIVED, REGISTRY_QUERY_RESPONSE, PROVIDE_AND_REGISTER_RECEIVED, PROVIDE_AND_REGISTER_RESPONSE
    }

    private final TYPE type;
    private String message;
    private List<Identifier> participantIdentifiers;
    private String uniqueId;
    private boolean outcome;
    private String homeCommunityId;
    private String sourceIP;


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

    public List<Identifier> getParticipantIdentifiers() {
        return participantIdentifiers;
    }

    public void setParticipantIdentifiers(List<Identifier> participantIdentifiers) {
        this.participantIdentifiers = participantIdentifiers;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public boolean getOutcome() {
        return outcome;
    }

    public void setOutcome(boolean outcome) {
        this.outcome = outcome;
    }

    public String getHomeCommunityId() {
        return homeCommunityId;
    }

    public void setHomeCommunityId(String homeCommunityId) {
        this.homeCommunityId = homeCommunityId;
    }

    public String getSourceIP() {
        return sourceIP;
    }

    public void setSourceIP(String sourceIP) {
        this.sourceIP = sourceIP;
    }
}
