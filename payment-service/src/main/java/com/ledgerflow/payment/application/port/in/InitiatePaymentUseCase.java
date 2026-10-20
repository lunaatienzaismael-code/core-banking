package com.ledgerflow.payment.application.port.in;

/** UC3: initiate a transfer between two ledger accounts; it starts in INITIATED (ADR 0011). */
public interface InitiatePaymentUseCase {

    /** @throws com.ledgerflow.payment.domain.InvalidPaymentException if the amount or the accounts break a rule */
    PaymentView initiate(InitiatePaymentCommand command);
}
