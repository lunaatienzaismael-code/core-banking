package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.Account;
import com.ledgerflow.ledger.domain.Movement;
import java.util.List;

public interface PostingRepository {

    /** The account's posting lines ordered by transaction time, then posting id so ties are deterministic. */
    List<Movement> movementsOf(Account account);
}
