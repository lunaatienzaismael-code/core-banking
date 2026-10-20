package com.ledgerflow.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ledgerflow.payment.application.port.in.PaymentView;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.Money;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.PaymentId;
import com.ledgerflow.payment.domain.PaymentNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetPaymentServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final GetPaymentService service = new GetPaymentService(payments);

    @Test
    void storedPaymentIsReturnedAsAView() {
        var payment = Payment.initiate(
                new AccountId(UUID.randomUUID()),
                new AccountId(UUID.randomUUID()),
                Money.of(2_500, "EUR"),
                Instant.parse("2026-01-15T09:30:00Z"));
        payment.reject("risk refused");
        when(payments.findById(payment.id())).thenReturn(Optional.of(payment));

        assertThat(service.paymentOf(payment.id())).isEqualTo(PaymentView.of(payment));
        assertThat(service.paymentOf(payment.id()).reason()).contains("risk refused");
    }

    @Test
    void unknownPaymentIsReportedAsNotFound() {
        var id = PaymentId.random();
        when(payments.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.paymentOf(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment " + id + " does not exist");
    }
}
