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
 * ITI-42: Register Document Set.b
 *
 * path: /xdsregistry
 *
 * Should pass-through the request unaltered
 */
public class RegisterDocumentSetE2ETest extends E2EBase {

    private void stubRegistry() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("RegisterDocumentSet-bResponse_SOAP.xml");
        String adhocResponse = IOUtils.toString(in);
        stubFor(post(urlEqualTo("/axis2/services/xdsregistryb"))
                .willReturn(aResponse().withStatus(201).withBody(adhocResponse).withHeader("Content-Type", "application/soap+xml"))
        );
    }

    @Override
    public void before() throws RoutingTable.RouteAlreadyMappedException, IOException {
        super.before();
        stubRegistry();
    }


    @Test
    public void runRegisterDocumentSetE2ETest() throws IOException, URISyntaxException {
        InputStream in = getClass().getClassLoader().getResourceAsStream("RegisterDocumentSet-bRequest_SOAP.xml");
        String rds = IOUtils.toString(in);

        E2EHTTPResponse response = executeHTTPRequest("POST", "/xdsregistry", rds, Collections.singletonMap("Content-Type", "application/soap+xml"), null);
        assertEquals(new Integer(201), response.status);

        verify(
                postRequestedFor(urlEqualTo("/axis2/services/xdsregistryb"))
                        .withHeader("Content-Type", equalTo("application/soap+xml"))
                        .withRequestBody(equalToXml(rds))
        );
    }
}
