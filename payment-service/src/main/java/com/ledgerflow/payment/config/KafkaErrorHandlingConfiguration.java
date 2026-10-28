package com.ledgerflow.payment.config;

import com.ledgerflow.messaging.errors.DeadLetters;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
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

    /** An input for a payment this service does not have: retrying cannot make it appear. */
    private static final List<Class<? extends Exception>> NOT_RETRYABLE = List.of(PaymentNotFoundException.class);

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
    NewTopic paymentsEventsDeadLetterTopic() {
        return TopicBuilder.name("payments.events" + DeadLetters.SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic ledgerRepliesDeadLetterTopic() {
        return TopicBuilder.name("ledger.replies" + DeadLetters.SUFFIX)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
