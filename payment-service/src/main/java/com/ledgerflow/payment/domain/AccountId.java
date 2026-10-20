package com.ledgerflow.payment.domain;

import java.util.Objects;
import java.util.UUID;

/** A ledger account, referenced by its ledger id (ADR 0010); the payment service owns no accounts. */
public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
