package com.ledgerflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.messaging.PaymentInitiated;
import com.ledgerflow.messaging.testing.MessagingContainers;
import com.ledgerflow.payment.application.port.in.InitiatePaymentCommand;
import com.ledgerflow.payment.application.port.in.InitiatePaymentUseCase;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.IdempotencyKey;
import com.ledgerflow.payment.domain.Money;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** AC4.1 transactional outbox for payment initiation (ADR 0013), against real PostgreSQL and Kafka. Fictitious data. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "ledgerflow.outbox.relay-interval=200ms",
            "ledgerflow.outbox.send-timeout=2s",
            "spring.kafka.producer.properties.max.block.ms=2000"
        })
@Import(TestcontainersConfiguration.class)
class PaymentOutboxIT {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InitiatePaymentUseCase initiatePayment;

    @Autowired
    private TransactionTemplate transactions;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName(
            "AC4.1 an initiated payment and its PaymentInitiated event are stored together and the event is published")
    void initiatedPaymentAndItsEventAreStoredTogetherAndPublished() throws Exception {
        var debtor = UUID.randomUUID();
        var creditor = UUID.randomUUID();

        var paymentId = initiate(UUID.randomUUID().toString(), body(debtor, creditor, 2_500));

        assertThat(outboxRowsOf(paymentId)).isOne();
        var event = awaitEvent(paymentId);
        assertThat(event.getDebtorAccountId()).isEqualTo(debtor.toString());
        assertThat(event.getCreditorAccountId()).isEqualTo(creditor.toString());
        assertThat(event.getAmountMinorUnits()).isEqualTo(2_500L);
        assertThat(event.getCurrency()).isEqualTo("EUR");
        await(() -> isPublished(paymentId));
    }

    @Test
    @DisplayName("AC4.1 when the initiation transaction rolls back, neither the payment nor its event exists")
    void rolledBackInitiationLeavesNeitherPaymentNorEvent() {
        var command = new InitiatePaymentCommand(
                new IdempotencyKey(UUID.randomUUID().toString()),
                new AccountId(UUID.randomUUID()),
                new AccountId(UUID.randomUUID()),
                Money.of(2_500, "EUR"));

        var view = transactions.execute(status -> {
            var initiated = initiatePayment.initiate(command);
            status.setRollbackOnly();
            return initiated;
        });

        var paymentId = view.id().value().toString();
        assertThat(jdbc.queryForObject(
                        "select count(*) from payments where id = ?",
                        Long.class,
                        view.id().value()))
                .isZero();
        assertThat(outboxRowsOf(paymentId)).isZero();
    }

    @Test
    @DisplayName("AC4.1 with Kafka down a payment is still created, and its event is published once Kafka is back")
    void withKafkaDownThePaymentIsCreatedAndItsEventPublishedOnceKafkaIsBack() throws Exception {
        // Warm up: the event's schema is registered and the topic's metadata cached before the outage.
        awaitEvent(initiate(UUID.randomUUID().toString(), body(UUID.randomUUID(), UUID.randomUUID(), 100)));

        String paymentId;
        MessagingContainers.pauseKafka();
        try {
            paymentId = initiate(UUID.randomUUID().toString(), body(UUID.randomUUID(), UUID.randomUUID(), 2_500));

            // Several relay runs fail meanwhile; the event waits in the outbox.
            Thread.sleep(3_000);
            assertThat(outboxRowsOf(paymentId)).isOne();
            assertThat(isPublished(paymentId)).isFalse();
        } finally {
            MessagingContainers.unpauseKafka();
        }

        assertThat(awaitEvent(paymentId).getAmountMinorUnits()).isEqualTo(2_500L);
        await(() -> isPublished(paymentId));
    }

    @Test
    @DisplayName("B2 an idempotent replay writes no second event")
    void idempotentReplayWritesNoSecondEvent() throws Exception {
        var key = UUID.randomUUID().toString();
        var body = body(UUID.randomUUID(), UUID.randomUUID(), 2_500);

        var paymentId = initiate(key, body);
        var replayed = initiate(key, body);

        assertThat(replayed).isEqualTo(paymentId);
        assertThat(outboxRowsOf(paymentId)).isOne();
    }

    private String initiate(String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asString();
    }

    private long outboxRowsOf(String paymentId) {
        return jdbc.queryForObject(
                "select count(*) from outbox_messages where topic = 'payments.events' and message_key = ?",
                Long.class,
                paymentId);
    }

    private boolean isPublished(String paymentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select bool_and(published_at is not null) from outbox_messages where message_key = ?",
                Boolean.class,
                paymentId));
    }

    private static PaymentInitiated awaitEvent(String paymentId) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "payment-outbox-it-" + UUID.randomUUID(),
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
            consumer.subscribe(List.of("payments.events"));
            var deadline = Instant.now().plus(WAIT);
            Optional<PaymentInitiated> found = Optional.empty();
            while (found.isEmpty() && Instant.now().isBefore(deadline)) {
                for (var received : consumer.poll(Duration.ofMillis(500))) {
                    if (paymentId.equals(received.key())) {
                        found = Optional.of((PaymentInitiated) received.value());
                    }
                }
            }
            assertThat(found)
                    .as("PaymentInitiated for %s on payments.events", paymentId)
                    .isPresent();
            return found.get();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        var deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static String body(UUID debtor, UUID creditor, long amount) {
        return """
                {"debtorAccountId": "%s", "creditorAccountId": "%s", "amount": {"amount": %d, "currency": "EUR"}}""".formatted(debtor, creditor, amount);
    }
}
