package com.ledgerflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.messaging.FundsReserved;
import com.ledgerflow.messaging.outbox.OutboxHeaders;
import com.ledgerflow.messaging.testing.MessagingContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/** AC4.4 on {@code ledger.replies}: failing records end on {@code ledger.replies.DLT} and the partition moves on. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DeadLetterIT {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @Autowired
    private KafkaTemplate<String, Object> kafka;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName(
            "AC4.4 an undeserialisable reply is parked on the DLT with its key and bytes; the next reply moves the payment on")
    void undeserialisableReplyIsParkedAndThePartitionMovesOn() throws Exception {
        var paymentId = initiate();
        await(() -> "RISK_APPROVED".equals(status(paymentId)));
        var poison = "not avro".getBytes(StandardCharsets.UTF_8);

        var sent = sendRaw(paymentId, poison);
        reply(paymentId, UUID.randomUUID(), fundsReserved(paymentId));

        var parked = deadLetterFor(paymentId);
        assertThat(parked.partition()).isEqualTo(sent.partition());
        assertThat(parked.value()).isEqualTo(poison);
        assertThat(header(parked, "kafka_dlt-exception-fqcn")).contains("DeserializationException");
        await(() -> "FUNDS_RESERVED".equals(status(paymentId)));
    }

    @Test
    @DisplayName("B1 a reply for a payment this service does not have is parked on the DLT without retries")
    void replyForAnUnknownPaymentIsParked() throws Exception {
        var unknown = UUID.randomUUID().toString();

        reply(unknown, UUID.randomUUID(), fundsReserved(unknown));

        assertThat(header(deadLetterFor(unknown), "kafka_dlt-exception-cause-fqcn"))
                .contains("PaymentNotFoundException");
    }

    @Test
    @DisplayName("B1 a reply without a message-id header cannot be deduplicated and is parked on the DLT")
    void replyWithoutMessageIdIsParked() throws Exception {
        var paymentId = initiate();

        kafka.send(new ProducerRecord<>("ledger.replies", paymentId, fundsReserved(paymentId)))
                .get();

        assertThat(header(deadLetterFor(paymentId), "kafka_dlt-exception-cause-fqcn"))
                .contains("IllegalArgumentException");
    }

    private static FundsReserved fundsReserved(String paymentId) {
        return FundsReserved.newBuilder()
                .setPaymentId(paymentId)
                .setTransactionId(UUID.randomUUID().toString())
                .build();
    }

    private String initiate() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"debtorAccountId": "%s", "creditorAccountId": "%s", "amount": {"amount": 2500, "currency": "EUR"}}""".formatted(UUID.randomUUID(), UUID.randomUUID())))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asString();
    }

    private String status(String paymentId) {
        try {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments/" + paymentId))
                    .build();
            return json.readTree(http.send(request, HttpResponse.BodyHandlers.ofString())
                            .body())
                    .get("status")
                    .asString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void reply(String paymentId, UUID messageId, Object reply) throws Exception {
        var record = new ProducerRecord<String, Object>("ledger.replies", paymentId, reply);
        record.headers().add(OutboxHeaders.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private static RecordMetadata sendRaw(String key, byte[] value) throws Exception {
        try (var producer = new KafkaProducer<String, byte[]>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, MessagingContainers.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class))) {
            var record = new ProducerRecord<String, byte[]>("ledger.replies", key, value);
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
            consumer.subscribe(List.of("ledger.replies.DLT"));
            var deadline = Instant.now().plus(WAIT);
            while (Instant.now().isBefore(deadline)) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
            throw new AssertionError("Nothing on ledger.replies.DLT for " + key);
        }
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        assertThat(header).as("header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        var deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
