package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.InvalidTransactionException;
import com.ledgerflow.ledger.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * A release-funds command as the ledger sees it.
 *
 * @param messageId id of the message that carried it, recorded so a redelivery changes nothing
 * @param paymentId the payment the reply refers to
 * @param amount positive amount, the one reserved for the payment
 * @param reason why the transfer failed; returned unchanged in the reply (ADR 0012)
 */
public record ReleaseFundsCommand(UUID messageId, String paymentId, AccountId debtor, Money amount, String reason) {

    public ReleaseFundsCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(debtor, "debtor");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        if (!amount.isPositive()) {
            throw new InvalidTransactionException("Amount to release must be positive, got " + amount);
        }
    }
}
