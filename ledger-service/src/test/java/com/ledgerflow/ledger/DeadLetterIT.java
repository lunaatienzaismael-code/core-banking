package com.ledgerflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.messaging.FundsReserved;
import com.ledgerflow.messaging.ReleaseFunds;
import com.ledgerflow.messaging.ReserveFunds;
import com.ledgerflow.messaging.outbox.OutboxHeaders;
import com.ledgerflow.messaging.testing.MessagingContainers;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/** AC4.4 on {@code ledger.commands}: failing records end on {@code ledger.commands.DLT} and the partition moves on. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DeadLetterIT {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaTemplate<String, Object> kafka;

    private UUID debtor;

    @BeforeEach
    void fundedDebtor() {
        var fixture = new LedgerFixture(jdbc);
        var cash = fixture.account("EUR", "ASSET");
        debtor = fixture.account("EUR", "LIABILITY", "NOT_ALLOWED");
        fixture.transfer(cash, debtor, 10_000, "EUR");
    }

    @Test
    @DisplayName(
            "AC4.4 an undeserialisable command is parked on the DLT with its key and bytes; the next one is processed")
    void undeserialisableCommandIsParkedAndThePartitionMovesOn() throws Exception {
        var paymentId = UUID.randomUUID().toString();
        var poison = "not avro".getBytes(StandardCharsets.UTF_8);

        var sent = sendRaw(paymentId, poison);
        send(paymentId, reserve(paymentId, debtor, 2_500));

        var parked = deadLetterFor(paymentId);
        assertThat(parked.partition()).isEqualTo(sent.partition());
        assertThat(parked.value()).isEqualTo(poison);
        assertThat(header(parked, "kafka_dlt-exception-fqcn")).contains("DeserializationException");
        assertThat(replyFor(paymentId)).isInstanceOf(FundsReserved.class);
    }

    @Test
    @DisplayName("B1 an invalid command (zero amount) is parked on the DLT without retries")
    void invalidCommandIsParked() throws Exception {
        var paymentId = UUID.randomUUID().toString();

        send(paymentId, reserve(paymentId, debtor, 0));

        var parked = deadLetterFor(paymentId);
        assertThat(header(parked, "kafka_dlt-exception-cause-fqcn")).contains("InvalidTransactionException");
    }

    @Test
    @DisplayName("AC4.4 a release that keeps failing is retried, then parked on the DLT; the next command is processed")
    void failingReleaseIsRetriedThenParked() throws Exception {
        var paymentId = UUID.randomUUID().toString();

        send(
                paymentId,
                ReleaseFunds.newBuilder()
                        .setPaymentId(paymentId)
                        .setDebtorAccountId(UUID.randomUUID().toString())
                        .setAmountMinorUnits(2_500)
                        .setCurrency("EUR")
                        .setReason("creditor account closed")
                        .build());
        send(paymentId, reserve(paymentId, debtor, 2_500));

        var parked = deadLetterFor(paymentId);
        assertThat(header(parked, "kafka_dlt-exception-cause-fqcn")).contains("AccountNotFoundException");
        assertThat(replyFor(paymentId)).isInstanceOf(FundsReserved.class);
    }

    private static ReserveFunds reserve(String paymentId, UUID debtor, long amount) {
        return ReserveFunds.newBuilder()
                .setPaymentId(paymentId)
                .setDebtorAccountId(debtor.toString())
                .setAmountMinorUnits(amount)
                .setCurrency("EUR")
                .build();
    }

    private void send(String paymentId, SpecificRecord command) throws Exception {
        var record = new ProducerRecord<String, Object>("ledger.commands", paymentId, command);
        record.headers()
                .add(OutboxHeaders.MESSAGE_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private static RecordMetadata sendRaw(String key, byte[] value) throws Exception {
        try (var producer = new KafkaProducer<String, byte[]>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, MessagingContainers.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            var record = new ProducerRecord<String, byte[]>("ledger.commands", key, value);
            record.headers()
                    .add(OutboxHeaders.MESSAGE_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            return producer.send(record).get();
        }
    }

    private static ConsumerRecord<String, byte[]> deadLetterFor(String key) {
        try (var consumer = new KafkaConsumer<String, byte[]>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class))) {
            consumer.subscribe(List.of("ledger.commands.DLT"));
            var deadline = Instant.now().plus(WAIT);
            while (Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
            throw new AssertionError("Nothing on ledger.commands.DLT for " + key);
        }
    }

    private static Object replyFor(String key) {
        try (var consumer = new KafkaConsumer<String, Object>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "dlt-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                MessagingContainers.schemaRegistryUrl(),
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
                true))) {
            consumer.subscribe(List.of("ledger.replies"));
            var deadline = Instant.now().plus(WAIT);
            while (Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record.value();
                    }
                }
            }
            throw new AssertionError("No reply on ledger.replies for " + key);
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        assertThat(header).as("header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
