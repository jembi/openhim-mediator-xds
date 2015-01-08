package org.openhim.mediator.dummies;

import akka.actor.*;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility for launching actors
*/
public class MockLauncher extends UntypedActor {
    public static class ActorToLaunch {
        private String name;
        private Class<? extends Actor> actorClass;

        public ActorToLaunch(String name, Class<? extends Actor> actorClass) {
            this.name = name;
            this.actorClass = actorClass;
        }
    }

    public MockLauncher(List<ActorToLaunch> actorsToLaunch) {
        for (ActorToLaunch actorToLaunch : actorsToLaunch) {
            getContext().actorOf(Props.create(actorToLaunch.actorClass), actorToLaunch.name);
        }
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        unhandled(msg);
    }


    public static void launchActors(ActorSystem system, String root, List<ActorToLaunch> actorsToLaunch) {
        system.actorOf(Props.create(MockLauncher.class, actorsToLaunch), root);
    }

    public static void clearActors(ActorSystem system, String root) {
        system.actorSelection("/user/" + root).tell(PoisonPill.getInstance(), ActorRef.noSender());
    }
}
