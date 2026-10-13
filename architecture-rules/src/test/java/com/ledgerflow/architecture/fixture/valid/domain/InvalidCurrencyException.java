package com.ledgerflow.architecture.fixture.valid.domain;

public final class InvalidCurrencyException extends DomainException {
    public InvalidCurrencyException(String code) {
        super("Unknown currency " + code);
    }
}
