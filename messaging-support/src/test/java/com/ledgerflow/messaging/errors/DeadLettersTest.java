package com.ledgerflow.messaging.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.mock.MockProducerFactory;

/**
 * The handler against Kafka's MockProducer: what reaches the dead-letter topic, and after how many attempts. The
 * partition is checked against a real broker in the services' dead-letter ITs (the recoverer verifies it exists).
 */
class DeadLettersTest {

    private static final Serializer<Object> TEXT =
            (topic, value) -> value.toString().getBytes(StandardCharsets.UTF_8);

    private final MockProducer<String, Object> records = new MockProducer<>(true, null, new StringSerializer(), TEXT);
    private final MockProducer<String, byte[]> rawBytes =
            new MockProducer<>(true, null, new StringSerializer(), new ByteArraySerializer());
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);
    private DefaultErrorHandler handler;

    /** A validation error of the service's own, declared not retryable. */
    static final class InvalidCommandException extends RuntimeException {
        InvalidCommandException() {
            super("amount must be positive");
        }
    }

    @BeforeEach
    void handlerWithTwoRetries() {
        when(container.isRunning()).thenReturn(true);
        handler = DeadLetters.errorHandler(
                new KafkaTemplate<>(new MockProducerFactory<>(() -> records)),
                new KafkaTemplate<>(new MockProducerFactory<>(() -> rawBytes)),
                new DeadLetters.RetrySettings(2, Duration.ofMillis(1), 2.0, Duration.ofMillis(5)),
                List.of(InvalidCommandException.class));
    }

    @Test
    @DisplayName("AC4.4 a retryable failure is retried the configured times, then parked on <topic>.DLT")
    void retryableFailureIsRetriedThenDeadLettered() {
        var record = new ConsumerRecord<String, Object>("ledger.commands", 2, 41L, "payment-1", "command");
        var failure = new IllegalStateException("database unavailable");

        var first = handler.handleOne(failure, record, consumer, container);
        var second = handler.handleOne(failure, record, consumer, container);
        var third = handler.handleOne(failure, record, consumer, container);

        assertThat(List.of(first, second)).containsOnly(false); // not recovered yet: the record is retried
        assertThat(third).isTrue();
        assertThat(records.history()).singleElement().satisfies(parked -> {
            assertThat(parked.topic()).isEqualTo("ledger.commands.DLT");
            assertThat(parked.key()).isEqualTo("payment-1");
            assertThat(parked.value()).isEqualTo("command");
            assertThat(header(parked.headers()
                            .lastHeader("kafka_dlt-exception-fqcn")
                            .value()))
                    .contains("IllegalStateException");
            assertThat(header(parked.headers()
                            .lastHeader("kafka_dlt-original-offset")
                            .value()))
                    .isNotEmpty();
        });
    }

    @Test
    @DisplayName("B1 a malformed message (IllegalArgumentException) skips the retries")
    void malformedMessageGoesStraightToTheDeadLetterTopic() {
        var record = new ConsumerRecord<String, Object>("ledger.replies", 0, 7L, "payment-2", "reply");

        var recovered = handler.handleOne(new IllegalArgumentException("not a UUID"), record, consumer, container);

        assertThat(recovered).isTrue();
        assertThat(records.history())
                .singleElement()
                .satisfies(parked -> assertThat(parked.topic()).isEqualTo("ledger.replies.DLT"));
    }

    @Test
    @DisplayName("B1 a validation error the service declares not retryable skips the retries")
    void declaredValidationErrorGoesStraightToTheDeadLetterTopic() {
        var record = new ConsumerRecord<String, Object>("ledger.commands", 1, 3L, "payment-3", "command");

        var recovered = handler.handleOne(
                new RuntimeException("listener failed", new InvalidCommandException()), record, consumer, container);

        assertThat(recovered).isTrue();
        assertThat(records.history()).hasSize(1);
    }

    @Test
    void retrySettingsRejectNonsense() {
        assertThatThrownBy(() -> new DeadLetters.RetrySettings(-1, Duration.ZERO, 2.0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxRetries must not be negative, got -1");
        assertThatThrownBy(() -> new DeadLetters.RetrySettings(1, Duration.ZERO, 0.5, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("multiplier must be at least 1, got 0.5");
    }

    private static String header(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }
}
