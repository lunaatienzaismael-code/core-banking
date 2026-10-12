package com.ledgerflow.ledger.application.port.in;

/** UC1: open an account in one currency, with a zero balance and no movements. */
public interface OpenAccountUseCase {

    AccountView open(OpenAccountCommand command);
}
