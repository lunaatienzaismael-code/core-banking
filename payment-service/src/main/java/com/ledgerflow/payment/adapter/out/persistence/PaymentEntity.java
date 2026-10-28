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

    /** Written once by the idempotent insert (ADR 0010); JPA only reads it. */
    @Column(name = "idempotency_key", nullable = false, insertable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String requestHash;

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

    /** A status change; {@code @Version} makes the update fail if another transaction changed the row meanwhile. */
    void changeStatus(String status, String reason) {
        this.status = status;
        this.reason = reason;
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

    String getRequestHash() {
        return requestHash;
    }

    Long getVersion() {
        return version;
    }
}
