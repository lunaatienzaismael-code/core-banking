package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.PostingLine;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.domain.TransactionId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Inserts and reads transactions and postings with plain SQL: both are append-only rows, never loaded and changed as
 * entities.
 */
@Component
class TransactionPersistenceAdapter implements TransactionRepository {

    private static final String INSERT_TRANSACTION_SQL = "insert into transactions (id, occurred_at) values (?, ?)";
    private static final String INSERT_POSTING_SQL =
            "insert into postings (id, transaction_id, account_id, amount, currency) values (?, ?, ?, ?, ?)";
    private static final String FIND_BY_ID_SQL = """
            select t.occurred_at, p.id, p.account_id, p.amount, p.currency
              from transactions t
              join postings p on p.transaction_id = t.id
             where t.id = ?
             order by p.id""";

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

    @Override
    public Optional<Transaction> findById(TransactionId id) {
        var rows = jdbc.sql(FIND_BY_ID_SQL)
                .param(id.value())
                .query((row, rowNum) -> new Row(
                        row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        new PostingLine(
                                new PostingId(row.getObject("id", UUID.class)),
                                id,
                                new AccountId(row.getObject("account_id", UUID.class)),
                                Money.of(row.getLong("amount"), row.getString("currency")))))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var lines = rows.stream().map(Row::line).toList();
        return Optional.of(new Transaction(id, rows.getFirst().occurredAt(), lines));
    }

    /** One posting joined with its transaction's time. */
    private record Row(Instant occurredAt, PostingLine line) {}
}
