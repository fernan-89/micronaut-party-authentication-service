package com.thinklab.infrastructure.adapter.out.mongo;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The real infrastructure for the integration suite, started once per JVM (Testcontainers' Ryuk sidecar
 * removes it on exit): MongoDB as a single-node replica set (transactions need one) and NATS JetStream for
 * the event backbone.
 */
public final class Infrastructure {

    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0")).withReplicaSet();

    static final GenericContainer<?> NATS = new GenericContainer<>(DockerImageName.parse("nats:2.10-alpine"))
            .withCommand("-js")
            .withExposedPorts(4222)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1));

    static {
        MONGO.start();
        NATS.start();
    }

    private Infrastructure() {
    }

    public static String mongoUri(String database) {
        return MONGO.getReplicaSetUrl(database);
    }

    public static String natsUrl() {
        return "nats://" + NATS.getHost() + ":" + NATS.getMappedPort(4222);
    }
}
