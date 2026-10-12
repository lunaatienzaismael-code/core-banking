package com.ledgerflow.architecture.fixture.valid.config;

import com.ledgerflow.architecture.fixture.valid.adapter.in.web.AccountController;
import com.ledgerflow.architecture.fixture.valid.adapter.out.persistence.AccountPersistenceAdapter;
import com.ledgerflow.architecture.fixture.valid.application.service.OpenAccountService;

public class Wiring {
    public AccountController controller() {
        return new AccountController(new OpenAccountService(new AccountPersistenceAdapter()));
    }
}
