package com.ledgerflow.risk.application.port.in;

/** Assesses a payment before funds are reserved and replies with the decision (ADR 0014). */
public interface AssessRiskUseCase {

    /** Assesses once per message: a redelivery changes nothing and replies nothing. */
    void assess(AssessRiskCommand command);
}
