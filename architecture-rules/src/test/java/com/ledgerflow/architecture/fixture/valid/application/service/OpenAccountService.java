package com.ledgerflow.architecture.fixture.valid.application.service;

import com.ledgerflow.architecture.fixture.valid.application.port.in.OpenAccountUseCase;
import com.ledgerflow.architecture.fixture.valid.application.port.out.AccountRepository;
import com.ledgerflow.architecture.fixture.valid.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenAccountService implements OpenAccountUseCase {
    private final AccountRepository repository;

    public OpenAccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Money open(String currency) {
        Money opening = new Money(0, currency);
        repository.save(opening);
        return opening;
    }
}
