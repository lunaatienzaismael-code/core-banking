package com.ledgerflow.payment.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A transfer between two ledger accounts and where it is in its lifecycle (UC3, ADR 0011). Its status changes only
 * through the business-event methods, each checked against {@link PaymentStatus#next()}.
 */
public final class Payment {

    private static final Set<PaymentStatus> NEEDS_REASON = EnumSet.of(PaymentStatus.REJECTED, PaymentStatus.FAILED);

    private final PaymentId id;
    private final AccountId debtor;
    private final AccountId creditor;
    private final Money amount;
    private final Instant initiatedAt;
    private PaymentStatus status;
    private String reason;

    private Payment(PaymentId id, AccountId debtor, AccountId creditor, Money amount, Instant initiatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.debtor = Objects.requireNonNull(debtor, "debtor");
        this.creditor = Objects.requireNonNull(creditor, "creditor");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.initiatedAt = Objects.requireNonNull(initiatedAt, "initiatedAt");
        if (!amount.isPositive()) {
            throw new InvalidPaymentException("Payment amount must be positive, got " + amount);
        }
        if (debtor.equals(creditor)) {
            throw new InvalidPaymentException("Debtor and creditor are the same account " + debtor);
        }
        this.status = PaymentStatus.INITIATED;
    }

    /** A new payment in {@link PaymentStatus#INITIATED}: accepted, nothing reserved. */
    public static Payment initiate(AccountId debtor, AccountId creditor, Money amount, Instant initiatedAt) {
        return new Payment(PaymentId.random(), debtor, creditor, amount, initiatedAt);
    }

    /**
     * A stored payment, as persisted. The same rules hold as for a new one, and the reason must be present exactly when
     * the status is {@link PaymentStatus#REJECTED} or {@link PaymentStatus#FAILED}.
     */
    public static Payment restore(
            PaymentId id,
            AccountId debtor,
            AccountId creditor,
            Money amount,
            Instant initiatedAt,
            PaymentStatus status,
            String reason) {
        var payment = new Payment(id, debtor, creditor, amount, initiatedAt);
        payment.status = Objects.requireNonNull(status, "status");
        if ((reason != null) != NEEDS_REASON.contains(status)) {
            throw new IllegalArgumentException("Payment " + id + " in " + status + " cannot have reason " + reason);
        }
        payment.reason = reason;
        return payment;
    }

    /** The risk check passed (UC5). */
    public void approveRisk() {
        moveTo(PaymentStatus.RISK_APPROVED);
    }

    /** The ledger holds the debtor's funds (UC4). */
    public void reserveFunds() {
        moveTo(PaymentStatus.FUNDS_RESERVED);
    }

    /** The transfer is posted; terminal. */
    public void complete() {
        moveTo(PaymentStatus.COMPLETED);
    }

    /** Refused before any funds were held; terminal. */
    public void reject(String reason) {
        moveTo(PaymentStatus.REJECTED, reason);
    }

    /** Failed after funds were held, and the hold was released; terminal. */
    public void fail(String reason) {
        moveTo(PaymentStatus.FAILED, reason);
    }

    private void moveTo(PaymentStatus target) {
        requireAllowed(target);
        status = target;
    }

    private void moveTo(PaymentStatus target, String reason) {
        Objects.requireNonNull(reason, "reason");
        requireAllowed(target);
        status = target;
        this.reason = reason;
    }

    private void requireAllowed(PaymentStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalPaymentTransitionException(id, status, target);
        }
    }

    public PaymentId id() {
        return id;
    }

    public AccountId debtor() {
        return debtor;
    }

    public AccountId creditor() {
        return creditor;
    }

    public Money amount() {
        return amount;
    }

    public Instant initiatedAt() {
        return initiatedAt;
    }

    public PaymentStatus status() {
        return status;
    }

    /** Why the payment was rejected or failed; empty otherwise. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }
}
