package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.PostingRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.Movement;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.PostingLine;
import com.ledgerflow.ledger.domain.TransactionId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads postings with plain SQL: a history is a query over rows, not entities. The schema guarantees every posting is in
 * its account's currency (composite foreign key).
 */
@Component
class PostingPersistenceAdapter implements PostingRepository {

    private static final String MOVEMENTS_SQL = """
            select p.id, p.transaction_id, p.account_id, p.amount, p.currency, t.occurred_at
              from postings p
              join transactions t on t.id = p.transaction_id
             where p.account_id = ?
             order by t.occurred_at, p.id""";

    private final JdbcClient jdbc;

    PostingPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Movement> movementsOf(Account account) {
        return jdbc.sql(MOVEMENTS_SQL)
                .param(account.id().value())
                .query((row, rowNum) -> toMovement(row))
                .list();
    }

    private static Movement toMovement(ResultSet row) throws SQLException {
        var line = new PostingLine(
                new PostingId(row.getObject("id", UUID.class)),
                new TransactionId(row.getObject("transaction_id", UUID.class)),
                new AccountId(row.getObject("account_id", UUID.class)),
                Money.of(row.getLong("amount"), row.getString("currency")));
        return new Movement(
                line, row.getObject("occurred_at", OffsetDateTime.class).toInstant());
    }
}
