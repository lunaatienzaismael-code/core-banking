---
name: persistence-postgres-liquibase
description: How a LedgerFlow service persists data in PostgreSQL - Liquibase changesets and constraint naming, JPA entities and adapters, JdbcClient read models, money in SQL, persistence tests. Use when adding or changing a changeset, a JPA entity, a persistence adapter or a persistence integration test.
---

# PostgreSQL + Liquibase persistence

Source of truth: ADR 0002 (adapter placement), ADR 0003 (Testcontainers), ADR
0004 (money), ADR 0009 (materialised balance). Placement of classes: `java-hexagonal`. This
skill covers the schema and the persistence adapter.

## Liquibase

1. **One changeset per file** under `db/changelog/changes/`, named
   `NNNN-<verb>-<what>.yaml`, included in order from
   `db.changelog-master.yaml`. Applied changesets are **never edited**; a
   change is a new file.
2. **Name every constraint** (Liquibase/PostgreSQL defaults are unreadable in
   errors and tests): `pk_<table>`, `fk_<table>_<what>`, `uq_<table>_<cols>`,
   `ck_<table>_<column or rule>`, `ix_<table>_<cols>`.
3. **Enum columns** are `varchar` with a `CHECK (col IN (...))` listing the
   domain enum's names.
4. **Currency** is `char(3)` with `CHECK (currency ~ '^[A-Z]{3}$')`; amounts
   are `bigint` minor units, never `numeric`/`float` columns.
5. **Balances are posting counters** (ADR 0009): `accounts.debits_posted` /
   `credits_posted`, non-negative `bigint`, changed only in the same
   transaction as the postings they count and always equal to their sums. No
   other stored balance; postings stay the source of truth.
6. Integrity the database can guarantee, it guarantees (e.g. a composite FK
   keeps each posting in its account's currency; `amount <> 0`).

## JPA

1. Entities, Spring Data repositories, adapters and mappers are
   **package-private** in `adapter.out.persistence`; only the outbound port is
   public.
2. Aggregate entities carry `@Version` from the first changeset
   (`AccountEntity`, column `version`) for attribute changes only. Columns
   written by conditional SQL (the posting counters, ADR 0009) are **not
   mapped** in the entity, so a JPA save can never overwrite them.
3. `char(n)` columns need `@JdbcTypeCode(SqlTypes.CHAR)`, or schema validation
   fails.
4. `spring.jpa.hibernate.ddl-auto: validate` and `open-in-view: false`:
   Liquibase owns the schema; Hibernate only checks the mappings.
5. Explicit static mappers both ways (`toEntity`, `toDomain`); entities never
   leave the adapter.

## JPA or JdbcClient

- **JPA** for aggregates that are written and loaded whole (`Account`).
- **`JdbcClient`** for queries over many rows, read models and counter
  columns (balance from counters, movements as a join). Plain SQL in a constant; map rows to domain types
  in the adapter.

## Money in SQL

- Counter columns are `bigint`: read them as `long`, combine with
  `Math.subtractExact`/`addExact`; an addition past the `bigint` range in SQL
  raises `bigint out of range`, so overflow fails loudly on both sides
  (`BalancePersistenceAdapter`, `BalancePersistenceAdapterIT`).
- Ad-hoc aggregates (backfills, invariant checks in tests): `sum(bigint)`
  returns `numeric`; read it as `BigDecimal` with `longValueExact()` and use
  `coalesce(sum(...), 0)` for accounts without rows.

## Changesets that move data

A changeset that adds derived columns backfills them in the same changeset,
and an IT proves it: run the real changelog in an isolated schema with
`updateCount` up to the previous changeset, insert rows, run the rest, assert
(`AccountCountersBackfillIT`). Use a connection outside the pool so
`search_path` cannot leak.

## Tests

- `*AdapterIT` against Testcontainers PostgreSQL only (ADR 0003): never H2.
- Schema-constraint tests insert a bad row with `JdbcTemplate` and assert the
  **constraint name** in the error (`hasMessageContaining("ck_accounts_type")`).
- Rows with no write path yet (postings before UC2) come from a `JdbcTemplate`
  fixture, inserting balanced transactions only, together with the account
  counters they move.

## Reference examples

- Master changelog: `ledger-service/src/main/resources/db/changelog/db.changelog-master.yaml`
- Changesets: `ledger-service/src/main/resources/db/changelog/changes/0001-create-accounts.yaml` (CHECKs), `ledger-service/src/main/resources/db/changelog/changes/0003-add-account-posting-counters.yaml` (counters + backfill), `ledger-service/src/main/resources/db/changelog/changes/0002-create-transactions-and-postings.yaml` (composite FK, index)
- Config: `ledger-service/src/main/resources/application.yml` (`validate`, `open-in-view`)
- Entity: `ledger-service/src/main/java/com/ledgerflow/ledger/adapter/out/persistence/AccountEntity.java` — `@Version`, `@JdbcTypeCode`
- JPA adapter + mapper: `ledger-service/src/main/java/com/ledgerflow/ledger/adapter/out/persistence/AccountPersistenceAdapter.java`, `ledger-service/src/main/java/com/ledgerflow/ledger/adapter/out/persistence/AccountPersistenceMapper.java`
- `JdbcClient` adapters: `ledger-service/src/main/java/com/ledgerflow/ledger/adapter/out/persistence/PostingPersistenceAdapter.java` (join, ordering), `ledger-service/src/main/java/com/ledgerflow/ledger/adapter/out/persistence/BalancePersistenceAdapter.java` (counters, `Math.subtractExact`)
- ITs: `ledger-service/src/test/java/com/ledgerflow/ledger/adapter/out/persistence/AccountPersistenceAdapterIT.java`, `ledger-service/src/test/java/com/ledgerflow/ledger/adapter/out/persistence/PostingPersistenceAdapterIT.java`, `ledger-service/src/test/java/com/ledgerflow/ledger/adapter/out/persistence/BalancePersistenceAdapterIT.java` — constraint names asserted; `AccountCountersBackfillIT` — backfill
- Fixture: `ledger-service/src/test/java/com/ledgerflow/ledger/LedgerFixture.java`
