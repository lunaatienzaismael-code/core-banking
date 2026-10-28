package com.ledgerflow.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Consumer retries before a record is parked on its dead-letter topic (ADR 0013), under {@code ledgerflow.kafka.retry}.
 *
 * @param maxRetries retries after the first attempt
 * @param initialInterval wait before the first retry
 * @param multiplier growth of the wait between retries
 * @param maxInterval cap on the wait between retries
 */
@ConfigurationProperties("ledgerflow.kafka.retry")
record KafkaRetryProperties(int maxRetries, Duration initialInterval, double multiplier, Duration maxInterval) {}
