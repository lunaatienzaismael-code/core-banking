/**
 * Inbox for idempotent consumers (ADR 0013): {@link com.ledgerflow.messaging.inbox.Inbox} records a message id in the
 * consumer's database transaction. Services own the table (a changeset applying
 * {@code db/messaging/create-processed-messages.sql}).
 */
package com.ledgerflow.messaging.inbox;
