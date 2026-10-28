package com.ledgerflow.messaging.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.messaging.MessagingDatabase;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InboxIT {

    private final Inbox inbox = new Inbox(MessagingDatabase.JDBC, Clock.systemUTC());

    @Test
    @DisplayName("AC4.2 a message is a first delivery once; its redelivery is recognised")
    void aMessageIsAFirstDeliveryOnlyOnce() {
        var messageId = UUID.randomUUID();

        var first = MessagingDatabase.TRANSACTIONS.execute(status -> inbox.firstDelivery(messageId));
        var redelivery = MessagingDatabase.TRANSACTIONS.execute(status -> inbox.firstDelivery(messageId));

        assertThat(first).isTrue();
        assertThat(redelivery).isFalse();
        assertThat(rowsOf(messageId)).isOne();
    }

    @Test
    @DisplayName("AC4.2 a message whose transaction rolled back is delivered again as new")
    void aRolledBackDeliveryIsNotRecorded() {
        var messageId = UUID.randomUUID();

        MessagingDatabase.TRANSACTIONS.executeWithoutResult(status -> {
            inbox.firstDelivery(messageId);
            status.setRollbackOnly();
        });
        var retry = MessagingDatabase.TRANSACTIONS.execute(status -> inbox.firstDelivery(messageId));

        assertThat(retry).isTrue();
        assertThat(rowsOf(messageId)).isOne();
    }

    @Test
    @DisplayName("B2 recording outside a transaction is rejected: it would not be atomic with the effect")
    void recordingOutsideATransactionIsRejected() {
        var messageId = UUID.randomUUID();

        assertThatThrownBy(() -> inbox.firstDelivery(messageId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inside the transaction");
        assertThat(rowsOf(messageId)).isZero();
    }

    private long rowsOf(UUID messageId) {
        return MessagingDatabase.JDBC
                .sql("select count(*) from processed_messages where message_id = ?")
                .param(messageId)
                .query(Long.class)
                .single();
    }
}
