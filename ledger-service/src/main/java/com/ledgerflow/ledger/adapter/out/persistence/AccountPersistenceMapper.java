package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.domain.Account;

/** Explicit mapping between the domain and the JPA entity (ADR 0002 rule 7). */
final class AccountPersistenceMapper {

    private AccountPersistenceMapper() {}

    static AccountEntity toEntity(Account account) {
        return new AccountEntity(
                account.id().value(),
                account.currency().getCurrencyCode(),
                account.type().name(),
                account.overdraftPolicy().name());
    }
}
