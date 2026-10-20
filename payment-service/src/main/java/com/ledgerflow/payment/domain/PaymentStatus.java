package com.ledgerflow.payment.domain;

import java.util.EnumSet;
import java.util.Set;

/** The payment lifecycle and the moves it allows (ADR 0011): the transition table lives here and nowhere else. */
public enum PaymentStatus {
    INITIATED,
    RISK_APPROVED,
    FUNDS_RESERVED,
    COMPLETED,
    REJECTED,
    FAILED;

    /** The statuses this one may move to; empty for a terminal status. */
    public Set<PaymentStatus> next() {
        return switch (this) {
            case INITIATED -> EnumSet.of(RISK_APPROVED, REJECTED);
            case RISK_APPROVED -> EnumSet.of(FUNDS_RESERVED, REJECTED);
            case FUNDS_RESERVED -> EnumSet.of(COMPLETED, FAILED);
            case COMPLETED, REJECTED, FAILED -> EnumSet.noneOf(PaymentStatus.class);
        };
    }

    public boolean canMoveTo(PaymentStatus target) {
        return next().contains(target);
    }

    public boolean isTerminal() {
        return next().isEmpty();
    }
}
