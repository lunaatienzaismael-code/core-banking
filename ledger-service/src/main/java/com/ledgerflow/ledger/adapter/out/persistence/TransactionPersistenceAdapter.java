package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.Transaction;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Inserts transactions and postings with plain SQL: both are append-only rows, never loaded and changed as entities. */
@Component
class TransactionPersistenceAdapter implements TransactionRepository {

    private static final String INSERT_TRANSACTION_SQL = "insert into transactions (id, occurred_at) values (?, ?)";
    private static final String INSERT_POSTING_SQL =
            "insert into postings (id, transaction_id, account_id, amount, currency) values (?, ?, ?, ?, ?)";

    private final JdbcClient jdbc;

    TransactionPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Transaction transaction) {
        jdbc.sql(INSERT_TRANSACTION_SQL)
                .params(transaction.id().value(), transaction.occurredAt().atOffset(ZoneOffset.UTC))
                .update();
        for (var line : transaction.lines()) {
            jdbc.sql(INSERT_POSTING_SQL)
                    .params(
                            line.id().value(),
                            transaction.id().value(),
                            line.accountId().value(),
                            line.amount().amount(),
                            line.amount().currency().getCurrencyCode())
                    .update();
        }
    }
}
