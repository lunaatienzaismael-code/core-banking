package com.ledgerflow.ledger.domain;

import java.util.Objects;
import java.util.UUID;

public record PostingId(UUID value) {

    public PostingId {
        Objects.requireNonNull(value, "value");
    }

    public static PostingId random() {
        return new PostingId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
