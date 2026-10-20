package com.ledgerflow.payment.domain;

/** The referenced payment does not exist. */
public final class PaymentNotFoundException extends DomainException {

    public PaymentNotFoundException(PaymentId id) {
        super("Payment " + id.value() + " does not exist");
    }
}
