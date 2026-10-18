package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import java.util.List;
import java.util.Objects;

/** The lines of a transaction to record; each amount is signed (debit positive, credit negative). */
public record RecordTransactionCommand(List<Line> lines) {

    public RecordTransactionCommand {
        lines = List.copyOf(lines);
    }

    public record Line(AccountId accountId, Money amount) {

        public Line {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
