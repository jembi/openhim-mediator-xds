/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.orchestration;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import ihe.iti.xds_b._2007.ObjectFactory;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.dcm4chee.xds2.common.XDSConstants;
import org.dcm4chee.xds2.infoset.util.InfosetUtil;
import org.openhim.mediator.Util;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.*;
import org.openhim.mediator.exceptions.CXParseException;
import org.openhim.mediator.exceptions.ValidationException;
import org.openhim.mediator.messages.*;
import org.openhim.mediator.normalization.ParseProvideAndRegisterRequestActor;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.*;

/**
 * An orchestrator for enriching XDS.b Provide and Register Document Set requests.
 * <br/><br/>
 * Messages supported:
 * <ul>
 *     <li>OrchestrateProvideAndRegisterRequest: responds with OrchestrateProvideAndRegisterRequestResponse</li>
 * </ul>
 */
public class ProvideAndRegisterOrchestrationActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    private abstract class IdentifierMapping {
        boolean resolved = false;
        boolean successful = false;
        Identifier fromId;
        String correlationId;
        MediatorRequestMessage resolveRequestMessage;

        abstract void resolve(Identifier resolvedId);
    }

    private class DocumentNode {
        String documentNodeURN;
        RegistryObjectType documentNode;

        public DocumentNode(String documentNodeURN, RegistryObjectType documentNode) {
            this.documentNodeURN = documentNodeURN;
            this.documentNode = documentNode;
        }
    }

    private class PatientIdentifierMapping extends IdentifierMapping {
        List<DocumentNode> nodes = new ArrayList<>();

        public PatientIdentifierMapping(Identifier fromId, DocumentNode node) {
            nodes.add(node);
            this.fromId = fromId;
        }

        @Override
        void resolve(Identifier resolvedId) {
            resolved = true;

            if (resolvedId!=null) {
                for (DocumentNode node : nodes) {
                    InfosetUtil.setExternalIdentifierValue(node.documentNodeURN, resolvedId.toString(), node.documentNode);
                }
                successful = true;
            }
        }
    }

    private class HealthcareWorkerIdentifierMapping extends IdentifierMapping {
        List<String> slotList;

        public HealthcareWorkerIdentifierMapping(Identifier fromId, List<String> slotList) {
            this.fromId = fromId;
            this.slotList = slotList;
        }

        @Override
        void resolve(Identifier resolvedId) {
            resolved = true;

            if (resolvedId!=null) {
                String newPersonXCN = resolvedId.toXCN();
                slotList.clear();
                slotList.add(newPersonXCN);
                successful = true;
            }
        }
    }

    private class FacilityIdentifierMapping extends IdentifierMapping {
        String localLocationName;
        List<String> slotList;

        public FacilityIdentifierMapping(Identifier fromId, String localLocationName, List<String> slotList) {
            this.fromId = fromId;
            this.localLocationName = localLocationName;
            this.slotList = slotList;
        }

        @Override
        void resolve(Identifier resolvedId) {
            resolved = true;

            if (resolvedId!=null) {
                String newInstitutionXON = resolvedId.toXON(localLocationName);
                slotList.clear();
                slotList.add(newInstitutionXON);
                successful = true;
            }
        }
    }

    private final MediatorConfig config;
    private final ActorRef resolvePatientIdHandler;
    private final ActorRef resolveHealthcareWorkerIdHandler;
    private final ActorRef resolveFacilityIdHandler;
    private final ActorRef registerNewPatientHandler;
    private String xForwardedFor;

    private OrchestrateProvideAndRegisterRequest originalRequest;
    private ProvideAndRegisterDocumentSetRequestType parsedRequest;
    private String messageBuffer;

    private List<IdentifierMapping> enterprisePatientIds = new ArrayList<>();
    private List<IdentifierMapping> enterpriseHealthcareWorkerIds = new ArrayList<>();
    private List<IdentifierMapping> enterpriseFacilityIds = new ArrayList<>();


    /* auto-register patient */

    //Should only ever attempt a single registration
    private boolean sentNewRegistrationRequest = false;
    //Identifiers that couldn't be resolved
    private List<IdentifierMapping> failedPatientIds = new ArrayList<>();

    /* */



    public ProvideAndRegisterOrchestrationActor(MediatorConfig config, ActorRef resolvePatientIdHandler,
                                                ActorRef resolveHealthcareWorkerIdHandler, ActorRef resolveFacilityIdHandler) {
        this(config, resolvePatientIdHandler, resolveHealthcareWorkerIdHandler, resolveFacilityIdHandler, resolvePatientIdHandler);
    }

    public ProvideAndRegisterOrchestrationActor(MediatorConfig config, ActorRef resolvePatientIdHandler, ActorRef resolveHealthcareWorkerIdHandler,
                                                ActorRef resolveFacilityIdHandler, ActorRef registerNewPatientHandler) {
        this.config = config;
        this.resolvePatientIdHandler = resolvePatientIdHandler;
        this.resolveHealthcareWorkerIdHandler = resolveHealthcareWorkerIdHandler;
        this.resolveFacilityIdHandler = resolveFacilityIdHandler;
        this.registerNewPatientHandler = registerNewPatientHandler;
    }


    private void parseRequest(OrchestrateProvideAndRegisterRequest msg) {
        log.info("Parsing Xds.b Provide and Register request");
        messageBuffer = msg.getRequestObject();
        ActorRef parseHandler = getContext().actorOf(Props.create(ParseProvideAndRegisterRequestActor.class, config), "xds-pnr-document-normalization");
        parseHandler.tell(new SimpleMediatorRequest<>(msg.getRequestHandler(), getSelf(), messageBuffer), getSelf());
    }

    private void processParsedRequest(ProvideAndRegisterDocumentSetRequestType doc) {
        log.info("Request parsed. Processing document");
        parsedRequest = doc;
        boolean outcome = true;
        try {
            initIdentifiersToBeResolvedMappings();
            if (!checkAndRespondIfAllResolved()) {
                resolveEnterpriseIdentifiers();
            }
        } catch (ValidationException ex) {
            respondBadRequest(ex.getMessage());
            outcome = false;
        } finally {
            sendAuditMessage(ATNAAudit.TYPE.PROVIDE_AND_REGISTER_RECEIVED, outcome);
        }
    }

    private void initIdentifiersToBeResolvedMappings() throws ValidationException {
        readPatientIdentifiers();

        if (Util.isPropertyTrue(config, "pnr.providers.enrich", true) || Util.isPropertyTrue(config, "pnr.facilities.enrich", true)) {
            readHealthcareWorkerAndFacilityIdentifiers();
        }
    }

    private void addPatientIdToResolve(Identifier id, DocumentNode node) {
        for (IdentifierMapping mapping : enterprisePatientIds) {
            if (mapping.fromId.equals(id)) {
                ((PatientIdentifierMapping)mapping).nodes.add(node);
                return;
            }
        }

        enterprisePatientIds.add(new PatientIdentifierMapping(id, node));
    }

    private void readPatientIdentifiers() throws CXParseException {
        RegistryPackageType regPac = InfosetUtil.getRegistryPackage(parsedRequest.getSubmitObjectsRequest(), XDSConstants.UUID_XDSSubmissionSet);
        String CX = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_patientId, regPac);
        addPatientIdToResolve(new Identifier(CX), new DocumentNode(XDSConstants.UUID_XDSSubmissionSet_patientId, regPac));

        List<ExtrinsicObjectType> eos = InfosetUtil.getExtrinsicObjects(parsedRequest.getSubmitObjectsRequest());
        for (ExtrinsicObjectType eo : eos) {
            String documentPatCX = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSDocumentEntry_patientId, eo);
            addPatientIdToResolve(new Identifier(documentPatCX), new DocumentNode(XDSConstants.UUID_XDSDocumentEntry_patientId, eo));
        }
    }

    protected void readHealthcareWorkerAndFacilityIdentifiers() throws ValidationException {
        List<ExtrinsicObjectType> eos = InfosetUtil.getExtrinsicObjects(parsedRequest.getSubmitObjectsRequest());
        for (ExtrinsicObjectType eo : eos) {
            List<Map<String, SlotType1>> authorClassSlots = null;
            try {
                authorClassSlots = this.getClassificationSlotsFromExtrinsicObject(XDSConstants.UUID_XDSDocumentEntry_author, eo);
            } catch (JAXBException e) {
                throw new ValidationException(e);
            }

            for (Map<String, SlotType1> slotMap : authorClassSlots) {

                String localProviderID = null;
                String localProviderIDAssigningAuthority = null;
                String localLocationID = null;
                String localLocationIDAssigningAuthority = null;
                String localLocationName = null;
                List<String> personSlotValList = null;
                List<String> institutionSlotValList = null;

                if (slotMap.containsKey(XDSConstants.SLOT_NAME_AUTHOR_PERSON)) {
                    SlotType1 personSlot = slotMap.get(XDSConstants.SLOT_NAME_AUTHOR_PERSON);
                    personSlotValList = personSlot.getValueList().getValue();

                    // loop through all values and find the first one with an ID and assigning authority
                    for (String val : personSlotValList) {
                        String[] xcnComponents = val.split("\\^", -1);

                        // if the identifier component exists
                        if (!xcnComponents[0].isEmpty() && !xcnComponents[8].isEmpty()) {
                            localProviderID = xcnComponents[0];
                            localProviderIDAssigningAuthority = xcnComponents[8].substring(xcnComponents[8].indexOf('&') + 1, xcnComponents[8].lastIndexOf('&'));
                            break;
                        }
                    }
                }

                if (slotMap.containsKey(XDSConstants.SLOT_NAME_AUTHOR_INSTITUTION)) {
                    SlotType1 institutionSlot = slotMap.get(XDSConstants.SLOT_NAME_AUTHOR_INSTITUTION);
                    institutionSlotValList = institutionSlot.getValueList().getValue();

                    // loop through all values and find the first one with an ID
                    for (String val : institutionSlotValList) {
                        String[] xonComponents = val.split("\\^", -1);

                        // if the identifier component exists
                        if (xonComponents.length >= 10 && !xonComponents[5].isEmpty() && !xonComponents[9].isEmpty()) {
                            localLocationID = xonComponents[9];
                            localLocationName = xonComponents[0];
                            localLocationIDAssigningAuthority = xonComponents[5].substring(xonComponents[5].indexOf('&') + 1, xonComponents[5].lastIndexOf('&'));
                        }
                    }

                }

                if (localProviderID == null && localLocationID == null) {
                    throw new ValidationException("Local provider and facility identifiers could not be extracted from the XDS metadata");
                }

                if (localProviderID!=null && Util.isPropertyTrue(config, "pnr.providers.enrich", true)) {
                    Identifier id = new Identifier(localProviderID, new AssigningAuthority("", localProviderIDAssigningAuthority));
                    enterpriseHealthcareWorkerIds.add(new HealthcareWorkerIdentifierMapping(id, personSlotValList));
                }

                if (localLocationID!=null && Util.isPropertyTrue(config, "pnr.facilities.enrich", true)) {
                    Identifier id = new Identifier(localLocationID, new AssigningAuthority("", localLocationIDAssigningAuthority));
                    enterpriseFacilityIds.add(new FacilityIdentifierMapping(id, localLocationName, institutionSlotValList));
                }
            }
        }
    }



    /**
     * @param classificationScheme - The classification scheme to look for
     * @param eo - The extrinsic object to process
     * @return A list of maps, each item in the list represents a classification definition for
     * this scheme. There may be multiple of these. Each list item contains a map of SlotType1
     * objects keyed by their slot name.
     * @throws JAXBException
     */
    protected List<Map<String, SlotType1>> getClassificationSlotsFromExtrinsicObject(String classificationScheme, ExtrinsicObjectType eo) throws JAXBException {
        List<ClassificationType> classifications = eo.getClassification();

        List<Map<String, SlotType1>> classificationMaps = new ArrayList<Map<String, SlotType1>>();
        for (ClassificationType c : classifications) {
            if (c.getClassificationScheme().equals(classificationScheme)) {
                Map<String, SlotType1> slotsFromRegistryObject = InfosetUtil.getSlotsFromRegistryObject(c);
                classificationMaps.add(slotsFromRegistryObject);
            }
        }
        return classificationMaps;
    }



    private void resolveEnterpriseIdentifiers() {
        log.info("Resolving identifiers");
        resolvePatientIdentifiers();
        resolveHealthcareWorkerIdentifiers();
        resolveFacilityIdentifiers();
    }

    private void resolvePatientIdentifiers() {
        AssigningAuthority targetPatientIdAuthority = new AssigningAuthority();
        targetPatientIdAuthority.setAssigningAuthority(config.getProperty("client.requestedAssigningAuthority"));
        targetPatientIdAuthority.setAssigningAuthorityId(config.getProperty("client.requestedAssigningAuthorityId"));

        for (IdentifierMapping mapping : enterprisePatientIds) {
            String correlationId = UUID.randomUUID().toString();
            mapping.correlationId = correlationId;

            ResolvePatientIdentifier msg = new ResolvePatientIdentifier(
                    originalRequest.getRequestHandler(), getSelf(), correlationId, mapping.fromId, targetPatientIdAuthority
            );
            resolvePatientIdHandler.tell(msg, getSelf());

            mapping.resolveRequestMessage = msg;
        }
    }

    private void resolveHealthcareWorkerIdentifiers() {
        AssigningAuthority targetHealthcareWorkerIdAuthority = new AssigningAuthority();
        targetHealthcareWorkerIdAuthority.setAssigningAuthority(config.getProperty("provider.requestedAssigningAuthority"));
        targetHealthcareWorkerIdAuthority.setAssigningAuthorityId(config.getProperty("provider.requestedAssigningAuthorityId"));

        for (IdentifierMapping mapping : enterpriseHealthcareWorkerIds) {
            String correlationId = UUID.randomUUID().toString();
            mapping.correlationId = correlationId;

            ResolveHealthcareWorkerIdentifier msg = new ResolveHealthcareWorkerIdentifier(
                    originalRequest.getRequestHandler(), getSelf(), correlationId, mapping.fromId, targetHealthcareWorkerIdAuthority
            );
            resolveHealthcareWorkerIdHandler.tell(msg, getSelf());

            mapping.resolveRequestMessage = msg;
        }
    }

    private void resolveFacilityIdentifiers() {
        AssigningAuthority targetFacilityIdAuthority = new AssigningAuthority();
        targetFacilityIdAuthority.setAssigningAuthority(config.getProperty("facility.requestedAssigningAuthority"));
        targetFacilityIdAuthority.setAssigningAuthorityId(config.getProperty("facility.requestedAssigningAuthorityId"));

        for (IdentifierMapping mapping : enterpriseFacilityIds) {
            String correlationId = UUID.randomUUID().toString();
            mapping.correlationId = correlationId;

            ResolveFacilityIdentifier msg = new ResolveFacilityIdentifier(
                    originalRequest.getRequestHandler(), getSelf(), correlationId, mapping.fromId, targetFacilityIdAuthority
            );
            resolveFacilityIdHandler.tell(msg, getSelf());

            mapping.resolveRequestMessage = msg;
        }
    }


    private void processResolvedPatientId(ResolvePatientIdentifierResponse response) {
        enrichResolvedId(response, enterprisePatientIds);
    }

    private void processResolvedHealthcareWorkerId(ResolveHealthcareWorkerIdentifierResponse response) {
        enrichResolvedId(response, enterpriseHealthcareWorkerIds);
    }

    private void processResolvedFacilityId(ResolveFacilityIdentifierResponse response) {
        enrichResolvedId(response, enterpriseFacilityIds);
    }

    private void enrichResolvedId(BaseResolveIdentifierResponse response, List<IdentifierMapping> lst) {
        for (IdentifierMapping mapping : lst) {
            if (mapping.correlationId.equals(response.getOriginalRequest().getCorrelationId())) {
                mapping.resolve(response.getIdentifier());
            }
        }
    }


    private List<Identifier> getAllKnownPatientIdentifiers() {
        List<Identifier> result = new LinkedList<>();
        for (IdentifierMapping mapping : enterprisePatientIds) {
            result.add(mapping.fromId);
        }
        return result;
    }

    /**
     * Attempt to read the message document at CDA level 2 for any available patient demographic information.
     *
     * It only attempts to read anything it can and won't fail if the document isn't a CDA.
     */
    private RegisterNewPatient buildRegistrationRequestFromCDA(String document) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(IOUtils.toInputStream(document));
            XPath xpath = XPathFactory.newInstance().newXPath();

            String givenName = xpath.compile("ClinicalDocument/recordTarget/patientRole/patient/name/given").evaluate(doc);
            String lastName = xpath.compile("ClinicalDocument/recordTarget/patientRole/patient/name/family").evaluate(doc);
            String gender = xpath.compile("ClinicalDocument/recordTarget/patientRole/patient/administrativeGenderCode/@code").evaluate(doc);
            String birthdate = xpath.compile("ClinicalDocument/recordTarget/patientRole/patient/birthTime/@value").evaluate(doc);
            String telecom = xpath.compile("ClinicalDocument/recordTarget/patientRole/telecom/@value").evaluate(doc);
            String languagePreference = xpath.compile("ClinicalDocument/recordTarget/patientRole/patient/languageCommunication/languageCode/@code").evaluate(doc);

            return new RegisterNewPatient(
                    originalRequest.getRequestHandler(), getSelf(), getAllKnownPatientIdentifiers(), givenName, lastName, gender, birthdate, telecom, languagePreference
            );

        } catch (SAXException ex) {
            //Not a failure if not a valid CDA document
            return new RegisterNewPatient(
                    originalRequest.getRequestHandler(), getSelf(), getAllKnownPatientIdentifiers(), null, null, null, null, null, null
            );

        } catch (XPathExpressionException | ParserConfigurationException | IOException ex) {
            originalRequest.getRequestHandler().tell(new ExceptError(ex), getSelf());
            return null;
        }
    }

    private void autoRegisterPatient() {
        String document = originalRequest.getDocument(); //is mime?
        if (document == null) { //else get from parsed message
            if (parsedRequest.getDocument()!=null && parsedRequest.getDocument().size()>=1) {
                document = new String(parsedRequest.getDocument().get(0).getValue());
            }
        }

        RegisterNewPatient registerNewPatient = buildRegistrationRequestFromCDA(document);
        if (registerNewPatient!=null) {
            registerNewPatientHandler.tell(registerNewPatient, getSelf());
            sentNewRegistrationRequest = true;
        }
    }


    private void checkForFailedPatientIdResolutionsAndAutoRegisterIfSo() {
        for (IdentifierMapping mapping : enterprisePatientIds) {
            if (!mapping.successful) {
                mapping.resolved = false;
                failedPatientIds.add(mapping);
            }
        }

        if (failedPatientIds.size()>0) {
            log.info("Failed to resolve patient identifier(s). Sending patient registration message to Client Registry.");
            autoRegisterPatient();
        }
    }

    private boolean checkAndRespondIfAllResolved() {
        if (Util.isPropertyTrue(config, "pnr.patients.autoRegister") && areAllIdentifiersResolvedForList(enterprisePatientIds) && !sentNewRegistrationRequest) {
            checkForFailedPatientIdResolutionsAndAutoRegisterIfSo();
        }

        if (areAllIdentifiersResolved()) {
            boolean outcome = false;
            try {
                String errors = getResolveIdentifierErrors();

                if (errors==null) {
                    respondSuccess();
                    outcome = true;
                } else {
                    respondBadRequest(errors);
                    outcome = false;
                }
            } catch (JAXBException ex) {
                originalRequest.getRequestHandler().tell(new ExceptError(ex), getSelf());
            } finally {
                sendAuditMessage(ATNAAudit.TYPE.PROVIDE_AND_REGISTER_ENRICHED, outcome);
                return true;
            }
        }
        return false;
    }

    private void respondSuccess() throws JAXBException {
        log.info("All identifiers resolved. Responding with enriched document.");
        messageBuffer = Util.marshallJAXBObject("ihe.iti.xds_b._2007", new ObjectFactory().createProvideAndRegisterDocumentSetRequest(parsedRequest), false);
        OrchestrateProvideAndRegisterRequestResponse response = new OrchestrateProvideAndRegisterRequestResponse(originalRequest, messageBuffer);
        originalRequest.getRespondTo().tell(response, getSelf());
    }

    private void respondBadRequest(String error) {
        FinishRequest fr = new FinishRequest(error, "text/plain", HttpStatus.SC_BAD_REQUEST);
        originalRequest.getRequestHandler().tell(fr, getSelf());
    }

    private boolean areAllIdentifiersResolved() {
        return areAllIdentifiersResolvedForList(enterprisePatientIds) &&
                areAllIdentifiersResolvedForList(enterpriseHealthcareWorkerIds) &&
                areAllIdentifiersResolvedForList(enterpriseFacilityIds);
    }

    private boolean areAllIdentifiersResolvedForList(List<IdentifierMapping> lst) {
        for (IdentifierMapping mapping : lst) {
            if (!mapping.resolved) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return null if all identifiers were resolved successfully, else an error message listing all failed identifiers
     */
    private String getResolveIdentifierErrors() {
        List<IdentifierMapping> unsuccessfulPatientIDs = getAllUnsuccessfulIdentifiers(enterprisePatientIds);
        List<IdentifierMapping> unsuccessfulHealthcareWorkerIDs = getAllUnsuccessfulIdentifiers(enterpriseHealthcareWorkerIds);
        List<IdentifierMapping> unsuccessfulFacilityIDs = getAllUnsuccessfulIdentifiers(enterpriseFacilityIds);

        //all successful
        if (unsuccessfulPatientIDs.isEmpty() && unsuccessfulHealthcareWorkerIDs.isEmpty() && unsuccessfulFacilityIDs.isEmpty()) {
            return null;
        }

        StringBuilder errors = new StringBuilder();

        if (!unsuccessfulPatientIDs.isEmpty()) {
            errors.append("Failed to resolve patient identifiers for:\n");
            for (IdentifierMapping id : unsuccessfulPatientIDs) {
                errors.append(id.fromId.toCX() + "\n");
            }
            errors.append("\n");
        }

        if (!unsuccessfulHealthcareWorkerIDs.isEmpty()) {
            errors.append("Failed to resolve healthcare worker identifiers for:\n");
            for (IdentifierMapping id : unsuccessfulHealthcareWorkerIDs) {
                errors.append(id.fromId.toXCN() + "\n");
            }
            errors.append("\n");
        }

        if (!unsuccessfulFacilityIDs.isEmpty()) {
            errors.append("Failed to resolve facility identifiers for:\n");
            for (IdentifierMapping id : unsuccessfulFacilityIDs) {
                FacilityIdentifierMapping fim = ((FacilityIdentifierMapping) id);
                errors.append(fim.fromId.toXON(fim.localLocationName) + "\n");
            }
            errors.append("\n");
        }

        return errors.toString();
    }

    private List<IdentifierMapping> getAllUnsuccessfulIdentifiers(List<IdentifierMapping> lst) {
        List<IdentifierMapping> result = new LinkedList<>();

        for (IdentifierMapping mapping : lst) {
            if (mapping.resolved && !mapping.successful) {
                result.add(mapping);
            }
        }

        return result;
    }

    private void processRegisterNewPatientResponse(RegisterNewPatientResponse response) {
        if (response.isSuccessful()) {
            log.info("Patient successfully registered. Resending resolve identifier request(s).");

            for (IdentifierMapping mapping : failedPatientIds) {
                resolvePatientIdHandler.tell(mapping.resolveRequestMessage, getSelf());
            }
        } else {
            respondBadRequest(response.getErr());
        }
    }

    private void sendAuditMessage(ATNAAudit.TYPE type, boolean outcome) {
        try {
            ATNAAudit audit = new ATNAAudit(type);
            audit.setMessage(messageBuffer);

            List<Identifier> participants = new ArrayList<>(enterprisePatientIds.size());
            for (IdentifierMapping mapping : enterprisePatientIds) {
                participants.add(mapping.fromId);
            }
            audit.setParticipantIdentifiers(participants);

            RegistryPackageType regPac = InfosetUtil.getRegistryPackage(parsedRequest.getSubmitObjectsRequest(), XDSConstants.UUID_XDSSubmissionSet);
            String uniqueId = InfosetUtil.getExternalIdentifierValue(XDSConstants.UUID_XDSSubmissionSet_uniqueId, regPac);
            audit.setUniqueId(uniqueId);
            audit.setOutcome(outcome);
            audit.setSourceIP(xForwardedFor);

            getContext().actorSelection(config.userPathFor("atna-auditing")).tell(audit, getSelf());
        } catch (Exception ex) {
            //quiet you!
        }
    }


    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof OrchestrateProvideAndRegisterRequest) {
            log.info("Orchestrating XDS.b Provide and Register request");

            originalRequest = (OrchestrateProvideAndRegisterRequest) msg;
            xForwardedFor = ((OrchestrateProvideAndRegisterRequest) msg).getXForwardedFor();
            parseRequest((OrchestrateProvideAndRegisterRequest) msg);

        } else if (SimpleMediatorResponse.isInstanceOf(ProvideAndRegisterDocumentSetRequestType.class, msg)) { //response from parser
            processParsedRequest(((SimpleMediatorResponse<ProvideAndRegisterDocumentSetRequestType>) msg).getResponseObject());

        } else if (msg instanceof ResolvePatientIdentifierResponse) {
            processResolvedPatientId((ResolvePatientIdentifierResponse) msg);
            checkAndRespondIfAllResolved();
        } else if (msg instanceof ResolveHealthcareWorkerIdentifierResponse) {
            processResolvedHealthcareWorkerId((ResolveHealthcareWorkerIdentifierResponse) msg);
            checkAndRespondIfAllResolved();
        } else if (msg instanceof ResolveFacilityIdentifierResponse) {
            processResolvedFacilityId((ResolveFacilityIdentifierResponse) msg);
            checkAndRespondIfAllResolved();

        } else if (msg instanceof RegisterNewPatientResponse) {
            processRegisterNewPatientResponse((RegisterNewPatientResponse) msg);

        } else {
            unhandled(msg);
        }
    }


}
