package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import java.util.Optional;

public interface AccountRepository {

    void save(Account account);

    Optional<Account> findById(AccountId id);
}
