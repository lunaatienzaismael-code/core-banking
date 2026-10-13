package com.ledgerflow.architecture.fixture.valid.domain;

public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
}
