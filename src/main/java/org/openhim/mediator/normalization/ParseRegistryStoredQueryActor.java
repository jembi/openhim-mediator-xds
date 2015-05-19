/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.normalization;

import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.openhim.mediator.datatypes.AssigningAuthority;
import org.openhim.mediator.datatypes.Identifier;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.exceptions.CXParseException;
import org.openhim.mediator.messages.ParsedRegistryStoredQuery;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;

/**
 * Parses registry stored query messages.
 * <br/><br/>
 * Messages supported:
 * <ul>
 * <li>SimpleMediatorRequest<String> - responds with ParsedRegistryStoredQuery</li>
 * </ul>
 */
public class ParseRegistryStoredQueryActor extends UntypedActor {

    LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    public static final String PATIENT_ID_SLOT_TYPE = "$XDSDocumentEntryPatientId";
    private static final String BASE_XPATH_EXPRESSION = "//AdhocQueryRequest[1]/AdhocQuery/Slot[@name='%s']/ValueList[1]/Value";
    private static final String MESSAGEID_XPATH_EXPRESSION = "Envelope/Header/MessageID";


    private String readPatientID(String msg) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(IOUtils.toInputStream(msg));
        XPath xpath = XPathFactory.newInstance().newXPath();
        return xpath.compile(String.format(BASE_XPATH_EXPRESSION, PATIENT_ID_SLOT_TYPE)).evaluate(doc);
    }

    private String readSOAPMessageID(String msg) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(IOUtils.toInputStream(msg));
        XPath xpath = XPathFactory.newInstance().newXPath();
        return xpath.compile(MESSAGEID_XPATH_EXPRESSION).evaluate(doc);
    }


    private void processMsg(SimpleMediatorRequest<String> msg) {
        try {
            String patientID_CX = readPatientID(msg.getRequestObject());
            patientID_CX = patientID_CX.replace("'", "");
            Identifier patientID = new Identifier(patientID_CX);

            String messageID = readSOAPMessageID(msg.getRequestObject());

            msg.getRespondTo().tell(new ParsedRegistryStoredQuery(patientID, messageID), getSelf());
        } catch (SAXException | CXParseException ex) {
            FinishRequest fr = new FinishRequest(ex.getMessage(), "text/plain", HttpStatus.SC_BAD_REQUEST);
            msg.getRequestHandler().tell(fr, getSelf());
        } catch (ParserConfigurationException | IOException | XPathExpressionException ex) {
            msg.getRequestHandler().tell(new ExceptError(ex), getSelf());
        }
    }
    @Override
    public void onReceive(Object msg) throws Exception {
        if (SimpleMediatorRequest.isInstanceOf(String.class, msg)) {
            processMsg((SimpleMediatorRequest<String>) msg);
        } else {
            unhandled(msg);
        }
    }
}
