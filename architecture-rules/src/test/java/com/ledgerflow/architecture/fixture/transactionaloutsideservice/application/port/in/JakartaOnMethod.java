package com.ledgerflow.architecture.fixture.transactionaloutsideservice.application.port.in;

import jakarta.transaction.Transactional;

public interface JakartaOnMethod {
    @Transactional
    void execute();
}
