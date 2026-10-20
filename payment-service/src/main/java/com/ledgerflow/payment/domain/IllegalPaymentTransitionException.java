package com.ledgerflow.payment.domain;

/** The payment's state machine does not allow this move from its current status (ADR 0011). */
public final class IllegalPaymentTransitionException extends DomainException {

    public IllegalPaymentTransitionException(PaymentId id, PaymentStatus from, PaymentStatus to) {
        super("Payment " + id + " cannot move from " + from + " to " + to);
    }
}
