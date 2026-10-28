package com.ledgerflow.ledger;

import com.ledgerflow.messaging.testing.MessagingContainers;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL for integration tests (ADR 0003: no H2), same major version as infra/docker-compose.yml; Kafka and
 * Schema Registry from the shared messaging fixture (ADR 0013).
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
        };
    }
}
