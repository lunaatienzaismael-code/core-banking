package com.ledgerflow.ledger.application.port.in;

/** UC2: record a balanced double-entry transaction between accounts. */
public interface RecordTransactionUseCase {

    TransactionView record(RecordTransactionCommand command);
}
