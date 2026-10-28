package com.ledgerflow.messaging.inbox;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records which messages a consumer has processed, in the transaction of their effect (AC4.2, ADR 0013): a message
 * redelivered after its effect committed is recognised and skipped; one whose transaction rolled back is not recorded
 * and is processed again.
 */
public final class Inbox {

    private static final String INSERT_SQL = """
            insert into processed_messages (message_id, processed_at) values (?, ?)
            on conflict (message_id) do nothing""";

    private final JdbcClient jdbc;
    private final Clock clock;

    public Inbox(JdbcClient jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Records the message as processed if it was not already. A concurrent delivery of the same id waits for this
     * transaction to commit or roll back, then sees the outcome.
     *
     * @return {@code true} the first time, when the caller must apply the message's effect; {@code false} for a
     *     redelivery, which must change nothing
     * @throws IllegalStateException when no transaction is active: the record would not be atomic with the effect
     */
    public boolean firstDelivery(UUID messageId) {
        Objects.requireNonNull(messageId, "messageId");
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A message must be recorded inside the transaction of its effect");
        }
        return jdbc.sql(INSERT_SQL)
                        .params(messageId, Timestamp.from(clock.instant()))
                        .update()
                == 1;
    }
}
