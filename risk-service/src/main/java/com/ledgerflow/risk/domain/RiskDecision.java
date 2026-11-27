package com.ledgerflow.risk.domain;

import java.util.Objects;

/** The outcome of assessing one payment (ADR 0014). */
public sealed interface RiskDecision {

    /** No rule objected. */
    record Approved() implements RiskDecision {}

    /** A rule rejected the payment; {@code detail} explains it to a human. */
    record Rejected(RejectionReason reason, String detail) implements RiskDecision {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
