package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.Side;
import java.util.Objects;

/** An account's balance from its postings, reported from its normal balance side. */
public record AccountBalanceView(AccountId accountId, Side normalBalanceSide, Money balance) {

    public AccountBalanceView {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(normalBalanceSide, "normalBalanceSide");
        Objects.requireNonNull(balance, "balance");
    }
}
