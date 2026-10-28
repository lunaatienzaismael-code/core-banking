package com.ledgerflow.messaging.errors;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Builds the consumers' error handler (ADR 0013): a failing record is retried in place with exponential backoff, which
 * keeps one key's records in order, and after the last retry it is published to {@code <topic>.DLT} on the same
 * partition, keeping its key and adding Spring Kafka's failure headers; the partition then moves on. Errors that
 * retrying cannot fix skip the retries.
 */
public final class DeadLetters {

    /** Suffix of every dead-letter topic; the DLT needs at least as many partitions as its topic. */
    public static final String SUFFIX = ".DLT";

    /**
     * @param maxRetries retries after the first attempt
     * @param initialInterval wait before the first retry
     * @param multiplier growth of the wait between retries
     * @param maxInterval cap on the wait between retries
     */
    public record RetrySettings(int maxRetries, Duration initialInterval, double multiplier, Duration maxInterval) {
        public RetrySettings {
            Objects.requireNonNull(initialInterval, "initialInterval");
            Objects.requireNonNull(maxInterval, "maxInterval");
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must not be negative, got " + maxRetries);
            }
            if (multiplier < 1) {
                throw new IllegalArgumentException("multiplier must be at least 1, got " + multiplier);
            }
        }
    }

    private DeadLetters() {}

    /**
     * @param records publishes failed records whose value was deserialised (Avro)
     * @param rawBytes publishes records that could not be deserialised, as their original bytes
     * @param notRetryable besides deserialisation failures (never retried by Spring Kafka) and
     *     {@link IllegalArgumentException} (a malformed message), the service's validation errors
     */
    public static DefaultErrorHandler errorHandler(
            KafkaOperations<?, ?> records,
            KafkaOperations<?, ?> rawBytes,
            RetrySettings retry,
            List<Class<? extends Exception>> notRetryable) {
        // Most specific type first: undeserialisable records carry their raw bytes as the value.
        var templates = new LinkedHashMap<Class<?>, KafkaOperations<?, ?>>();
        templates.put(byte[].class, rawBytes);
        templates.put(Object.class, records);
        var recoverer = new DeadLetterPublishingRecoverer(
                templates, (record, failure) -> new TopicPartition(record.topic() + SUFFIX, record.partition()));
        var backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        notRetryable.forEach(handler::addNotRetryableExceptions);
        return handler;
    }
}
