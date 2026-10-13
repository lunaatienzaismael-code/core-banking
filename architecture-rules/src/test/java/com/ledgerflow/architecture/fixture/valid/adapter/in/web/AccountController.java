package com.ledgerflow.architecture.fixture.valid.adapter.in.web;

import com.ledgerflow.architecture.fixture.valid.adapter.in.web.model.OpenedAccountDto;
import com.ledgerflow.architecture.fixture.valid.application.port.in.OpenAccountUseCase;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {
    private final OpenAccountUseCase openAccount;

    public AccountController(OpenAccountUseCase openAccount) {
        this.openAccount = openAccount;
    }

    public OpenedAccountDto open(String currency) {
        return new OpenedAccountDto(openAccount.open(currency).amountMinor());
    }
}
