package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountType;
import com.ledgerflow.ledger.domain.OverdraftPolicy;
import java.util.Currency;
import java.util.Objects;

public record OpenAccountCommand(Currency currency, AccountType type, OverdraftPolicy overdraftPolicy) {

    public OpenAccountCommand {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(overdraftPolicy, "overdraftPolicy");
    }
}
