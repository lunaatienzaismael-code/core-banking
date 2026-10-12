package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;

public interface AccountRepository {

    void save(Account account);
}
