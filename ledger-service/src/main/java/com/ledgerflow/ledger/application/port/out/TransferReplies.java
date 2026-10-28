package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.TransactionId;

/**
 * Answers the commands that move a payment's money through the transit account (ADR 0012). Implementations must send
 * in the caller's transaction, so a reply exists exactly when what it reports committed.
 */
public interface TransferReplies {

    void fundsReserved(String paymentId, TransactionId transaction);

    void reservationRefused(String paymentId, Refusal reason, String detail);

    void transferPosted(String paymentId, TransactionId transaction);

    void postingRefused(String paymentId, Refusal reason, String detail);

    /** @param reason why the transfer failed, as the release command carried it */
    void fundsReleased(String paymentId, TransactionId transaction, String reason);

    /** Why nothing was moved. */
    enum Refusal {
        INSUFFICIENT_FUNDS,
        UNKNOWN_ACCOUNT,
        CURRENCY_MISMATCH
    }
}
