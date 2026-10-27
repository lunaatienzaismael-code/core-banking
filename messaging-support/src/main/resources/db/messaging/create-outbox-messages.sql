-- Outbox table (ADR 0013). Each service applies this file from its own changeset, so the table lives in the
-- service's database and the DDL is written once. Rows are marked published, never deleted.
CREATE TABLE outbox_messages (
    id           bigint GENERATED ALWAYS AS IDENTITY CONSTRAINT pk_outbox_messages PRIMARY KEY,
    message_id   uuid         NOT NULL CONSTRAINT uq_outbox_messages_message_id UNIQUE,
    topic        varchar(249) NOT NULL,
    message_key  varchar(255) NOT NULL,
    payload      bytea        NOT NULL,
    payload_type varchar(255) NOT NULL,
    created_at   timestamptz  NOT NULL,
    published_at timestamptz
);

-- The relay reads unpublished rows in id order; published rows drop out of the index.
CREATE INDEX ix_outbox_messages_unpublished ON outbox_messages (id) WHERE published_at IS NULL;
