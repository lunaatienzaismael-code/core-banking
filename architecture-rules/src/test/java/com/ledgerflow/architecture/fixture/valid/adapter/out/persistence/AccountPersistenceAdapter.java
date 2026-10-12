package com.ledgerflow.architecture.fixture.valid.adapter.out.persistence;

import com.ledgerflow.architecture.fixture.valid.application.port.out.AccountRepository;
import com.ledgerflow.architecture.fixture.valid.domain.Money;

public class AccountPersistenceAdapter implements AccountRepository {
    @Override
    public void save(Money opening) {
        AccountEntity entity = new AccountEntity();
        entity.balanceMinor = opening.amountMinor();
    }
}
