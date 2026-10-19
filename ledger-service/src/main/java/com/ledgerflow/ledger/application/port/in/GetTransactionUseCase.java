package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.TransactionId;

public interface GetTransactionUseCase {

    /**
     * The recorded transaction, its lines ordered by posting id.
     *
     * @throws com.ledgerflow.ledger.domain.TransactionNotFoundException if no transaction has this id
     */
    TransactionView transactionOf(TransactionId id);
}
