package com.ledgerflow.payment.domain;

import java.util.Objects;

/** The client's key for one payment initiation (ADR 0010): 1–255 characters. */
public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 255;

    public IdempotencyKey {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException(value.length());
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
