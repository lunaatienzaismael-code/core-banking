package com.ledgerflow.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.messaging.AssessRisk;
import com.ledgerflow.messaging.RiskApproved;
import com.ledgerflow.messaging.RiskRejected;
import com.ledgerflow.messaging.RiskRejectionReason;
import com.ledgerflow.messaging.outbox.OutboxHeaders;
import com.ledgerflow.messaging.testing.MessagingContainers;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * AssessRisk over real Kafka against real PostgreSQL (ADR 0013, ADR 0014). The limit is 100 000 minor units, set in
 * {@link TestcontainersConfiguration}. All data is fictitious.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AssessRiskIT {

    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final String DEBTOR = "22222222-2222-2222-2222-222222222222";

    @Autowired
    private KafkaTemplate<String, Object> kafka;

    @Test
    @DisplayName("AC5.1 a payment above the threshold is answered with RiskRejected")
    void paymentAboveTheThresholdIsRejected() throws Exception {
        var paymentId = UUID.randomUUID().toString();

        send(paymentId, UUID.randomUUID(), 100_001);

        var reply = (RiskRejected) repliesFor(paymentId, 1).getFirst();
        assertThat(reply.getReason()).isEqualTo(RiskRejectionReason.AMOUNT_LIMIT_EXCEEDED);
        assertThat(reply.getDetail()).isNotBlank();
    }

    @Test
    @DisplayName("AC5.1 a payment at the threshold is answered with RiskApproved")
    void paymentAtTheThresholdIsApproved() throws Exception {
        var paymentId = UUID.randomUUID().toString();

        send(paymentId, UUID.randomUUID(), 100_000);

        assertThat(repliesFor(paymentId, 1).getFirst()).isInstanceOf(RiskApproved.class);
    }

    @Test
    @DisplayName("B1 the same message delivered twice produces exactly one reply")
    void redeliveredMessageIsAnsweredOnce() throws Exception {
        var paymentId = UUID.randomUUID().toString();
        var messageId = UUID.randomUUID();

        send(paymentId, messageId, 100_000);
        send(paymentId, messageId, 100_000);

        assertThat(repliesFor(paymentId, 1)).hasSize(1);
    }

    private void send(String paymentId, UUID messageId, long amount) throws Exception {
        var command = AssessRisk.newBuilder()
                .setPaymentId(paymentId)
                .setDebtorAccountId(DEBTOR)
                .setAmountMinorUnits(amount)
                .setCurrency("EUR")
                .build();
        var record = new ProducerRecord<String, Object>("risk.commands", paymentId, command);
        record.headers().add(OutboxHeaders.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    /** Waits for {@code expected} replies keyed by the payment, then checks no more arrive shortly after. */
    private static List<Object> repliesFor(String paymentId, int expected) {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "assess-risk-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                KafkaAvroDeserializer.class,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                MessagingContainers.schemaRegistryUrl(),
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG,
                true);
        try (var consumer = new KafkaConsumer<String, Object>(config)) {
            consumer.subscribe(List.of("risk.replies"));
            var replies = new ArrayList<Object>();
            var deadline = Instant.now().plus(WAIT);
            while (replies.size() < expected && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (paymentId.equals(record.key())) {
                        replies.add(record.value());
                    }
                });
            }
            consumer.poll(Duration.ofSeconds(1)).forEach(record -> {
                if (paymentId.equals(record.key())) {
                    replies.add(record.value());
                }
            });
            assertThat(replies).as("replies for payment %s", paymentId).hasSize(expected);
            return replies;
        }
    }
}
