package com.ledgerflow.ledger.domain;

/** A posting line breaks a ledger rule (zero amount, wrong account). */
public final class InvalidPostingException extends DomainException {

    public InvalidPostingException(String message) {
        super(message);
    }
}
