package com.ledgerflow.ledger.adapter.out.persistence;

import com.ledgerflow.ledger.application.port.out.BalanceRepository;
import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Reads the posting counters with plain SQL. They are not part of {@link AccountEntity}, so saving an account through
 * JPA can never overwrite them (ADR 0009).
 */
@Component
class BalancePersistenceAdapter implements BalanceRepository {

    private static final String COUNTERS_SQL = "select debits_posted, credits_posted from accounts where id = ?";
    // One statement adds to the counter in place: no value is read into Java and written back (ADR 0009).
    private static final String APPLY_SQL = """
            update accounts
               set debits_posted = debits_posted + ?,
                   credits_posted = credits_posted + ?
             where id = ?""";

    private final JdbcClient jdbc;

    BalancePersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Money signedNetOf(Account account) {
        var net = jdbc.sql(COUNTERS_SQL)
                .param(account.id().value())
                .query((row, rowNum) -> Math.subtractExact(row.getLong("debits_posted"), row.getLong("credits_posted")))
                .single();
        return new Money(net, account.currency());
    }

    @Override
    public void apply(AccountId account, Money signedAmount) {
        var debit = signedAmount.isPositive() ? signedAmount.amount() : 0L;
        var credit = signedAmount.isPositive() ? 0L : Math.negateExact(signedAmount.amount());
        var updated = jdbc.sql(APPLY_SQL).params(debit, credit, account.value()).update();
        if (updated != 1) {
            throw new IllegalStateException("Account " + account + " vanished while applying a posting");
        }
    }
}
