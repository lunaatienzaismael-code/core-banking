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
    /**
     * One statement adds to the counters in place and checks the overdraft rule on the result (ADR 0009): no value is
     * read into Java and written back. A writer that waited on the row lock re-evaluates the WHERE against the newly
     * committed row, so the guard always sees the latest counters. ck_accounts_no_overdraft repeats the rule.
     */
    private static final String APPLY_SQL = """
            update accounts
               set debits_posted = debits_posted + :debit,
                   credits_posted = credits_posted + :credit
             where id = :id
               and (overdraft_policy = 'ALLOWED'
                    or (type in ('ASSET', 'EXPENSE')
                        and debits_posted + :debit - (credits_posted + :credit) >= 0)
                    or (type in ('LIABILITY', 'EQUITY', 'INCOME')
                        and credits_posted + :credit - (debits_posted + :debit) >= 0))""";

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
    public boolean apply(AccountId account, Money signedAmount) {
        var debit = signedAmount.isPositive() ? signedAmount.amount() : 0L;
        var credit = signedAmount.isPositive() ? 0L : Math.negateExact(signedAmount.amount());
        // The use case loaded the account in this transaction, so zero rows means the guard refused the amount.
        return jdbc.sql(APPLY_SQL)
                        .param("debit", debit)
                        .param("credit", credit)
                        .param("id", account.value())
                        .update()
                == 1;
    }
}
