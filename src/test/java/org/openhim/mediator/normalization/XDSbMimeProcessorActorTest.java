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
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class XDSbMimeProcessorActorTest {

    private static final String CONTENT_TYPE = "multipart/related; boundary=MIMEBoundaryurn_uuid_76A2C3D9BCD3AECFF31217932910180; " +
            "type=\"application/xop+xml\"; start=\"<0.urn:uuid76A2C3D9BCD3AECFF31217932910181@apache.org>\"; " +
            "start-info=\"application/soap+xml\"; action=\"urn:ihe:iti:2007:ProvideAndRegisterDocumentSet-b\"";

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
    public void testMimeMessage() throws Exception {
        InputStream testPnRBasicMtomIn = getClass().getClassLoader().getResourceAsStream("PnRBasicMtom.xml");
        final String testPnRBasicMtom = IOUtils.toString(testPnRBasicMtomIn);

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(XDSbMimeProcessorActor.class));
            XDSbMimeProcessorActor.MimeMessage testMsg = new XDSbMimeProcessorActor.MimeMessage(getRef(), getRef(), testPnRBasicMtom, CONTENT_TYPE);
            actor.tell(testMsg, getRef());

            XDSbMimeProcessorActor.XDSbMimeProcessorResponse result = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), XDSbMimeProcessorActor.XDSbMimeProcessorResponse.class);
            assertEquals("(Not an actual) SOAP part for testing", result.getResponseObject().trim());
        }};
    }

    @Test
    public void testMimeMessage_shouldReturnDocuments() throws Exception {
        InputStream testPnRBasicMtomIn = getClass().getClassLoader().getResourceAsStream("PnRBasicMtom.xml");
        final String testPnRBasicMtom = IOUtils.toString(testPnRBasicMtomIn);

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(XDSbMimeProcessorActor.class));
            XDSbMimeProcessorActor.MimeMessage testMsg = new XDSbMimeProcessorActor.MimeMessage(getRef(), getRef(), testPnRBasicMtom, CONTENT_TYPE);
            actor.tell(testMsg, getRef());

            XDSbMimeProcessorActor.XDSbMimeProcessorResponse result = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), XDSbMimeProcessorActor.XDSbMimeProcessorResponse.class);
            assertEquals(1, result.documents.size());
            assertEquals("This is my document.\nIt is great!\n", result.getDocuments().get(0));
        }};
    }

    @Test
    public void testEnrichedMimeMessage() throws Exception {
        InputStream testPnRBasicMtomIn = getClass().getClassLoader().getResourceAsStream("PnRBasicMtom.xml");
        final String testPnRBasicMtom = IOUtils.toString(testPnRBasicMtomIn);
        InputStream testPnRModifiedMtomIn = getClass().getClassLoader().getResourceAsStream("PnRModifiedMtom.xml");
        final String testPnRModifiedMtom = IOUtils.toString(testPnRModifiedMtomIn);

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(XDSbMimeProcessorActor.class));

            XDSbMimeProcessorActor.MimeMessage testMsg = new XDSbMimeProcessorActor.MimeMessage(getRef(), getRef(), testPnRBasicMtom, CONTENT_TYPE);
            actor.tell(testMsg, getRef());
            expectMsgClass(Duration.create(60, TimeUnit.SECONDS), XDSbMimeProcessorActor.XDSbMimeProcessorResponse.class);

            XDSbMimeProcessorActor.EnrichedMessage enrichedMessage = new XDSbMimeProcessorActor.EnrichedMessage(getRef(), getRef(), "My test");
            actor.tell(enrichedMessage, getRef());

            XDSbMimeProcessorActor.XDSbMimeProcessorResponse result = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), XDSbMimeProcessorActor.XDSbMimeProcessorResponse.class);
            assertEquals(testPnRModifiedMtom, result.getResponseObject().replaceAll("\r", ""));
        }};
    }
}