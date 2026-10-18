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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    void applyAddsADebitToDebitsPostedAndACreditToCreditsPosted() {
        var account = ledger.account("EUR", "ASSET");

        balances.apply(new AccountId(account), Money.of(1_000, "EUR"));
        balances.apply(new AccountId(account), Money.of(-300, "EUR"));
        balances.apply(new AccountId(account), Money.of(50, "EUR"));

        assertThat(counters(account)).containsExactly(1_050L, 300L);
    }

    @Test
    void creditNormalNotAllowedAccountMayBeDebitedDownToExactlyZero() {
        var customer = funded("LIABILITY", 1_000);

        assertThat(balances.apply(new AccountId(customer), Money.of(1_000, "EUR")))
                .isTrue();

        assertThat(counters(customer)).containsExactly(1_000L, 1_000L);
    }

    @Test
    void creditNormalNotAllowedAccountRefusesADebitOneMinorUnitShort() {
        var customer = funded("LIABILITY", 1_000);

        assertThat(balances.apply(new AccountId(customer), Money.of(1_001, "EUR")))
                .isFalse();

        assertThat(counters(customer)).containsExactly(0L, 1_000L);
    }

    @Test
    void debitNormalNotAllowedAccountMayBeCreditedDownToExactlyZero() {
        var nostro = funded("ASSET", 500);

        assertThat(balances.apply(new AccountId(nostro), Money.of(-500, "EUR"))).isTrue();

        assertThat(counters(nostro)).containsExactly(500L, 500L);
    }

    @Test
    void debitNormalNotAllowedAccountRefusesACreditOneMinorUnitShort() {
        var nostro = funded("ASSET", 500);

        assertThat(balances.apply(new AccountId(nostro), Money.of(-501, "EUR"))).isFalse();

        assertThat(counters(nostro)).containsExactly(500L, 0L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ASSET", "EXPENSE", "LIABILITY", "EQUITY", "INCOME"})
    void everyNotAllowedAccountTypeIsHeldToExactlyZeroOnItsNormalSide(String type) {
        var account = funded(type, 700);
        var id = new AccountId(account);
        var reducing = debitNormal(type) ? -1 : 1;

        assertThat(balances.apply(id, Money.of(701L * reducing, "EUR"))).isFalse();
        assertThat(balances.apply(id, Money.of(700L * reducing, "EUR"))).isTrue();

        assertThat(counters(account)).containsExactly(700L, 700L);
    }

    @Test
    void amountThatRaisesTheNormalSideBalanceIsNeverRefused() {
        var customer = ledger.account("EUR", "LIABILITY", "NOT_ALLOWED");

        assertThat(balances.apply(new AccountId(customer), Money.of(-1, "EUR"))).isTrue();

        assertThat(counters(customer)).containsExactly(0L, 1L);
    }

    @Test
    void allowedAccountMayGoBelowZeroOnItsNormalSide() {
        var suspense = ledger.account("EUR", "LIABILITY", "ALLOWED");

        assertThat(balances.apply(new AccountId(suspense), Money.of(5_000, "EUR")))
                .isTrue();

        assertThat(counters(suspense)).containsExactly(5_000L, 0L);
    }

    @Test
    void notAllowedAccountCannotBeOverdrawnEvenByAnUnguardedWrite() {
        var customer = ledger.account("EUR", "LIABILITY", "NOT_ALLOWED");
        var nostro = ledger.account("EUR", "ASSET", "NOT_ALLOWED");

        assertThatThrownBy(() -> jdbc.update("update accounts set debits_posted = 1 where id = ?", customer))
                .hasMessageContaining("ck_accounts_no_overdraft");
        assertThatThrownBy(() -> jdbc.update("update accounts set credits_posted = 1 where id = ?", nostro))
                .hasMessageContaining("ck_accounts_no_overdraft");
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

    /** A {@code NOT_ALLOWED} account whose normal-side balance is {@code amount}, funded from an {@code ALLOWED} one. */
    private UUID funded(String type, long amount) {
        var account = ledger.account("EUR", type, "NOT_ALLOWED");
        var counterpart = ledger.account("EUR", debitNormal(type) ? "LIABILITY" : "ASSET");
        if (debitNormal(type)) {
            ledger.transfer(account, counterpart, amount, "EUR");
        } else {
            ledger.transfer(counterpart, account, amount, "EUR");
        }
        return account;
    }

    private static boolean debitNormal(String type) {
        return type.equals("ASSET") || type.equals("EXPENSE");
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
