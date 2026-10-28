package com.ledgerflow.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.payment.application.port.in.SagaInput;
import com.ledgerflow.payment.application.port.out.LedgerCommands;
import com.ledgerflow.payment.application.port.out.MessageInbox;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import com.ledgerflow.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AdvancePaymentSagaServiceTest {

    private final MessageInbox inbox = mock(MessageInbox.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final LedgerCommands ledger = mock(LedgerCommands.class);
    private final AdvancePaymentSagaService saga = new AdvancePaymentSagaService(inbox, payments, ledger);

    @BeforeEach
    void firstDeliveryUnlessATestSaysOtherwise() {
        when(inbox.firstDelivery(any())).thenReturn(true);
    }

    @Test
    @DisplayName("ADR 0012 PaymentInitiated approves risk and asks the ledger to reserve funds")
    void initiatedPaymentIsApprovedAndItsFundsRequested() {
        var payment = stored(PaymentStatus.INITIATED);

        saga.advance(new SagaInput.PaymentInitiated(UUID.randomUUID(), payment.id()));

        assertThat(payment.status()).isEqualTo(PaymentStatus.RISK_APPROVED);
        verify(ledger).reserveFunds(payment);
        verify(ledger, never()).postTransfer(any());
        verify(payments).update(payment);
    }

    @Test
    @DisplayName("ADR 0012 FundsReserved records the reservation and asks the ledger to post the transfer")
    void reservedFundsAreRecordedAndTheTransferRequested() {
        var payment = stored(PaymentStatus.RISK_APPROVED);

        saga.advance(new SagaInput.FundsReserved(UUID.randomUUID(), payment.id()));

        assertThat(payment.status()).isEqualTo(PaymentStatus.FUNDS_RESERVED);
        verify(ledger).postTransfer(payment);
        verify(ledger, never()).reserveFunds(any());
        verify(payments).update(payment);
    }

    @Test
    @DisplayName("B2 ReservationRefused rejects the payment with the reason and sends nothing")
    void refusedReservationRejectsThePaymentWithTheReason() {
        var payment = stored(PaymentStatus.RISK_APPROVED);

        saga.advance(new SagaInput.ReservationRefused(
                UUID.randomUUID(), payment.id(), "INSUFFICIENT_FUNDS", "not enough on the debtor's account"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.reason()).contains("INSUFFICIENT_FUNDS: not enough on the debtor's account");
        verifyNoInteractions(ledger);
        verify(payments).update(payment);
    }

    @Test
    @DisplayName("ADR 0012 TransferPosted completes the payment and sends nothing")
    void postedTransferCompletesThePayment() {
        var payment = stored(PaymentStatus.FUNDS_RESERVED);

        saga.advance(new SagaInput.TransferPosted(UUID.randomUUID(), payment.id()));

        assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
        verifyNoInteractions(ledger);
        verify(payments).update(payment);
    }

    @Test
    @DisplayName("B3 a redelivered input does not re-run its step")
    void redeliveredInputChangesNothing() {
        when(inbox.firstDelivery(any())).thenReturn(false);

        saga.advance(new SagaInput.FundsReserved(UUID.randomUUID(), PaymentId.random()));

        verifyNoInteractions(payments, ledger);
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("inputsTheTransitionTableDoesNotAllow")
    @DisplayName(
            "B3 a stale or out-of-order input is ignored outside its ADR 0012 status: nothing changes, nothing sent")
    void inputNotAllowedFromTheCurrentStatusIsIgnored(String input, PaymentStatus status) {
        var payment = stored(status);

        saga.advance(INPUTS.get(input).apply(payment.id()));

        assertThat(payment.status()).isEqualTo(status);
        verifyNoInteractions(ledger);
        verify(payments, never()).update(any());
    }

    @Test
    void anInputForAnUnknownPaymentFails() {
        var unknown = PaymentId.random();
        when(payments.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saga.advance(new SagaInput.TransferPosted(UUID.randomUUID(), unknown)))
                .isInstanceOf(PaymentNotFoundException.class);
        verifyNoInteractions(ledger);
    }

    private static final Map<String, Function<PaymentId, SagaInput>> INPUTS = Map.of(
            "PaymentInitiated", id -> new SagaInput.PaymentInitiated(UUID.randomUUID(), id),
            "FundsReserved", id -> new SagaInput.FundsReserved(UUID.randomUUID(), id),
            "ReservationRefused", id -> new SagaInput.ReservationRefused(UUID.randomUUID(), id, "UNKNOWN_ACCOUNT", "-"),
            "TransferPosted", id -> new SagaInput.TransferPosted(UUID.randomUUID(), id));

    /** Every (input, status) pair except the four the saga table of ADR 0012 acts on. */
    static Stream<Arguments> inputsTheTransitionTableDoesNotAllow() {
        var allowed = List.of(
                "PaymentInitiated/INITIATED",
                "FundsReserved/RISK_APPROVED",
                "ReservationRefused/RISK_APPROVED",
                "TransferPosted/FUNDS_RESERVED");
        var pairs = new ArrayList<Arguments>();
        for (var input : List.of("PaymentInitiated", "FundsReserved", "ReservationRefused", "TransferPosted")) {
            for (var status : PaymentStatus.values()) {
                if (!allowed.contains(input + "/" + status)) {
                    pairs.add(Arguments.of(input, status));
                }
            }
        }
        return pairs.stream();
    }

    private Payment stored(PaymentStatus status) {
        var payment = Payment.restore(
                PaymentId.random(),
                new AccountId(UUID.randomUUID()),
                new AccountId(UUID.randomUUID()),
                Money.of(2_500, "EUR"),
                Instant.parse("2026-10-28T09:00:00Z"),
                status,
                status == PaymentStatus.REJECTED || status == PaymentStatus.FAILED ? "earlier reason" : null);
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));
        return payment;
    }
}
