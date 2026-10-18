package com.ledgerflow.ledger.domain;

/** A transaction's signed amounts do not sum to zero, so it would create or destroy money (spec AC2.1). */
public final class UnbalancedTransactionException extends DomainException {
    public UnbalancedTransactionException(TransactionId id, Money imbalance) {
        super("Transaction " + id + " is unbalanced: its lines sum to " + imbalance + " instead of zero");
    }
}
