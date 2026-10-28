-- Inbox table for idempotent consumers (ADR 0013). Each service applies this file from its own changeset. A row is
-- written in the same transaction as the message's effect, so a redelivered message finds it and does nothing.
CREATE TABLE processed_messages (
    message_id   uuid        NOT NULL CONSTRAINT pk_processed_messages PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
