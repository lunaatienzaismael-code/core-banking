package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.Money;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import com.ledgerflow.ledger.domain.Side;
import java.util.Currency;

/** An account as seen by use case callers, with its balance derived from postings. */
public record AccountView(
        AccountId id,
        Currency currency,
        AccountType type,
        Side normalBalanceSide,
        OverdraftPolicy overdraftPolicy,
        Money balance) {}
