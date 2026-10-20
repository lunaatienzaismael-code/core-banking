package com.ledgerflow.payment.domain;

/** The Idempotency-Key already belongs to a payment initiated with a different request (ADR 0010). */
public final class IdempotencyKeyReusedException extends DomainException {

    public IdempotencyKeyReusedException(IdempotencyKey key) {
        super("Idempotency-Key '" + key + "' was already used for a different request");
    }
}
