package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.Side;
import com.ledgerflow.ledger.domain.TransactionId;
import java.time.Instant;
import java.util.Objects;

/** One posting line of an account: unsigned amount plus its direction, and the transaction that owns it. */
public record MovementView(
        PostingId postingId, TransactionId transactionId, Money amount, Side direction, Instant occurredAt) {

    public MovementView {
        Objects.requireNonNull(postingId, "postingId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
