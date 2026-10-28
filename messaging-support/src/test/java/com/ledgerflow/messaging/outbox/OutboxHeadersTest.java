package com.ledgerflow.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class OutboxHeadersTest {

    @Test
    void readsTheMessageIdHeader() {
        var messageId = UUID.randomUUID();
        var headers = new RecordHeaders();
        headers.add(OutboxHeaders.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));

        assertThat(OutboxHeaders.messageIdOf(headers)).isEqualTo(messageId);
    }

    @Test
    void aMessageWithoutTheHeaderCannotBeDeduplicated() {
        assertThatThrownBy(() -> OutboxHeaders.messageIdOf(new RecordHeaders()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Message has no message-id header");
    }

    @Test
    void aHeaderThatIsNotAUuidIsRejected() {
        var headers = new RecordHeaders();
        headers.add(OutboxHeaders.MESSAGE_ID, "not-a-uuid".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> OutboxHeaders.messageIdOf(headers)).isInstanceOf(IllegalArgumentException.class);
    }
}
