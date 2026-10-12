package com.ledgerflow.ledger.domain;

import java.util.Objects;

/**
 * One line of a ledger transaction against one account. The amount is signed (spec "Sign convention"): debit is
 * positive, credit is negative, and it is never zero.
 */
public record PostingLine(PostingId id, TransactionId transactionId, AccountId accountId, Money amount) {

    public PostingLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(amount, "amount");
        if (amount.isZero()) {
            throw new InvalidPostingException("Posting " + id + " has a zero amount");
        }
    }

    public Side side() {
        return amount.isPositive() ? Side.DEBIT : Side.CREDIT;
    }

    /** The unsigned amount; {@link #side()} carries the direction. */
    public Money magnitude() {
        return amount.isPositive() ? amount : amount.negate();
    }
}
