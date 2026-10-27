package com.ledgerflow.messaging.testing;

import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Kafka (KRaft) and Schema Registry for integration tests, started once per JVM on first use and shared by every test
 * class (ADR 0003, ADR 0013). Same images as {@code infra/docker-compose.yml}. Services reach it through the
 * {@code messaging-contracts} test-jar.
 */
public final class MessagingContainers {

    public static final String KAFKA_IMAGE = "apache/kafka:4.2.1";
    public static final String SCHEMA_REGISTRY_IMAGE = "confluentinc/cp-schema-registry:8.2.4";

    private static final int SCHEMA_REGISTRY_PORT = 8081;
    // Listener the registry uses inside the Docker network; tests use the mapped port.
    private static final String INTERNAL_BOOTSTRAP = "kafka:19092";

    private static final Network NETWORK = Network.newNetwork();

    private static final KafkaContainer KAFKA =
            new KafkaContainer(KAFKA_IMAGE).withNetwork(NETWORK).withListener(INTERNAL_BOOTSTRAP);

    private static final GenericContainer<?> SCHEMA_REGISTRY = new GenericContainer<>(SCHEMA_REGISTRY_IMAGE)
            .withNetwork(NETWORK)
            .withExposedPorts(SCHEMA_REGISTRY_PORT)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:" + SCHEMA_REGISTRY_PORT)
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + INTERNAL_BOOTSTRAP)
            .dependsOn(KAFKA)
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

    static {
        Startables.deepStart(KAFKA, SCHEMA_REGISTRY).join();
    }

    private MessagingContainers() {}

    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    /** Freezes the broker, as an outage would, until {@link #unpauseKafka()}; tests that call it must unpause. */
    public static void pauseKafka() {
        DockerClientFactory.instance()
                .client()
                .pauseContainerCmd(KAFKA.getContainerId())
                .exec();
    }

    public static void unpauseKafka() {
        DockerClientFactory.instance()
                .client()
                .unpauseContainerCmd(KAFKA.getContainerId())
                .exec();
    }

    public static String schemaRegistryUrl() {
        return "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(SCHEMA_REGISTRY_PORT);
    }
}
