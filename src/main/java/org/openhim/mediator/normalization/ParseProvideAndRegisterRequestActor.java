package org.openhim.mediator.normalization;

import akka.actor.UntypedActor;
import org.apache.commons.io.IOUtils;
import org.openhim.mediator.engine.messages.ExceptError;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.messages.ParsedProvideAndRegisterRequest;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

/**
 * Parses XDS.b Provide and Register Document Set transactions. Only pulls out the info the HIM needs.
 * <br/><br/>
 * Messages supported:
 * <ul>
 *     <li>SimpleMediatorRequest<String> - responds with ParsedProvideAndRegisterRequest</li>
 * </ul>
 */
public class ParseProvideAndRegisterRequestActor extends UntypedActor {

    ParsedProvideAndRegisterRequest result;
    Document document;

    private void processMsg(SimpleMediatorRequest<String> msg) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = builder.parse(IOUtils.toInputStream(msg.getRequestObject()));
            result = new ParsedProvideAndRegisterRequest();

            //...

            msg.getRespondTo().tell(result, getSelf());
        } catch (ParserConfigurationException | SAXException | IOException ex) {
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
