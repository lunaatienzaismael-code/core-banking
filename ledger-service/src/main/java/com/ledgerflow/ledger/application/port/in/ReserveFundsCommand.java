package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.InvalidTransactionException;
import com.ledgerflow.ledger.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * A reserve-funds command as the ledger sees it.
 *
 * @param messageId id of the message that carried it, recorded so a redelivery changes nothing
 * @param paymentId the payment the reply refers to; the ledger knows nothing else about payments (ADR 0012)
 * @param amount positive amount to reserve
 */
public record ReserveFundsCommand(UUID messageId, String paymentId, AccountId debtor, Money amount) {

    public ReserveFundsCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new InvalidTransactionException("Amount to reserve must be positive, got " + amount);
        }
    }
}
