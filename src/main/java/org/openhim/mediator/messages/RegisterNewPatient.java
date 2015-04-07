/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

/**
 * Create a new patient demographic record.
 */
public class RegisterNewPatient extends MediatorRequestMessage {
    private final Identifier patientIdentifier;
    private final String givenName;
    private final String familyName;
    private final String gender;
    private final String birthDate;
    private final String telecom;
    private final String languageCommunicationCode;

    public RegisterNewPatient(ActorRef requestHandler, ActorRef respondTo, String orchestration, String correlationId,
                              Identifier patientIdentifier, String givenName, String familyName, String gender, String birthDate, String telecom, String languageCommunicationCode) {
        super(requestHandler, respondTo, orchestration, correlationId);
        this.patientIdentifier = patientIdentifier;
        this.givenName = givenName;
        this.familyName = familyName;
        this.gender = gender;
        this.birthDate = birthDate;
        this.telecom = telecom;
        this.languageCommunicationCode = languageCommunicationCode;
    }

    public Identifier getPatientIdentifier() {
        return patientIdentifier;
    }

    public String getTelecom() {
        return telecom;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public String getGender() {
        return gender;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public String getLanguageCommunicationCode() {
        return languageCommunicationCode;
    }
}
