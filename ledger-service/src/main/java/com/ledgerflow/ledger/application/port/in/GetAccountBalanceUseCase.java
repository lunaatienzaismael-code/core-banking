package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;

public interface GetAccountBalanceUseCase {

    /** @throws com.ledgerflow.ledger.domain.AccountNotFoundException if no account has this id */
    AccountBalanceView balanceOf(AccountId id);
}
