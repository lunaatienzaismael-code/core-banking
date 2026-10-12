package com.ledgerflow.ledger.domain;

/** The referenced account does not exist. */
public final class AccountNotFoundException extends DomainException {

    public AccountNotFoundException(AccountId id) {
        super("Account " + id.value() + " does not exist");
    }
}
