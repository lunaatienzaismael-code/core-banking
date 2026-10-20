package com.ledgerflow.payment.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount of one currency: integer minor units (e.g. cents) plus an ISO-4217 currency (ADR 0004). The payment
 * service's own type (ADR 0010), with only what payments use.
 */
public record Money(long amount, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(amount, currencyOf(currencyCode));
    }

    /** Resolves an ISO-4217 code, strictly (upper case, three letters, known to the JDK); a null code is a bug. */
    public static Currency currencyOf(String code) {
        Objects.requireNonNull(code, "code");
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidCurrencyException(code);
        }
    }

    public boolean isPositive() {
        return amount > 0;
    }

    @Override
    public String toString() {
        return amount + " " + currency.getCurrencyCode();
    }
}
