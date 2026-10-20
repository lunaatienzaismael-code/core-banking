package com.ledgerflow.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void createsAmountInMinorUnitsWithIsoCurrency() {
        var money = Money.of(12_345, "EUR");

        assertThat(money.amount()).isEqualTo(12_345);
        assertThat(money.currency()).isEqualTo(Currency.getInstance("EUR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "EU", "EURO", "eur", "ABC"})
    void rejectsCodesThatAreNotIso4217(String code) {
        assertThatThrownBy(() -> Money.of(100, code))
                .isInstanceOf(InvalidCurrencyException.class)
                .hasMessage("Not an ISO-4217 currency code: '" + code + "'");
    }

    @Test
    void missingCurrencyIsAProgrammingError() {
        assertThatThrownBy(() -> Money.of(100, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(100, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void isPositiveFromOneMinorUnit() {
        assertThat(Money.of(1, "EUR").isPositive()).isTrue();
        assertThat(Money.of(0, "EUR").isPositive()).isFalse();
        assertThat(Money.of(-1, "EUR").isPositive()).isFalse();
    }
}
