package org.openhim.mediator.denormalization;

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
import org.openhim.mediator.engine.connectors.MLLPConnector;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.MediatorRequestMessage;
import org.openhim.mediator.engine.messages.MediatorSocketRequest;
import org.openhim.mediator.engine.messages.MediatorSocketResponse;
import org.openhim.mediator.engine.testing.MockLauncher;
import org.openhim.mediator.messages.ResolvePatientIdentifier;
import org.openhim.mediator.messages.ResolvePatientIdentifierResponse;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class PIXRequestActorTest {
    private static class MockPIXReceiver_Valid extends UntypedActor {
        @Override
        public void onReceive(Object msg) throws Exception {
            if (msg instanceof MediatorSocketRequest) {
                InputStream in = getClass().getClassLoader().getResourceAsStream("pixResponse.er7");
                String responseMsg = IOUtils.toString(in);
                MediatorSocketResponse response = new MediatorSocketResponse((MediatorRequestMessage) msg, responseMsg);
                ((MediatorSocketRequest) msg).getRespondTo().tell(response, getSelf());
            } else {
                fail("Unexpected message received");
            }
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

    @Test
    public void testValidPIXQuery() {
        new JavaTestKit(system) {{
            MockLauncher.launchActors(system, testConfig.getName(), Collections.singletonList(new MockLauncher.ActorToLaunch("mllp-connector", MockPIXReceiver_Valid.class)));
            TestActorRef<PIXRequestActor> actor = TestActorRef.create(system, Props.create(PIXRequestActor.class, testConfig));

            Identifier fromId = new Identifier("1234", new AssigningAuthority("test-auth", "1.2.3"));
            AssigningAuthority targetDomain = new AssigningAuthority("ECID", "ECID");

            actor.tell(new ResolvePatientIdentifier(getRef(), getRef(), fromId, targetDomain), getRef());

            ResolvePatientIdentifierResponse response = expectMsgClass(Duration.create(1, TimeUnit.SECONDS), ResolvePatientIdentifierResponse.class);
            assertNotNull(response.getIdentifier());
            assertEquals("975cac30-68e5-11e4-bf2a-04012ce65b02", response.getIdentifier().getIdentifier());

            MockLauncher.clearActors(system, testConfig.getName());
        }};
    }
}