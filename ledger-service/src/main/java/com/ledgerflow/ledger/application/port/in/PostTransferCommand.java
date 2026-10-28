package com.ledgerflow.ledger.application.port.in;

import com.ledgerflow.ledger.domain.AccountId;
import com.ledgerflow.ledger.domain.InvalidTransactionException;
import com.ledgerflow.ledger.domain.Money;
import java.util.Objects;
import java.util.UUID;

/**
 * A post-transfer command as the ledger sees it.
 *
 * @param messageId id of the message that carried it, recorded so a redelivery changes nothing
 * @param paymentId the payment the reply refers to
 * @param amount positive amount, the one reserved for the payment
 */
public record PostTransferCommand(UUID messageId, String paymentId, AccountId creditor, Money amount) {

    public PostTransferCommand {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(creditor, "creditor");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new InvalidTransactionException("Amount to post must be positive, got " + amount);
        }
    }
}
