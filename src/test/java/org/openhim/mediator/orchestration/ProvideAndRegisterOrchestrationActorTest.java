package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
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
import org.openhim.mediator.messages.*;
import org.openhim.mediator.normalization.ParseProvideAndRegisterRequestActor;
import scala.concurrent.duration.Duration;

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


    @Test
    public void shouldSendResolvePatientIDRequests() throws Exception {
        InputStream testPnRIn = getClass().getClassLoader().getResourceAsStream("pnr1.xml");
        final String testPnR = IOUtils.toString(testPnRIn);

        final List<DummyResolveIdentifierActor.ExpectedRequest> expectedPatientIds = new ArrayList<>();
        expectedPatientIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("1111111111", new AssigningAuthority("", "1.2.3"))));
        expectedPatientIds.add(new DummyResolveIdentifierActor.ExpectedRequest(new Identifier("76cc765a442f410", new AssigningAuthority("", "1.3.6.1.4.1.21367.2005.3.7"))));

        setupResolvePatientIDMock(expectedPatientIds);
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(ProvideAndRegisterOrchestrationActor.class, testConfig, resolvePIDDummy, resolveHWIDDummy, resolveFIDDummy));
            OrchestrateProvideAndRegisterRequest testMsg = new OrchestrateProvideAndRegisterRequest(getRef(), getRef(), testPnR);

            actor.tell(testMsg, getRef());

            OrchestrateProvideAndRegisterRequestResponse response = expectMsgClass(
                    Duration.create(5000, TimeUnit.MILLISECONDS),
                    OrchestrateProvideAndRegisterRequestResponse.class
            );

            for (DummyResolveIdentifierActor.ExpectedRequest er : expectedPatientIds) {
                if (!er.wasSeen()) {
                    fail("Resolve id request for " + er.getIdentifier() + " wasn't sent");
                }
            }
        }};
    }

    @Test
    public void validateAndEnrichClient_shouldEnrichPNRWithECIDForSubmissionSet() throws Exception {
        InputStream testPnRIn = getClass().getClassLoader().getResourceAsStream("pnr1.xml");
        final String testPnR = IOUtils.toString(testPnRIn);

        setupResolvePatientIDMock();
        setupResolveHCWIDMock();
        setupResolveFacilityIDMock();

        new JavaTestKit(system) {{
            ActorRef actor = system.actorOf(Props.create(ProvideAndRegisterOrchestrationActor.class, testConfig, resolvePIDDummy, resolveHWIDDummy, resolveFIDDummy));
            OrchestrateProvideAndRegisterRequest testMsg = new OrchestrateProvideAndRegisterRequest(getRef(), getRef(), testPnR);

            actor.tell(testMsg, getRef());

            OrchestrateProvideAndRegisterRequestResponse response = expectMsgClass(
                    Duration.create(5000, TimeUnit.MILLISECONDS),
                    OrchestrateProvideAndRegisterRequestResponse.class
            );

            ProvideAndRegisterDocumentSetRequestType pnr = ParseProvideAndRegisterRequestActor.parseRequest(response.getResponseObject());

            RegistryPackageType regPac = InfosetUtil.getRegistryPackage(pnr.getSubmitObjectsRequest(), XDSConstants.UUID_XDSSubmissionSet);
            String submissionPatCX = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_patientId, regPac);
            assertEquals("ECID1^^^ECID&ECID&ISO", submissionPatCX);
        }};
    }

}