package com.ledgerflow.risk.adapter.out.redis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** No Redis listens on the port, so the counter must fail instead of answering (B2, ADR 0014). */
class RedisVelocityCounterUnavailableTest {

    @Test
    @DisplayName("B2 an unreachable Redis makes the counter throw, never return a count")
    void unreachableRedisThrows() {
        var factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1));
        factory.afterPropertiesSet();
        try {
            var counter = new RedisVelocityCounter(new StringRedisTemplate(factory), Duration.ofMinutes(1));

            assertThatThrownBy(() -> counter.increment("debtor", UUID.randomUUID()))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            factory.destroy();
        }
    }
}
