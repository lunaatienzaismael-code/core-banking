# ADR 0010: Idempotent Payment Initiation

- Status: Accepted
- Date: 2026-10-20
- Deciders: Project owner
- Extends: [ADR 0004](0004-persistence-and-money.md) (database per service),
  [ADR 0006](0006-api-and-error-model.md) (HTTP status mapping for UC3)

## Context

UC3 adds a second service, `payment-service`, whose first operation is
`POST /payments`: initiate a transfer. The caller is a trusted channel (spec
"Actors"), which retries on timeouts and connection errors. Without
protection, a retry after a lost response creates a second payment, and later
(Phase 7) moves the money twice.

- **AC3.1** — the same `Idempotency-Key` sent twice for the same transfer
  creates exactly one payment, and both responses are identical.
- **AC3.2** — a reused key with a *different* request body is rejected with
  Problem Details.

Two retries can also arrive at the same time (a client that times out quickly
and retries while the first request is still running), so the guarantee must
hold under concurrency, not only for sequential retries.

The service also needs its boundary fixed before its first line of code:
which database it owns, whether initiation talks to the ledger, and how it
represents money.

## Options Considered

### Where the key lives

- **Generic idempotency table storing the response** — a table keyed by the
  idempotency key, holding a request fingerprint and the saved status and
  body, consulted before the endpoint runs. This is Stripe's model for API v1:
  it "saves the status code and body of the first request made with a given
  idempotency key after endpoint execution begins" and replays it, "including
  `500` errors" [1].
  - Pros: works for any endpoint, including ones whose effect is not a single
    row; replays exactly what was sent, even for failures.
  - Cons: a second write per request that must be atomic with the business
    write (or it can record a response for work that rolled back, or miss
    one); needs an "in progress" state for concurrent retries; stores response
    bodies that duplicate the resource.
- **Key on the resource row** — the `payments` row carries the key (unique)
  and a fingerprint of the request; a replay reads the payment and renders it.
  - Pros: the key and the payment are one insert, so they commit or roll back
    together by construction; the unique constraint serialises concurrent
    retries; nothing duplicated.
  - Cons: only fits an operation that creates exactly one row; a replay renders
    the *current* payment, not a frozen copy of the first response (see
    Decision).
- **Key in a cache (e.g. Redis) with a TTL** — check-and-set before the work.
  - Pros: cheap; expiry built in.
  - Cons: a separate store that cannot share the database transaction, so a
    crash between the payment insert and the cache write breaks the guarantee
    — the dual-write problem Phase 7 avoids with an outbox. Adds a dependency
    the service does not need yet.

### Status code for a key reused with a different body

The IETF HTTPAPI working group's Internet-Draft *The Idempotency-Key HTTP
Header Field* (draft 07, 2025-10-15, expired; not an RFC) recommends, in
section 2.7 [2]:

- missing header on an operation that requires it → `400`;
- key reused "with a different request payload" → `422`;
- retry "while the original request is still being processed" → `409`.

The spec's AC3.2 originally said "conflict response". Options: `409` (the
literal reading), or `422` (the draft). `409` would then mean two different
things in the same API, and there is no "still processing" case in the chosen
design to give it.

## Decision

- **`Idempotency-Key` header, required on `POST /payments`**, 1–255
  characters (Stripe's limit [1]); clients are told to send a random UUID.
  Missing, empty or longer → `400` Problem Details.
- **Key on the payment row.** `payments.idempotency_key` (unique, named
  constraint) and `payments.request_hash` are written by the same `INSERT` that
  creates the payment, in the use case's database transaction.
- **Fingerprint of the canonical command, not of raw bytes.** `request_hash` =
  SHA-256 of the parsed request's fields in a fixed order (debtor account,
  creditor account, amount, currency), so field order and whitespace in the
  JSON do not make two equal requests differ. The draft lists request digests
  among the acceptable fingerprints (section 2.4) [2].
- **Concurrent duplicates without an "in progress" state.** The insert is
  `INSERT … ON CONFLICT (idempotency_key) DO NOTHING`. If no row was inserted,
  the use case reads the stored payment and compares fingerprints. In
  PostgreSQL a second insert with the same key waits for the first
  transaction to commit or roll back before it decides, so the loser always
  sees a committed payment (or inserts its own if the first rolled back).
- **Replay = the stored payment, same response.** Same key and same
  fingerprint → `201`, the same `Location`, and the payment rendered from the
  stored row (id, amounts and initiation time come from the row, never from
  the retry). While the payment has not changed, the body is identical
  (AC3.1). Once the saga moves it on (Phase 7), a replay shows the current
  status: the client retried the *initiation* and learns the payment's
  current state, which is more useful than a frozen copy. Status codes and
  `Location` never change.
- **Different fingerprint → `422` Problem Details**, following the draft; the
  stored payment is not touched. AC3.2's wording is amended to say `422`.
- **Rejected requests store no key.** A request that fails validation creates
  no payment, so nothing is stored; a retry is evaluated again and, being the
  same request, fails the same way. Stripe likewise does not save results when
  parameters fail validation [1].
- **No expiry.** A key lives as long as its payment. The draft lets a server
  define an expiration policy (section 2.3) [2] and Stripe prunes keys after
  24 hours [1]; with the key on the payment row there is nothing separate to
  purge. **Re-evaluation trigger:** a client that legitimately reuses keys
  across days, or a need to delete payments.

### `payment-service` boundary

- **Own database `payment-db`** (ADR 0004: one database per service). No
  access to the ledger's tables.
- **No synchronous call to the ledger at initiation.** Initiation validates
  the request's shape only: positive amount, ISO-4217 currency, debtor ≠
  creditor. Whether the accounts exist, share the currency and have funds is
  decided by the asynchronous saga (Phase 7), which moves the payment to
  `REJECTED` when they do not (ADR 0011). A synchronous check would couple
  initiation's availability to the ledger's and still be stale by the time
  funds are reserved.
- **Accounts referenced by ledger account id.** IBANs, and a directory that
  maps them to accounts, stay out of scope (spec "Out of scope": real bank
  connectivity).
- **Its own `Money` value object**, not a shared-kernel module with the
  ledger. Each service models money with ADR 0004's representation (`long`
  minor units + ISO-4217), and a shared library would couple the release of
  two services over a ~50-line class. **Re-evaluation trigger:** a third
  service needing the same type.

## Consequences

- AC3.1 and AC3.2 hold under concurrency with one constraint and one
  statement, and are testable with real threads against PostgreSQL
  (java-testing rule 8), as UC2 did.
- The pattern fits single-row creations only. An idempotent operation with
  several effects (e.g. an idempotent consumer in Phase 7) needs its own
  design — the outbox/inbox ADR of Phase 7.
- Replay bodies can differ from the first response after the payment's status
  changes; the `POST /payments` contract must say so when the header is added
  (T-022).
- Keys are never purged; the unique index grows with `payments`, which it
  indexes one-to-one.
- A changed fingerprint definition (e.g. a new request field) changes hashes;
  adding optional fields must keep old requests hashing the same (include a
  field only when present), or old keys would start returning `422`.

## References

1. Stripe API reference, *Idempotent requests* —
   <https://docs.stripe.com/api/idempotent_requests>
2. IETF HTTPAPI WG, *The Idempotency-Key HTTP Header Field*,
   draft-ietf-httpapi-idempotency-key-header-07 (2025-10-15), sections 2.3,
   2.4, 2.7 —
   <https://www.ietf.org/archive/id/draft-ietf-httpapi-idempotency-key-header-07.html>
