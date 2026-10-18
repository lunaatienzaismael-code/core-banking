package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Transaction;

public interface TransactionRepository {

    /** Inserts the transaction and its posting lines; postings are immutable once written. */
    void save(Transaction transaction);
}
