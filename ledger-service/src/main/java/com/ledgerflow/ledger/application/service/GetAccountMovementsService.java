package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.GetAccountMovementsUseCase;
import com.ledgerflow.ledger.application.port.in.MovementView;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.PostingRepository;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import com.ledgerflow.ledger.domain.Movement;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetAccountMovementsService implements GetAccountMovementsUseCase {

    private final AccountRepository accounts;
    private final PostingRepository postings;

    GetAccountMovementsService(AccountRepository accounts, PostingRepository postings) {
        this.accounts = accounts;
        this.postings = postings;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovementView> movementsOf(AccountId id) {
        var account = accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        return postings.movementsOf(account).stream()
                .map(GetAccountMovementsService::toView)
                .toList();
    }

    private static MovementView toView(Movement movement) {
        var line = movement.line();
        return new MovementView(line.id(), line.transactionId(), line.magnitude(), line.side(), movement.occurredAt());
    }
}
