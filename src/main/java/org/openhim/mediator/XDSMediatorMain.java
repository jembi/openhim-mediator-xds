/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.openhim.mediator;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.openhim.mediator.denormalization.ATNAAuditingActor;
import org.openhim.mediator.denormalization.EnrichRegistryStoredQueryActor;
import org.openhim.mediator.engine.*;
import org.openhim.mediator.engine.messages.SetupHTTPSCertificate;
import org.openhim.mediator.normalization.ParseRegistryStoredQueryActor;
import org.openhim.mediator.orchestration.RegistryActor;
import org.openhim.mediator.orchestration.RepositoryActor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class XDSMediatorMain {

    private static RoutingTable buildRoutingTable() throws RoutingTable.RouteAlreadyMappedException {
        RoutingTable routingTable = new RoutingTable();
        routingTable.addRoute("/xdsregistry", RegistryActor.class);
        routingTable.addRoute("/xdsrepository", RepositoryActor.class);
        return routingTable;
    }

    private static StartupActorsConfig buildStartupActorsConfig() {
        StartupActorsConfig startupActors = new StartupActorsConfig();
        startupActors.addActor("parse-registry-stored-query", ParseRegistryStoredQueryActor.class);
        startupActors.addActor("enrich-registry-stored-query", EnrichRegistryStoredQueryActor.class);
        startupActors.addActor("atna-auditing", ATNAAuditingActor.class);
        return startupActors;
    }

    private static MediatorConfig loadConfig(String configPath) throws IOException, RoutingTable.RouteAlreadyMappedException {
        MediatorConfig config = new MediatorConfig();

        if (configPath!=null) {
            Properties props = new Properties();
            File conf = new File(configPath);
            InputStream in = FileUtils.openInputStream(conf);
            props.load(in);
            IOUtils.closeQuietly(in);

            config.setProperties(props);
        } else {
            config.setProperties("mediator.properties");
        }

        config.setName(config.getProperty("mediator.name"));
        config.setServerHost(config.getProperty("mediator.host"));
        config.setServerPort( Integer.parseInt(config.getProperty("mediator.port")) );
        config.setRootTimeout(Integer.parseInt(config.getProperty("mediator.timeout")));

        config.setCoreHost(config.getProperty("core.host"));
        config.setCoreAPIUsername(config.getProperty("core.api.user"));
        config.setCoreAPIPassword(config.getProperty("core.api.password"));
        if (config.getProperty("core.api.port") != null) {
            config.setCoreAPIPort(Integer.parseInt(config.getProperty("core.api.port")));
        }

        config.setRoutingTable(buildRoutingTable());
        config.setStartupActors(buildStartupActorsConfig());

        InputStream regInfo = XDSMediatorMain.class.getClassLoader().getResourceAsStream("mediator-registration-info.json");
        RegistrationConfig regConfig = new RegistrationConfig(regInfo);
        config.setRegistrationConfig(regConfig);

        return config;
    }

    private static void loadSSLConfig(MediatorConfig config) {
        System.setProperty("javax.net.ssl.keyStore", config.getProperty("ihe.keystore"));
        System.setProperty("javax.net.ssl.keyStorePassword", config.getProperty("ihe.keypassword"));
        System.setProperty("javax.net.ssl.trustStore", config.getProperty("ihe.keystore"));
        System.setProperty("javax.net.ssl.trustStorePassword", config.getProperty("ihe.storepassword"));
    }

    private static boolean isSecure(MediatorConfig config) {
        return  (config.getProperty("pix.secure").equalsIgnoreCase("true")
                || config.getProperty("xds.registry.secure").equalsIgnoreCase("true")
                || config.getProperty("xds.repository.secure").equalsIgnoreCase("true")
                || config.getProperty("atna.secure").equalsIgnoreCase("true"));
    }

    public static void main(String... args) throws Exception {

        //setup actor system
        final ActorSystem system = ActorSystem.create("mediator");
        //setup logger for main
        final LoggingAdapter log = Logging.getLogger(system, "main");

        //setup server
        log.info("Initializing mediator server...");

        String configPath = null;
        //for now only --conf param is supported... poorly
        //TODO support custom config for mediator registration info json
        //TODO better parameter handling
        if (args.length==2 && args[0].equals("--conf")) {
            configPath = args[1];
            log.info("Loading mediator configuration from '" + configPath + "'...");
        } else {
            log.info("No configuration specified. Using default properties...");
        }

        MediatorConfig config = loadConfig(configPath);
        final MediatorServer server = new MediatorServer(system, config);

        if (isSecure(config)) {
            loadSSLConfig(config);
        }

        //setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                log.info("Shutting down mediator");
                server.stop();
                system.shutdown();
            }
        });

        log.info("Starting HTTP server...");
        server.start();


        //notify http-connector about the ihe cert
        if (isSecure(config)) {
            ActorSelection httpConnector = system.actorSelection(config.userPathFor("http-connector"));
            httpConnector.tell(
                    new SetupHTTPSCertificate(
                            config.getProperty("ihe.keystore"),
                            config.getProperty("ihe.keypassword"),
                            config.getProperty("ihe.keystore"),
                            true
                    ),
                    ActorRef.noSender()
            );
        }

        log.info(String.format("%s listening on %s:%s", config.getName(), config.getServerHost(), config.getServerPort()));
        Thread.currentThread().join();
    }
}
