package com.ledgerflow.ledger.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.ledger.LedgerFixture;
import com.ledgerflow.ledger.TestcontainersConfiguration;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BalancePersistenceAdapterIT {

    @Autowired
    private BalanceRepository balances;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private AccountJpaRepository jpa;

    @Autowired
    private JdbcTemplate jdbc;

    private LedgerFixture ledger;

    @BeforeEach
    void setUp() {
        ledger = new LedgerFixture(jdbc);
    }

    @Test
    void signedNetIsDebitsPostedMinusCreditsPostedInTheAccountCurrency() {
        var account = ledger.account("EUR", "ASSET");
        var other = ledger.account("EUR", "LIABILITY");
        ledger.transfer(account, other, 10_000, "EUR");
        ledger.transfer(other, account, 3_000, "EUR");

        assertThat(balances.signedNetOf(load(account))).isEqualTo(Money.of(7_000, "EUR"));
        assertThat(balances.signedNetOf(load(other))).isEqualTo(Money.of(-7_000, "EUR"));
    }

    @Test
    void newAccountStartsWithZeroCountersAndAZeroNet() {
        var account = Account.open(Currency.getInstance("SEK"), AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);

        accounts.save(account);

        assertThat(counters(account.id().value())).containsExactly(0L, 0L);
        assertThat(balances.signedNetOf(account)).isEqualTo(Money.of(0, "SEK"));
    }

    @Test
    void counterOverflowFailsLoudlyInsteadOfWrapping() {
        var account = ledger.account("EUR", "ASSET");
        var other = ledger.account("EUR", "LIABILITY");
        ledger.transfer(account, other, Long.MAX_VALUE, "EUR");

        assertThatThrownBy(() -> ledger.transfer(account, other, 1, "EUR")).hasMessageContaining("bigint out of range");
    }

    @Test
    void savingAnAccountEntityLeavesItsCountersUntouched() {
        var account = ledger.account("EUR", "ASSET");
        var other = ledger.account("EUR", "LIABILITY");
        ledger.transfer(account, other, 4_200, "EUR");

        var entity = jpa.findById(account).orElseThrow();
        ReflectionTestUtils.setField(entity, "overdraftPolicy", "NOT_ALLOWED");

        jpa.saveAndFlush(entity);

        assertThat(jdbc.queryForObject("select overdraft_policy from accounts where id = ?", String.class, account))
                .isEqualTo("NOT_ALLOWED");
        assertThat(counters(account)).containsExactly(4_200L, 0L);
    }

    @Test
    void countersCannotBeNegative() {
        var account = ledger.account("EUR", "ASSET");

        assertThatThrownBy(() -> jdbc.update("update accounts set debits_posted = -1 where id = ?", account))
                .hasMessageContaining("ck_accounts_debits_posted_non_negative");
        assertThatThrownBy(() -> jdbc.update("update accounts set credits_posted = -1 where id = ?", account))
                .hasMessageContaining("ck_accounts_credits_posted_non_negative");
    }

    private Long[] counters(UUID account) {
        return jdbc.queryForObject(
                "select debits_posted, credits_posted from accounts where id = ?",
                (row, rowNum) -> new Long[] {row.getLong(1), row.getLong(2)},
                account);
    }

    private Account load(UUID id) {
        return accounts.findById(new AccountId(id)).orElseThrow();
    }
}
