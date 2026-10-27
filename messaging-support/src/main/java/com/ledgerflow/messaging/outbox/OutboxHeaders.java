package com.ledgerflow.messaging.outbox;

/** Kafka headers the relay adds to every message. */
public final class OutboxHeaders {

    /** UUID of the outbox row; stable across redeliveries, so consumers deduplicate on it (ADR 0013). */
    public static final String MESSAGE_ID = "message-id";

    private OutboxHeaders() {}
}
