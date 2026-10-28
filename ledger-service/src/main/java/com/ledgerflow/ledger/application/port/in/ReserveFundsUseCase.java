package com.ledgerflow.ledger.application.port.in;

/**
 * Reserves a payment's amount by moving it from the debtor to the payments-in-transit account (ADR 0012), at most once
 * per message (AC4.2), and answers with a reply either way.
 */
public interface ReserveFundsUseCase {

    void reserve(ReserveFundsCommand command);
}
