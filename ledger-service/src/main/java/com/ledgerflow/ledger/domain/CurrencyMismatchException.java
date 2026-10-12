package com.ledgerflow.ledger.domain;

import java.util.Currency;

/** An operation combined amounts in different currencies; the ledger never mixes currencies (ADR 0004). */
public final class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Currency mismatch: expected " + expected.getCurrencyCode() + " but got " + actual.getCurrencyCode());
    }
}
