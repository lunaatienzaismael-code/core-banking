package com.ledgerflow.risk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RiskServiceApplicationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("B1 the service boots with its own database and applies its Liquibase changelog")
    void bootsWithItsOwnDatabase() {
        Boolean trackingTableExists =
                jdbc.queryForObject("select to_regclass('public.databasechangelog') is not null", Boolean.class);

        assertThat(trackingTableExists).isTrue();
    }

    @Test
    @DisplayName("B2 a Testcontainers Redis is available to tests")
    void redisIsAvailableToTests() {
        redis.opsForValue().set("risk:probe", "ok");

        assertThat(redis.opsForValue().get("risk:probe")).isEqualTo("ok");
    }
}
