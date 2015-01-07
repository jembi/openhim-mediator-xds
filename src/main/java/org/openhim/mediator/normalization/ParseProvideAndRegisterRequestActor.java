package org.openhim.mediator.normalization;

import akka.actor.UntypedActor;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.messages.FinishRequest;
import org.openhim.mediator.engine.messages.SimpleMediatorRequest;
import org.openhim.mediator.engine.messages.SimpleMediatorResponse;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

/**
 * Parses XDS.b Provide and Register Document Set transactions.
 * <br/><br/>
 * Messages supported:
 * <ul>
 *     <li>SimpleMediatorRequest<String> - responds with SimpleMediatorResponse<></li>
 * </ul>
 *
 * TODO Parsing the DOM is really slow! Around 800ms during unit testing
 */
public class ParseProvideAndRegisterRequestActor extends UntypedActor {

    public ProvideAndRegisterDocumentSetRequestType parseRequest(String document) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance("ihe.iti.xds_b._2007");
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement result = (JAXBElement)(unmarshaller.unmarshal(IOUtils.toInputStream(document)));
        return (ProvideAndRegisterDocumentSetRequestType) result.getValue();
    }


    private void processMsg(SimpleMediatorRequest<String> msg) {
        try {
            ProvideAndRegisterDocumentSetRequestType result = parseRequest(msg.getRequestObject());
            msg.getRespondTo().tell(new SimpleMediatorResponse<>(msg, result), getSelf());
        } catch (JAXBException ex) {
            FinishRequest fr = new FinishRequest("Failed to parse XDS.b Provide and Register Document Set request: " + ex.getMessage(), "text/plain", HttpStatus.SC_BAD_REQUEST);
            msg.getRequestHandler().tell(fr, getSelf());
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
