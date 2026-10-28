package com.ledgerflow.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.common.header.Headers;

/** Kafka headers the relay adds to every message. */
public final class OutboxHeaders {

    /** UUID of the outbox row; stable across redeliveries, so consumers deduplicate on it (ADR 0013). */
    public static final String MESSAGE_ID = "message-id";

    private OutboxHeaders() {}

    /**
     * The message id a consumer records in its inbox.
     *
     * @throws IllegalArgumentException when the header is missing or not a UUID: the message did not come from an
     *     outbox and cannot be deduplicated
     */
    public static UUID messageIdOf(Headers headers) {
        var header = headers.lastHeader(MESSAGE_ID);
        if (header == null) {
            throw new IllegalArgumentException("Message has no " + MESSAGE_ID + " header");
        }
        return UUID.fromString(new String(header.value(), StandardCharsets.UTF_8));
    }
}
