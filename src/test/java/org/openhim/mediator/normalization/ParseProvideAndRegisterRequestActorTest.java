/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.normalization;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.engine.messages.SimpleMediatorResponse;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ParseProvideAndRegisterRequestActorTest {

    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testEnrichMessage() throws Exception {
        InputStream testPnRIn = getClass().getClassLoader().getResourceAsStream("pnr1.xml");
        final String testPnR = IOUtils.toString(testPnRIn);

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(ParseProvideAndRegisterRequestActor.class));

            SimpleMediatorRequest<String> testMsg = new SimpleMediatorRequest<String>(getRef(), getRef(), testPnR);
            actor.tell(testMsg, getRef());

            SimpleMediatorResponse result = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), SimpleMediatorResponse.class);
            assertTrue(SimpleMediatorResponse.isInstanceOf(ProvideAndRegisterDocumentSetRequestType.class, result));
        }};
    }
}