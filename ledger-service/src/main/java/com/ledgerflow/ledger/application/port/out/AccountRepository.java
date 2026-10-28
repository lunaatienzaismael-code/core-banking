package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import java.util.Optional;

public interface AccountRepository {

    void save(Account account);

    /** Inserts the account unless one with its id exists; safe when several transactions try at once. */
    void saveIfAbsent(Account account);

    Optional<Account> findById(AccountId id);
}
