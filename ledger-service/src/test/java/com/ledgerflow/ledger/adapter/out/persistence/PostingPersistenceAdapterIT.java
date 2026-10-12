package com.ledgerflow.ledger.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.ledger.LedgerFixture;
import com.ledgerflow.ledger.TestcontainersConfiguration;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.PostingRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.Movement;
import com.ledgerflow.ledger.domain.Side;
import com.ledgerflow.ledger.domain.TransactionId;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostingPersistenceAdapterIT {

    @Autowired
    private PostingRepository postings;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    private LedgerFixture ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(jdbc);
    }

    @Test
    void signedNetIsDebitsMinusCreditsOfTheAccountInItsCurrency() {
        var account = ledger.account("EUR", "ASSET");
        var other = ledger.account("EUR", "LIABILITY");
        ledger.transfer(account, other, 10_000, "EUR");
        ledger.transfer(other, account, 3_000, "EUR");

        assertThat(postings.signedNetOf(load(account))).isEqualTo(Money.of(7_000, "EUR"));
        assertThat(postings.signedNetOf(load(other))).isEqualTo(Money.of(-7_000, "EUR"));
    }

    @Test
    void accountWithoutPostingsHasAZeroNet() {
        var account = ledger.account("SEK", "LIABILITY");

        assertThat(postings.signedNetOf(load(account))).isEqualTo(Money.of(0, "SEK"));
    }

    @Test
    void netOutsideTheLongRangeFailsLoudlyInsteadOfWrapping() {
        var account = ledger.account("EUR", "ASSET");
        var other = ledger.account("EUR", "LIABILITY");
        ledger.transfer(account, other, Long.MAX_VALUE, "EUR");
        ledger.transfer(account, other, 1, "EUR");

        assertThatThrownBy(() -> postings.signedNetOf(load(account))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void movementsAreOrderedByTransactionTimeRegardlessOfInsertOrder() {
        var account = ledger.account("EUR", "LIABILITY");
        var cash = ledger.account("EUR", "ASSET");
        var third = ledger.transfer(account, cash, 300, "EUR", at(3));
        var first = ledger.transfer(cash, account, 10_000, "EUR", at(1));
        var second = ledger.transfer(account, cash, 2_500, "EUR", at(2));

        var movements = postings.movementsOf(load(account));

        assertThat(movements)
                .extracting(movement -> movement.line().transactionId())
                .containsExactly(new TransactionId(first), new TransactionId(second), new TransactionId(third));
        assertThat(movements)
                .extracting(Movement::occurredAt)
                .containsExactly(at(1).toInstant(), at(2).toInstant(), at(3).toInstant());
    }

    @Test
    void movementsAtTheSameInstantAreOrderedByPostingId() {
        var account = ledger.account("EUR", "ASSET");
        var other = ledger.account("EUR", "LIABILITY");
        for (int i = 1; i <= 5; i++) {
            ledger.transfer(account, other, i, "EUR", at(1));
        }

        var ids = postings.movementsOf(load(account)).stream()
                .map(movement -> movement.line().id().value().toString())
                .toList();

        // PostgreSQL compares uuids byte by byte, which matches their lowercase text form, not UUID.compareTo.
        assertThat(ids).hasSize(5).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    void movementsHoldOnlyThisAccountsSignedLines() {
        var account = ledger.account("SEK", "LIABILITY");
        var other = ledger.account("SEK", "LIABILITY");
        var cash = ledger.account("SEK", "ASSET");
        var deposit = ledger.transfer(cash, account, 10_000, "SEK", at(1));
        ledger.transfer(cash, other, 99_000, "SEK", at(2));

        var movements = postings.movementsOf(load(account));

        assertThat(movements).singleElement().satisfies(movement -> {
            assertThat(movement.line().accountId()).isEqualTo(new AccountId(account));
            assertThat(movement.line().transactionId()).isEqualTo(new TransactionId(deposit));
            assertThat(movement.line().amount()).isEqualTo(Money.of(-10_000, "SEK"));
            assertThat(movement.line().side()).isEqualTo(Side.CREDIT);
        });
    }

    @Test
    void accountWithoutPostingsHasNoMovements() {
        assertThat(postings.movementsOf(load(ledger.account("EUR", "ASSET")))).isEmpty();
    }

    @Test
    void schemaRejectsZeroAmountPostings() {
        var account = ledger.account("EUR", "ASSET");

        assertThatThrownBy(() -> ledger.transfer(account, account, 0, "EUR"))
                .hasMessageContaining("ck_postings_amount_non_zero");
    }

    @Test
    void schemaRejectsPostingsInAnotherCurrencyThanTheAccount() {
        var euro = ledger.account("EUR", "ASSET");
        var krona = ledger.account("SEK", "LIABILITY");

        assertThatThrownBy(() -> ledger.transfer(euro, krona, 100, "EUR"))
                .hasMessageContaining("fk_postings_account_currency");
    }

    private static OffsetDateTime at(int day) {
        return OffsetDateTime.of(2026, 1, day, 9, 30, 0, 0, ZoneOffset.UTC);
    }

    private Account load(UUID id) {
        return accounts.findById(new AccountId(id)).orElseThrow();
    }
}
