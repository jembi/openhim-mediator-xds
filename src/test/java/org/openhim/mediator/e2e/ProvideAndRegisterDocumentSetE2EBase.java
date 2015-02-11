/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Base class for Provide and Register Document Set.b e2e tests
 */
public abstract class ProvideAndRegisterDocumentSetE2EBase extends E2EBase {

    protected static class PnRQueryPIXServer extends MockPIXServer {

        public PnRQueryPIXServer(int port) {
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

    PnRQueryPIXServer pixServer;
    String pnrResponse;


    private void stubRegistries() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("pnrResponse_e2e.xml");
        pnrResponse = IOUtils.toString(in);

        stubFor(post(urlEqualTo("/openmrs-standalone/ms/xdsrepository"))
                .willReturn(aResponse().withStatus(201).withBody(pnrResponse).withHeader("Content-Type", "multipart/related"))
        );

        String csdResponse = "<CSD xmlns='urn:ihe:iti:csd:2013'>\n"
                + "  <serviceDirectory/>\n"
                + "  <organizationDirectory/>\n"
                + "  <providerDirectory>\n"
                + "    <provider entityID='urn:oid:1.2.3.1234'>\n"
                + "      <!-- POTENTIALLY LARGE AMOUNT OF CONTENT ON THE PROVIDER -->\n"
                + "    </provider>\n"
                + "  </providerDirectory>\n"
                + "  <facilityDirectory>\n"
                + "    <facility entityID='urn:oid:1.2.3.2345'>\n"
                + "      <!-- POTENTIALLY LARGE AMOUNT OF CONTENT ON THE FACILITY -->\n"
                + "    </facility>\n"
                + "  </facilityDirectory>\n"
                + "</CSD>\n";
        stubFor(post(urlEqualTo("/CSD/csr/jembi-ecgroup-testing/careServicesRequest"))
                        .willReturn(aResponse().withStatus(200).withBody(csdResponse).withHeader("Content-Type", "application/xml"))
        );
    }

    @Override
    public void before() throws RoutingTable.RouteAlreadyMappedException, IOException {
        super.before();
        pixServer = new PnRQueryPIXServer(Integer.parseInt(testConfig.getProperty("pix.manager.port")));
        pixServer.start();
        stubRegistries();
    }

    @Override
    public void after() {
        super.after();
        pixServer.kill();
    }
}
