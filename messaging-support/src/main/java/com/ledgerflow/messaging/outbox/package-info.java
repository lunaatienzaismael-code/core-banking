/**
 * Transactional outbox (ADR 0013): {@link com.ledgerflow.messaging.outbox.OutboxWriter} stores a message in the
 * caller's database transaction, {@link com.ledgerflow.messaging.outbox.OutboxRelay} publishes stored messages to
 * Kafka. Services own the table (a changeset applying {@code db/messaging/create-outbox-messages.sql}) and the wiring.
 */
package com.ledgerflow.messaging.outbox;
