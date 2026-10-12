package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.AccountBalanceView;
import com.ledgerflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.PostingRepository;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetAccountBalanceService implements GetAccountBalanceUseCase {

    private final AccountRepository accounts;
    private final PostingRepository postings;

    GetAccountBalanceService(AccountRepository accounts, PostingRepository postings) {
        this.accounts = accounts;
        this.postings = postings;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBalanceView balanceOf(AccountId id) {
        var account = accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        // Storage sums the signed postings; the domain decides which side the balance is reported from.
        var balance = account.balanceFromNet(postings.signedNetOf(account));
        return new AccountBalanceView(account.id(), account.normalSide(), balance);
    }
}
