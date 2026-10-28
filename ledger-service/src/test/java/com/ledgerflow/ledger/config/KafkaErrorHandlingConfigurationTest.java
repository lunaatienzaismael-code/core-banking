package com.ledgerflow.ledger.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.InvalidCurrencyException;
import com.ledgerflow.ledger.domain.InvalidTransactionException;
import java.time.Duration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.mock.MockProducerFactory;

/** The ledger's real classification: which listener failures skip the retries (B1) and which are retried. */
class KafkaErrorHandlingConfigurationTest {

    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final ConsumerRecord<String, Object> record =
            new ConsumerRecord<>("ledger.commands", 0, 1L, "payment-1", "command");
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
    @DisplayName("B1 an invalid amount skips the retries")
    void invalidAmountSkipsTheRetries() {
        assertThat(handler.handleOne(failed(new InvalidTransactionException("amount 0")), record, consumer, container))
                .isTrue();
    }

    @Test
    @DisplayName("B1 an invalid currency skips the retries")
    void invalidCurrencySkipsTheRetries() {
        assertThat(handler.handleOne(failed(new InvalidCurrencyException("EURO")), record, consumer, container))
                .isTrue();
    }

    @Test
    @DisplayName("a failing release (unknown account) is technical: it is retried before the DLT")
    void failingReleaseIsRetried() {
        var failure = failed(new AccountNotFoundException(AccountId.random()));

        assertThat(handler.handleOne(failure, record, consumer, container)).isFalse();
    }

    /** As the listener container reports it: the listener's exception wrapped. */
    private static ListenerExecutionFailedException failed(Exception cause) {
        return new ListenerExecutionFailedException("listener failed", cause);
    }
}
