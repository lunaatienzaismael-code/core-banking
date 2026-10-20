package com.ledgerflow.payment.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA mapping of the {@code payments} table, owned by Liquibase. Never leaves this adapter. */
@Entity
@Table(name = "payments")
class PaymentEntity {

    @Id
    private UUID id;

    @Column(name = "debtor_account_id", nullable = false)
    private UUID debtorAccountId;

    @Column(name = "creditor_account_id", nullable = false)
    private UUID creditorAccountId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(nullable = false)
    private String status;

    private String reason;

    @Column(name = "initiated_at", nullable = false)
    private OffsetDateTime initiatedAt;

    /** Optimistic locking for status changes (ADR 0011). Null until first persisted, which marks the entity as new. */
    @Version
    private Long version;

    protected PaymentEntity() {}

    PaymentEntity(
            UUID id,
            UUID debtorAccountId,
            UUID creditorAccountId,
            long amount,
            String currency,
            String status,
            String reason,
            OffsetDateTime initiatedAt) {
        this.id = id;
        this.debtorAccountId = debtorAccountId;
        this.creditorAccountId = creditorAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.reason = reason;
        this.initiatedAt = initiatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getDebtorAccountId() {
        return debtorAccountId;
    }

    UUID getCreditorAccountId() {
        return creditorAccountId;
    }

    long getAmount() {
        return amount;
    }

    String getCurrency() {
        return currency;
    }

    String getStatus() {
        return status;
    }

    String getReason() {
        return reason;
    }

    OffsetDateTime getInitiatedAt() {
        return initiatedAt;
    }

    Long getVersion() {
        return version;
    }
}
