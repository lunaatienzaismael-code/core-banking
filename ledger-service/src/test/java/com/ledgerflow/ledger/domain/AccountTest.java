package com.ledgerflow.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void openedAccountKeepsItsCurrencyTypeAndPolicy() {
        var account = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);

        assertThat(account.id()).isNotNull();
        assertThat(account.currency()).isEqualTo(EUR);
        assertThat(account.type()).isEqualTo(AccountType.LIABILITY);
        assertThat(account.overdraftPolicy()).isEqualTo(OverdraftPolicy.NOT_ALLOWED);
        assertThat(account.normalSide()).isEqualTo(Side.CREDIT);
    }

    @Test
    void paymentsInTransitIsACreditNormalLiabilityThatCannotGoNegative() {
        var transit = Account.paymentsInTransit(EUR);

        assertThat(transit.id()).isEqualTo(AccountId.paymentsInTransit(EUR));
        assertThat(transit.currency()).isEqualTo(EUR);
        assertThat(transit.type()).isEqualTo(AccountType.LIABILITY);
        assertThat(transit.normalSide()).isEqualTo(Side.CREDIT);
        assertThat(transit.overdraftPolicy()).isEqualTo(OverdraftPolicy.NOT_ALLOWED);
    }

    @Test
    void paymentsInTransitHasOneStableIdPerCurrency() {
        var usd = Currency.getInstance("USD");

        assertThat(AccountId.paymentsInTransit(EUR)).isEqualTo(AccountId.paymentsInTransit(EUR));
        assertThat(AccountId.paymentsInTransit(EUR)).isNotEqualTo(AccountId.paymentsInTransit(usd));
        // Pinned: the id names an account already stored, so it must never change between releases.
        assertThat(AccountId.paymentsInTransit(EUR).value())
                .isEqualTo(
                        UUID.nameUUIDFromBytes("ledgerflow:payments-in-transit:EUR".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void eachOpenedAccountGetsAUniqueId() {
        var first = Account.open(EUR, AccountType.ASSET, OverdraftPolicy.ALLOWED);
        var second = Account.open(EUR, AccountType.ASSET, OverdraftPolicy.ALLOWED);

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void cannotBeBuiltWithoutCurrencyTypeOrPolicy() {
        var id = AccountId.random();

        assertThatThrownBy(() -> new Account(id, null, AccountType.ASSET, OverdraftPolicy.ALLOWED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Account(id, EUR, null, OverdraftPolicy.ALLOWED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Account(id, EUR, AccountType.ASSET, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void debitNormalAccountReportsTheNetOfItsPostings() {
        var asset = Account.open(EUR, AccountType.ASSET, OverdraftPolicy.ALLOWED);

        assertThat(asset.balanceFromNet(Money.of(700, "EUR"))).isEqualTo(Money.of(700, "EUR"));
    }

    @Test
    void creditNormalAccountReportsTheNegatedNetOfItsPostings() {
        var deposit = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);

        // Customer deposited 700: the liability account was credited, so its signed net is -700.
        assertThat(deposit.balanceFromNet(Money.of(-700, "EUR"))).isEqualTo(Money.of(700, "EUR"));
    }

    @Test
    void accountWithoutPostingsHasZeroBalance() {
        var deposit = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);

        assertThat(deposit.balanceOf(List.of())).isEqualTo(Money.zero(EUR));
        assertThat(deposit.balanceFromNet(Money.zero(EUR))).isEqualTo(Money.zero(EUR));
    }

    @Test
    void rejectsANetInAnotherCurrency() {
        var asset = Account.open(EUR, AccountType.ASSET, OverdraftPolicy.ALLOWED);

        assertThatThrownBy(() -> asset.balanceFromNet(Money.of(100, "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void creditNormalBalanceFailsLoudlyWhenTheNetCannotBeNegated() {
        var deposit = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
        var minimum = new Money(Long.MIN_VALUE, EUR);

        assertThatThrownBy(() -> deposit.balanceFromNet(minimum)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void balanceOfPostingLinesSumsDebitsMinusCreditsFromTheNormalSide() {
        var deposit = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
        var cash = Account.open(EUR, AccountType.ASSET, OverdraftPolicy.ALLOWED);
        var lines = List.of(line(deposit, -1_000), line(deposit, 250), line(deposit, -50));
        var cashLines = List.of(line(cash, 1_000), line(cash, -250), line(cash, 50));

        assertThat(deposit.balanceOf(lines)).isEqualTo(Money.of(800, "EUR"));
        assertThat(cash.balanceOf(cashLines)).isEqualTo(Money.of(800, "EUR"));
    }

    @Test
    void balanceOfRejectsPostingLinesOfAnotherAccount() {
        var deposit = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
        var other = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);

        assertThatThrownBy(() -> deposit.balanceOf(List.of(line(other, -100))))
                .isInstanceOf(InvalidPostingException.class);
    }

    @Test
    void balanceOfRejectsPostingLinesInAnotherCurrency() {
        var deposit = Account.open(EUR, AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);
        var dollars = new PostingLine(PostingId.random(), TransactionId.random(), deposit.id(), Money.of(-100, "USD"));

        assertThatThrownBy(() -> deposit.balanceOf(List.of(dollars))).isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void balanceOfFailsLoudlyWhenTheSumOverflows() {
        var cash = Account.open(EUR, AccountType.ASSET, OverdraftPolicy.ALLOWED);
        var lines = List.of(line(cash, Long.MAX_VALUE), line(cash, 1));

        assertThatThrownBy(() -> cash.balanceOf(lines)).isInstanceOf(ArithmeticException.class);
    }

    private static PostingLine line(Account account, long signedMinorUnits) {
        return new PostingLine(
                PostingId.random(), TransactionId.random(), account.id(), new Money(signedMinorUnits, EUR));
    }
}
