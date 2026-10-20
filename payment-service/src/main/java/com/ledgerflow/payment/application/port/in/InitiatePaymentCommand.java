package com.ledgerflow.payment.application.port.in;

import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.Money;
import java.util.Objects;

public record InitiatePaymentCommand(AccountId debtor, AccountId creditor, Money amount) {

    public InitiatePaymentCommand {
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(creditor, "creditor");
        Objects.requireNonNull(amount, "amount");
    }
}
