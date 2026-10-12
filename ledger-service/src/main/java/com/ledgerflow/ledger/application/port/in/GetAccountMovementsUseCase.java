package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import java.util.List;

public interface GetAccountMovementsUseCase {

    /**
     * The account's posting lines, oldest first.
     *
     * @throws com.ledgerflow.ledger.domain.AccountNotFoundException if no account has this id
     */
    List<MovementView> movementsOf(AccountId id);
}
