package com.ledgerflow.ledger.domain;

import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId random() {
        return new AccountId(UUID.randomUUID());
    }

    /**
     * The id of the payments-in-transit account for a currency (ADR 0012): derived from the currency, so every
     * instance names the same account without looking it up.
     */
    public static AccountId paymentsInTransit(Currency currency) {
        var name = "ledgerflow:payments-in-transit:" + currency.getCurrencyCode();
        return new AccountId(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
