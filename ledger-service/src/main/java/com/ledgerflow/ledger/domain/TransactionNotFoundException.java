package com.ledgerflow.ledger.domain;

/** The referenced transaction does not exist. */
public final class TransactionNotFoundException extends DomainException {

    public TransactionNotFoundException(TransactionId id) {
        super("Transaction " + id.value() + " does not exist");
    }
}
