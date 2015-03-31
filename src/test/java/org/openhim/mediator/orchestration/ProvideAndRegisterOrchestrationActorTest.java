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
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import org.apache.commons.io.IOUtils;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.dummies.DummyResolveIdentifierActor;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.messages.*;
import org.openhim.mediator.normalization.ParseProvideAndRegisterRequestActor;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProvideAndRegisterOrchestrationActorTest {

    static ActorSystem system;
    MediatorConfig testConfig;
    ActorRef resolvePIDDummy;
    ActorRef resolveHWIDDummy;
    ActorRef resolveFIDDummy;
    final FiniteDuration waitTime = Duration.create(60, TimeUnit.SECONDS);


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
        testConfig.setProperties("mediator-unit-test.properties");
        testConfig.getProperties().setProperty("pnr.sendParseOrchestration", "false");
    }

    private void setupResolvePatientIDMock() {
        setupResolvePatientIDMock(null);
    }

    private void setupResolvePatientIDMock(List<DummyResolveIdentifierActor.ExpectedRequest> expectedRequestList) {
        Identifier ecid = new Identifier("ECID1", new AssigningAuthority("ECID", "ECID"));
        if (expectedRequestList!=null) {
            resolvePIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolvePatientIdentifier.class, ResolvePatientIdentifierResponse.class, ecid, expectedRequestList));
        } else {
            resolvePIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolvePatientIdentifier.class, ResolvePatientIdentifierResponse.class, ecid));
        }
    }

    private void setupResolveHCWIDMock() {
        setupResolveHCWIDMock(null);
    }

    private void setupResolveHCWIDMock(List<DummyResolveIdentifierActor.ExpectedRequest> expectedRequestList) {
        Identifier epid = new Identifier("EPID1", new AssigningAuthority("EPID", "EPID"));
        if (expectedRequestList!=null) {
            resolveHWIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolveHealthcareWorkerIdentifier.class, ResolveHealthcareWorkerIdentifierResponse.class, epid, expectedRequestList));
        } else {
            resolveHWIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolveHealthcareWorkerIdentifier.class, ResolveHealthcareWorkerIdentifierResponse.class, epid));
        }
    }

    private void setupResolveFacilityIDMock() {
        setupResolveFacilityIDMock(null);
    }

    private void setupResolveFacilityIDMock(List<DummyResolveIdentifierActor.ExpectedRequest> expectedRequestList) {
        Identifier elid = new Identifier("ELID1", new AssigningAuthority("ELID", "ELID"));
        if (expectedRequestList!=null) {
            resolveFIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolveFacilityIdentifier.class, ResolveFacilityIdentifierResponse.class, elid, expectedRequestList));
        } else {
            resolveFIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolveFacilityIdentifier.class, ResolveFacilityIdentifierResponse.class, elid));
        }
    }

    private void validateExpectedIdentifiersList(List<DummyResolveIdentifierActor.ExpectedRequest> expectedRequests) {
        for (DummyResolveIdentifierActor.ExpectedRequest er : expectedRequests) {
            if (!er.wasSeen()) {
                fail("Resolve id request for " + er.getIdentifier() + " wasn't sent");
            }
        }
    }

    private void sendPnRMessage(ActorSystem system, ActorRef ref, String resource) throws Exception {
        sendPnRMessage(testConfig, system, ref, resource);
    }

    private void sendPnRMessage(MediatorConfig config, ActorSystem system, ActorRef ref, String resource) throws Exception {
        InputStream testPnRIn = getClass().getClassLoader().getResourceAsStream(resource);
        final String testPnR = IOUtils.toString(testPnRIn);

        ActorRef actor = system.actorOf(Props.create(ProvideAndRegisterOrchestrationActor.class, config, resolvePIDDummy, resolveHWIDDummy, resolveFIDDummy));
        OrchestrateProvideAndRegisterRequest testMsg = new OrchestrateProvideAndRegisterRequest(ref, ref, testPnR, null);

        actor.tell(testMsg, ref);
    }

    @Test
    public void shouldSendResolvePatientIDRequests() throws Exception {
        final List<DummyResolveIdentifierActor.ExpectedRequest> expectedPatientIds = new ArrayList<>();
        expectedPatientIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("1111111111", new AssigningAuthority("", "1.2.3"))));
        expectedPatientIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("76cc765a442f410", new AssigningAuthority("", "1.3.6.1.4.1.21367.2005.3.7"))));

        setupResolvePatientIDMock(expectedPatientIds);
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            expectMsgClass(waitTime, OrchestrateProvideAndRegisterRequestResponse.class);
            validateExpectedIdentifiersList(expectedPatientIds);
        }};
    }

    @Test
    public void shouldSendResolveHealtcareWorkerIDRequests() throws Exception {
        final List<DummyResolveIdentifierActor.ExpectedRequest> expectedHealthcareWorkerIds = new ArrayList<>();
        expectedHealthcareWorkerIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("pro111", new AssigningAuthority("", "1.2.3"))));
        expectedHealthcareWorkerIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("pro112", new AssigningAuthority("", "1.2.3"))));

        setupResolvePatientIDMock();
        setupResolveHCWIDMock(expectedHealthcareWorkerIds);
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            expectMsgClass(waitTime, OrchestrateProvideAndRegisterRequestResponse.class);
            validateExpectedIdentifiersList(expectedHealthcareWorkerIds);
        }};
    }

    @Test
    public void shouldSendResolveFacilityIDRequests() throws Exception {
        final List<DummyResolveIdentifierActor.ExpectedRequest> expectedFacilityIds = new ArrayList<>();
        expectedFacilityIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("45", new AssigningAuthority("", "1.2.3.4.5.6.7.8.9.1789"))));
        expectedFacilityIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("53", new AssigningAuthority("", "1.2.3.4.5.6.7.8.9.1789"))));

        setupResolvePatientIDMock();
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock(expectedFacilityIds);

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            expectMsgClass(waitTime, OrchestrateProvideAndRegisterRequestResponse.class);
            validateExpectedIdentifiersList(expectedFacilityIds);
        }};
    }

    @Test
    public void validateAndEnrichClient_shouldEnrichPNRWithECIDForSubmissionSet() throws Exception {
        setupResolvePatientIDMock();
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            OrchestrateProvideAndRegisterRequestResponse response = expectMsgClass(waitTime, OrchestrateProvideAndRegisterRequestResponse.class);

            ProvideAndRegisterDocumentSetRequestType pnr = ParseProvideAndRegisterRequestActor.parseRequest(response.getResponseObject());

            RegistryPackageType regPac = InfosetUtil.getRegistryPackage(pnr.getSubmitObjectsRequest(), XDSConstants.UUID_XDSSubmissionSet);
            String submissionPatCX = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_patientId, regPac);
            assertEquals("ECID1^^^ECID&ECID&ISO", submissionPatCX);
        }};
    }

    @Test
    public void validateAndEnrichClient_shouldEnrichPNRWithECIDForDocumentEntries() throws Exception {
        setupResolvePatientIDMock();
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            OrchestrateProvideAndRegisterRequestResponse response = expectMsgClass(waitTime, OrchestrateProvideAndRegisterRequestResponse.class);

            ProvideAndRegisterDocumentSetRequestType pnr = ParseProvideAndRegisterRequestActor.parseRequest(response.getResponseObject());

            ExtrinsicObjectType eo = InfosetUtil.getExtrinsicObjects(pnr.getSubmitObjectsRequest()).get(0);
            String documentPatCX = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_patientId, eo);
            assertEquals("ECID1^^^ECID&ECID&ISO", documentPatCX);
        }};
    }

    @Test
    public void validateAndEnrichPatient_shouldRespondWithBadRequestIfPatientNotResolved() throws Exception {
        Identifier responseId = null;
        resolvePIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolvePatientIdentifier.class, ResolvePatientIdentifierResponse.class, responseId));
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            FinishRequest response = expectMsgClass(waitTime, FinishRequest.class);

            assertEquals(new Integer(400), response.getResponseStatus());
        }};
    }

    @Test
    public void validateAndEnrichProvider_shouldRespondWithBadRequestIfProviderNotResolved() throws Exception {
        setupResolvePatientIDMock();
        Identifier responseId = null;
        resolveHWIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolveHealthcareWorkerIdentifier.class, ResolveHealthcareWorkerIdentifierResponse.class, responseId));
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            FinishRequest response = expectMsgClass(waitTime, FinishRequest.class);

            assertEquals(new Integer(400), response.getResponseStatus());
        }};
    }

    @Test
    public void validateAndEnrichFacility_shouldRespondWithBadRequestIfFacilityNotResolved() throws Exception {
        setupResolvePatientIDMock();
        setupResolveHCWIDMock();
        Identifier responseId = null;
        resolveFIDDummy = system.actorOf(Props.create(DummyResolveIdentifierActor.class, ResolveFacilityIdentifier.class, ResolveFacilityIdentifierResponse.class, responseId));

        new JavaTestKit(system) {{
            sendPnRMessage(system, getRef(), "pnr1.xml");
            FinishRequest response = expectMsgClass(waitTime, FinishRequest.class);

            assertEquals(new Integer(400), response.getResponseStatus());
        }};
    }

    @Test
    public void shouldNotSendResolveHealtcareWorkerIDRequestsIfDisabled() throws Exception {
        final MediatorConfig config = new MediatorConfig();
        config.setProperties("mediator-unit-test.properties");
        config.getProperties().setProperty("pnr.sendParseOrchestration", "false");
        config.getProperties().setProperty("pnr.providers.enrich", "false");

        final List<DummyResolveIdentifierActor.ExpectedRequest> expectedHealthcareWorkerIds = new ArrayList<>();
        expectedHealthcareWorkerIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("pro111", new AssigningAuthority("", "1.2.3"))));
        expectedHealthcareWorkerIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("pro112", new AssigningAuthority("", "1.2.3"))));

        setupResolvePatientIDMock();
        setupResolveHCWIDMock(expectedHealthcareWorkerIds);
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            sendPnRMessage(config, system, getRef(), "pnr1.xml");
            expectMsgClass(waitTime, OrchestrateProvideAndRegisterRequestResponse.class);

            for (DummyResolveIdentifierActor.ExpectedRequest expectedRequest : expectedHealthcareWorkerIds) {
                assertFalse(expectedRequest.wasSeen());
            }
        }};
    }

    @Test
    public void shouldNotSendResolveFacilityIDRequestsIfDisabled() throws Exception {
        final MediatorConfig config = new MediatorConfig();
        config.setProperties("mediator-unit-test.properties");
        config.getProperties().setProperty("pnr.sendParseOrchestration", "false");
        config.getProperties().setProperty("pnr.facilities.enrich", "false");

        final List<DummyResolveIdentifierActor.ExpectedRequest> expectedFacilityIds = new ArrayList<>();
        expectedFacilityIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("45", new AssigningAuthority("", "1.2.3.4.5.6.7.8.9.1789"))));
        expectedFacilityIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("53", new AssigningAuthority("", "1.2.3.4.5.6.7.8.9.1789"))));

        setupResolvePatientIDMock();
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock(expectedFacilityIds);

        new JavaTestKit(system) {{
            sendPnRMessage(config, system, getRef(), "pnr1.xml");
            expectMsgClass(waitTime, OrchestrateProvideAndRegisterRequestResponse.class);

            for (DummyResolveIdentifierActor.ExpectedRequest expectedRequest : expectedFacilityIds) {
                assertFalse(expectedRequest.wasSeen());
            }
        }};
    }
}