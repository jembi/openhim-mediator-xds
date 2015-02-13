/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator.e2e;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.openhim.mediator.denormalization.ATNAAuditingActor;
import org.openhim.mediator.denormalization.EnrichRegistryStoredQueryActor;
import org.openhim.mediator.engine.MediatorConfig;
import org.openhim.mediator.engine.MediatorServer;
import org.openhim.mediator.engine.RoutingTable;
import org.openhim.mediator.engine.StartupActorsConfig;
import org.openhim.mediator.engine.connectors.MLLPConnector;
import org.openhim.mediator.normalization.ParseRegistryStoredQueryActor;
import org.openhim.mediator.orchestration.RegistryActor;
import org.openhim.mediator.orchestration.RepositoryActor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.junit.Assert.*;

/**
 */
public class E2EBase {
    protected static abstract class MockPIXServer extends Thread {
        ServerSocket socket;
        int called = 0;

        public MockPIXServer(int port) {
            try {
                socket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
        }

        public void kill() {
            IOUtils.closeQuietly(socket);
        }

        abstract String getResponse();
        abstract void onReceive(String receivedMessage);

        public void verifyCalled() {
            assertTrue(called>0);
        }

        public void verifyCalled(int numTimesCalled) {
            assertTrue(called == numTimesCalled);
        }

        @Override
        public void run() {
            try {
                called = 0;
                do {
                    final Socket conn = socket.accept();

                    (new Thread() {
                        @Override
                        public void run() {
                            try {
                                ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024 * 1024);
                                InputStream in = conn.getInputStream();
                                int lastByte = -1;
                                int lastLastByte;
                                do {
                                    lastLastByte = lastByte;
                                    lastByte = in.read();
                                    if (lastByte != -1) {
                                        buffer.write(lastByte);
                                    }
                                }
                                while (lastByte != -1 && lastLastByte != MLLPConnector.MLLP_FOOTER_FS && lastByte != MLLPConnector.MLLP_FOOTER_CR);

                                String receivedMessage = buffer.toString();
                                onReceive(receivedMessage);

                                conn.getOutputStream().write(MLLPConnector.wrapMLLP(getResponse()).getBytes());

                            } catch (IOException e) {
                                System.out.println("Warning: " + e.getMessage());
                            }
                        }
                    }).start();

                    called++;
                } while (!socket.isClosed());
            } catch (IOException e) {
                System.out.println("Warning: " + e.getMessage());
            }
        }
    }

    protected static class MockATNAServer extends Thread {
        ServerSocket socket;
        List<String> calledFor = Collections.synchronizedList(new LinkedList<String>());
        Integer activeConnections = 0;

        public MockATNAServer(int port) {
            try {
                socket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
        }

        public void kill() {
            IOUtils.closeQuietly(socket);
        }

        public void verifyCalled() {
            waitOnActiveConnections(null);
            assertTrue(calledFor.size()>0);
        }

        public void verifyCalled(int numTimesCalled) {
            waitOnActiveConnections(numTimesCalled);
            assertTrue(calledFor.size() == numTimesCalled);
        }

        public void verifyCalledFor(String eventType) {
            waitOnActiveConnections(null);
            for (String ev : calledFor) {
                if (ev.equals(eventType)) {
                    return;
                }
            }
            fail("ATNA audit should be sent for event " + eventType);
        }

        //atna runs asynchronously and will likely be only complete after a request finishes, so we need to wait
        private void waitOnActiveConnections(Integer expectedNumCalls) {
            int tries = 0;
            while ((activeConnections > 0 //are there active connections?
                    || (expectedNumCalls!=null && calledFor.size()<expectedNumCalls)) //or maybe they haven't had a chance to run yet?
                && tries<600) //but don't wait for more than 60 seconds (600*100ms)
            {
                try {
                    Thread.sleep(100);

                    tries++;
                } catch (InterruptedException e) {}
            }
        }

        private void alive() {
            synchronized (activeConnections) {
                activeConnections++;
            }
        }

        private void dead() {
            synchronized (activeConnections) {
                activeConnections--;
            }
        }

        @Override
        public void run() {
            try {
                calledFor.clear();
                do {
                    final Socket conn = socket.accept();

                    (new Thread() {
                        @Override
                        public void run() {
                            try {
                                alive();
                                String message = IOUtils.toString(conn.getInputStream());

                                int startI = message.indexOf("EventTypeCode code=\"");
                                if (startI==-1) {
                                    fail("Unparseable ATNA audit received");
                                    return;
                                }

                                message = message.substring(startI + "EventTypeCode code=\"".length());
                                int endI = message.indexOf("\"");
                                if (endI==-1) {
                                    fail("Unparseable ATNA audit received");
                                    return;
                                }

                                message = message.substring(0, endI);
                                calledFor.add(message);
                            } catch (IOException e) {
                                System.out.println("Warning: " + e.getMessage());
                            } finally {
                                dead();
                            }
                        }
                    }).start();
                } while (!socket.isClosed());
            } catch (IOException e) {
                System.out.println("Warning: " + e.getMessage());
            }
        }
    }

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8520);

    protected MediatorConfig testConfig;
    protected MediatorServer server;
    protected MockATNAServer atnaServer;


    private void loadTestConfig() throws RoutingTable.RouteAlreadyMappedException, IOException {
        testConfig = new MediatorConfig();
        testConfig.setProperties("mediator-e2e-test.properties");

        testConfig.setName("mediator-server-tests");
        testConfig.setServerHost("localhost");
        testConfig.setServerPort(8432);

        testConfig.setName(testConfig.getProperty("mediator.name"));
        testConfig.setServerHost(testConfig.getProperty("mediator.host"));
        testConfig.setServerPort( Integer.parseInt(testConfig.getProperty("mediator.port")) );
        testConfig.setRootTimeout(Integer.parseInt(testConfig.getProperty("mediator.timeout")));

        testConfig.setCoreHost(testConfig.getProperty("core.host"));
        testConfig.setCoreAPIUsername(testConfig.getProperty("core.api.user"));
        testConfig.setCoreAPIPassword(testConfig.getProperty("core.api.password"));
        testConfig.setCoreAPIPort(Integer.parseInt(testConfig.getProperty("core.api.port")));

        RoutingTable routingTable = new RoutingTable();
        routingTable.addRoute("/xdsregistry", RegistryActor.class);
        routingTable.addRoute("/xdsrepository", RepositoryActor.class);
        testConfig.setRoutingTable(routingTable);

        StartupActorsConfig startupActors = new StartupActorsConfig();
        startupActors.addActor("parse-registry-stored-query", ParseRegistryStoredQueryActor.class);
        startupActors.addActor("enrich-registry-stored-query", EnrichRegistryStoredQueryActor.class);
        startupActors.addActor("atna-auditing", ATNAAuditingActor.class);
        testConfig.setStartupActors(startupActors);
    }

    @Before
    public void before() throws RoutingTable.RouteAlreadyMappedException, IOException {
        loadTestConfig();
        atnaServer = new MockATNAServer(Integer.parseInt(testConfig.getProperty("atna.tcpPort")));
        atnaServer.start();
        server = new MediatorServer(testConfig);
        server.start(false);
    }

    @After
    public void after() {
        atnaServer.kill();
        server.stop();
    }


    private HttpUriRequest buildUriRequest(String method, String body, URI uri) throws UnsupportedEncodingException {
        HttpUriRequest uriReq;

        switch (method) {
            case "GET":
                uriReq = new HttpGet(uri);
                break;
            case "POST":
                uriReq = new HttpPost(uri);
                StringEntity entity = new StringEntity(body);
                if (body.length()>1024) {
                    //always test big requests chunked
                    entity.setChunked(true);
                }
                ((HttpPost) uriReq).setEntity(entity);
                break;
            case "PUT":
                uriReq = new HttpPut(uri);
                StringEntity putEntity = new StringEntity(body);
                ((HttpPut) uriReq).setEntity(putEntity);
                break;
            case "DELETE":
                uriReq = new HttpDelete(uri);
                break;
            default:
                throw new UnsupportedOperationException(method + " requests not supported");
        }

        return uriReq;
    }

    protected E2EHTTPResponse executeHTTPRequest(String method, String path, String body, Map<String, String> headers, Map<String, String> params) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder()
                .setScheme("http")
                .setHost(testConfig.getServerHost())
                .setPort(testConfig.getServerPort())
                .setPath(path);

        if (params!=null) {
            Iterator<String> iter = params.keySet().iterator();
            while (iter.hasNext()) {
                String param = iter.next();
                builder.addParameter(param, params.get(param));
            }
        }

        HttpUriRequest uriReq = buildUriRequest(method, body, builder.build());

        if (headers!=null) {
            Iterator<String> iter = headers.keySet().iterator();
            while (iter.hasNext()) {
                String header = iter.next();
                uriReq.addHeader(header, headers.get(header));
            }
        }

        RequestConfig.Builder reqConf = RequestConfig.custom()
                .setConnectTimeout(1000)
                .setConnectionRequestTimeout(1000);
        CloseableHttpClient client = HttpClientBuilder.create()
                .setDefaultRequestConfig(reqConf.build())
                .build();

        CloseableHttpResponse response = client.execute(uriReq);
        E2EHTTPResponse finalResponse = new E2EHTTPResponse();

        if (response.getEntity()!=null && response.getEntity().getContent()!=null) {
            finalResponse.body = IOUtils.toString(response.getEntity().getContent());
        }

        finalResponse.status = response.getStatusLine().getStatusCode();

        for (Header hdr : response.getAllHeaders()) {
            finalResponse.headers.put(hdr.getName(), hdr.getValue());
        }

        IOUtils.closeQuietly(response);
        return finalResponse;
    }

    public static class E2EHTTPResponse {
        Integer status;
        String body;
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
}
