package com.ledgerflow.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRelaySettingsTest {

    @Test
    void rejectsABatchSizeBelowOne() {
        assertThatThrownBy(() -> new OutboxRelay.Settings(1L, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize must be positive, got 0");
    }
}
