package com.ledgerflow.ledger.domain;

import java.util.Currency;
import java.util.Objects;

/**
 * An exact amount of one currency: integer minor units (e.g. cents) plus an ISO-4217 currency (ADR 0004).
 *
 * <p>The amount may be negative, so the same type carries signed posting amounts and nets. Arithmetic never mixes
 * currencies and never wraps around on overflow.
 */
public record Money(long amount, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(amount, currencyOf(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
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

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amount), currency);
    }

    public boolean isZero() {
        return amount == 0;
    }

    public boolean isPositive() {
        return amount > 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return amount + " " + currency.getCurrencyCode();
    }
}
