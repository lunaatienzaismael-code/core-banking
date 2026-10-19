package com.ledgerflow.ledger.adapter.in.web;

import com.ledgerflow.ledger.adapter.in.web.api.TransactionsApi;
import com.ledgerflow.ledger.adapter.in.web.model.RecordTransactionRequestDto;
import com.ledgerflow.ledger.adapter.in.web.model.TransactionDto;
import com.ledgerflow.ledger.application.port.in.GetTransactionUseCase;
import com.ledgerflow.ledger.application.port.in.RecordTransactionUseCase;
import com.ledgerflow.ledger.domain.TransactionId;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Implements the generated contract interface (ADR 0008); routes, payloads and validation come from the contract. */
@RestController
class TransactionController implements TransactionsApi {

    private final RecordTransactionUseCase recordTransaction;
    private final GetTransactionUseCase getTransaction;

    TransactionController(RecordTransactionUseCase recordTransaction, GetTransactionUseCase getTransaction) {
        this.recordTransaction = recordTransaction;
        this.getTransaction = getTransaction;
    }

    @Override
    public ResponseEntity<TransactionDto> recordTransaction(RecordTransactionRequestDto request) {
        var transaction = recordTransaction.record(TransactionWebMapper.toCommand(request));
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(transaction.id().value())
                .toUri();
        return ResponseEntity.created(location).body(TransactionWebMapper.toDto(transaction));
    }

    @Override
    public ResponseEntity<TransactionDto> getTransaction(UUID transactionId) {
        var transaction = getTransaction.transactionOf(new TransactionId(transactionId));
        return ResponseEntity.ok(TransactionWebMapper.toDto(transaction));
    }
}
