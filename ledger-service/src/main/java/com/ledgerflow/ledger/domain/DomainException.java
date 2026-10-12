package com.ledgerflow.ledger.domain;

/** Base type for violations of ledger domain rules. Adapters translate subtypes into their own error model. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
