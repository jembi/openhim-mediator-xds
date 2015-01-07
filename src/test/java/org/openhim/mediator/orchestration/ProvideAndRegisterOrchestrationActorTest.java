package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.dummies.DummyResolveFacilityIDActor;
import org.openhim.mediator.dummies.DummyResolveHealthcareWorkerIDActor;
import org.openhim.mediator.dummies.DummyResolvePatientIDActor;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.messages.OrchestrateProvideAndRegisterRequest;
import org.openhim.mediator.messages.OrchestrateProvideAndRegisterRequestResponse;
import scala.concurrent.duration.Duration;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class ProvideAndRegisterOrchestrationActorTest {

    static ActorSystem system;
    MediatorConfig testConfig;
    ActorRef resolvePIDDummy;
    ActorRef resolveHWIDDummy;
    ActorRef resolveFIDDummy;

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
        testConfig.setProperties("mediator.properties");
        resolvePIDDummy = system.actorOf(Props.create(DummyResolvePatientIDActor.class));
        resolveHWIDDummy = system.actorOf(Props.create(DummyResolveHealthcareWorkerIDActor.class));
        resolveFIDDummy = system.actorOf(Props.create(DummyResolveFacilityIDActor.class));
    }

    @Test
    public void testEnrichMessage() throws Exception {
        InputStream testPnRIn = getClass().getClassLoader().getResourceAsStream("pnr1.xml");
        final String testPnR = IOUtils.toString(testPnRIn);

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(ProvideAndRegisterOrchestrationActor.class, testConfig, resolvePIDDummy, resolveHWIDDummy, resolveFIDDummy));
            OrchestrateProvideAndRegisterRequest testMsg = new OrchestrateProvideAndRegisterRequest(
                    getRef(), getRef(), testPnR
            );

            actor.tell(testMsg, getRef());

            OrchestrateProvideAndRegisterRequestResponse response = expectMsgClass(
                    Duration.create(5000, TimeUnit.MILLISECONDS),
                    OrchestrateProvideAndRegisterRequestResponse.class
            );
            System.out.println(response.getResponseObject());
        }};
    }
}