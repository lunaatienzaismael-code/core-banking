package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.domain.TransactionId;
import java.util.Optional;

public interface TransactionRepository {

    /** Inserts the transaction and its posting lines; postings are immutable once written. */
    void save(Transaction transaction);

    /** The transaction with its posting lines ordered by posting id; empty when no transaction has this id. */
    Optional<Transaction> findById(TransactionId id);
}
