package com.ledgerflow.payment.application.port.out;

import com.ledgerflow.payment.domain.Payment;

/**
 * Commands the saga sends to risk-service (ADR 0014). Implementations must send in the caller's transaction, so a
 * command exists exactly when the step that asks for it commits.
 */
public interface RiskCommands {

    /** Asks risk-service to assess the payment before any funds are reserved. */
    void assess(Payment payment);
}
