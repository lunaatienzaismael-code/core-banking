package com.ledgerflow.architecture.fixture.valid.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class AccountEntity {
    @Id
    long id;

    long balanceMinor;
}
