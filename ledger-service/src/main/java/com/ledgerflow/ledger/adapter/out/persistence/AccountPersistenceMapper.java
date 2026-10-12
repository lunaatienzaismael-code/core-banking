package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;

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

    static Account toDomain(AccountEntity entity) {
        return new Account(
                new AccountId(entity.getId()),
                Money.currencyOf(entity.getCurrency()),
                AccountType.valueOf(entity.getType()),
                OverdraftPolicy.valueOf(entity.getOverdraftPolicy()));
    }
}
