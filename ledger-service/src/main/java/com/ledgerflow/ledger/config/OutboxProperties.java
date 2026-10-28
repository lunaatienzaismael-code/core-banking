package com.ledgerflow.ledger.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbox relay settings (ADR 0013), under {@code ledgerflow.outbox}.
 *
 * @param relayInterval pause between relay runs
 * @param batchSize rows published per run at most
 * @param sendTimeout wait for the broker's acknowledgement of one message
 * @param lockKey advisory lock key that makes one instance of this service publish at a time
 */
@ConfigurationProperties("ledgerflow.outbox")
record OutboxProperties(Duration relayInterval, int batchSize, Duration sendTimeout, long lockKey) {}
