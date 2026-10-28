package com.ledgerflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.messaging.FundsReleased;
import com.ledgerflow.messaging.FundsReserved;
import com.ledgerflow.messaging.PostTransfer;
import com.ledgerflow.messaging.PostingRefused;
import com.ledgerflow.messaging.RefusalReason;
import com.ledgerflow.messaging.ReleaseFunds;
import com.ledgerflow.messaging.ReservationRefused;
import com.ledgerflow.messaging.ReserveFunds;
import com.ledgerflow.messaging.TransferPosted;
import com.ledgerflow.messaging.outbox.OutboxHeaders;
import com.ledgerflow.messaging.testing.MessagingContainers;
import com.ledgerflow.payment.application.port.in.AdvancePaymentSagaUseCase;
import com.ledgerflow.payment.application.port.in.SagaInput;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.PaymentId;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
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
import org.apache.avro.specific.SpecificRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The payment saga (ADR 0012) over real HTTP, Kafka and PostgreSQL, with the test playing the ledger: it reads the
 * commands payment-service sends on {@code ledger.commands} and answers on {@code ledger.replies}. The ledger's side of
 * the same commands, including the money moving, is proven by ledger-service's {@code PaymentTransfersIT}; the two meet
 * in the shared Avro contracts. All data is fictitious.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PaymentSagaIT {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaTemplate<String, Object> kafka;

    @Autowired
    private AdvancePaymentSagaUseCase saga;

    @Autowired
    private PaymentRepository payments;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private KafkaConsumer<String, Object> ledgerCommands;

    @BeforeEach
    void listenAsTheLedger() {
        ledgerCommands = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "fake-ledger-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                MessagingContainers.schemaRegistryUrl(),
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
                true));
        ledgerCommands.subscribe(List.of("ledger.commands"));
    }

    @AfterEach
    void stopListening() {
        ledgerCommands.close();
    }

    @Test
    @DisplayName(
            "B1 happy path: INITIATED → RISK_APPROVED → FUNDS_RESERVED → COMPLETED, with the ledger asked to move the amount")
    void paymentIsCompletedThroughTheSaga() throws Exception {
        var debtor = UUID.randomUUID();
        var creditor = UUID.randomUUID();
        var paymentId = initiate(debtor, creditor, 2_500);

        var reserve = nextCommandFor(paymentId, ReserveFunds.class);
        assertThat(reserve.getDebtorAccountId()).isEqualTo(debtor.toString());
        assertThat(reserve.getAmountMinorUnits()).isEqualTo(2_500L);
        assertThat(reserve.getCurrency()).isEqualTo("EUR");
        assertThat(status(paymentId)).isEqualTo("RISK_APPROVED");

        reply(paymentId, UUID.randomUUID(), fundsReserved(paymentId));
        var post = nextCommandFor(paymentId, PostTransfer.class);
        assertThat(post.getCreditorAccountId()).isEqualTo(creditor.toString());
        assertThat(post.getAmountMinorUnits()).isEqualTo(2_500L);
        assertThat(post.getCurrency()).isEqualTo("EUR");
        await(() -> "FUNDS_RESERVED".equals(status(paymentId)));

        reply(
                paymentId,
                UUID.randomUUID(),
                TransferPosted.newBuilder()
                        .setPaymentId(paymentId)
                        .setTransactionId(UUID.randomUUID().toString())
                        .build());
        await(() -> "COMPLETED".equals(status(paymentId)));
        assertThat(commandsSentFor(paymentId)).isEqualTo(2);
    }

    @Test
    @DisplayName("B2 reservation refused: REJECTED with the reason, and no transfer is ever requested")
    void refusedReservationRejectsThePayment() throws Exception {
        var paymentId = initiate(UUID.randomUUID(), UUID.randomUUID(), 2_500);
        nextCommandFor(paymentId, ReserveFunds.class);

        reply(
                paymentId,
                UUID.randomUUID(),
                ReservationRefused.newBuilder()
                        .setPaymentId(paymentId)
                        .setReason(RefusalReason.INSUFFICIENT_FUNDS)
                        .setDetail("not enough on the debtor's account")
                        .build());

        await(() -> "REJECTED".equals(status(paymentId)));
        assertThat(payment(paymentId).get("reason").asString())
                .isEqualTo("INSUFFICIENT_FUNDS: not enough on the debtor's account");
        assertThat(commandsSentFor(paymentId)).isOne();
    }

    @Test
    @DisplayName("B3 a redelivered reply does not re-run its step; a stale reply afterwards is ignored")
    void redeliveredAndStaleRepliesChangeNothing() throws Exception {
        var paymentId = initiate(UUID.randomUUID(), UUID.randomUUID(), 2_500);
        nextCommandFor(paymentId, ReserveFunds.class);
        var replyId = UUID.randomUUID();
        var staleId = UUID.randomUUID();

        reply(paymentId, replyId, fundsReserved(paymentId));
        reply(paymentId, replyId, fundsReserved(paymentId));
        // Stale for FUNDS_RESERVED; on the same key, so once it is recorded every reply before it was processed.
        reply(
                paymentId,
                staleId,
                ReservationRefused.newBuilder()
                        .setPaymentId(paymentId)
                        .setReason(RefusalReason.UNKNOWN_ACCOUNT)
                        .setDetail("late")
                        .build());

        await(() -> processed(staleId));
        assertThat(status(paymentId)).isEqualTo("FUNDS_RESERVED");
        assertThat(commandsSentFor(paymentId)).isEqualTo(2); // ReserveFunds and one PostTransfer
        assertThat(processed(replyId)).isTrue();
    }

    @Test
    @DisplayName(
            "B4 a transition that loses an optimistic-lock race commits nothing: no status, no command, no inbox row")
    void transitionLosingTheOptimisticLockCommitsNothing() throws Exception {
        var paymentId = initiate(UUID.randomUUID(), UUID.randomUUID(), 2_500);
        nextCommandFor(paymentId, ReserveFunds.class);
        var id = new PaymentId(UUID.fromString(paymentId));
        var losingInput = UUID.randomUUID();
        var outer = new TransactionTemplate(transactionManager);
        var concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                    saga.advance(new SagaInput.FundsReserved(losingInput, id));
                    // Another instance moves the same payment on and commits first.
                    concurrent.executeWithoutResult(other -> {
                        var payment = payments.findById(id).orElseThrow();
                        payment.reject("rejected concurrently");
                        payments.update(payment);
                    });
                }))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(status(paymentId)).isEqualTo("REJECTED");
        assertThat(commandsSentFor(paymentId)).isOne();
        assertThat(processed(losingInput)).isFalse();
    }

    @Test
    @DisplayName(
            "AC4.3 posting refused after the reservation: the funds are released and the payment ends FAILED with the reason")
    void refusedPostingIsCompensated() throws Exception {
        var debtor = UUID.randomUUID();
        var paymentId = initiate(debtor, UUID.randomUUID(), 2_500);
        nextCommandFor(paymentId, ReserveFunds.class);
        reply(paymentId, UUID.randomUUID(), fundsReserved(paymentId));
        nextCommandFor(paymentId, PostTransfer.class);

        reply(paymentId, UUID.randomUUID(), postingRefused(paymentId));

        var release = nextCommandFor(paymentId, ReleaseFunds.class);
        assertThat(release.getDebtorAccountId()).isEqualTo(debtor.toString());
        assertThat(release.getAmountMinorUnits()).isEqualTo(2_500L);
        assertThat(release.getCurrency()).isEqualTo("EUR");
        assertThat(release.getReason()).isEqualTo("UNKNOWN_ACCOUNT: creditor account does not exist");
        assertThat(status(paymentId)).isEqualTo("FUNDS_RESERVED");

        reply(
                paymentId,
                UUID.randomUUID(),
                FundsReleased.newBuilder()
                        .setPaymentId(paymentId)
                        .setTransactionId(UUID.randomUUID().toString())
                        .setReason(release.getReason())
                        .build());
        await(() -> "FAILED".equals(status(paymentId)));
        assertThat(payment(paymentId).get("reason").asString())
                .isEqualTo("UNKNOWN_ACCOUNT: creditor account does not exist");
        assertThat(commandsSentFor(paymentId)).isEqualTo(3); // ReserveFunds, PostTransfer, ReleaseFunds
    }

    @Test
    @DisplayName("B1 a redelivered PostingRefused sends no second release")
    void redeliveredPostingRefusedSendsNoSecondRelease() throws Exception {
        var paymentId = initiate(UUID.randomUUID(), UUID.randomUUID(), 2_500);
        nextCommandFor(paymentId, ReserveFunds.class);
        reply(paymentId, UUID.randomUUID(), fundsReserved(paymentId));
        nextCommandFor(paymentId, PostTransfer.class);
        var refusalId = UUID.randomUUID();
        var markerId = UUID.randomUUID();

        reply(paymentId, refusalId, postingRefused(paymentId));
        reply(paymentId, refusalId, postingRefused(paymentId));
        // Stale for FUNDS_RESERVED; on the same key, so once it is recorded both copies were processed.
        reply(paymentId, markerId, fundsReserved(paymentId));

        await(() -> processed(markerId));
        assertThat(commandsSentFor(paymentId)).isEqualTo(3); // ReserveFunds, PostTransfer, one ReleaseFunds
        assertThat(status(paymentId)).isEqualTo("FUNDS_RESERVED");
    }

    private static PostingRefused postingRefused(String paymentId) {
        return PostingRefused.newBuilder()
                .setPaymentId(paymentId)
                .setReason(RefusalReason.UNKNOWN_ACCOUNT)
                .setDetail("creditor account does not exist")
                .build();
    }

    private static FundsReserved fundsReserved(String paymentId) {
        return FundsReserved.newBuilder()
                .setPaymentId(paymentId)
                .setTransactionId(UUID.randomUUID().toString())
                .build();
    }

    private String initiate(UUID debtor, UUID creditor, long amount) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"debtorAccountId": "%s", "creditorAccountId": "%s", "amount": {"amount": %d, "currency": "EUR"}}""".formatted(debtor, creditor, amount)))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("id").asString();
    }

    private JsonNode payment(String paymentId) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/payments/" + paymentId))
                .build();
        return json.readTree(
                http.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private String status(String paymentId) {
        try {
            return payment(paymentId).get("status").asString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> T nextCommandFor(String paymentId, Class<T> type) {
        var deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            for (var record : ledgerCommands.poll(Duration.ofMillis(500))) {
                if (paymentId.equals(record.key())) {
                    assertThat(record.value()).isInstanceOf(type);
                    return type.cast(record.value());
                }
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " for payment " + paymentId);
    }

    private void reply(String paymentId, UUID messageId, SpecificRecord reply) throws Exception {
        var record = new ProducerRecord<String, Object>("ledger.replies", paymentId, reply);
        record.headers().add(OutboxHeaders.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private long commandsSentFor(String paymentId) {
        return jdbc.queryForObject(
                "select count(*) from outbox_messages where topic = 'ledger.commands' and message_key = ?",
                Long.class,
                paymentId);
    }

    private boolean processed(UUID messageId) {
        return jdbc.queryForObject(
                        "select count(*) from processed_messages where message_id = ?", Long.class, messageId)
                == 1;
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        var deadline = Instant.now().plus(WAIT);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(100);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
