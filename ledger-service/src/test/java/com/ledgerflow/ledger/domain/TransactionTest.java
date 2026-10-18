package com.ledgerflow.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-18T09:30:00Z");

    private final TransactionId id = TransactionId.random();
    private final AccountId alice = AccountId.random();
    private final AccountId bob = AccountId.random();
    private final AccountId carol = AccountId.random();

    @Test
    void acceptsABalancedTwoLineTransaction() {
        var debit = line(alice, 2_500, "EUR");
        var credit = line(bob, -2_500, "EUR");

        var transaction = new Transaction(id, OCCURRED_AT, List.of(debit, credit));

        assertThat(transaction.id()).isEqualTo(id);
        assertThat(transaction.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(transaction.lines()).containsExactly(debit, credit);
    }

    @Test
    void acceptsABalancedMultiLineTransaction() {
        var lines = List.of(line(alice, 1_000, "EUR"), line(bob, -700, "EUR"), line(carol, -300, "EUR"));

        var transaction = new Transaction(id, OCCURRED_AT, lines);

        assertThat(transaction.lines()).containsExactlyElementsOf(lines);
    }

    @Test
    void rejectsTransactionWithoutLines() {
        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, List.of()))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("at least two lines, got 0");
    }

    @Test
    void rejectsTransactionWithASingleLine() {
        var lines = List.of(line(alice, 100, "EUR"));

        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, lines))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("at least two lines, got 1");
    }

    @Test
    void rejectsTransactionWhoseLinesSumAboveZero() {
        var lines = List.of(line(alice, 101, "EUR"), line(bob, -100, "EUR"));

        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, lines))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("unbalanced")
                .hasMessageContaining("1 EUR");
    }

    @Test
    void rejectsTransactionWhoseLinesSumBelowZero() {
        var lines = List.of(line(alice, 100, "EUR"), line(bob, -101, "EUR"));

        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, lines))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("-1 EUR");
    }

    @Test
    void rejectsLinesInMoreThanOneCurrency() {
        var lines = List.of(line(alice, 100, "EUR"), line(bob, -100, "USD"));

        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, lines))
                .isInstanceOf(CurrencyMismatchException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("USD");
    }

    @Test
    void rejectsTwoLinesOnTheSameAccount() {
        var lines = List.of(line(alice, 100, "EUR"), line(alice, -100, "EUR"));

        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, lines))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("more than one line on account " + alice);
    }

    @Test
    void rejectsALineOfAnotherTransaction() {
        var foreign = new PostingLine(PostingId.random(), TransactionId.random(), bob, Money.of(-100, "EUR"));
        var lines = List.of(line(alice, 100, "EUR"), foreign);

        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, lines))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("belongs to transaction " + foreign.transactionId());
    }

    @Test
    void overflowWhileSummingLinesFailsInsteadOfWrappingAround() {
        var lines = List.of(
                line(alice, Long.MAX_VALUE, "EUR"),
                line(bob, 1, "EUR"),
                line(carol, -Long.MAX_VALUE, "EUR"),
                line(AccountId.random(), -1, "EUR"));

        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, lines)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void linesCannotBeModifiedThroughTheTransaction() {
        var transaction = new Transaction(id, OCCURRED_AT, List.of(line(alice, 100, "EUR"), line(bob, -100, "EUR")));

        assertThatThrownBy(() -> transaction.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void laterChangesToTheCallersListDoNotAffectTheTransaction() {
        var lines = new ArrayList<>(List.of(line(alice, 100, "EUR"), line(bob, -100, "EUR")));
        var transaction = new Transaction(id, OCCURRED_AT, lines);

        lines.add(line(carol, 5, "EUR"));

        assertThat(transaction.lines()).hasSize(2);
    }

    @Test
    void exposesTheSignedAmountEachAccountMustAbsorbInLineOrder() {
        var transaction = new Transaction(
                id, OCCURRED_AT, List.of(line(carol, 1_000, "EUR"), line(alice, -700, "EUR"), line(bob, -300, "EUR")));

        var amounts = transaction.amountsByAccount();

        assertThat(amounts)
                .containsExactly(
                        entry(carol, Money.of(1_000, "EUR")),
                        entry(alice, Money.of(-700, "EUR")),
                        entry(bob, Money.of(-300, "EUR")));
        assertThatThrownBy(amounts::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresIdOccurredAtAndLines() {
        var lines = List.of(line(alice, 100, "EUR"), line(bob, -100, "EUR"));

        assertThatThrownBy(() -> new Transaction(null, OCCURRED_AT, lines)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Transaction(id, null, lines)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Transaction(id, OCCURRED_AT, null)).isInstanceOf(NullPointerException.class);
    }

    private PostingLine line(AccountId account, long amount, String currency) {
        return new PostingLine(PostingId.random(), id, account, Money.of(amount, currency));
    }

    private static Map.Entry<AccountId, Money> entry(AccountId account, Money amount) {
        return Map.entry(account, amount);
    }
}
