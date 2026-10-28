package com.ledgerflow.ledger.application.port.in;

/**
 * Completes a reserved transfer by moving its amount from the payments-in-transit account to the creditor (ADR 0012),
 * at most once per message, and answers with a reply either way.
 */
public interface PostTransferUseCase {

    void post(PostTransferCommand command);
}
