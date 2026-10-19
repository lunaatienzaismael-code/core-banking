package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.GetTransactionUseCase;
import com.ledgerflow.ledger.application.port.in.TransactionView;
import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.TransactionId;
import com.ledgerflow.ledger.domain.TransactionNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetTransactionService implements GetTransactionUseCase {

    private final TransactionRepository transactions;

    GetTransactionService(TransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionView transactionOf(TransactionId id) {
        return transactions
                .findById(id)
                .map(TransactionView::of)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
