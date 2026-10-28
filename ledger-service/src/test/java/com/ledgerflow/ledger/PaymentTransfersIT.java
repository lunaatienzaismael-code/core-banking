package com.ledgerflow.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.messaging.FundsReserved;
import com.ledgerflow.messaging.RefusalReason;
import com.ledgerflow.messaging.ReservationRefused;
import com.ledgerflow.messaging.ReserveFunds;
import com.ledgerflow.messaging.outbox.OutboxHeaders;
import com.ledgerflow.messaging.testing.MessagingContainers;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * ReserveFunds commands over real Kafka against real PostgreSQL (ADR 0012, ADR 0013): the reply, the counters of ADR
 * 0009 and the inbox. The transit account is shared by every test, so its counters are compared as deltas. All data is
 * fictitious.
 */
@SpringBootTest(properties = "ledgerflow.outbox.relay-interval=200ms")
@Import(TestcontainersConfiguration.class)
class ReserveFundsIT {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final UUID TRANSIT =
            AccountId.paymentsInTransit(Currency.getInstance("EUR")).value();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaTemplate<String, Object> kafka;

    private LedgerFixture fixture;
    private UUID debtor;

    @BeforeEach
    void fundedCustomerAccount() {
        fixture = new LedgerFixture(jdbc);
        var cash = fixture.account("EUR", "ASSET");
        debtor = fixture.account("EUR", "LIABILITY", "NOT_ALLOWED");
        fixture.transfer(cash, debtor, 10_000, "EUR");
    }

    @Test
    @DisplayName(
            "ADR 0012 ReserveFunds moves the amount from the debtor to the transit account and replies FundsReserved")
    void reserveFundsMovesTheAmountToTransitAndReplies() throws Exception {
        var paymentId = UUID.randomUUID().toString();
        var transitBefore = counters(TRANSIT);

        send(paymentId, UUID.randomUUID(), reserve(paymentId, debtor, 2_500, "EUR"));

        var reply = (FundsReserved) repliesFor(paymentId, 1).getFirst();
        var transaction = UUID.fromString(reply.getTransactionId());
        assertThat(postingsOf(transaction))
                .containsExactlyInAnyOrderEntriesOf(Map.of(debtor, 2_500L, TRANSIT, -2_500L));
        assertThat(balanceOf(debtor)).isEqualTo(10_000L - 2_500L);
        assertThat(counters(TRANSIT)).containsExactly(transitBefore.get(0), transitBefore.get(1) + 2_500L);
    }

    @Test
    @DisplayName("AC4.2 a redelivered ReserveFunds records no second transaction and sends no second reply")
    void redeliveredReserveFundsHasNoSecondEffect() throws Exception {
        var paymentId = UUID.randomUUID().toString();
        var messageId = UUID.randomUUID();
        var command = reserve(paymentId, debtor, 2_500, "EUR");
        var transitBefore = counters(TRANSIT);

        send(paymentId, messageId, command);
        send(paymentId, messageId, command);
        // Same key, same partition, consumed in order: once the marker's reply arrives, both copies were processed.
        send(paymentId, UUID.randomUUID(), reserve(paymentId, UUID.randomUUID(), 1, "EUR"));

        var replies = repliesFor(paymentId, 2);
        assertThat(replies).hasExactlyElementsOfTypes(FundsReserved.class, ReservationRefused.class);
        assertThat(transactionsOf(debtor)).isEqualTo(2); // the funding and one reservation
        assertThat(balanceOf(debtor)).isEqualTo(10_000L - 2_500L);
        assertThat(counters(TRANSIT)).containsExactly(transitBefore.get(0), transitBefore.get(1) + 2_500L);
        assertThat(jdbc.queryForObject(
                        "select count(*) from processed_messages where message_id = ?", Long.class, messageId))
                .isOne();
    }

    @Test
    @DisplayName("B3 insufficient funds: ReservationRefused INSUFFICIENT_FUNDS and nothing moved")
    void insufficientFundsIsRefusedAndNothingMoves() throws Exception {
        var paymentId = UUID.randomUUID().toString();
        var transitBefore = counters(TRANSIT);

        send(paymentId, UUID.randomUUID(), reserve(paymentId, debtor, 10_001, "EUR"));

        assertRefused(paymentId, RefusalReason.INSUFFICIENT_FUNDS);
        assertThat(transactionsOf(debtor)).isOne();
        assertThat(balanceOf(debtor)).isEqualTo(10_000L);
        assertThat(counters(TRANSIT)).isEqualTo(transitBefore);
    }

    @Test
    @DisplayName("B3 unknown debtor: ReservationRefused UNKNOWN_ACCOUNT and nothing moved")
    void unknownDebtorIsRefusedAndNothingMoves() throws Exception {
        var paymentId = UUID.randomUUID().toString();
        var transitBefore = counters(TRANSIT);

        send(paymentId, UUID.randomUUID(), reserve(paymentId, UUID.randomUUID(), 2_500, "EUR"));

        assertRefused(paymentId, RefusalReason.UNKNOWN_ACCOUNT);
        assertThat(counters(TRANSIT)).isEqualTo(transitBefore);
    }

    @Test
    @DisplayName("B3 amount in another currency than the debtor's: ReservationRefused CURRENCY_MISMATCH, nothing moved")
    void currencyMismatchIsRefusedAndNothingMoves() throws Exception {
        var paymentId = UUID.randomUUID().toString();

        send(paymentId, UUID.randomUUID(), reserve(paymentId, debtor, 2_500, "USD"));

        assertRefused(paymentId, RefusalReason.CURRENCY_MISMATCH);
        assertThat(transactionsOf(debtor)).isOne();
        assertThat(balanceOf(debtor)).isEqualTo(10_000L);
    }

    private void assertRefused(String paymentId, RefusalReason reason) {
        var reply = (ReservationRefused) repliesFor(paymentId, 1).getFirst();
        assertThat(reply.getReason()).isEqualTo(reason);
        assertThat(reply.getDetail()).isNotBlank();
    }

    private static ReserveFunds reserve(String paymentId, UUID debtor, long amount, String currency) {
        return ReserveFunds.newBuilder()
                .setPaymentId(paymentId)
                .setDebtorAccountId(debtor.toString())
                .setAmountMinorUnits(amount)
                .setCurrency(currency)
                .build();
    }

    private void send(String paymentId, UUID messageId, ReserveFunds command) throws Exception {
        var record = new ProducerRecord<String, Object>("ledger.commands", paymentId, command);
        record.headers().add(OutboxHeaders.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    /** Waits for {@code expected} replies keyed by the payment, then checks no more arrive shortly after. */
    private static List<Object> repliesFor(String paymentId, int expected) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "reserve-funds-it-" + UUID.randomUUID(),
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
            consumer.subscribe(List.of("ledger.replies"));
            var replies = new ArrayList<Object>();
            var deadline = Instant.now().plus(WAIT);
            while (replies.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (paymentId.equals(record.key())) {
                        replies.add(record.value());
                    }
                });
            }
            consumer.poll(Duration.ofSeconds(1)).forEach(record -> {
                if (paymentId.equals(record.key())) {
                    replies.add(record.value());
                }
            });
            assertThat(replies).as("replies for payment %s", paymentId).hasSize(expected);
            return replies;
        }
    }

    private Map<UUID, Long> postingsOf(UUID transaction) {
        var postings = new HashMap<UUID, Long>();
        jdbc.query(
                "select account_id, amount from postings where transaction_id = ?",
                row -> {
                    postings.put(row.getObject("account_id", UUID.class), row.getLong("amount"));
                },
                transaction);
        return postings;
    }

    /** Normal-side (credit) balance of a credit-normal account, from its counters (ADR 0009). */
    private long balanceOf(UUID account) {
        var counters = counters(account);
        return counters.get(1) - counters.get(0);
    }

    private List<Long> counters(UUID account) {
        return jdbc
                .query(
                        "select debits_posted, credits_posted from accounts where id = ?",
                        (row, number) -> List.of(row.getLong(1), row.getLong(2)),
                        account)
                .stream()
                .findFirst()
                .orElse(List.of(0L, 0L));
    }

    private long transactionsOf(UUID account) {
        return jdbc.queryForObject(
                "select count(distinct transaction_id) from postings where account_id = ?", Long.class, account);
    }
}
