package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.AccountBalanceView;
import com.ledgerflow.ledger.application.port.in.GetAccountBalanceUseCase;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class GetAccountBalanceService implements GetAccountBalanceUseCase {

    private final AccountRepository accounts;
    private final BalanceRepository balances;

    GetAccountBalanceService(AccountRepository accounts, BalanceRepository balances) {
        this.accounts = accounts;
        this.balances = balances;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountBalanceView balanceOf(AccountId id) {
        var account = accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        // Storage keeps the signed net in counters; the domain decides which side the balance is reported from.
        var balance = account.balanceFromNet(balances.signedNetOf(account));
        return new AccountBalanceView(account.id(), account.normalSide(), balance);
    }
}
