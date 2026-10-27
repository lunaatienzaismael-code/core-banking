package com.ledgerflow.payment.adapter.out.messaging;

import com.ledgerflow.messaging.PaymentInitiated;
import com.ledgerflow.messaging.outbox.OutboxWriter;
import com.ledgerflow.payment.application.port.out.PaymentEventPublisher;
import com.ledgerflow.payment.domain.Payment;
import org.springframework.stereotype.Component;

/** Writes payment events to the outbox in the use case's transaction; the relay publishes them (ADR 0013). */
@Component
class PaymentEventOutboxAdapter implements PaymentEventPublisher {

    static final String PAYMENTS_EVENTS = "payments.events";

    private final OutboxWriter outbox;

    PaymentEventOutboxAdapter(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    @Override
    public void paymentInitiated(Payment payment) {
        var paymentId = payment.id().value().toString();
        var event = PaymentInitiated.newBuilder()
                .setPaymentId(paymentId)
                .setDebtorAccountId(payment.debtor().value().toString())
                .setCreditorAccountId(payment.creditor().value().toString())
                .setAmountMinorUnits(payment.amount().amount())
                .setCurrency(payment.amount().currency().getCurrencyCode())
                .setInitiatedAt(payment.initiatedAt())
                .build();
        // Key = paymentId: every message of one payment lands on one partition, in order (ADR 0013).
        outbox.write(PAYMENTS_EVENTS, paymentId, event);
    }
}
