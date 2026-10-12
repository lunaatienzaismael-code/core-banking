package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.Movement;
import java.util.List;

public interface PostingRepository {

    /** Signed net of the account's postings (Σ debits − Σ credits) in its currency; zero when it has none. */
    Money signedNetOf(Account account);

    /** The account's posting lines ordered by transaction time, then posting id so ties are deterministic. */
    List<Movement> movementsOf(Account account);
}
