package com.ledgerflow.architecture.fixture.valid.adapter.in.web;

import com.ledgerflow.architecture.fixture.valid.application.port.in.OpenAccountUseCase;

public class AccountController {
    private final OpenAccountUseCase openAccount;

    public AccountController(OpenAccountUseCase openAccount) {
        this.openAccount = openAccount;
    }

    public long open(String currency) {
        return openAccount.open(currency).amountMinor();
    }
}
