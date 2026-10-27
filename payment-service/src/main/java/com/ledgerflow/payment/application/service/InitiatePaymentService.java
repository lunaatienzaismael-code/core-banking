package com.ledgerflow.payment.application.service;

import com.ledgerflow.payment.application.port.in.InitiatePaymentCommand;
import com.ledgerflow.payment.application.port.in.InitiatePaymentUseCase;
import com.ledgerflow.payment.application.port.in.PaymentView;
import com.ledgerflow.payment.application.port.out.PaymentEventPublisher;
import com.ledgerflow.payment.application.port.out.PaymentRepository;
import com.ledgerflow.payment.domain.IdempotencyKeyReusedException;
import com.ledgerflow.payment.domain.Payment;
import com.ledgerflow.payment.domain.RequestFingerprint;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InitiatePaymentService implements InitiatePaymentUseCase {

    private final PaymentRepository payments;
    private final PaymentEventPublisher events;
    private final Clock clock;

    InitiatePaymentService(PaymentRepository payments, PaymentEventPublisher events, Clock clock) {
        this.payments = payments;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentView initiate(InitiatePaymentCommand command) {
        // Microseconds, the precision PostgreSQL stores, so the returned time equals the one read back later.
        var initiatedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var payment = Payment.initiate(command.debtor(), command.creditor(), command.amount(), initiatedAt);
        var fingerprint = RequestFingerprint.of(command.debtor(), command.creditor(), command.amount());
        var stored = payments.insertIfAbsent(payment, command.key(), fingerprint);
        if (!stored.fingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(command.key());
        }
        // Only the request that created the payment announces it; a replay publishes nothing (ADR 0013).
        if (stored.payment().id().equals(payment.id())) {
            events.paymentInitiated(payment);
        }
        // A replay returns the payment that holds the key, never the one built for this request.
        return PaymentView.of(stored.payment());
    }
}
