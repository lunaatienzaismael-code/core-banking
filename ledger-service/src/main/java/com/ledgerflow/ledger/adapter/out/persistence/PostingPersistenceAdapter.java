package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.PostingRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.Money;
import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads postings with plain SQL: the balance is an aggregate over rows, not an entity. The schema guarantees every
 * posting is in its account's currency (composite foreign key), so the sum is in that currency.
 */
@Component
class PostingPersistenceAdapter implements PostingRepository {

    private static final String SIGNED_NET_SQL = "select coalesce(sum(amount), 0) from postings where account_id = ?";

    private final JdbcClient jdbc;

    PostingPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Money signedNetOf(Account account) {
        // PostgreSQL widens sum(bigint) to numeric; longValueExact fails loudly instead of wrapping on overflow.
        var net = jdbc.sql(SIGNED_NET_SQL)
                .param(account.id().value())
                .query(BigDecimal.class)
                .single();
        return new Money(net.longValueExact(), account.currency());
    }
}
