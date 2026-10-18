package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.Money;

/** The account's materialised posting counters (ADR 0009), which always equal the sums of its postings. */
public interface BalanceRepository {

    /** Signed net of the account's postings (debits posted − credits posted) in its currency; zero when it has none. */
    Money signedNetOf(Account account);
}
