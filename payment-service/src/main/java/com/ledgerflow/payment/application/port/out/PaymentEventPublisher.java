package com.ledgerflow.payment.application.port.out;

import com.ledgerflow.payment.domain.Payment;

/**
 * Announces payment lifecycle events to other services (ADR 0012). Implementations must publish in the caller's
 * transaction, so an event exists exactly when the change it announces commits (AC4.1).
 */
public interface PaymentEventPublisher {

    void paymentInitiated(Payment payment);
}
