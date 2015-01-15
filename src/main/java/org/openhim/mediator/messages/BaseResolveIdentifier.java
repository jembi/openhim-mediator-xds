/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.messages;

import akka.actor.ActorRef;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;

public abstract class BaseResolveIdentifier extends MediatorRequestMessage {
    private final Identifier identifier;
    private final AssigningAuthority targetAssigningAuthority;

    public BaseResolveIdentifier(ActorRef requestHandler, ActorRef respondTo, String orchestration, String correlationId, Identifier identifier, AssigningAuthority targetAssigningAuthority) {
        super(requestHandler, respondTo, orchestration, correlationId);
        this.identifier = identifier;
        this.targetAssigningAuthority = targetAssigningAuthority;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public AssigningAuthority getTargetAssigningAuthority() {
        return targetAssigningAuthority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BaseResolveIdentifier that = (BaseResolveIdentifier) o;

        if (!identifier.equals(that.identifier)) return false;
        if (!targetAssigningAuthority.equals(that.targetAssigningAuthority)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = identifier.hashCode();
        result = 31 * result + targetAssigningAuthority.hashCode();
        return result;
    }
}
