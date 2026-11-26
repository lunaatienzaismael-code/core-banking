package com.ledgerflow.risk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL and Redis for integration tests (ADR 0003: no H2, no embedded fakes), same images as
 * infra/docker-compose.yml. Every integration test imports this one configuration so they share one context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String POSTGRES_IMAGE = "postgres:18-alpine";
    static final String REDIS_IMAGE = "redis:8-alpine";
    private static final int REDIS_PORT = 6379;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);
    }
}
