package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.Money;

public interface PostingRepository {

    /** Signed net of the account's postings (Σ debits − Σ credits) in its currency; zero when it has none. */
    Money signedNetOf(Account account);
}
