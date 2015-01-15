/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.denormalization;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.messages.EnrichRegistryStoredQuery;
import org.openhim.mediator.messages.EnrichRegistryStoredQueryResponse;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class EnrichRegistryStoredQueryActorTest {

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
    public void testParseValidRequest() throws Exception {
        InputStream testAdhocRequestIn = getClass().getClassLoader().getResourceAsStream("adhocQueryRequest_wSOAP.xml");
        final String testAdhocRequest = IOUtils.toString(testAdhocRequestIn);
        InputStream expectedAdhocRequestIn = getClass().getClassLoader().getResourceAsStream("adhocQueryRequest_enriched_wSOAP.xml");
        final String expectedAdhocRequest = IOUtils.toString(expectedAdhocRequestIn);

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(EnrichRegistryStoredQueryActor.class));

            Identifier id = new Identifier("ECID1", new AssigningAuthority("ECID", "ECID"));
            EnrichRegistryStoredQuery msg = new EnrichRegistryStoredQuery(getRef(), getRef(), testAdhocRequest, id);
            actor.tell(msg, getRef());

            EnrichRegistryStoredQueryResponse response = expectMsgClass(Duration.create(100, TimeUnit.MILLISECONDS), EnrichRegistryStoredQueryResponse.class);
            assertEquals(trimXML(expectedAdhocRequest), trimXML(response.getEnrichedMessage()));
        }};
    }


    /**
     * Removes newlines and whitespace around tags
     */
    public static String trimXML(String xml) {
        return xml.replace("\n", "").replaceAll(">\\s*<", "><");
    }
}