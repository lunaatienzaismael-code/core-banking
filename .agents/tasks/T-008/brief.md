# T-008 — Skill persistence-postgres-liquibase

- Use case: none (Phase 4 conventions) · Phase: 4 · Type: chore
- Issue: #17 · Branch: `chore/T-008-persistence-skill`
- Depends on: T-006

## Goal

A project skill captures how LedgerFlow persists data, extracted from the UC1
adapters and changesets, so UC2+ follow it without rediscovering it.

## Acceptance criteria

- **B1** — `.claude/skills/persistence-postgres-liquibase/SKILL.md` (< ~100
  lines, frontmatter with a description that says when to use it) covers:
  - Liquibase: one changeset per file under `changes/`, included in order,
    applied changesets never edited; constraint names `pk_`, `fk_`, `uq_`,
    `ck_`, `ix_`; CHECK constraints for enum columns; `char(3)` currency with
    an ISO pattern check; no stored balances (ADR 0004).
  - JPA: entities package-private in the adapter, `@Version` for optimistic
    locking, `@JdbcTypeCode(SqlTypes.CHAR)` for `char(n)`,
    `ddl-auto: validate`, `open-in-view: false`, explicit mappers both ways.
  - When to use JPA (aggregates written and loaded) vs `JdbcClient`
    (aggregates over rows, read models such as balances and movements).
  - Money in SQL: `sum(bigint)` returns `numeric`; convert with
    `longValueExact`.
  - Tests: Testcontainers PostgreSQL only (ADR 0003), schema-constraint tests
    assert the constraint name, `JdbcTemplate` fixtures for rows without a
    write path.
- **B2** — "Reference examples" with real paths (changesets, entity, adapters,
  ITs, fixture).
- **B3** — `evaluator` reads it among its rules; `CLAUDE.md` lists it with the
  skills and drops it from the growth list.

## Scope

- In: new skill folder, `.claude/agents/evaluator.md`, `CLAUDE.md`.
- Out: new persistence code.

## Design notes

- Link ADRs 0003/0004 instead of repeating them; every statement must match
  current code (no aspirational rules).

## Definition of done

- [ ] Every criterion above has a check (paths exist; build still green)
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
