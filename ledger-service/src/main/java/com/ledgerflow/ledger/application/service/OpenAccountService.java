package com.ledgerflow.ledger.application.service;

import com.ledgerflow.ledger.application.port.in.AccountView;
import com.ledgerflow.ledger.application.port.in.OpenAccountCommand;
import com.ledgerflow.ledger.application.port.in.OpenAccountUseCase;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OpenAccountService implements OpenAccountUseCase {

    private final AccountRepository accounts;

    OpenAccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public AccountView open(OpenAccountCommand command) {
        var account = Account.open(command.currency(), command.type(), command.overdraftPolicy());
        accounts.save(account);
        // A new account has no postings, so its derived balance is the normal-side view of a zero net.
        var balance = account.balanceFromNet(Money.zero(account.currency()));
        return new AccountView(
                account.id(),
                account.currency(),
                account.type(),
                account.normalSide(),
                account.overdraftPolicy(),
                balance);
    }
}
