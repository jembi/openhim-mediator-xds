/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.normalization;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import ihe.iti.xds_b._2007.ProvideAndRegisterDocumentSetRequestType;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.openhim.mediator.engine.CoreResponse;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.messages.AddOrchestrationToCoreResponse;
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
 *     <li>SimpleMediatorRequest<String> - responds with SimpleMediatorResponse<ProvideAndRegisterDocumentSetRequestType></li>
 * </ul>
 */
public class ParseProvideAndRegisterRequestActor extends UntypedActor {

    private MediatorConfig config;

    public ParseProvideAndRegisterRequestActor() {
    }

    public ParseProvideAndRegisterRequestActor(MediatorConfig config) {
        this.config = config;
    }


    public static ProvideAndRegisterDocumentSetRequestType parseRequest(String document) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance("ihe.iti.xds_b._2007");
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement result = (JAXBElement)(unmarshaller.unmarshal(IOUtils.toInputStream(document)));
        return (ProvideAndRegisterDocumentSetRequestType) result.getValue();
    }


    private void processMsg(SimpleMediatorRequest<String> msg) {
        ActorRef requestHandler = msg.getRequestHandler();
        
        CoreResponse.Orchestration orch = null;
        boolean sendParseOrchestration = (config==null || config.getProperty("pnr.sendParseOrchestration")==null ||
                "true".equalsIgnoreCase(config.getProperty("pnr.sendOrchestration")));

        try {
            if (sendParseOrchestration) {
                orch = new CoreResponse.Orchestration();
                orch.setName("Parse Provider and Register Document Set.b contents");
                orch.setRequest(new CoreResponse.Request());
            }

            ProvideAndRegisterDocumentSetRequestType result = parseRequest(msg.getRequestObject());
            msg.getRespondTo().tell(new SimpleMediatorResponse<>(msg, result), getSelf());

            if (sendParseOrchestration) {
                orch.setResponse(new CoreResponse.Response());
                requestHandler.tell(new AddOrchestrationToCoreResponse(orch), getSelf());
            }

        } catch (JAXBException ex) {
            FinishRequest fr = new FinishRequest("Failed to parse XDS.b Provide and Register Document Set request: " + ex.getMessage(), "text/plain", HttpStatus.SC_BAD_REQUEST);
            requestHandler.tell(fr, getSelf());
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
