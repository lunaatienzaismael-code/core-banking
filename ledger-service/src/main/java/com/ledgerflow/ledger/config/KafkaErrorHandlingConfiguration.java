package com.ledgerflow.ledger.config;

import com.ledgerflow.ledger.domain.InvalidCurrencyException;
import com.ledgerflow.ledger.domain.InvalidTransactionException;
import com.ledgerflow.messaging.errors.DeadLetters;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;

/**
 * Bounded retries, then the dead-letter topic, for every listener of this service (ADR 0013). Spring Boot applies the
 * {@link CommonErrorHandler} bean to its listener containers.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KafkaRetryProperties.class)
class KafkaErrorHandlingConfiguration {

    /**
     * Malformed commands, which retrying cannot fix. Refusals are replies, not errors; a failed release
     * ({@code AccountNotFoundException}) is technical and retried (ADR 0012).
     */
    private static final List<Class<? extends Exception>> NOT_RETRYABLE =
            List.of(InvalidTransactionException.class, InvalidCurrencyException.class);

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> records, KafkaProperties kafka, KafkaRetryProperties retry) {
        // Not a bean: a KafkaTemplate or ProducerFactory bean would switch off Spring Boot's own. Its producer is
        // created on the first undeserialisable record only.
        var rawBytes = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                kafka.buildProducerProperties(), new StringSerializer(), new ByteArraySerializer()));
        return DeadLetters.errorHandler(
                records,
                rawBytes,
                new DeadLetters.RetrySettings(
                        retry.maxRetries(), retry.initialInterval(), retry.multiplier(), retry.maxInterval()),
                NOT_RETRYABLE);
    }

    @Bean
    NewTopic ledgerCommandsDeadLetterTopic() {
        return TopicBuilder.name("ledger.commands" + DeadLetters.SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
