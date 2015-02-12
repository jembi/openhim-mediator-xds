/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.e2e;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.openhim.mediator.engine.RoutingTable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

/**
 * ITI-18: Registry stored query - Adhoc Query
 *
 * path: /xdsregistry
 *
 * Should enrich the request with an ECID and forward the enriched result to the registry
 */
public class AdhocQueryE2ETest extends E2EBase {

    protected static class AdhocQueryPIXServer extends MockPIXServer {

        public AdhocQueryPIXServer(int port) {
            super(port);
        }

        @Override
        String getResponse() {
            InputStream in = getClass().getClassLoader().getResourceAsStream("pixResponse-ECID1.er7");
            try {
                return IOUtils.toString(in);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
            return null;
        }

        @Override
        void onReceive(String receivedMessage) {
        }
    }

    AdhocQueryPIXServer pixServer;


    private void stubRegistry() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("adhocQueryResponse_wSOAP.xml");
        String adhocResponse = IOUtils.toString(in);
        stubFor(post(urlEqualTo("/axis2/services/xdsregistryb"))
                .willReturn(aResponse().withStatus(201).withBody(adhocResponse).withHeader("Content-Type", "application/soap+xml"))
        );
    }

    @Override
    public void before() throws RoutingTable.RouteAlreadyMappedException, IOException {
        super.before();
        pixServer = new AdhocQueryPIXServer(Integer.parseInt(testConfig.getProperty("pix.manager.port")));
        pixServer.start();
        stubRegistry();
    }

    @Override
    public void after() {
        super.after();
        pixServer.kill();
    }


    @Test
    public void runAdhocQueryE2ETest() throws IOException, URISyntaxException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("adhocQueryRequest_wSOAP.xml");
        String adhoc = IOUtils.toString(in);
        InputStream in2 = getClass().getClassLoader().getResourceAsStream("adhocQueryRequest_enriched_wSOAP.xml");
        String expectedEnrichedAdhoc = IOUtils.toString(in2);

        E2EHTTPResponse response = executeHTTPRequest("POST", "/xdsregistry", adhoc, Collections.singletonMap("Content-Type", "application/soap+xml"), null);
        assertEquals(new Integer(201), response.status);

        pixServer.verifyCalled();

        atnaServer.verifyCalled(3);
        atnaServer.verifyCalledFor("ITI-18");
        atnaServer.verifyCalledFor("ITI-9");

        verify(
                postRequestedFor(urlEqualTo("/axis2/services/xdsregistryb"))
                        .withHeader("Content-Type", equalTo("application/soap+xml"))
                        .withRequestBody(equalToXml(expectedEnrichedAdhoc))
        );
    }
}
