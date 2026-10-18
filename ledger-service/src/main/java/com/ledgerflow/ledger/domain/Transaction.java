package com.ledgerflow.ledger.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A double-entry transaction: at least two posting lines on distinct accounts, all in one currency, whose signed
 * amounts sum to zero (spec AC2.1, AC2.2, AC2.4). The canonical constructor is the only way to build one and rejects
 * anything else, so an instance is always a valid transaction.
 */
public record Transaction(TransactionId id, Instant occurredAt, List<PostingLine> lines) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        lines = List.copyOf(lines);
        if (lines.size() < 2) {
            throw new InvalidTransactionException(
                    "Transaction " + id + " needs at least two lines, got " + lines.size());
        }
        var accounts = new HashSet<AccountId>();
        var sum = Money.zero(lines.getFirst().amount().currency());
        for (var line : lines) {
            if (!id.equals(line.transactionId())) {
                throw new InvalidTransactionException(
                        "Posting " + line.id() + " belongs to transaction " + line.transactionId() + ", not " + id);
            }
            if (!accounts.add(line.accountId())) {
                throw new InvalidTransactionException(
                        "Transaction " + id + " has more than one line on account " + line.accountId());
            }
            sum = sum.plus(line.amount());
        }
        if (!sum.isZero()) {
            throw new UnbalancedTransactionException(id, sum);
        }
    }

    /** Signed amount per account, in line order: what each account's debit/credit counters must absorb. */
    public Map<AccountId, Money> amountsByAccount() {
        var amounts = new LinkedHashMap<AccountId, Money>();
        for (var line : lines) {
            amounts.put(line.accountId(), line.amount());
        }
        return Collections.unmodifiableMap(amounts);
    }
}
