package com.ledgerflow.payment.application.port.out;

import com.ledgerflow.payment.domain.Payment;

/**
 * Commands the saga sends to the ledger (ADR 0012). Implementations must send in the caller's transaction, so a command
 * exists exactly when the transition that asks for it commits.
 */
public interface LedgerCommands {

    /** Debtor → payments in transit. */
    void reserveFunds(Payment payment);

    /** Payments in transit → creditor. */
    void postTransfer(Payment payment);

    /** Compensation: payments in transit → debtor; {@code reason} comes back in the reply (ADR 0012). */
    void releaseFunds(Payment payment, String reason);
}
