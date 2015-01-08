package org.openhim.mediator.denormalization;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPRequest;
import org.openhim.mediator.engine.messages.MediatorHTTPResponse;
import org.openhim.mediator.exceptions.ValidationException;
import org.openhim.mediator.messages.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Messages supported:
 * <ul>
 *     <li>ResolveHealthcareWorkerIdentifier - responds with ResolveHealthcareWorkerIdentifierResponse</li>
 *     <li>ResolveFacilityIdentifier - responds with ResolveFacilityIdentifierResponse</li>
 * </ul>
 */
public class CSDRequestActor extends UntypedActor {
    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    public static final String UUID_OID_AUTHORITY = "2.25";

    private MediatorConfig config;
    private Map<String, BaseResolveIdentifier> originalRequests = new HashMap<>();


    public CSDRequestActor(MediatorConfig config) {
        this.config = config;
    }

    private void sendCSDRequest(String request, BaseResolveIdentifier originalRequest) {
        String correlationId = UUID.randomUUID().toString();
        originalRequests.put(correlationId, originalRequest);

        ActorSelection httpConnector = getContext().actorSelection("/user/" + config.getName() + "/http-connector");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/xml");
        MediatorHTTPRequest httpRequest = new MediatorHTTPRequest(
                originalRequest.getRequestHandler(), getSelf(), "CSD", "POST", "http",
                config.getProperties().getProperty("ilr.host"),
                Integer.parseInt(config.getProperties().getProperty("ilr.port")),
                config.getProperties().getProperty("ilr.csr.path"),
                request, headers, null, correlationId
        );

        httpConnector.tell(httpRequest, getSelf());
    }

    private void sendResolveHealthcareWorkerIdentifierRequest(ResolveHealthcareWorkerIdentifier msg) {
        String csdTemplate = "<csd:careServicesRequest xmlns='urn:ihe:iti:csd:2013' xmlns:csd='urn:ihe:iti:csd:2013'>"
                + "	<function urn='urn:ihe:iti:csd:2014:stored-function:provider-search'>"
                + "		<requestParams>"
                + "			<otherID code='" + msg.getIdentifier() + "' assigningAuthorityName='" + msg.getIdentifier().getAssigningAuthority().getAssigningAuthorityId() + "'/>"
                + "		</requestParams>"
                + "	</function>"
                + "</csd:careServicesRequest>";

        sendCSDRequest(csdTemplate, msg);
    }

    private void sendResolveFacilityIdentifierRequest(ResolveFacilityIdentifier msg) {
        String csdTemplate = "<csd:careServicesRequest xmlns='urn:ihe:iti:csd:2013' xmlns:csd='urn:ihe:iti:csd:2013'>"
                + "	<function urn='urn:ihe:iti:csd:2014:stored-function:facility-search'>"
                + "		<requestParams>"
                + "			<otherID code='" + msg.getIdentifier() + "' assigningAuthorityName='" + msg.getIdentifier().getAssigningAuthority().getAssigningAuthorityId() + "'/>"
                + "		</requestParams>"
                + "	</function>"
                + "</csd:careServicesRequest>";

        sendCSDRequest(csdTemplate, msg);
    }

    private String getXPAthExpressionForQueryType(BaseResolveIdentifier query) throws XPathExpressionException {
        if (query instanceof ResolveHealthcareWorkerIdentifier) {
            return "//CSD/providerDirectory/provider/@entityID";
        } else if (query instanceof ResolveFacilityIdentifier) {
            return "//CSD/facilityDirectory/facility/@entityID";
        }

        throw new XPathExpressionException("Cannot create expression for unknown BaseResolveIdentifier class");
    }

    protected static Identifier buildIdentifier(String resolvedId) throws ValidationException {
        if (resolvedId.startsWith("urn:uuid:")) {
            resolvedId = resolvedId.replace("urn:uuid:", "");
            return new Identifier(resolvedId, new AssigningAuthority("", UUID_OID_AUTHORITY));
        } else if (resolvedId.startsWith("urn:oid:")) {
            try {
                resolvedId = resolvedId.replace("urn:oid:", "");
                String id = resolvedId.substring(resolvedId.lastIndexOf('.') + 1);
                String authId = resolvedId.substring(0, resolvedId.lastIndexOf('.'));
                return new Identifier(id, new AssigningAuthority("", authId));
            } catch (StringIndexOutOfBoundsException ex) {
                throw new ValidationException("Received identifier could not be parsed as an OID");
            }
        } else {
            throw new ValidationException("Unsupported id received");
        }
    }

    private BaseResolveIdentifierResponse buildResponse(BaseResolveIdentifier originalRequest, String resolvedId) throws ValidationException {
        Identifier id = buildIdentifier(resolvedId);

        if (originalRequest instanceof ResolveHealthcareWorkerIdentifier) {
            return new ResolveHealthcareWorkerIdentifierResponse(originalRequest, id);
        } else if (originalRequest instanceof ResolveFacilityIdentifier) {
            return new ResolveFacilityIdentifierResponse(originalRequest, id);
        }

        throw new RuntimeException("Unknown BaseResolveIdentifier class");
    }

    private void processHTTPResponse(MediatorHTTPResponse response) {
        BaseResolveIdentifier originalRequest = originalRequests.remove(response.getOriginalRequest().getCorrelationId());
        String csdResponse = response.getBody();

        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(IOUtils.toInputStream(csdResponse));
            XPath xpath = XPathFactory.newInstance().newXPath();
            String resolvedId = xpath.compile(getXPAthExpressionForQueryType(originalRequest)).evaluate(doc);
            if (resolvedId==null || resolvedId.isEmpty()) {
                throw new ValidationException("Failed to read identifier from CSD response");
            }

            BaseResolveIdentifierResponse finalResponse = buildResponse(originalRequest, resolvedId);
            originalRequest.getRespondTo().tell(finalResponse, getSelf());
        } catch (ValidationException ex) {
            FinishRequest fr = new FinishRequest(ex.getMessage(), "text/plain", HttpStatus.SC_BAD_REQUEST);
            originalRequest.getRequestHandler().tell(fr, getSelf());
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException ex) {
            originalRequest.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        if (msg instanceof ResolveHealthcareWorkerIdentifier) {
            sendResolveHealthcareWorkerIdentifierRequest((ResolveHealthcareWorkerIdentifier) msg);
        } else if (msg instanceof ResolveFacilityIdentifier) {
            sendResolveFacilityIdentifierRequest((ResolveFacilityIdentifier) msg);
        } else if (msg instanceof MediatorHTTPResponse) {
            processHTTPResponse((MediatorHTTPResponse) msg);
        } else {
            unhandled(msg);
        }
    }
}
