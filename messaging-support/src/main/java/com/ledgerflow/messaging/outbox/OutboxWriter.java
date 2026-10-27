package com.ledgerflow.messaging.outbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stores an outgoing message in {@code outbox_messages} within the caller's database transaction, so the message
 * commits or rolls back with the state change that caused it (AC4.1, ADR 0013).
 */
public final class OutboxWriter {

    private static final String INSERT_SQL = """
            insert into outbox_messages (message_id, topic, message_key, payload, payload_type, created_at)
            values (?, ?, ?, ?, ?, ?)""";

    private final JdbcClient jdbc;
    private final Clock clock;

    public OutboxWriter(JdbcClient jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Stores the message for the relay to publish to {@code topic} with {@code key}.
     *
     * @throws IllegalStateException when no transaction is active: a message written on its own would be the
     *     dual-write the outbox exists to avoid
     */
    public void write(String topic, String key, SpecificRecord message) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(message, "message");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("An outbox message must be written inside the transaction of its change");
        }
        jdbc.sql(INSERT_SQL)
                .params(
                        UUID.randomUUID(),
                        topic,
                        key,
                        AvroBinary.encode(message),
                        message.getSchema().getFullName(),
                        Timestamp.from(clock.instant()))
                .update();
    }
}
