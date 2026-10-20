package com.ledgerflow.payment.domain;

/** The given code is not an ISO-4217 currency code. */
public final class InvalidCurrencyException extends DomainException {

    public InvalidCurrencyException(String code) {
        super("Not an ISO-4217 currency code: '" + code + "'");
    }
}
