package com.ledgerflow.risk.adapter.out.redis;

import com.ledgerflow.risk.application.port.out.VelocityCounter;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter per debtor account (ADR 0014). One Lua script increments and, on the first hit of a window,
 * sets the expiry, so no crash between the two can leave a counter that never expires. A marker per message makes
 * the count idempotent: a retry after a rolled-back transaction returns the current count without incrementing.
 */
@Component
class RedisVelocityCounter implements VelocityCounter {

    private static final String KEY_PREFIX = "risk:velocity:";
    private static final String SEEN_PREFIX = "risk:velocity-seen:";

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            if redis.call('SET', KEYS[2], '1', 'NX', 'PX', ARGV[1]) then
              local count = redis.call('INCR', KEYS[1])
              if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
              end
              return count
            end
            return tonumber(redis.call('GET', KEYS[1]) or '1')
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String window;

    RedisVelocityCounter(StringRedisTemplate redis, @Value("${ledgerflow.risk.velocity.window}") Duration window) {
        this.redis = redis;
        this.window = Long.toString(window.toMillis());
    }

    @Override
    public long increment(String debtorAccountId, UUID messageId) {
        var count = redis.execute(INCREMENT, List.of(KEY_PREFIX + debtorAccountId, SEEN_PREFIX + messageId), window);
        if (count == null) {
            throw new IllegalStateException("Redis returned no count for " + debtorAccountId);
        }
        return count;
    }
}
