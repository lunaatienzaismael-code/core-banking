package com.ledgerflow.ledger.config;

import com.ledgerflow.messaging.inbox.Inbox;
import com.ledgerflow.messaging.outbox.OutboxRelay;
import com.ledgerflow.messaging.outbox.OutboxWriter;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** Wires the shared outbox and inbox (ADR 0013) into this service and runs the outbox relay on a schedule. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxConfiguration {

    @Bean
    OutboxWriter outboxWriter(JdbcClient jdbc, Clock clock) {
        return new OutboxWriter(jdbc, clock);
    }

    @Bean
    OutboxRelay outboxRelay(
            JdbcClient jdbc,
            TransactionTemplate transactions,
            KafkaTemplate<String, Object> kafka,
            Clock clock,
            OutboxProperties properties) {
        return new OutboxRelay(
                jdbc,
                transactions,
                kafka,
                clock,
                new OutboxRelay.Settings(properties.lockKey(), properties.batchSize(), properties.sendTimeout()));
    }

    @Bean
    Inbox inbox(JdbcClient jdbc, Clock clock) {
        return new Inbox(jdbc, clock);
    }

    /** The ledger owns its command and reply topics; three partitions, one payment's messages on one of them. */
    @Bean
    NewTopic ledgerCommandsTopic() {
        return TopicBuilder.name("ledger.commands").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic ledgerRepliesTopic() {
        return TopicBuilder.name("ledger.replies").partitions(3).replicas(1).build();
    }

    @Bean
    OutboxRelayJob outboxRelayJob(OutboxRelay relay) {
        return new OutboxRelayJob(relay);
    }

    /** Runs the relay; a run that finds nothing to publish costs one lock attempt and one indexed query. */
    static final class OutboxRelayJob {

        private final OutboxRelay relay;

        OutboxRelayJob(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${ledgerflow.outbox.relay-interval}")
        void run() {
            relay.publishPending();
        }
    }
}
