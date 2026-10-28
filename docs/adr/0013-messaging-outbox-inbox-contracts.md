# ADR 0013: Messaging — Outbox, Idempotent Consumers and Contracts

- Status: Accepted
- Date: 2026-10-27
- Deciders: Project owner
- Extends: [ADR 0001](0001-stack-and-versions.md) (adds Kafka and Schema
  Registry to the stack), [ADR 0004](0004-persistence-and-money.md)
  (database per service)
- Related: [ADR 0012](0012-payment-saga-and-fund-reservation.md) (the saga
  these messages carry)

## Context

ADR 0012 makes the payment a saga of local transactions in two services that
talk through Kafka. Each step changes a database and sends a message; each
service receives messages that Kafka may deliver more than once.

- **AC4.1** — the domain state change and the outgoing event are committed in
  the same database transaction; no dual-write.
- **AC4.2** — redelivery of the same event produces no duplicate effect.
- **AC4.4** — poison messages end up on a dead-letter topic after bounded
  retries.

The roadmap also asks for Avro contracts with BACKWARD compatibility checked
in CI. This ADR fixes how messages leave a service, how they are consumed,
how they are named, keyed and versioned, and what happens when one keeps
failing.

## Options Considered

### Getting a message out with the state change

- **Dual-write** — commit the database transaction, then send to Kafka (or the
  reverse).
  - Pros: nothing to build.
  - Cons: a crash or a Kafka outage between the two leaves state without its
    message, or a message without its state. AC4.1 rules it out.
- **Transactional outbox with a polling relay** — the use case inserts the
  message into an `outbox_messages` table in its own transaction; a relay
  reads unpublished rows, sends them and marks them published [1][2].
  - Pros: state and message commit together by construction; only PostgreSQL
    and Kafka involved; easy to test with Testcontainers.
  - Cons: polling adds latency (the polling interval) and load; delivery is
    at least once (a crash after sending and before marking resends), so
    consumers must be idempotent anyway.
- **Transactional outbox with CDC (Debezium)** — Debezium reads the outbox
  inserts from PostgreSQL's write-ahead log through Kafka Connect and routes
  them to topics [3].
  - Pros: no polling; low latency; the database is not queried by a relay.
  - Cons: Kafka Connect, a connector and a replication slot to run locally, in
    CI and in tests; still at least once.

### Consuming exactly once in effect

- **Kafka transactions / exactly-once semantics** — cover Kafka-to-Kafka
  processing; they do not make a PostgreSQL write and a Kafka offset commit
  atomic.
- **Idempotent consumer with an inbox table** — the consumer records the
  message id in a `processed_messages` table in the same database transaction
  as its effect; a redelivered id is skipped [4].
- **Natural idempotency only** — rely on each effect being idempotent (e.g.
  the transition table rejecting a repeated move).
  - Cons: true for status changes, not for "record a ledger transaction",
    which would post twice.

### Retries

- **Blocking retries, then dead-letter** — the listener container retries the
  record in place with backoff, then publishes it to a dead-letter topic and
  moves on (Spring Kafka `DefaultErrorHandler` with a
  `DeadLetterPublishingRecoverer` [5]).
  - Pros: records of one partition stay in order; simple to reason about.
  - Cons: the partition waits during the retries.
- **Non-blocking retry topics** — failed records go to retry topics with
  delays [5].
  - Pros: the main partition keeps flowing.
  - Cons: a retried message can be overtaken by later messages for the same
    key, which breaks per-payment ordering the saga relies on.

## Decision

### Outbox with a polling relay

- **One `outbox_messages` table per service database**: `id` (identity,
  ordering), `message_id` (UUID), `topic`, `message_key`, `payload` (Avro
  binary), `payload_type` (Avro full name), `created_at`, `published_at`
  (null until sent). Rows are marked, not deleted; retention is a later
  concern.
- **Written by an outbound adapter.** Use cases depend on their own outbound
  ports (e.g. a payment event publisher); the adapter writes the outbox row in
  the use case's transaction (ADR 0002). No use case sees Kafka.
- **One publisher at a time per service.** The relay runs on a schedule; each
  run takes a PostgreSQL transaction-level advisory lock
  (`pg_try_advisory_xact_lock`) [6] and skips the run if another instance
  holds it, then reads unpublished rows in `id` order, sends each one and
  waits for the broker's acknowledgement, and marks them published. A single
  publisher keeps the order of rows for one key, which several instances
  splitting a batch (`FOR UPDATE SKIP LOCKED`) would not.
- **Order per key holds.** Rows for one payment are written by transactions
  that are serialised: in `payment-service` by the payment's optimistic lock;
  in `ledger-service` because each reply answers a command of that payment,
  consumed in order from one partition by one consumer. So their `id` order
  is their commit order. Rows of different payments may commit out of `id` order; the
  relay selects by `published_at is null`, never by a last-published id, so a
  late commit is not skipped.
- **Serialisation outside the database transaction.** The payload is the
  record's Avro binary; the relay rebuilds the record and the Kafka
  serializer registers or looks up the schema, so no Schema Registry call
  happens inside a business transaction.
- **At least once.** A relay crash between sending and marking resends the
  row; consumers absorb it (below).

### Idempotent consumers with an inbox

- Every message carries its `message_id` (from the outbox row) in a Kafka
  header. A consumer inserts it into `processed_messages` (`message_id`
  primary key, `processed_at`) with `insert … on conflict do nothing` in the
  same transaction as its effect and its outgoing outbox rows; if nothing was
  inserted, the message is a duplicate and is acknowledged without effect.
- The Kafka offset is committed after the database transaction. A crash in
  between redelivers the record, and the inbox turns it into a no-op.
- Business refusals are not errors: the consumer commits the inbox row and a
  refusal reply (ADR 0012).

### Shared `messaging-support` module

The outbox writer, the relay and the inbox are the same in every service, so
they live in a small library module, `messaging-support`, as the ArchUnit
rules live in `architecture-rules`. Services own their tables (a changeset in
each service) and their ports; adapters call the library. ADR 0010 kept
`Money` per service because sharing a domain type couples domains; this
module is infrastructure with no domain content.
**Re-evaluation trigger:** a service whose needs make the module grow
service-specific options.

### Topics, messages and keys

| Topic | Producer | Messages |
|-------|----------|----------|
| `payments.events` | payment-service | `PaymentInitiated` |
| `ledger.commands` | payment-service | `ReserveFunds`, `PostTransfer`, `ReleaseFunds` |
| `ledger.replies` | ledger-service | `FundsReserved`, `ReservationRefused`, `TransferPosted`, `PostingRefused`, `FundsReleased` |

- **Key = `paymentId`** on every topic. Kafka orders records within a
  partition and the default partitioner sends one key to one partition [7],
  so all messages of one payment are consumed in order.
- **Commands vs events.** Commands are addressed to one service and named as
  orders; replies and events state facts. The ledger's commands and replies
  carry no payment concept beyond the `paymentId` (ADR 0012).
- **Several message types per topic**, grouped by conversation so one
  payment's messages share a partition and an order [8].

### Avro contracts

- Schemas (`.avsc`) live in a `messaging-contracts` module; Java classes are
  generated at build time; namespace `com.ledgerflow.messaging`.
- **Confluent Schema Registry** with the `TopicRecordNameStrategy` subject
  naming, which registers each message type per topic, as several types share
  a topic [9].
- **Compatibility BACKWARD** (the registry's default [10]): a consumer using a
  new schema can read data written with the previous one, so consumers are
  upgraded before producers. CI checks it without a registry: a test compares
  each schema with its version at the last release tag using Avro's
  schema-compatibility check and fails the build on an incompatible change.
  A schema with no version at that tag (a new message type, or every schema
  until the first release that ships them) has nothing to break and passes.

### Retries and dead-letter topics

- **Blocking retries with backoff, then a dead-letter topic** named
  `<topic>.DLT`, on the record's partition, keeping the original key and adding
  Spring Kafka's failure headers. The handler is built once in
  `messaging-support` (`DeadLetters`) and each service declares its DLTs with
  the partitions of their topics.
- **Retry settings** (`ledgerflow.kafka.retry.*`, per service): 3 retries after
  the first attempt, waiting 500 ms, then ×2 up to 5 s — about 3.5 s of
  retries before a record is parked, during which its partition waits.
- **Not retried:** deserialisation and validation failures (retrying cannot
  fix the bytes) go straight to the dead-letter topic. Spring Kafka already
  treats deserialisation failures as not retryable [5]; consumers read through
  Spring Kafka's `ErrorHandlingDeserializer`, so an unreadable payload reaches
  the handler (and the DLT as its original bytes) instead of stopping the
  consumer. Also not retried: `IllegalArgumentException` (a missing
  `message-id`, an id that is not a UUID, an unsupported message type) and
  each service's validation errors — the ledger's invalid amount or currency,
  and in payment-service a reply for a payment it does not have.
- **Retried:** optimistic-lock conflicts, database and broker errors, and a
  ledger release that fails (ADR 0012: never a refusal).

### Infrastructure

- Kafka in KRaft mode (no ZooKeeper) and Confluent Schema Registry in
  `infra/docker-compose.yml` and in Testcontainers, with the same images:
  `apache/kafka:4.2.1` (the Apache Kafka line whose `kafka-clients` Spring
  Boot 4.1 manages) and `confluentinc/cp-schema-registry:8.2.4`; Confluent
  serializers `8.2.4` and Avro `1.12.1` are pinned in the parent POM.
  `spring-kafka` and the Testcontainers Kafka module are managed by Spring
  Boot.

## Consequences

- AC4.1 holds by construction (one insert in the business transaction) and is
  testable by rolling the transaction back and by stopping Kafka.
- AC4.2 holds for every consumer through one mechanism, tested by delivering
  the same record twice.
- AC4.4 is configuration plus a poison-message IT per service.
- Latency of each saga step includes up to one relay interval; acceptable for
  an asynchronous transfer, tunable by configuration.
- A single publisher per service caps outbox throughput.
  **Re-evaluation trigger:** relay lag under load — then partition the outbox
  by key hash with one lock per bucket, or move to CDC [3].
- Both inbox and outbox tables grow without bound until a retention task
  exists.
- A schema change that is not backward compatible fails CI and needs a new
  message type instead.

## References

1. C. Richardson, *Pattern: Transactional outbox* —
   <https://microservices.io/patterns/data/transactional-outbox.html>
2. C. Richardson, *Pattern: Polling publisher* —
   <https://microservices.io/patterns/data/polling-publisher.html>
3. Debezium, *Outbox Event Router* —
   <https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html>
4. C. Richardson, *Pattern: Idempotent Consumer* —
   <https://microservices.io/patterns/communication-style/idempotent-consumer.html>
5. Spring for Apache Kafka, *Handling Exceptions* (`DefaultErrorHandler`,
   `DeadLetterPublishingRecoverer`, non-blocking retries) —
   <https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html>
6. PostgreSQL, *Advisory Lock Functions* —
   <https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS>
7. Apache Kafka documentation, *Main Concepts and Terminology* —
   <https://kafka.apache.org/documentation/#intro_concepts_and_terms>
8. M. Kleppmann, *Should You Put Several Event Types in the Same Kafka Topic?*
   — <https://www.confluent.io/blog/put-several-event-types-kafka-topic/>
9. Confluent, *Schema Registry: Subject name strategy* —
   <https://docs.confluent.io/platform/current/schema-registry/fundamentals/serdes-develop/index.html#subject-name-strategy>
10. Confluent, *Schema Evolution and Compatibility* —
    <https://docs.confluent.io/platform/current/schema-registry/fundamentals/schema-evolution.html>
