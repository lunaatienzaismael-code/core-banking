package com.ledgerflow.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void createsAmountInMinorUnitsWithIsoCurrency() {
        var money = Money.of(12_345, "EUR");

        assertThat(money.amount()).isEqualTo(12_345);
        assertThat(money.currency()).isEqualTo(EUR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "EU", "EURO", "eur", "ABC"})
    void rejectsCodesThatAreNotIso4217(String code) {
        assertThatThrownBy(() -> Money.of(100, code))
                .isInstanceOf(InvalidCurrencyException.class)
                .hasMessageContaining(code);
    }

    @Test
    void missingCurrencyCodeIsAProgrammingError() {
        assertThatThrownBy(() -> Money.of(100, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsMissingCurrency() {
        assertThatThrownBy(() -> new Money(100, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void addsAndSubtractsAmountsOfTheSameCurrency() {
        var hundred = Money.of(100, "EUR");
        var thirty = Money.of(30, "EUR");

        assertThat(hundred.plus(thirty)).isEqualTo(Money.of(130, "EUR"));
        assertThat(thirty.minus(hundred)).isEqualTo(Money.of(-70, "EUR"));
    }

    @Test
    void rejectsAdditionAcrossCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "EUR").plus(Money.of(100, "USD")))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");
    }

    @Test
    void rejectsSubtractionAcrossCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "EUR").minus(Money.of(100, "GBP")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void additionOverflowFailsInsteadOfWrappingAround() {
        var max = new Money(Long.MAX_VALUE, EUR);

        assertThatThrownBy(() -> max.plus(Money.of(1, "EUR"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void subtractionOverflowFailsInsteadOfWrappingAround() {
        var min = new Money(Long.MIN_VALUE, EUR);

        assertThatThrownBy(() -> min.minus(Money.of(1, "EUR"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void negatingTheMinimumAmountFailsInsteadOfWrappingAround() {
        var min = new Money(Long.MIN_VALUE, EUR);

        assertThatThrownBy(min::negate).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void negatesAmountKeepingCurrency() {
        assertThat(Money.of(250, "EUR").negate()).isEqualTo(Money.of(-250, "EUR"));
    }

    @Test
    void zeroHasNoAmount() {
        assertThat(Money.zero(EUR).isZero()).isTrue();
        assertThat(Money.of(1, "EUR").isZero()).isFalse();
    }

    @Test
    void zeroIsNotPositive() {
        assertThat(Money.zero(EUR).isPositive()).isFalse();
        assertThat(Money.of(1, "EUR").isPositive()).isTrue();
        assertThat(Money.of(-1, "EUR").isPositive()).isFalse();
    }
}
