package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.domain.Account;
import org.springframework.stereotype.Component;

@Component
class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;

    AccountPersistenceAdapter(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(Account account) {
        jpa.save(AccountPersistenceMapper.toEntity(account));
    }
}
