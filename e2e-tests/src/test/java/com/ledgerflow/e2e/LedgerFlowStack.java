package com.ledgerflow.e2e;

import com.github.dockerjava.api.exception.NotFoundException;
import com.ledgerflow.messaging.testing.MessagingContainers;
import java.time.Duration;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Both services as their Docker images, each with its own PostgreSQL (ADR 0004), plus Kafka and Schema Registry, on one
 * network; started once per JVM on first use. Wired as {@code infra/docker-compose.yml} wires them, through the
 * services' own environment variables.
 *
 * <p>The service images are built from their Dockerfiles beforehand, by {@code scripts/e2e.sh} or CI, as
 * {@code ledgerflow/<service>:e2e}; the system property {@code e2e.<service>.image} names another.
 */
final class LedgerFlowStack {

    private static final String POSTGRES_IMAGE = "postgres:18-alpine";
    private static final String KAFKA_BOOTSTRAP = "kafka:19092";
    private static final String SCHEMA_REGISTRY_URL = "http://schema-registry:8081";
    private static final int LEDGER_PORT = 8080;
    private static final int PAYMENT_PORT = 8081;
    // Liquibase, Kafka clients and Spring on a cold container; well above a warm start.
    private static final Duration SERVICE_STARTUP = Duration.ofMinutes(2);

    private static final Network NETWORK = Network.newNetwork();

    private static final KafkaContainer KAFKA = new KafkaContainer(MessagingContainers.KAFKA_IMAGE)
            .withNetwork(NETWORK)
            .withListener(KAFKA_BOOTSTRAP);

    private static final GenericContainer<?> SCHEMA_REGISTRY = new GenericContainer<>(
                    MessagingContainers.SCHEMA_REGISTRY_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases("schema-registry")
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://" + KAFKA_BOOTSTRAP)
            .dependsOn(KAFKA)
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

    private static final PostgreSQLContainer LEDGER_DB = database("ledger");
    private static final PostgreSQLContainer PAYMENT_DB = database("payment");

    private static final GenericContainer<?> LEDGER =
            service("ledger-service", "LEDGER", LEDGER_PORT, "ledger").dependsOn(LEDGER_DB, SCHEMA_REGISTRY);

    // After the ledger, which declares the topics both services share; a broker would otherwise auto-create them.
    private static final GenericContainer<?> PAYMENT = service("payment-service", "PAYMENT", PAYMENT_PORT, "payment")
            .dependsOn(PAYMENT_DB, SCHEMA_REGISTRY, LEDGER);

    static {
        Startables.deepStart(PAYMENT).join();
    }

    private LedgerFlowStack() {}

    static String ledgerUrl() {
        return "http://" + LEDGER.getHost() + ":" + LEDGER.getMappedPort(LEDGER_PORT);
    }

    static String paymentUrl() {
        return "http://" + PAYMENT.getHost() + ":" + PAYMENT.getMappedPort(PAYMENT_PORT);
    }

    /** Database, user and password are all {@code name}, at host {@code <name>-db}, as in docker-compose. */
    private static PostgreSQLContainer database(String name) {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withNetwork(NETWORK)
                .withNetworkAliases(name + "-db")
                .withDatabaseName(name)
                .withUsername(name)
                .withPassword(name);
    }

    private static GenericContainer<?> service(String name, String envPrefix, int port, String database) {
        return new GenericContainer<>(image(name))
                .withNetwork(NETWORK)
                .withEnv(envPrefix + "_DB_URL", "jdbc:postgresql://" + database + "-db:5432/" + database)
                .withEnv(envPrefix + "_DB_USER", database)
                .withEnv(envPrefix + "_DB_PASSWORD", database)
                .withEnv(envPrefix + "_KAFKA_BOOTSTRAP_SERVERS", KAFKA_BOOTSTRAP)
                .withEnv(envPrefix + "_SCHEMA_REGISTRY_URL", SCHEMA_REGISTRY_URL)
                .withExposedPorts(port)
                .waitingFor(Wait.forHttp("/actuator/health/readiness").forStatusCode(200))
                .withStartupTimeout(SERVICE_STARTUP);
    }

    private static String image(String service) {
        var image = System.getProperty("e2e." + service + ".image", "ledgerflow/" + service + ":e2e");
        try {
            DockerClientFactory.instance().client().inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            throw new IllegalStateException("No image " + image + ": run scripts/e2e.sh, which builds it", e);
        }
        return image;
    }
}
