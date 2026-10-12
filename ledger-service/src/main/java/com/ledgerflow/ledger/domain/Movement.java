package com.ledgerflow.ledger.domain;

import java.time.Instant;
import java.util.Objects;

/** A posting line as it appears in an account's history: the line plus when its transaction occurred. */
public record Movement(PostingLine line, Instant occurredAt) {

    public Movement {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
