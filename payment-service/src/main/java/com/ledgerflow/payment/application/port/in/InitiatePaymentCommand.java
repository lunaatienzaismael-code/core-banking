package com.ledgerflow.payment.application.port.in;

import com.ledgerflow.payment.domain.AccountId;
import com.ledgerflow.payment.domain.IdempotencyKey;
import com.ledgerflow.payment.domain.Money;
import java.util.Objects;

public record InitiatePaymentCommand(IdempotencyKey key, AccountId debtor, AccountId creditor, Money amount) {

    public InitiatePaymentCommand {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(creditor, "creditor");
        Objects.requireNonNull(amount, "amount");
    }
}
