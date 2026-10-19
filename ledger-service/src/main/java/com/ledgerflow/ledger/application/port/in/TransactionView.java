package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.Side;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.domain.TransactionId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** A recorded transaction as seen by use case callers: unsigned line amounts with their direction. */
public record TransactionView(TransactionId id, Instant occurredAt, List<Line> lines) {

    public TransactionView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        lines = List.copyOf(lines);
    }

    /** The transaction's lines in its own order, each as an unsigned amount plus its direction. */
    public static TransactionView of(Transaction transaction) {
        var lines = transaction.lines().stream()
                .map(line -> new Line(line.id(), line.accountId(), line.magnitude(), line.side()))
                .toList();
        return new TransactionView(transaction.id(), transaction.occurredAt(), lines);
    }

    public record Line(PostingId postingId, AccountId accountId, Money amount, Side direction) {

        public Line {
            Objects.requireNonNull(postingId, "postingId");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(direction, "direction");
        }
    }
}
