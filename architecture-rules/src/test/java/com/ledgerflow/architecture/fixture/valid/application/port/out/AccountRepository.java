package com.ledgerflow.architecture.fixture.valid.application.port.out;

import com.ledgerflow.architecture.fixture.valid.domain.Money;

public interface AccountRepository {
    void save(Money opening);
}
