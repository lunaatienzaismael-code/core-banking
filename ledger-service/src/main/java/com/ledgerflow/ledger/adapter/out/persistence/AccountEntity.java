package com.ledgerflow.ledger.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA mapping of the {@code accounts} table, owned by Liquibase. Never leaves this adapter. */
@Entity
@Table(name = "accounts")
class AccountEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false)
    private String type;

    @Column(name = "overdraft_policy", nullable = false)
    private String overdraftPolicy;

    /** Optimistic locking (ADR 0004). Null until first persisted, which also marks the entity as new. */
    @Version
    private Long version;

    protected AccountEntity() {}

    AccountEntity(UUID id, String currency, String type, String overdraftPolicy) {
        this.id = id;
        this.currency = currency;
        this.type = type;
        this.overdraftPolicy = overdraftPolicy;
    }

    UUID getId() {
        return id;
    }

    String getCurrency() {
        return currency;
    }

    String getType() {
        return type;
    }

    String getOverdraftPolicy() {
        return overdraftPolicy;
    }

    Long getVersion() {
        return version;
    }
}
