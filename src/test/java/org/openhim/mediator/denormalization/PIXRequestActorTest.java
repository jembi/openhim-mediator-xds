/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.denormalization;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.MediatorSocketRequest;
import org.openhim.mediator.engine.messages.MediatorSocketResponse;
import org.openhim.mediator.engine.testing.MockLauncher;
import org.openhim.mediator.engine.testing.TestingUtils;
import org.openhim.mediator.messages.ResolvePatientIdentifier;
import org.openhim.mediator.messages.ResolvePatientIdentifierResponse;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class PIXRequestActorTest {
    private abstract static class MockPIXReceiver extends UntypedActor {
        public abstract String getResponse() throws Exception;

        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorSocketRequest) {
                MediatorSocketResponse response = new MediatorSocketResponse((MediatorRequestMessage) msg, getResponse());
                ((MediatorSocketRequest) msg).getRespondTo().tell(response, getSelf());
            } else {
                fail("Unexpected message received");
            }
        }
    }

    private static class MockPIXReceiver_Valid extends MockPIXReceiver {
        @Override
        public String getResponse() throws Exception {
            InputStream in = getClass().getClassLoader().getResourceAsStream("pixResponse.er7");
            return IOUtils.toString(in);
        }
    }

    private static class MockPIXReceiver_NotFound extends MockPIXReceiver {
        @Override
        public String getResponse() throws Exception {
            InputStream in = getClass().getClassLoader().getResourceAsStream("pixResponse_notFound.er7");
            return IOUtils.toString(in);
        }
    }

    private static class MockPIXReceiver_BadResponse extends MockPIXReceiver {
        @Override
        public String getResponse() throws Exception {
            return "a bad response!";
        }
    }

    static ActorSystem system;
    MediatorConfig testConfig;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Before
    public void setUp() throws Exception {
        testConfig = new MediatorConfig();
        testConfig.setName("pix-tests");
        testConfig.setProperties("mediator-unit-test.properties");
    }

    private void sendTestRequest(ActorRef ref, Class<? extends UntypedActor> handler) {
        TestingUtils.launchActors(system, testConfig.getName(), Collections.singletonList(new MockLauncher.ActorToLaunch("mllp-connector", handler)));
        TestActorRef<PIXRequestActor> actor = TestActorRef.create(system, Props.create(PIXRequestActor.class, testConfig));

        Identifier fromId = new Identifier("1234", new AssigningAuthority("test-auth", "1.2.3"));
        AssigningAuthority targetDomain = new AssigningAuthority("ECID", "ECID");

        actor.tell(new ResolvePatientIdentifier(ref, ref, fromId, targetDomain), ref);
    }

    @Test
    public void testValidPIXQuery() {
        new JavaTestKit(system) {{
            sendTestRequest(getRef(), MockPIXReceiver_Valid.class);

            ResolvePatientIdentifierResponse response = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), ResolvePatientIdentifierResponse.class);
            assertNotNull(response.getIdentifier());
            assertEquals("975cac30-68e5-11e4-bf2a-04012ce65b02", response.getIdentifier().getIdentifier());

            TestingUtils.clearRootContext(system, testConfig.getName());
        }};
    }

    @Test
    public void testNotFoundPIXQuery() {
        new JavaTestKit(system) {{
            sendTestRequest(getRef(), MockPIXReceiver_NotFound.class);

            ResolvePatientIdentifierResponse response = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), ResolvePatientIdentifierResponse.class);
            assertNull(response.getIdentifier());

            TestingUtils.clearRootContext(system, testConfig.getName());
        }};
    }

    @Test
    public void testInvalidPIXQuery() {
        new JavaTestKit(system) {{
            sendTestRequest(getRef(), MockPIXReceiver_BadResponse.class);

            ExceptError response = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), ExceptError.class);
            assertNotNull(response.getError());

            TestingUtils.clearRootContext(system, testConfig.getName());
        }};
    }
}