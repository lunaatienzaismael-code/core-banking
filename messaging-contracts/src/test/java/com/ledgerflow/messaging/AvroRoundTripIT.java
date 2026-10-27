package com.ledgerflow.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.messaging.testing.MessagingContainers;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvroRoundTripIT {

    private static final String TOPIC = "payments.events";

    @Test
    @DisplayName("B4 an Avro message round-trips through Kafka and Schema Registry")
    void avroMessageRoundTripsThroughKafkaAndSchemaRegistry() throws Exception {
        var paymentId = UUID.randomUUID().toString();
        var sent = PaymentInitiated.newBuilder()
                .setPaymentId(paymentId)
                .setDebtorAccountId(UUID.randomUUID().toString())
                .setCreditorAccountId(UUID.randomUUID().toString())
                .setAmountMinorUnits(12_345L)
                .setCurrency("EUR")
                .setInitiatedAt(Instant.parse("2026-10-27T10:15:30.123Z"))
                .build();

        try (var producer = new KafkaProducer<String, Object>(producerConfig())) {
            producer.send(new ProducerRecord<>(TOPIC, paymentId, sent)).get();
        }
        var received = consumeOne();

        assertThat(received.key()).isEqualTo(paymentId);
        assertThat(received.value()).isInstanceOf(PaymentInitiated.class).isEqualTo(sent);
        assertThat(registeredSubjects()).contains("\"" + TOPIC + "-com.ledgerflow.messaging.PaymentInitiated\"");
    }

    private static Map<String, Object> producerConfig() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, MessagingContainers.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, MessagingContainers.schemaRegistryUrl(),
                KafkaAvroSerializerConfig.VALUE_SUBJECT_NAME_STRATEGY, TopicRecordNameStrategy.class.getName());
    }

    private static ConsumerRecord<String, Object> consumeOne() {
        var config = Map.<String, Object>of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                MessagingContainers.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "round-trip-" + UUID.randomUUID(),
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
            consumer.subscribe(List.of(TOPIC));
            var records = new ArrayList<ConsumerRecord<String, Object>>();
            var deadline = Instant.now().plus(Duration.ofSeconds(30));
            while (records.isEmpty() && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
            assertThat(records).hasSize(1);
            return records.getFirst();
        }
    }

    private static String registeredSubjects() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(MessagingContainers.schemaRegistryUrl() + "/subjects"))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
        }
    }
}
