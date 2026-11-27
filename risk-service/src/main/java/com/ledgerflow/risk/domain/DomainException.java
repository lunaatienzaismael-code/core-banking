package com.ledgerflow.risk.domain;

/** Base type for violations of risk domain rules. Adapters translate subtypes into their own error model. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
