package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads the posting counters with plain SQL. They are not part of {@link AccountEntity}, so saving an account through
 * JPA can never overwrite them (ADR 0009).
 */
@Component
class BalancePersistenceAdapter implements BalanceRepository {

    private static final String COUNTERS_SQL = "select debits_posted, credits_posted from accounts where id = ?";

    private final JdbcClient jdbc;

    BalancePersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Money signedNetOf(Account account) {
        var net = jdbc.sql(COUNTERS_SQL)
                .param(account.id().value())
                .query((row, rowNum) -> Math.subtractExact(row.getLong("debits_posted"), row.getLong("credits_posted")))
                .single();
        return new Money(net, account.currency());
    }
}
