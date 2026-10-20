package com.ledgerflow.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ledgerflow.payment.application.port.in.InitiatePaymentCommand;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.InvalidPaymentException;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InitiatePaymentServiceTest {

    private static final AccountId DEBTOR = new AccountId(UUID.randomUUID());
    private static final AccountId CREDITOR = new AccountId(UUID.randomUUID());

    private final PaymentRepository payments = mock(PaymentRepository.class);

    @Test
    void savesAnInitiatedPaymentAndReturnsItAtTheMicrosecondPrecisionTheDatabaseStores() {
        var clock = Clock.fixed(Instant.parse("2026-10-20T09:30:00.123456789Z"), ZoneOffset.UTC);
        var service = new InitiatePaymentService(payments, clock);

        var view = service.initiate(new InitiatePaymentCommand(DEBTOR, CREDITOR, Money.of(2_500, "EUR")));

        var saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        var micros = Instant.parse("2026-10-20T09:30:00.123456Z");
        assertThat(saved.getValue().id()).isEqualTo(view.id());
        assertThat(saved.getValue().initiatedAt()).isEqualTo(micros);
        assertThat(view.status()).isEqualTo(PaymentStatus.INITIATED);
        assertThat(view.debtor()).isEqualTo(DEBTOR);
        assertThat(view.creditor()).isEqualTo(CREDITOR);
        assertThat(view.amount()).isEqualTo(Money.of(2_500, "EUR"));
        assertThat(view.initiatedAt()).isEqualTo(micros);
        assertThat(view.reason()).isEmpty();
    }

    @Test
    void invalidPaymentIsRejectedBeforeAnythingIsSaved() {
        var service = new InitiatePaymentService(payments, Clock.systemUTC());

        assertThatThrownBy(() -> service.initiate(new InitiatePaymentCommand(DEBTOR, DEBTOR, Money.of(2_500, "EUR"))))
                .isInstanceOf(InvalidPaymentException.class);
        verifyNoInteractions(payments);
    }
}
