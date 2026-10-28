package com.ledgerflow.payment;

import com.ledgerflow.messaging.testing.MessagingContainers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL for integration tests (ADR 0003: no H2), same major version as infra/docker-compose.yml; Kafka and
 * Schema Registry from the shared messaging fixture (ADR 0013).
 *
 * <p>Every integration test must load the same context: {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} with
 * this import and nothing else that changes the context. A second cached context would run a second consumer in the
 * service's group, on its own database, and take part of the partitions: messages would land where their data is not.
 * Settings tests need are set here, for all of them.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String POSTGRES_IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    DynamicPropertyRegistrar messagingProperties() {
        return registry -> {
            registry.add("spring.kafka.bootstrap-servers", MessagingContainers::bootstrapServers);
            registry.add("spring.kafka.properties.schema.registry.url", MessagingContainers::schemaRegistryUrl);
            // A fast relay keeps message round trips in tests well under a second.
            registry.add("ledgerflow.outbox.relay-interval", () -> "200ms");
            // Short retries so a record reaches its dead-letter topic quickly.
            registry.add("ledgerflow.kafka.retry.initial-interval", () -> "50ms");
            registry.add("ledgerflow.kafka.retry.max-interval", () -> "200ms");
            // PaymentOutboxIT pauses Kafka: sends must give up quickly so the relay retries after the outage.
            registry.add("ledgerflow.outbox.send-timeout", () -> "2s");
            registry.add("spring.kafka.producer.properties.max.block.ms", () -> "2000");
        };
    }
}
