package com.ledgerflow.payment.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.mock.MockProducerFactory;

/** Payment-service's real classification: which listener failures skip the retries (B1) and which are retried. */
class KafkaErrorHandlingConfigurationTest {

    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final ConsumerRecord<String, Object> record =
            new ConsumerRecord<>("ledger.replies", 0, 1L, "payment-1", "reply");
    private CommonErrorHandler handler;

    @BeforeEach
    void handlerAsTheServiceBuildsIt() {
        when(container.isRunning()).thenReturn(true);
        var records = new KafkaTemplate<String, Object>(new MockProducerFactory<>(
                () -> new MockProducer<>(true, null, new StringSerializer(), (topic, value) -> new byte[0])));
        handler = new KafkaErrorHandlingConfiguration()
                .kafkaErrorHandler(
                        records,
                        new KafkaProperties(),
                        new KafkaRetryProperties(3, Duration.ofMillis(1), 2.0, Duration.ofMillis(5)));
    }

    @Test
    @DisplayName("B1 a reply for an unknown payment skips the retries")
    void unknownPaymentSkipsTheRetries() {
        var failure = failed(new PaymentNotFoundException(PaymentId.random()));

        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
    }

    @Test
    @DisplayName("B1 a malformed reply (IllegalArgumentException) skips the retries")
    void malformedReplySkipsTheRetries() {
        var failure = failed(new IllegalArgumentException("Message has no message-id header"));

        assertThat(handler.handleOne(failure, record, consumer, container)).isTrue();
    }

    @Test
    @DisplayName("a lost optimistic-lock race is retried: the next attempt re-reads the payment")
    void optimisticLockConflictIsRetried() {
        var failure = failed(new OptimisticLockingFailureException("payment changed"));

        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
    }

    /** As the listener container reports it: the listener's exception wrapped. */
    private static ListenerExecutionFailedException failed(Exception cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }
}
