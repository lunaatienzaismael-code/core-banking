---
name: kafka-events
description: How a LedgerFlow service publishes and consumes Kafka messages (ADR 0013, saga per ADR 0012) - Avro contracts and compatibility, the transactional outbox, idempotent consumers with the inbox, topics and keys, retries and dead-letter topics, and messaging tests. Use when adding or changing a message type, a producer, a listener, retry/DLT settings or a messaging test.
---

# Kafka events and commands

Source of truth: ADR 0013 (outbox, inbox, contracts, retries), ADR 0012 (the
saga). Placement of classes: `java-hexagonal`; listener wiring:
`spring-boot-conventions`. Reference code: `ledger-service`
`adapter/in/messaging` and `adapter/out/messaging`, `messaging-support`.

## Contracts (`messaging-contracts`)

1. One `.avsc` per message type under `src/main/avro`, namespace
   `com.ledgerflow.messaging`; Java classes are generated, never hand-written.
2. **BACKWARD compatible only**: add fields with a default; never rename,
   retype or remove one. `SchemaCompatibilityTest` compares every schema with
   its version at the last release tag and fails the build otherwise; an
   incompatible change is a new message type.
3. Commands are orders to one service (`ReserveFunds`), replies and events
   state facts (`FundsReserved`). The ledger's messages carry no payment
   concept beyond `paymentId` (ADR 0012).
4. Money in a message is `long` minor units + ISO-4217 `string`, as in the
   API (ADR 0004).

## Topics and keys

1. **Key = `paymentId`** on every topic, so one payment's messages share a
   partition and an order. Several message types per topic, grouped by
   conversation; subjects use `TopicRecordNameStrategy`.
2. The service that **produces** a topic declares it as a `NewTopic` bean
   (`TopicBuilder`, 3 partitions); each service declares the `<topic>.DLT` of
   every topic it **consumes**, with the same partitions.
3. Topic names are constants in the adapter (`LedgerTopics`), never strings in
   use cases.

## Producing: the outbox, never `KafkaTemplate`

1. The use case calls its own outbound port (`TransferReplies`); an adapter in
   `adapter.out.messaging` builds the Avro record and calls
   `OutboxWriter.write(topic, key, record)` **inside the use case's
   transaction**. No use case sees Kafka; no adapter sends directly (that is a
   dual write, AC4.1).
2. The relay (`OutboxRelay`) publishes, one instance at a time per service
   (advisory lock, `ledgerflow.outbox.lock-key` unique per service). Delivery
   is at least once, so every consumer is idempotent.

## Consuming: idempotent, in one transaction

1. A `@KafkaListener` in `adapter.in.messaging` reads the `message-id` header
   (`OutboxHeaders.messageIdOf`), maps the record to a command with a static
   mapper and calls one input port; `switch` on the record type, `default`
   throws `IllegalArgumentException` (goes to the DLT, not retried).
2. The use case calls `Inbox.firstDelivery(messageId)` (behind its
   `MessageInbox` port) **in the same transaction** as its effect and its
   outgoing outbox rows; `false` means a duplicate: return without effect.
3. **Business refusals are replies, not exceptions**: commit the inbox row and
   a refusal reply (`ReservationRefused`), so nothing is retried or parked.
   When the refusal is detected after writes, roll the attempt back and record
   inbox + reply in a second transaction (`PaymentTransferService`).

## Retries and dead letters

1. One `CommonErrorHandler` bean per service from `DeadLetters.errorHandler`
   (bounded blocking retries with backoff, then `<topic>.DLT` on the same
   partition). Settings in `ledgerflow.kafka.retry.*`.
2. List the service's **not-retryable** exceptions (validation errors that
   retrying cannot fix); deserialisation failures and
   `IllegalArgumentException` are never retried. Technical failures
   (optimistic lock, database, broker) are retried.
3. Consumers read through `ErrorHandlingDeserializer`, so a poison payload
   reaches the handler and is parked as its original bytes.

## Tests

1. **Unit**: mappers, the saga's transitions, the error handler's
   classification (`KafkaErrorHandlingConfigurationTest`) — no broker.
2. **ITs** use `MessagingContainers` (Kafka + Schema Registry, one per JVM)
   and the service's single Spring context (see `TestcontainersConfiguration`:
   a second context splits the consumer group). Named tests for:
   - the message committed with the state change and not without it (AC4.1);
   - **the same record delivered twice has one effect** (AC4.2);
   - a poison record reaches `<topic>.DLT` after the retries (AC4.4).
3. Asynchronous outcomes are polled until a bounded deadline (the ITs'
   `await` helper; Awaitility in `e2e-tests`), never one fixed sleep before
   asserting. A broker outage is simulated with
   `MessagingContainers.pauseKafka()`/`unpauseKafka()`.
4. Cross-service behaviour belongs in `e2e-tests` (`scripts/e2e.sh`), through
   the HTTP APIs only.
