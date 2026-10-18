# ADR 0009: Materialised Balance with Atomic Conditional Updates

- Status: Accepted
- Date: 2026-10-18
- Deciders: Project owner
- Supersedes in part: [ADR 0004](0004-persistence-and-money.md) (derived
  balance; optimistic locking for balance writes)
- Extends: [ADR 0006](0006-api-and-error-model.md) (HTTP status mapping for
  UC2 rejections)

## Context

ADR 0004 deferred whether to store a precomputed balance. UC2 (record a
double-entry transaction) forces the decision:

- **Overdraft check on every write.** AC2.3 rejects a transaction that would
  drive a `NOT_ALLOWED` account's normal-side balance below zero. The check
  must see the balance *including* every concurrently committed transaction,
  or two withdrawals that each fit the balance can both pass (AC2.5).
- **Cost of summing history.** With a derived balance, each check sums all
  postings of the account. The cost grows with the account's history, on the
  hot write path.
- **Hot internal accounts.** Settlement and suspense accounts (`ALLOWED`) take
  part in a large share of transactions. Whatever concurrency control is
  chosen must not turn them into a retry or deadlock hotspot.

Postings are an immutable, append-only journal (inserted, never updated), so
they need no concurrency control of their own. The contention is entirely
about the balance check.

## Options Considered

### Where the balance lives

- **Derived** — balance = Σ postings, computed on each read and check.
  - Pros: no second copy of the truth, so nothing can drift; already built in
    UC1.
  - Cons: O(history) per check on the write path; and it does **not** remove
    the concurrency problem: "sum, then insert" is a check-then-act race, so
    concurrent writers still have to be serialised on something (an account
    row lock or `SERIALIZABLE`).
- **Materialised** — the account row stores running totals, updated in the
  same database transaction as the postings.
  - Pros: O(1) read and check; the account row is the natural lock target for
    the check, so storage and concurrency control fit together.
  - Cons: a second copy that must equal Σ postings; needs a tested invariant
    and a reconciliation (UC6).
- **Snapshot + delta** — periodic balance snapshots plus the sum of postings
  since the last one.
  - Pros: bounded read cost without updating the account on every write.
  - Cons: the overdraft check still needs a consistent, serialised view, so
    the locking problem is unchanged; adds a snapshot job and its edge cases.
    It suits reporting (statements, end-of-day balances), not the write path.

Most core banking and payment ledgers keep a stored balance next to an
immutable entry journal. TigerBeetle, a purpose-built financial ledger, stores
exactly `debits_posted` / `credits_posted` per account and enforces "debits
must not exceed credits" (and vice versa) as an account flag at write time.

### How concurrent writers are serialised

- **Optimistic locking (`@Version`) + retry** — read the account, compute in
  Java, write with a version check, retry on conflict.
  - Pros: no locks held while the application thinks; good when conflicts are
    rare.
  - Cons: a read-modify-write in Java; on a hot account most attempts
    conflict, so throughput collapses into retry storms; retries need bounds,
    backoff and idempotent use cases.
- **Pessimistic `SELECT … FOR UPDATE`** — lock the account row, read, check in
  Java, then update.
  - Pros: easy to reason about; no retries.
  - Cons: an extra round trip; the lock is held across application code;
    multi-account transactions must lock in a fixed order to avoid deadlocks.
- **Atomic conditional `UPDATE`** — one statement adds the amount and checks
  the rule in its `WHERE` clause; zero rows updated means rejected.
  - Pros: no read-modify-write in Java, no retries; the row lock is taken and
    the rule checked in the same statement. Under PostgreSQL `READ COMMITTED`,
    a writer that waited on the row lock re-evaluates the `WHERE` clause
    against the newly committed row version, so the check always sees the
    latest balance.
  - Cons: the business rule is expressed in SQL as well as in the domain; the
    row lock is held until commit; multi-account transactions still need a
    fixed update order.
- **`SERIALIZABLE` isolation** — let PostgreSQL detect conflicting
  transactions.
  - Pros: correct without explicit locking design.
  - Cons: conflicts abort with serialization failures that must be retried,
    so it has the same retry-storm problem as optimistic locking on hot
    accounts, plus predicate-locking overhead on every transaction.

## Decision

- **Materialised counters.** `accounts` gets `debits_posted` and
  `credits_posted`: `bigint`, minor units (ADR 0004), non-negative, each a
  running total of the account's debit and credit postings. Two monotonic
  counters, not one signed balance: they keep debit and credit turnover
  (useful for statements and reconciliation), and each one only grows.
  Normal-side balance = `debits_posted − credits_posted` for debit-normal
  types, `credits_posted − debits_posted` for credit-normal types.
- **Same transaction as the postings.** Counters are updated in the database
  transaction that inserts the postings (the use case boundary, ADR 0002),
  with `SET debits_posted = debits_posted + :debit, credits_posted =
  credits_posted + :credit`. No value is read into Java and written back.
- **Overdraft guard in the `WHERE`.** For a `NOT_ALLOWED` account the same
  statement also requires the resulting normal-side balance to be ≥ 0:

  ```sql
  UPDATE accounts
     SET debits_posted  = debits_posted  + :debit,
         credits_posted = credits_posted + :credit
   WHERE id = :id
     AND (overdraft_policy = 'ALLOWED'
          OR (type IN ('ASSET', 'EXPENSE')
              AND debits_posted + :debit - (credits_posted + :credit) >= 0)
          OR (type IN ('LIABILITY', 'EQUITY', 'INCOME')
              AND credits_posted + :credit - (debits_posted + :debit) >= 0))
  ```

  The use case has already loaded the accounts in the same transaction
  (unknown account and currency checks happen first), so zero rows updated
  means **insufficient funds**. The use case raises the domain exception and
  the whole transaction, postings included, rolls back.
- **`CHECK` as defence in depth.** A table `CHECK` constraint enforces the
  same rule (a `NOT_ALLOWED` account's normal-side balance is ≥ 0), so a
  faulty code path that bypasses the guarded statement fails at the database.
- **Fixed update order.** A transaction updates its accounts in ascending id
  order, so two transactions over the same accounts cannot deadlock.
- **Postings remain the source of truth.** The invariant *counters = Σ
  postings per account* is covered by a named test, and UC6 reconciles it in
  production.
- **`@Version` only for account attributes.** Optimistic locking stays for
  changes to account attributes (e.g. overdraft policy). Counters are not part
  of the JPA entity's write set, so saving an account never overwrites them,
  and counter updates do not bump the version.
- **HTTP mapping for UC2 rejections** (extends ADR 0006): unbalanced
  transaction, mixed currencies and insufficient funds → `422 Unprocessable
  Content` Problem Details; malformed input (schema or bean-validation
  failures) → `400`.

### Why hot internal accounts do not conflict

Settlement and suspense accounts are `ALLOWED`, so their `WHERE` condition is
always true: the statement never returns zero rows and nothing is retried.
Concurrent transactions only queue on the account's row lock for the rest of
their database transaction, which the use case keeps short (no remote calls
inside it).

## Consequences

- Balance reads and overdraft checks are O(1) and correct under concurrency
  without retry logic; AC2.5's outcome (no lost update, final balance = exact
  sum of accepted transactions) follows from the row lock plus the
  re-evaluated `WHERE`.
- The overdraft rule exists in the domain (`OverdraftPolicy`, used for
  validation and tests) and in SQL (the guard and the `CHECK`). Tests at the
  persistence boundary must prove the SQL version.
- The counters are a second copy of the truth, so the counter invariant test
  and the UC6 reconciliation are mandatory, not optional.
- Throughput on a single account is bounded by how long its row lock is held.
  **Re-evaluation trigger:** measured contention on a single constrained
  account. Known options then: splitting a hot account into sub-accounts, or
  batching postings per account.
- `bigint` counter overflow raises a PostgreSQL error; at 2⁶³ − 1 minor units
  no special handling is needed.
- Reserved or pending amounts (e.g. `debits_pending` for holds) are out of
  scope; if UC3 needs them, they follow the same pattern.
