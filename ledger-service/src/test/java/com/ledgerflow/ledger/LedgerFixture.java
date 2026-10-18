package com.ledgerflow.ledger;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Writes ledger rows straight into the schema. Postings have no write path until UC2 (Phase 5), so tests that need
 * them insert balanced transactions here, together with the account counters they move (ADR 0009). All data is
 * fictitious.
 */
public final class LedgerFixture {

    private final JdbcTemplate jdbc;

    public LedgerFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts an account that may go negative ({@code ALLOWED}) and returns its id. */
    public UUID account(String currency, String type) {
        return account(currency, type, "ALLOWED");
    }

    /** Inserts an account with the given overdraft policy ({@code ALLOWED} or {@code NOT_ALLOWED}) and returns its id. */
    public UUID account(String currency, String type, String overdraftPolicy) {
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into accounts (id, currency, type, overdraft_policy, version) values (?, ?, ?, ?, 0)",
                id,
                currency,
                type,
                overdraftPolicy);
        return id;
    }

    /** Inserts a balanced transaction: {@code +amount} on the debited account, {@code -amount} on the credited one. */
    public UUID transfer(UUID debited, UUID credited, long amount, String currency) {
        return transfer(debited, credited, amount, currency, OffsetDateTime.now());
    }

    /** As {@link #transfer(UUID, UUID, long, String)}, with the transaction occurring at {@code occurredAt}. */
    public UUID transfer(UUID debited, UUID credited, long amount, String currency, OffsetDateTime occurredAt) {
        var transaction = UUID.randomUUID();
        jdbc.update("insert into transactions (id, occurred_at) values (?, ?)", transaction, occurredAt);
        posting(transaction, debited, amount, currency);
        posting(transaction, credited, -amount, currency);
        jdbc.update("update accounts set debits_posted = debits_posted + ? where id = ?", amount, debited);
        jdbc.update("update accounts set credits_posted = credits_posted + ? where id = ?", amount, credited);
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
