package com.ledgerflow.payment.application.port.in;

/** UC3: initiate a transfer between two ledger accounts; it starts in INITIATED (ADR 0011). */
public interface InitiatePaymentUseCase {

    /**
     * Initiates the payment once per idempotency key (ADR 0010): a retry with the same key and request returns the
     * stored payment.
     *
     * @throws com.ledgerflow.payment.domain.InvalidPaymentException if the amount or the accounts break a rule
     * @throws com.ledgerflow.payment.domain.IdempotencyKeyReusedException if the key belongs to a different request
     */
    PaymentView initiate(InitiatePaymentCommand command);
}
