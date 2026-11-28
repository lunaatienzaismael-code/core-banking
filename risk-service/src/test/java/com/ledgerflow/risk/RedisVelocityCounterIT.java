package com.ledgerflow.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.risk.application.port.out.VelocityCounter;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/** The velocity counter against a real Redis (ADR 0003, ADR 0014). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RedisVelocityCounterIT {

    @Autowired
    private VelocityCounter counter;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("B1 concurrent increments of one debtor are all counted, none lost or repeated")
    void concurrentIncrementsAreAtomic() throws Exception {
        var debtor = UUID.randomUUID().toString();
        var threads = 20;

        try (var pool = Executors.newFixedThreadPool(threads)) {
            var tasks = IntStream.range(0, threads)
                    .<Callable<Long>>mapToObj(i -> () -> counter.increment(debtor))
                    .toList();
            var counts = pool.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();

            assertThat(counts)
                    .containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, threads)
                            .mapToObj(Long::valueOf)
                            .toList());
        }
    }

    @Test
    @DisplayName("ADR 0014 the first increment of a window sets the expiry, so counters never live forever")
    void counterExpires() {
        var debtor = UUID.randomUUID().toString();

        counter.increment(debtor);
        counter.increment(debtor);

        var ttl = redis.getExpire("risk:velocity:" + debtor);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("counters of different debtors are independent")
    void debtorsAreIndependent() {
        counter.increment(UUID.randomUUID().toString());

        assertThat(counter.increment(UUID.randomUUID().toString())).isEqualTo(1);
    }
}
