# ADR 0004: Persistence and Money Representation

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner

> **2026-10-18 — Superseded in part by [ADR 0009](0009-materialised-balance.md).**
> The balance is now materialised as per-account `debits_posted` /
> `credits_posted` counters updated by atomic conditional SQL, and `@Version`
> applies only to account attribute changes, not to balance writes. The
> "Concurrency on postings" option analysis and the optimistic-locking and
> derived-balance bullets of the Decision below are kept as originally
> written; ADR 0009 replaces them. Money representation and database per
> service are unchanged.

## Context

Two decisions with legal and correctness weight in a ledger: how money is
represented in memory and storage, and how each service owns its data. Getting
money representation wrong (floating point) is a classic, disqualifying defect
in a banking portfolio.

## Options Considered

### Money representation
- **`double` / `float`** — rejected outright: binary floating point cannot
  represent decimal currency amounts exactly; forbidden.
- **`BigDecimal`** — exact, but invites inconsistent scale/rounding handling and
  is heavier. Reserved for display/FX conversion if ever needed, not core state.
- **Minor units as `long` + ISO-4217 currency code** — amounts stored and
  computed as integer minor units (e.g. cents); currency carried alongside.
  Exact, simple, cheap, and the standard approach in payment cores.

### Data ownership
- **Shared database** — rejected: couples services, blocks independent
  evolution and the later event-driven split.
- **Database per service** — each service owns its schema; no cross-service
  SQL. Prepares the Phase 7 event-driven architecture.

### Concurrency on postings
- **Pessimistic locking** — simpler to reason about, but holds DB locks and
  scales worse under contention.
- **Optimistic locking (`@Version`)** — detects conflicting concurrent writes
  and retries; fits a high-read ledger and is validated by a concurrency test
  in Phase 5.

## Decision

- Money is represented as a **`Money` value object = amount (`long`, minor
  units) + currency (ISO-4217 code)**. No `double`/`float` anywhere. Arithmetic
  is integer; operations across different currencies are rejected in the domain.
- **PostgreSQL** per service, schema migrations managed by **Liquibase**
  (changelog versioned in the repo). Amounts stored as `BIGINT` minor units,
  currency as a fixed 3-char code.
- **One database per service.** No cross-service database access.
- **Optimistic locking** (`@Version`) for account/posting writes; conflicts
  surface as retryable errors.
- **Balance is derived from postings** (sum of debits − credits per account,
  per currency) from the very first vertical. Whether to additionally
  **materialise** a precomputed balance is deferred to a dedicated Phase 5 ADR;
  postings remain the source of truth either way.

## Consequences

- No floating-point rounding risk; amounts are exact by construction.
- Integration tests must run against real PostgreSQL (see ADR 0003), since
  locking and SQL semantics matter.
- Database-per-service means later cross-service consistency is handled by
  events/saga (Phase 7), not by distributed transactions.
- The `persistence-postgres-liquibase` skill (created in Phase 4) will encode
  the concrete adapter conventions once the first adapter exists.
