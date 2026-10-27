package com.ledgerflow.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.messaging.PaymentInitiated;
import com.ledgerflow.messaging.testing.MessagingContainers;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayIT {

    private static final JdbcClient JDBC = OutboxDatabase.JDBC;
    private static final TransactionTemplate TRANSACTIONS = OutboxDatabase.TRANSACTIONS;
    private static final DefaultKafkaProducerFactory<String, Object> PRODUCERS =
            new DefaultKafkaProducerFactory<>(producerConfig());
    private static final KafkaTemplate<String, Object> KAFKA = new KafkaTemplate<>(PRODUCERS);
    private static final long LOCK_KEY = 4_242L;

    private final OutboxWriter writer = new OutboxWriter(JDBC, Clock.systemUTC());
    private final String topic = "outbox-it-" + UUID.randomUUID();

    @AfterAll
    static void closeProducers() {
        PRODUCERS.destroy();
    }

    @Test
    @DisplayName("B1 a written message is published with its key and message-id header, then marked")
    void writtenMessageIsPublishedWithItsKeyAndMessageIdThenMarked() {
        var message = paymentInitiated(100);
        var key = message.getPaymentId();
        TRANSACTIONS.executeWithoutResult(status -> writer.write(topic, key, message));

        var published = relay(KAFKA, 10).publishPending();

        assertThat(published).isEqualTo(1);
        var received = consume(1).getFirst();
        assertThat(received.key()).isEqualTo(key);
        assertThat(received.value()).isEqualTo(message);
        assertThat(new String(
                        received.headers().lastHeader(OutboxHeaders.MESSAGE_ID).value(), StandardCharsets.UTF_8))
                .isEqualTo(messageIdOf(key).toString());
        assertThat(unpublishedCount()).isZero();
    }

    @Test
    @DisplayName("B3 messages of one key arrive in insertion order")
    void messagesOfOneKeyArriveInInsertionOrder() {
        var key = UUID.randomUUID().toString();
        for (long amount = 1; amount <= 5; amount++) {
            var message = paymentInitiated(key, amount);
            TRANSACTIONS.executeWithoutResult(status -> writer.write(topic, key, message));
        }

        relay(KAFKA, 10).publishPending();

        assertThat(consume(5))
                .extracting(received -> ((PaymentInitiated) received.value()).getAmountMinorUnits())
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @DisplayName("B3 while another instance holds the lock, the relay publishes nothing")
    void whileAnotherInstanceHoldsTheLockNothingIsPublished() throws Exception {
        var message = paymentInitiated(100);
        TRANSACTIONS.executeWithoutResult(status -> writer.write(topic, message.getPaymentId(), message));
        var relay = relay(KAFKA, 10);

        try (Connection otherInstance = OutboxDatabase.DATA_SOURCE.getConnection()) {
            otherInstance.setAutoCommit(false);
            try (var statement = otherInstance.prepareStatement("select pg_advisory_xact_lock(?)")) {
                statement.setLong(1, LOCK_KEY);
                statement.execute();
            }

            assertThat(relay.publishPending()).isZero();
            assertThat(unpublishedCount()).isEqualTo(1);

            otherInstance.rollback();
        }
        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(unpublishedCount()).isZero();
    }

    @Test
    @DisplayName("B3 an unacknowledged message stops the run: it and the rows after it stay unpublished, in order")
    void unacknowledgedMessageStopsTheRunAndKeepsOrder() {
        var key = UUID.randomUUID().toString();
        for (long amount = 1; amount <= 3; amount++) {
            var message = paymentInitiated(key, amount);
            TRANSACTIONS.executeWithoutResult(status -> writer.write(topic, key, message));
        }
        var failingOnTwo = new KafkaTemplate<>(PRODUCERS) {
            @Override
            public CompletableFuture<SendResult<String, Object>> send(ProducerRecord<String, Object> record) {
                if (((PaymentInitiated) record.value()).getAmountMinorUnits() == 2) {
                    return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
                }
                return super.send(record);
            }
        };

        assertThat(relay(failingOnTwo, 10).publishPending()).isEqualTo(1);
        assertThat(unpublishedCount()).isEqualTo(2);

        assertThat(relay(KAFKA, 10).publishPending()).isEqualTo(2);
        assertThat(consume(3))
                .extracting(received -> ((PaymentInitiated) received.value()).getAmountMinorUnits())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("B1 a run publishes at most one batch")
    void aRunPublishesAtMostOneBatch() {
        for (long amount = 1; amount <= 3; amount++) {
            var message = paymentInitiated(amount);
            TRANSACTIONS.executeWithoutResult(status -> writer.write(topic, message.getPaymentId(), message));
        }
        var relay = relay(KAFKA, 2);

        assertThat(relay.publishPending()).isEqualTo(2);
        assertThat(relay.publishPending()).isEqualTo(1);
        assertThat(relay.publishPending()).isZero();
    }

    @Test
    @DisplayName("AC4.1 a message written in a transaction that rolls back is never stored")
    void messageOfARolledBackTransactionIsNeverStored() {
        var message = paymentInitiated(100);

        TRANSACTIONS.executeWithoutResult(status -> {
            writer.write(topic, message.getPaymentId(), message);
            status.setRollbackOnly();
        });

        assertThat(storedCount()).isZero();
    }

    @Test
    @DisplayName("AC4.1 writing outside a transaction is rejected: it would be a dual-write")
    void writingOutsideATransactionIsRejected() {
        var message = paymentInitiated(100);

        assertThatThrownBy(() -> writer.write(topic, message.getPaymentId(), message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inside the transaction");
        assertThat(storedCount()).isZero();
    }

    private OutboxRelay relay(KafkaTemplate<String, Object> kafka, int batchSize) {
        return new OutboxRelay(
                JDBC,
                TRANSACTIONS,
                kafka,
                Clock.systemUTC(),
                new OutboxRelay.Settings(LOCK_KEY, batchSize, Duration.ofSeconds(10)));
    }

    private UUID messageIdOf(String key) {
        return JDBC.sql("select message_id from outbox_messages where topic = ? and message_key = ?")
                .params(topic, key)
                .query(UUID.class)
                .single();
    }

    private long unpublishedCount() {
        return JDBC.sql("select count(*) from outbox_messages where topic = ? and published_at is null")
                .param(topic)
                .query(Long.class)
                .single();
    }

    private long storedCount() {
        return JDBC.sql("select count(*) from outbox_messages where topic = ?")
                .param(topic)
                .query(Long.class)
                .single();
    }

    private List<ConsumerRecord<String, Object>> consume(int expected) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                MessagingContainers.schemaRegistryUrl(),
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
                true);
        try (var consumer = new KafkaConsumer<String, Object>(config)) {
            consumer.subscribe(List.of(topic));
            var records = new ArrayList<ConsumerRecord<String, Object>>();
            var deadline = Instant.now().plus(Duration.ofSeconds(30));
            while (records.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
            // One more short poll: nothing beyond the expected messages may have been published.
            consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            assertThat(records).hasSize(expected);
            return records;
        }
    }

    private static PaymentInitiated paymentInitiated(long amount) {
        return paymentInitiated(UUID.randomUUID().toString(), amount);
    }

    private static PaymentInitiated paymentInitiated(String paymentId, long amount) {
        return PaymentInitiated.newBuilder()
                .setPaymentId(paymentId)
                .setDebtorAccountId(UUID.randomUUID().toString())
                .setCreditorAccountId(UUID.randomUUID().toString())
                .setAmountMinorUnits(amount)
                .setCurrency("EUR")
                .setInitiatedAt(Instant.parse("2026-10-27T10:15:30Z"))
                .build();
    }

    private static Map<String, Object> producerConfig() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, MessagingContainers.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MessagingContainers.schemaRegistryUrl(),
                KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY, TopicRecordNameStrategy.class.getName());
    }
}
