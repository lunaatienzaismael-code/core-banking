package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class AccountPersistenceAdapter implements AccountRepository {

    // Plain SQL: a JPA save of an entity with an assigned id cannot say "do nothing if it exists" atomically.
    private static final String INSERT_IF_ABSENT_SQL = """
            insert into accounts (id, currency, type, overdraft_policy, version)
            values (?, ?, ?, ?, 0)
            on conflict (id) do nothing""";

    private final AccountJpaRepository jpa;
    private final JdbcClient jdbc;

    AccountPersistenceAdapter(AccountJpaRepository jpa, JdbcClient jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    @Override
    public void save(Account account) {
        jpa.save(AccountPersistenceMapper.toEntity(account));
    }

    @Override
    public void saveIfAbsent(Account account) {
        jdbc.sql(INSERT_IF_ABSENT_SQL)
                .params(
                        account.id().value(),
                        account.currency().getCurrencyCode(),
                        account.type().name(),
                        account.overdraftPolicy().name())
                .update();
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return jpa.findById(id.value()).map(AccountPersistenceMapper::toDomain);
    }
}
