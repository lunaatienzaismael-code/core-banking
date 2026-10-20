package com.ledgerflow.payment.domain;

/** The Idempotency-Key is empty or longer than {@link IdempotencyKey#MAX_LENGTH} characters. */
public final class InvalidIdempotencyKeyException extends DomainException {

    public InvalidIdempotencyKeyException(int length) {
        super("Idempotency-Key must be 1 to " + IdempotencyKey.MAX_LENGTH + " characters, got " + length);
    }
}
