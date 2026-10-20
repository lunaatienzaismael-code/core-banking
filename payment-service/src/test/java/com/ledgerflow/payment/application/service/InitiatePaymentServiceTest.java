package com.ledgerflow.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ledgerflow.payment.application.port.in.InitiatePaymentCommand;
import com.ledgerflow.payment.application.port.in.PaymentView;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.application.port.out.PaymentRepository.KeyedPayment;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.IdempotencyKey;
import com.ledgerflow.payment.domain.IdempotencyKeyReusedException;
import com.ledgerflow.payment.domain.InvalidPaymentException;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentStatus;
import com.ledgerflow.payment.domain.RequestFingerprint;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InitiatePaymentServiceTest {

    private static final IdempotencyKey KEY = new IdempotencyKey("0b6f4f8e-2c1a-4d3e-9f50-6a7b8c9d0e1f");
    private static final AccountId DEBTOR = new AccountId(UUID.randomUUID());
    private static final AccountId CREDITOR = new AccountId(UUID.randomUUID());
    private static final Money AMOUNT = Money.of(2_500, "EUR");
    private static final RequestFingerprint FINGERPRINT = RequestFingerprint.of(DEBTOR, CREDITOR, AMOUNT);

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T09:30:00.123456789Z"), ZoneOffset.UTC);
    private final InitiatePaymentService service = new InitiatePaymentService(payments, clock);

    @Test
    void newKeyStoresAnInitiatedPaymentAtTheMicrosecondPrecisionTheDatabaseStores() {
        when(payments.insertIfAbsent(any(), eq(KEY), eq(FINGERPRINT)))
                .thenAnswer(call -> new KeyedPayment(call.getArgument(0), call.getArgument(2)));

        var view = service.initiate(new InitiatePaymentCommand(KEY, DEBTOR, CREDITOR, AMOUNT));

        var inserted = ArgumentCaptor.forClass(Payment.class);
        verify(payments).insertIfAbsent(inserted.capture(), eq(KEY), eq(FINGERPRINT));
        var micros = Instant.parse("2026-10-20T09:30:00.123456Z");
        assertThat(inserted.getValue().initiatedAt()).isEqualTo(micros);
        assertThat(view).isEqualTo(PaymentView.of(inserted.getValue()));
        assertThat(view.status()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(view.debtor()).isEqualTo(DEBTOR);
        assertThat(view.creditor()).isEqualTo(CREDITOR);
        assertThat(view.amount()).isEqualTo(AMOUNT);
        assertThat(view.initiatedAt()).isEqualTo(micros);
    }

    @Test
    void retryWithTheSameRequestReturnsTheStoredPaymentNotTheNewOne() {
        var stored = Payment.initiate(DEBTOR, CREDITOR, AMOUNT, Instant.parse("2026-10-20T09:00:00Z"));
        when(payments.insertIfAbsent(any(), eq(KEY), eq(FINGERPRINT)))
                .thenReturn(new KeyedPayment(stored, FINGERPRINT));

        var view = service.initiate(new InitiatePaymentCommand(KEY, DEBTOR, CREDITOR, AMOUNT));

        assertThat(view).isEqualTo(PaymentView.of(stored));
    }

    @Test
    void keyHeldByADifferentRequestIsRejected() {
        var other = RequestFingerprint.of(DEBTOR, CREDITOR, Money.of(9_999, "EUR"));
        var stored = Payment.initiate(DEBTOR, CREDITOR, Money.of(9_999, "EUR"), Instant.parse("2026-10-20T09:00:00Z"));
        when(payments.insertIfAbsent(any(), eq(KEY), eq(FINGERPRINT))).thenReturn(new KeyedPayment(stored, other));

        assertThatThrownBy(() -> service.initiate(new InitiatePaymentCommand(KEY, DEBTOR, CREDITOR, AMOUNT)))
                .isInstanceOf(IdempotencyKeyReusedException.class)
                .hasMessage("Idempotency-Key '" + KEY + "' was already used for a different request");
    }

    @Test
    void invalidPaymentIsRejectedBeforeAnyKeyIsStored() {
        assertThatThrownBy(() -> service.initiate(new InitiatePaymentCommand(KEY, DEBTOR, DEBTOR, AMOUNT)))
                .isInstanceOf(InvalidPaymentException.class);
        verifyNoInteractions(payments);
    }
}
