package com.ledgerflow.ledger.domain;

/** A transaction is not well formed: too few lines, two lines on one account, or a line of another transaction. */
public final class InvalidTransactionException extends DomainException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
