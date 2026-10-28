package com.ledgerflow.ledger.application.port.out;

import com.ledgerflow.ledger.domain.TransactionId;

/**
 * Answers reserve-funds commands (ADR 0012). Implementations must send in the caller's transaction, so a reply exists
 * exactly when what it reports committed.
 */
public interface ReservationReplies {

    void fundsReserved(String paymentId, TransactionId transaction);

    void reservationRefused(String paymentId, Refusal reason, String detail);

    /** Why nothing was reserved. */
    enum Refusal {
        INSUFFICIENT_FUNDS,
        UNKNOWN_ACCOUNT,
        CURRENCY_MISMATCH
    }
}
