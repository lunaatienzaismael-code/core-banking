package com.ledgerflow.ledger.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.ledger.LedgerFixture;
import com.ledgerflow.ledger.TestcontainersConfiguration;
import com.ledgerflow.ledger.application.port.out.TransactionRepository;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.PostingId;
import com.ledgerflow.ledger.domain.PostingLine;
import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.domain.TransactionId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransactionPersistenceAdapterIT {

    private static final OffsetDateTime JAN_15 = OffsetDateTime.of(2026, 1, 15, 9, 30, 0, 0, ZoneOffset.UTC);

    @Autowired
    private TransactionRepository transactions;

    @Autowired
    private JdbcTemplate jdbc;

    private LedgerFixture ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(jdbc);
    }

    @Test
    void foundTransactionHasItsTimeAndSignedLinesOrderedByPostingIdRegardlessOfInsertOrder() {
        var customer = ledger.account("EUR", "LIABILITY");
        var cash = ledger.account("EUR", "ASSET");
        var fees = ledger.account("EUR", "INCOME");
        // PostgreSQL orders uuids by their unsigned bytes, which is the order of their lowercase text form.
        var ids = Stream.generate(UUID::randomUUID)
                .limit(3)
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        var id = UUID.randomUUID();
        jdbc.update("insert into transactions (id, occurred_at) values (?, ?)", id, JAN_15);
        posting(ids.get(2), id, fees, -50);
        posting(ids.get(0), id, customer, 1_000);
        posting(ids.get(1), id, cash, -950);
        ledger.transfer(cash, customer, 700, "EUR");

        var transaction = new TransactionId(id);

        var found = transactions.findById(transaction);

        assertThat(found)
                .contains(new Transaction(
                        transaction,
                        JAN_15.toInstant(),
                        List.of(
                                line(ids.get(0), transaction, customer, 1_000),
                                line(ids.get(1), transaction, cash, -950),
                                line(ids.get(2), transaction, fees, -50))));
    }

    @Test
    void unknownTransactionIsEmpty() {
        assertThat(transactions.findById(TransactionId.random())).isEmpty();
    }

    /** Inserts one posting and keeps the account's counters equal to its postings (ADR 0009). */
    private void posting(UUID posting, UUID transaction, UUID account, long amount) {
        jdbc.update(
                "insert into postings (id, transaction_id, account_id, amount, currency) values (?, ?, ?, ?, 'EUR')",
                posting,
                transaction,
                account,
                amount);
        jdbc.update(
                "update accounts set debits_posted = debits_posted + ?, credits_posted = credits_posted + ? where id = ?",
                Math.max(amount, 0),
                Math.max(-amount, 0),
                account);
    }

    private static PostingLine line(UUID posting, TransactionId transaction, UUID account, long amount) {
        return new PostingLine(new PostingId(posting), transaction, new AccountId(account), Money.of(amount, "EUR"));
    }
}
