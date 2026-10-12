package com.ledgerflow.ledger.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.ledger.TestcontainersConfiguration;
import com.ledgerflow.ledger.application.port.out.AccountRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import java.util.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountPersistenceAdapterIT {

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savedAccountIsStoredWithItsAttributesAndAnInitialVersion() {
        var account = Account.open(Currency.getInstance("EUR"), AccountType.LIABILITY, OverdraftPolicy.NOT_ALLOWED);

        accounts.save(account);

        var row = jdbc.queryForMap(
                "select currency, type, overdraft_policy, version, created_at from accounts where id = ?",
                account.id().value());
        assertThat(row)
                .containsEntry("currency", "EUR")
                .containsEntry("type", "LIABILITY")
                .containsEntry("overdraft_policy", "NOT_ALLOWED")
                .containsEntry("version", 0L);
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void savedAccountIsFoundByIdWithTheSameAttributes() {
        var account = Account.open(Currency.getInstance("SEK"), AccountType.ASSET, OverdraftPolicy.ALLOWED);
        accounts.save(account);

        assertThat(accounts.findById(account.id())).contains(account);
    }

    @Test
    void unknownIdIsNotFound() {
        assertThat(accounts.findById(AccountId.random())).isEmpty();
    }

    @Test
    void schemaRejectsUnknownAccountTypes() {
        var insert = "insert into accounts (id, currency, type, overdraft_policy, version) values (gen_random_uuid(),"
                + " 'EUR', 'SAVINGS', 'ALLOWED', 0)";

        assertThatThrownBy(() -> jdbc.update(insert)).hasMessageContaining("ck_accounts_type");
    }
}
