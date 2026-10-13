package com.ledgerflow.architecture.fixture.entityoutsidepersistence.adapter.out.messaging;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class OutboxRecord {
    @Id
    long id;
}
