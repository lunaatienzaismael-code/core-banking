package com.ledgerflow.payment.domain;

import static com.ledgerflow.payment.domain.PaymentStatus.COMPLETED;
import static com.ledgerflow.payment.domain.PaymentStatus.FAILED;
import static com.ledgerflow.payment.domain.PaymentStatus.FUNDS_RESERVED;
import static com.ledgerflow.payment.domain.PaymentStatus.INITIATED;
import static com.ledgerflow.payment.domain.PaymentStatus.REJECTED;
import static com.ledgerflow.payment.domain.PaymentStatus.RISK_APPROVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentTest {

    private static final Instant JAN_15 = Instant.parse("2026-01-15T09:30:00Z");
    private static final AccountId DEBTOR = new AccountId(UUID.randomUUID());
    private static final AccountId CREDITOR = new AccountId(UUID.randomUUID());

    /** ADR 0011's table of allowed transitions, written out independently of {@link PaymentStatus#next()}. */
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            INITIATED, EnumSet.of(RISK_APPROVED, REJECTED),
            RISK_APPROVED, EnumSet.of(FUNDS_RESERVED, REJECTED),
            FUNDS_RESERVED, EnumSet.of(COMPLETED, FAILED),
            COMPLETED, EnumSet.noneOf(PaymentStatus.class),
            REJECTED, EnumSet.noneOf(PaymentStatus.class),
            FAILED, EnumSet.noneOf(PaymentStatus.class));

    /** The business event that moves a payment to each status; nothing moves a payment back to INITIATED. */
    private static final Map<PaymentStatus, Consumer<Payment>> EVENT_TO = Map.of(
            RISK_APPROVED, Payment::approveRisk,
            FUNDS_RESERVED, Payment::reserveFunds,
            COMPLETED, Payment::complete,
            REJECTED, payment -> payment.reject("risk refused"),
            FAILED, payment -> payment.fail("transfer failed, funds released"));

    /** Allowed events that bring a new payment to each status. */
    private static final Map<PaymentStatus, List<PaymentStatus>> PATH_TO = Map.of(
            INITIATED, List.of(),
            RISK_APPROVED, List.of(RISK_APPROVED),
            FUNDS_RESERVED, List.of(RISK_APPROVED, FUNDS_RESERVED),
            COMPLETED, List.of(RISK_APPROVED, FUNDS_RESERVED, COMPLETED),
            REJECTED, List.of(REJECTED),
            FAILED, List.of(RISK_APPROVED, FUNDS_RESERVED, FAILED));

    @Test
    @DisplayName("B1 a new payment is INITIATED with its accounts, amount and time")
    void initiatedPaymentCarriesItsData() {
        var payment = Payment.initiate(DEBTOR, CREDITOR, Money.of(2_500, "EUR"), JAN_15);

        assertThat(payment.id()).isNotNull();
        assertThat(payment.debtor()).isEqualTo(DEBTOR);
        assertThat(payment.creditor()).isEqualTo(CREDITOR);
        assertThat(payment.amount()).isEqualTo(Money.of(2_500, "EUR"));
        assertThat(payment.initiatedAt()).isEqualTo(JAN_15);
        assertThat(payment.status()).isEqualTo(INITIATED);
        assertThat(payment.reason()).isEmpty();
    }

    @Test
    void eachPaymentGetsItsOwnId() {
        assertThat(initiated().id()).isNotEqualTo(initiated().id());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("B3 a payment of zero or a negative amount is rejected")
    void nonPositiveAmountIsRejected(long amount) {
        assertThatThrownBy(() -> Payment.initiate(DEBTOR, CREDITOR, Money.of(amount, "EUR"), JAN_15))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage("Payment amount must be positive, got " + amount + " EUR");
    }

    @Test
    @DisplayName("B3 a payment of one minor unit is accepted")
    void smallestPositiveAmountIsAccepted() {
        assertThat(Payment.initiate(DEBTOR, CREDITOR, Money.of(1, "EUR"), JAN_15)
                        .amount())
                .isEqualTo(Money.of(1, "EUR"));
    }

    @Test
    @DisplayName("B3 the same account as debtor and creditor is rejected")
    void sameDebtorAndCreditorIsRejected() {
        assertThatThrownBy(() -> Payment.initiate(DEBTOR, DEBTOR, Money.of(100, "EUR"), JAN_15))
                .isInstanceOf(InvalidPaymentException.class)
                .hasMessage("Debtor and creditor are the same account " + DEBTOR);
    }

    @Test
    void missingDataIsAProgrammingError() {
        var amount = Money.of(100, "EUR");
        assertThatThrownBy(() -> Payment.initiate(null, CREDITOR, amount, JAN_15))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Payment.initiate(DEBTOR, null, amount, JAN_15))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Payment.initiate(DEBTOR, CREDITOR, null, JAN_15))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Payment.initiate(DEBTOR, CREDITOR, amount, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("AC3.3 INITIATED → RISK_APPROVED when the risk check passes")
    void riskApproval() {
        var payment = initiated();

        payment.approveRisk();

        assertThat(payment.status()).isEqualTo(RISK_APPROVED);
        assertThat(payment.reason()).isEmpty();
    }

    @Test
    @DisplayName("AC3.3 INITIATED → REJECTED with the reason")
    void rejectionBeforeRiskApproval() {
        var payment = initiated();

        payment.reject("risk refused");

        assertThat(payment.status()).isEqualTo(REJECTED);
        assertThat(payment.reason()).contains("risk refused");
    }

    @Test
    @DisplayName("AC3.3 RISK_APPROVED → FUNDS_RESERVED when the ledger holds the funds")
    void fundsReservation() {
        var payment = in(RISK_APPROVED);

        payment.reserveFunds();

        assertThat(payment.status()).isEqualTo(FUNDS_RESERVED);
    }

    @Test
    @DisplayName("AC3.3 RISK_APPROVED → REJECTED when the ledger refuses the reservation")
    void rejectionOfTheReservation() {
        var payment = in(RISK_APPROVED);

        payment.reject("insufficient funds");

        assertThat(payment.status()).isEqualTo(REJECTED);
        assertThat(payment.reason()).contains("insufficient funds");
    }

    @Test
    @DisplayName("AC3.3 FUNDS_RESERVED → COMPLETED when the transfer is posted")
    void completion() {
        var payment = in(FUNDS_RESERVED);

        payment.complete();

        assertThat(payment.status()).isEqualTo(COMPLETED);
        assertThat(payment.reason()).isEmpty();
    }

    @Test
    @DisplayName("AC3.3 FUNDS_RESERVED → FAILED with the reason, once the hold is released")
    void failureAfterReservation() {
        var payment = in(FUNDS_RESERVED);

        payment.fail("transfer failed, funds released");

        assertThat(payment.status()).isEqualTo(FAILED);
        assertThat(payment.reason()).contains("transfer failed, funds released");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("illegalTransitions")
    @DisplayName("AC3.3 every transition outside ADR 0011's table is rejected and changes nothing")
    void illegalTransitionIsRejected(PaymentStatus from, PaymentStatus to) {
        var payment = in(from);

        assertThatThrownBy(() -> EVENT_TO.get(to).accept(payment))
                .isInstanceOf(IllegalPaymentTransitionException.class)
                .hasMessage("Payment " + payment.id() + " cannot move from " + from + " to " + to);
        assertThat(payment.status()).isEqualTo(from);
        assertThat(payment.reason()).isEqualTo(in(from).reason());
    }

    @Test
    void theIllegalTransitionsAreEveryPairOutsideTheTable() {
        // 6 statuses × 5 event targets, minus the 6 allowed moves.
        assertThat(illegalTransitions()).hasSize(24);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void statusTableMatchesAdr0011(PaymentStatus status) {
        assertThat(status.next()).isEqualTo(ALLOWED.get(status));
        assertThat(status.isTerminal()).isEqualTo(ALLOWED.get(status).isEmpty());
    }

    @Test
    void rejectionAndFailureNeedAReason() {
        assertThatThrownBy(() -> initiated().reject(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> in(FUNDS_RESERVED).fail(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void restoredPaymentKeepsItsStoredStatusAndReason(PaymentStatus status) {
        var stored = in(status);

        var restored = Payment.restore(
                stored.id(),
                DEBTOR,
                CREDITOR,
                Money.of(2_500, "EUR"),
                JAN_15,
                status,
                stored.reason().orElse(null));

        assertThat(restored.id()).isEqualTo(stored.id());
        assertThat(restored.debtor()).isEqualTo(DEBTOR);
        assertThat(restored.creditor()).isEqualTo(CREDITOR);
        assertThat(restored.amount()).isEqualTo(Money.of(2_500, "EUR"));
        assertThat(restored.initiatedAt()).isEqualTo(JAN_15);
        assertThat(restored.status()).isEqualTo(status);
        assertThat(restored.reason()).isEqualTo(stored.reason());
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentStatus.class,
            names = {"REJECTED", "FAILED"})
    void restoredRejectionOrFailureNeedsAReason(PaymentStatus status) {
        var id = PaymentId.random();

        assertThatThrownBy(() -> restore(id, status, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment " + id + " in " + status + " cannot have reason null");
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentStatus.class,
            names = {"INITIATED", "RISK_APPROVED", "FUNDS_RESERVED", "COMPLETED"})
    void restoredPaymentOutsideRejectionOrFailureHasNoReason(PaymentStatus status) {
        var id = PaymentId.random();

        assertThatThrownBy(() -> restore(id, status, "stray"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Payment " + id + " in " + status + " cannot have reason stray");
    }

    @Test
    void restoredPaymentObeysTheSameRulesAsANewOne() {
        assertThatThrownBy(() -> Payment.restore(
                        PaymentId.random(), DEBTOR, DEBTOR, Money.of(100, "EUR"), JAN_15, INITIATED, null))
                .isInstanceOf(InvalidPaymentException.class);
        assertThatThrownBy(() -> Payment.restore(
                        PaymentId.random(), DEBTOR, CREDITOR, Money.of(0, "EUR"), JAN_15, INITIATED, null))
                .isInstanceOf(InvalidPaymentException.class);
        assertThatThrownBy(() -> restore(PaymentId.random(), null, null)).isInstanceOf(NullPointerException.class);
    }

    private static Payment restore(PaymentId id, PaymentStatus status, String reason) {
        return Payment.restore(id, DEBTOR, CREDITOR, Money.of(100, "EUR"), JAN_15, status, reason);
    }

    static List<Arguments> illegalTransitions() {
        var pairs = new ArrayList<Arguments>();
        for (var from : PaymentStatus.values()) {
            EVENT_TO.keySet().stream()
                    .filter(to -> !ALLOWED.get(from).contains(to))
                    .sorted()
                    .forEach(to -> pairs.add(Arguments.of(from, to)));
        }
        return pairs;
    }

    private static Payment initiated() {
        return Payment.initiate(DEBTOR, CREDITOR, Money.of(2_500, "EUR"), JAN_15);
    }

    private static Payment in(PaymentStatus status) {
        var payment = initiated();
        PATH_TO.get(status).forEach(step -> EVENT_TO.get(step).accept(payment));
        return payment;
    }
}
