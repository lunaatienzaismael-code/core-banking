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

    private Account load(UUID id) {
        return accounts.findById(new AccountId(id)).orElseThrow();
    }
}
