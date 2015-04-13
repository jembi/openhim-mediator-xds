/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.denormalization.EnrichRegistryStoredQueryActor;
import org.openhim.mediator.dummies.DummyResolveIdentifierActor;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.testing.MockHTTPConnector;
import org.openhim.mediator.engine.testing.MockLauncher;
import org.openhim.mediator.engine.testing.TestingUtils;
import org.openhim.mediator.messages.ResolvePatientIdentifier;
import org.openhim.mediator.messages.ResolvePatientIdentifierResponse;
import org.openhim.mediator.normalization.ParseRegistryStoredQueryActor;
import scala.concurrent.duration.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RegistryActorTest {

    private static class MockRegistry extends MockHTTPConnector {
        String response;
        String expectedMessage;

        public MockRegistry() throws IOException {
            InputStream in = getClass().getClassLoader().getResourceAsStream("adhocQueryResponse_wSOAP.xml");
            response = IOUtils.toString(in);

            InputStream expected = getClass().getClassLoader().getResourceAsStream("adhocQueryRequest_enriched_wSOAP.xml");
            expectedMessage = IOUtils.toString(expected);
        }

        @Override
        public String getResponse() {
            return response;
        }

        @Override
        public Integer getStatus() {
            return 200;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Collections.emptyMap();
        }

        @Override
        public void executeOnReceive(MediatorHTTPRequest msg) {
            assertEquals("Expected an enriched adhoc query", trimXML(expectedMessage), trimXML(msg.getBody()));
        }
    }

    static ActorSystem system;
    MediatorConfig testConfig;


    @Before
    public void before() throws Exception {
        system = ActorSystem.create();

        testConfig = new MediatorConfig();
        testConfig.setName("registry-tests");
        testConfig.setProperties("mediator-unit-test.properties");

        List<MockLauncher.ActorToLaunch> toLaunch = new LinkedList<>();
        toLaunch.add(new MockLauncher.ActorToLaunch("http-connector", MockRegistry.class));
        toLaunch.add(new MockLauncher.ActorToLaunch("parse-registry-stored-query", ParseRegistryStoredQueryActor.class));
        toLaunch.add(new MockLauncher.ActorToLaunch("enrich-registry-stored-query", EnrichRegistryStoredQueryActor.class));
        TestingUtils.launchActors(system, testConfig.getName(), toLaunch);
    }

    @After
    public void after() {
        TestingUtils.clearRootContext(system, testConfig.getName());
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    private MediatorHTTPRequest buildTestAdhocQueryRequest(ActorRef ref) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("adhocQueryRequest_wSOAP.xml");
        String adhoc = IOUtils.toString(in);
        return new MediatorHTTPRequest(
                ref, ref, "unit-test", "POST", "http", null, null, "/xdsregistry", adhoc, Collections.<String, String>emptyMap(), null
        );
    }

    @Test
    public void testValidRegistryStoredQuery() throws Exception {
        new JavaTestKit(system) {{
            //mock pix resolver
            Identifier ecid = new Identifier("ECID1", new AssigningAuthority("ECID", "ECID", "ECID"));
            ActorRef resolvePIDDummy = system.actorOf(
                    Props.create(DummyResolveIdentifierActor.class, ResolvePatientIdentifier.class, ResolvePatientIdentifierResponse.class, ecid)
            );

            TestActorRef<RegistryActor> actor = TestActorRef.create(system, Props.create(RegistryActor.class, testConfig));
            actor.underlyingActor().resolvePatientIDActor = resolvePIDDummy;

            actor.tell(buildTestAdhocQueryRequest(getRef()), getRef());

            FinishRequest response = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), FinishRequest.class);

            assertEquals(new Integer(200), response.getResponseStatus());
            InputStream rIn = getClass().getClassLoader().getResourceAsStream("adhocQueryResponse_wSOAP.xml");
            String responseBody = IOUtils.toString(rIn);
            assertEquals(responseBody, response.getResponse());
        }};
    }

    @Test
    public void testUnresolvedPatientId() throws Exception {
        new JavaTestKit(system) {{
            //mock pix resolver
            Identifier respondId = null;
            ActorRef resolvePIDDummy = system.actorOf(
                    Props.create(DummyResolveIdentifierActor.class, ResolvePatientIdentifier.class, ResolvePatientIdentifierResponse.class, respondId)
            );

            TestActorRef<RegistryActor> actor = TestActorRef.create(system, Props.create(RegistryActor.class, testConfig));
            actor.underlyingActor().resolvePatientIDActor = resolvePIDDummy;

            actor.tell(buildTestAdhocQueryRequest(getRef()), getRef());

            FinishRequest response = expectMsgClass(Duration.create(60, TimeUnit.SECONDS), FinishRequest.class);

            assertEquals("Expected 404 if patient resolve failed", new Integer(404), response.getResponseStatus());
        }};
    }

    @Test
    public void testIsAdhocQuery() throws Exception {
        new JavaTestKit(system) {{
            TestActorRef<RegistryActor> actor = TestActorRef.create(system, Props.create(RegistryActor.class, testConfig));

            InputStream in = getClass().getClassLoader().getResourceAsStream("adhocQueryRequest_wSOAP.xml");
            String msg = IOUtils.toString(in);
            assertTrue(actor.underlyingActor().isAdhocQuery(msg));

            InputStream in2 = getClass().getClassLoader().getResourceAsStream("pnr1.xml");
            String msg2 = IOUtils.toString(in2);
            assertFalse(actor.underlyingActor().isAdhocQuery(msg2));

            assertFalse(actor.underlyingActor().isAdhocQuery("random stuff"));
        }};
    }

    public static String trimXML(String xml) {
        return xml.replace("\n", "").replaceAll(">\\s*<", "><");
    }
}