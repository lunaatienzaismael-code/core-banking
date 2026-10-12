package com.ledgerflow.ledger.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostingLineTest {

    @Test
    void positiveAmountIsADebit() {
        assertThat(line(Money.of(500, "EUR")).side()).isEqualTo(Side.DEBIT);
    }

    @Test
    void negativeAmountIsACredit() {
        assertThat(line(Money.of(-500, "EUR")).side()).isEqualTo(Side.CREDIT);
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> line(Money.of(0, "EUR")))
                .isInstanceOf(InvalidPostingException.class)
                .hasMessageContaining("zero");
    }

    @Test
    void requiresTransactionAndAccount() {
        var amount = Money.of(1, "EUR");

        assertThatThrownBy(() -> new PostingLine(PostingId.random(), null, AccountId.random(), amount))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PostingLine(PostingId.random(), TransactionId.random(), null, amount))
                .isInstanceOf(NullPointerException.class);
    }

    private static PostingLine line(Money amount) {
        return new PostingLine(PostingId.random(), TransactionId.random(), AccountId.random(), amount);
    }
}
