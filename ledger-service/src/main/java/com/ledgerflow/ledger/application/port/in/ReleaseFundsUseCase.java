package com.ledgerflow.ledger.application.port.in;

/**
 * Compensation: moves a reserved amount from the payments-in-transit account back to the debtor (ADR 0012), at most
 * once per message. It is never refused; a failure is technical and the message is retried.
 */
public interface ReleaseFundsUseCase {

    void release(ReleaseFundsCommand command);
}
