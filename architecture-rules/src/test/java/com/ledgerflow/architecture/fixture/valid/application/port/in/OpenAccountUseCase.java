package com.ledgerflow.architecture.fixture.valid.application.port.in;

import com.ledgerflow.architecture.fixture.valid.domain.Money;

public interface OpenAccountUseCase {
    Money open(String currency);
}
