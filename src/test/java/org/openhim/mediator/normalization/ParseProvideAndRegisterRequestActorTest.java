package org.openhim.mediator.normalization;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.messages.ParsedProvideAndRegisterRequest;
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

            ParsedProvideAndRegisterRequest result = expectMsgClass(Duration.create(100, TimeUnit.MILLISECONDS), ParsedProvideAndRegisterRequest.class);
        }};
    }
}