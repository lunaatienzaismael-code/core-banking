package com.ledgerflow.payment.domain;

/** The payment's own data breaks a rule: a non-positive amount, or the same account on both sides. */
public final class InvalidPaymentException extends DomainException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
