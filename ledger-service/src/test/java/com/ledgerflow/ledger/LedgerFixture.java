package com.ledgerflow.ledger;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes ledger rows straight into the schema. Postings have no write path until UC2 (Phase 5), so tests that need
 * them insert balanced transactions here. All data is fictitious.
 */
public final class LedgerFixture {

    private final JdbcTemplate jdbc;

    public LedgerFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts an account and returns its id. */
    public UUID account(String currency, String type) {
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into accounts (id, currency, type, overdraft_policy, version) values (?, ?, ?, 'ALLOWED', 0)",
                id,
                currency,
                type);
        return id;
    }

    /** Inserts a balanced transaction: {@code +amount} on the debited account, {@code -amount} on the credited one. */
    public UUID transfer(UUID debited, UUID credited, long amount, String currency) {
        var transaction = UUID.randomUUID();
        jdbc.update("insert into transactions (id, occurred_at) values (?, ?)", transaction, OffsetDateTime.now());
        posting(transaction, debited, amount, currency);
        posting(transaction, credited, -amount, currency);
        return transaction;
    }

    private void posting(UUID transaction, UUID account, long amount, String currency) {
        jdbc.update(
                "insert into postings (id, transaction_id, account_id, amount, currency) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                transaction,
                account,
                amount,
                currency);
    }
}
