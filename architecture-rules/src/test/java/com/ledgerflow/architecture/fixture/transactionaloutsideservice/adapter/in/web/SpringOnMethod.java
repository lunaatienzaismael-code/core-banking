package com.ledgerflow.architecture.fixture.transactionaloutsideservice.adapter.in.web;

import org.springframework.transaction.annotation.Transactional;

public class SpringOnMethod {
    @Transactional
    public void handle() {}
}
