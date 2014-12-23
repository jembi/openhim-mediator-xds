package org.openhim.mediator;

import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.openhim.mediator.denormalization.EnrichRegistryStoredQueryActor;
import org.openhim.mediator.engine.*;
import org.openhim.mediator.normalization.ParseRegistryStoredQueryActor;
import org.openhim.mediator.orchestration.RegistryActor;
import org.openhim.mediator.orchestration.RepositoryActor;

import java.io.InputStream;
import java.util.Properties;

public class XDSMediatorMain {

    public static void main(String... args) throws Exception {
        InputStream propsStream = XDSMediatorMain.class.getClassLoader().getResourceAsStream("mediator.properties");
        Properties props = new Properties();
        props.load(propsStream);
        propsStream.close();

        //setup actor system
        final ActorSystem system = ActorSystem.create("mediator");
        //setup logger for main
        final LoggingAdapter log = Logging.getLogger(system, "main");

        //setup actors
        log.info("Initializing mediator actors...");

        MediatorConfig config = new MediatorConfig(
                props.getProperty("mediator.name"),
                props.getProperty("mediator.host"),
                Integer.parseInt(props.getProperty("mediator.port"))
        );

        config.setProperties(props);
        config.setRootTimeout(5000);

        config.setCoreHost(props.getProperty("core.host"));
        config.setCoreAPIUsername(props.getProperty("core.api.user"));
        config.setCoreAPIPassword(props.getProperty("core.api.password"));
        if (props.getProperty("core.api.port") != null) {
            config.setCoreAPIPort(Integer.parseInt(props.getProperty("core.api.port")));
        }

        RoutingTable routingTable = new RoutingTable();
        routingTable.addRoute("/xdsregistry", RegistryActor.class);
        routingTable.addRoute("/xdsrepository", RepositoryActor.class);
        config.setRoutingTable(routingTable);

        StartupActorsConfig startupActors = new StartupActorsConfig();
        startupActors.addActor("parse-registry-stored-query", ParseRegistryStoredQueryActor.class);
        startupActors.addActor("enrich-registry-stored-query", EnrichRegistryStoredQueryActor.class);
        config.setStartupActors(startupActors);

        InputStream regInfo = XDSMediatorMain.class.getClassLoader().getResourceAsStream("mediator-registration-info.json");
        RegistrationConfig regConfig = new RegistrationConfig(regInfo);
        config.setRegistrationConfig(regConfig);

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
