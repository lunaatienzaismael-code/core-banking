package com.ledgerflow.ledger.domain;

/** A posting would take a {@code NOT_ALLOWED} account below zero on its normal side (spec AC2.3, ADR 0009). */
public final class InsufficientFundsException extends DomainException {
    public InsufficientFundsException(AccountId account, Money amount) {
        super("Account " + account + " has insufficient funds for a posting of " + amount);
    }
}
