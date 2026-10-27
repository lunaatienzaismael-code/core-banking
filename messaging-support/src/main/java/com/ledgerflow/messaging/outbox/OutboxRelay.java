package com.ledgerflow.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Publishes unpublished outbox rows to Kafka in {@code id} order and marks them published (ADR 0013). Each run holds a
 * transaction-level advisory lock, so one instance of a service publishes at a time and the order of one key's rows is
 * kept. Delivery is at least once: a crash between sending and marking sends the row again.
 *
 * <p>The transaction, and so the lock, stays open while messages are sent: releasing it before the broker acknowledges
 * would let another instance send the same rows ahead of the ones in flight. A run therefore lasts at most about
 * {@code batchSize × sendTimeout} on a slow but answering broker, and stops at the first failure; size the batch with
 * that in mind.
 */
public final class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private static final String LOCK_SQL = "select pg_try_advisory_xact_lock(?)";
    // By published_at, never by a last-published id: a row that commits late with a lower id is still picked up.
    private static final String PENDING_SQL = """
            select id, message_id, topic, message_key, payload, payload_type
              from outbox_messages
             where published_at is null
             order by id
             limit ?""";
    private static final String MARK_SQL = "update outbox_messages set published_at = :now where id in (:ids)";

    private final JdbcClient jdbc;
    private final TransactionOperations transactions;
    private final KafkaOperations<String, Object> kafka;
    private final Clock clock;
    private final Settings settings;

    /**
     * @param lockKey advisory lock key, unique per service sharing a database server
     * @param batchSize rows published per run at most
     * @param sendTimeout how long to wait for the broker's acknowledgement of one message
     */
    public record Settings(long lockKey, int batchSize, Duration sendTimeout) {
        public Settings {
            Objects.requireNonNull(sendTimeout, "sendTimeout");
            if (batchSize < 1) {
                throw new IllegalArgumentException("batchSize must be positive, got " + batchSize);
            }
        }
    }

    public OutboxRelay(
            JdbcClient jdbc,
            TransactionOperations transactions,
            KafkaOperations<String, Object> kafka,
            Clock clock,
            Settings settings) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.kafka = Objects.requireNonNull(kafka, "kafka");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Publishes up to one batch. Stops at the first message the broker does not acknowledge, so a later row of the
     * same key is never sent before it; the rows sent so far are marked.
     *
     * @return how many messages were published; 0 when another instance holds the lock
     */
    public int publishPending() {
        Integer published = transactions.execute(status -> {
            if (!lockAcquired()) {
                return 0;
            }
            var sent = sendInOrder(pending());
            if (!sent.isEmpty()) {
                jdbc.sql(MARK_SQL)
                        .param("now", Timestamp.from(clock.instant()))
                        .param("ids", sent)
                        .update();
            }
            return sent.size();
        });
        return published == null ? 0 : published;
    }

    private boolean lockAcquired() {
        return Boolean.TRUE.equals(jdbc.sql(LOCK_SQL)
                .param(settings.lockKey())
                .query(Boolean.class)
                .single());
    }

    private List<PendingMessage> pending() {
        return jdbc.sql(PENDING_SQL)
                .param(settings.batchSize())
                .query((row, number) -> new PendingMessage(
                        row.getLong("id"),
                        row.getObject("message_id", UUID.class),
                        row.getString("topic"),
                        row.getString("message_key"),
                        row.getBytes("payload"),
                        row.getString("payload_type")))
                .list();
    }

    private List<Long> sendInOrder(List<PendingMessage> messages) {
        var sent = new ArrayList<Long>();
        for (var message : messages) {
            if (!send(message)) {
                break;
            }
            sent.add(message.id());
        }
        return sent;
    }

    private boolean send(PendingMessage message) {
        var record = new ProducerRecord<String, Object>(
                message.topic(), message.key(), AvroBinary.decode(message.type(), message.payload()));
        record.headers()
                .add(OutboxHeaders.MESSAGE_ID, message.messageId().toString().getBytes(StandardCharsets.UTF_8));
        try {
            kafka.send(record).get(settings.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            // RuntimeException: the serializer fails before sending when Schema Registry is unreachable.
            log.warn("Outbox message {} not acknowledged; retrying on the next run", message.messageId(), exception);
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record PendingMessage(long id, UUID messageId, String topic, String key, byte[] payload, String type) {}
}
