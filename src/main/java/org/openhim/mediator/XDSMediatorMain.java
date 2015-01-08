package org.openhim.mediator;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.openhim.mediator.denormalization.ATNAAuditingActor;
import org.openhim.mediator.denormalization.EnrichRegistryStoredQueryActor;
import org.openhim.mediator.engine.*;
import org.openhim.mediator.normalization.ParseRegistryStoredQueryActor;
import org.openhim.mediator.orchestration.RegistryActor;
import org.openhim.mediator.orchestration.RepositoryActor;

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

    private static MediatorConfig loadConfig() throws IOException, RoutingTable.RouteAlreadyMappedException {
        MediatorConfig config = new MediatorConfig();

        config.setProperties("mediator.properties");

        config.setName(config.getProperties().getProperty("mediator.name"));
        config.setServerHost(config.getProperties().getProperty("mediator.host"));
        config.setServerPort( Integer.parseInt(config.getProperties().getProperty("mediator.port")) );
        config.setRootTimeout(Integer.parseInt(config.getProperties().getProperty("mediator.timeout")));

        config.setCoreHost(config.getProperties().getProperty("core.host"));
        config.setCoreAPIUsername(config.getProperties().getProperty("core.api.user"));
        config.setCoreAPIPassword(config.getProperties().getProperty("core.api.password"));
        if (config.getProperties().getProperty("core.api.port") != null) {
            config.setCoreAPIPort(Integer.parseInt(config.getProperties().getProperty("core.api.port")));
        }

        config.setRoutingTable(buildRoutingTable());
        config.setStartupActors(buildStartupActorsConfig());

        InputStream regInfo = XDSMediatorMain.class.getClassLoader().getResourceAsStream("mediator-registration-info.json");
        RegistrationConfig regConfig = new RegistrationConfig(regInfo);
        config.setRegistrationConfig(regConfig);

        return config;
    }

    public static void main(String... args) throws Exception {

        //setup actor system
        final ActorSystem system = ActorSystem.create("mediator");
        //setup logger for main
        final LoggingAdapter log = Logging.getLogger(system, "main");

        //setup server
        log.info("Initializing mediator server...");
        MediatorConfig config = loadConfig();
        final MediatorServer server = new MediatorServer(system, config);

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

        log.info(String.format("%s listening on %s:%s", config.getName(), config.getServerHost(), config.getServerPort()));
        while (true) {
            System.in.read();
        }
    }
}
