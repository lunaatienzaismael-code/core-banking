package com.ledgerflow.payment.application.port.in;

/**
 * Moves a payment along its saga (ADR 0012) in reaction to one input, at most once per message (ADR 0013): applies
 * the ADR 0011 transition and sends the next command in one transaction.
 */
public interface AdvancePaymentSagaUseCase {

    void advance(SagaInput input);
}
