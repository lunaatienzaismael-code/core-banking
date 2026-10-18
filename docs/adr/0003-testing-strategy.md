# ADR 0003: Testing Strategy

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner

## Context

This is a financial system: the cost of a silent rounding, concurrency or
money-loss defect is high. Tests are the primary evidence a reviewer uses to
trust the code. "Deterministic first" is a project principle: anything that can
be checked by a test, an ArchUnit rule or a linter is checked that way, not by
a human or an LLM.

## Options Considered

- **H2 in-memory for integration tests** — fast, but lies about PostgreSQL
  (types, locking, SQL dialect). Rejected: it would hide the very concurrency
  and SQL issues we care about.
- **Testcontainers with real PostgreSQL** — slower, needs Docker, but exercises
  the real database engine, Liquibase changelogs and locking behaviour.
- **Mock-heavy unit tests** — fast but can test the mocks instead of the logic.

## Decision

A test pyramid with three deterministic layers:

1. **Unit tests** — pure `domain` and `application` logic.
   - JUnit 5 + AssertJ.
   - No Spring context.
   - Mockito is used only to stand in for **outbound ports** when testing an
     application service. The domain is **never** mocked when testing itself.
2. **Spring slice tests** — adapter boundaries.
   - `@WebMvcTest` for inbound web adapters (serialization, status codes,
     Problem Details mapping).
   - Persistence adapter tested against a real database (see layer 3), not H2.
3. **Integration tests (Testcontainers + PostgreSQL)** — run by Maven Failsafe.
   - Boot the Spring context, apply the Liquibase changelog, exercise the
     outbound persistence adapter and full verticals end to end.

**Conventions**
- Unit/slice tests: `*Test` (Surefire). Integration tests: `*IT` (Failsafe).
- Financial invariants (debits = credits, no money created/destroyed, currency
  consistency, idempotency, concurrency) get explicit, named tests.

**Coverage and mutation**
- **JaCoCo** enforces a minimum line/branch threshold. It starts deliberately
  low and is **ratcheted up to the real achieved coverage** at the end of each
  feature phase so it can only go up, never down.
  - **Ratchet rule:** raise the thresholds when real coverage stays
    comfortably above them, keeping a few points of headroom so small,
    unrelated PRs do not fail on rounding. Never lower them, nor add a
    JaCoCo exclusion to meet them, without a dated note below.
  - Thresholds live in the parent `pom.xml` (`jacoco.minimum.line`,
    `jacoco.minimum.branch`) and apply per module; only bootstrap
    (`*Application`) and generated OpenAPI code are excluded.
- **PIT** mutation testing is configured but run **on demand**, not in the
  default CI path, to keep CI fast.
  *(Superseded by the 2026-10-18 note below: PIT is now a PR gate.)*

## Consequences

- CI requires Docker (Testcontainers); the GitHub Actions runner provides it.
- Tests are slower than an H2 setup but trustworthy for a payments domain.
- The ratcheting JaCoCo threshold means a PR cannot silently reduce coverage.
- The `java-testing` skill encodes what to test at each layer and what not to
  mock.

## Threshold history

| Date | Line | Branch | Measured | Note |
|------|------|--------|----------|------|
| 2026-10-12 | 0.30 | — | — | Initial, before any feature code (`c3a4026`). |
| 2026-10-13 | 0.90 | 0.75 | `ledger-service` line 96.5 %, branch 83.3 %; `architecture-rules` 100 % / 100 % | End of UC1 (Phase 4, T-009). |

## Notes

### 2026-10-18 — PIT becomes a PR gate (T-016)

- **Why now:** UC2 adds the ledger's core logic (balanced transactions,
  overdraft rule). Line coverage proves code ran, not that a test would notice
  it breaking; mutation score does. The measured cost is small: the
  `mutation` profile runs in about 20 seconds locally (unit tests only).
- **How:** Maven profile `mutation` binds `pitest-maven:mutationCoverage` to
  `verify` on `domain` and `application` classes. CI job "Mutation testing"
  runs `./mvnw -B verify -Pmutation -DskipITs -Djacoco.skip=true` on every
  PR and uploads the HTML report. It is a separate job from "Build and test"
  so a mutation failure never hides a test failure. Plain `./mvnw -B verify`
  is unchanged.
- **Threshold:** `pitest.mutation.threshold` in the parent `pom.xml` = 90 %,
  under the same ratchet rule as JaCoCo (only up, a few points of headroom,
  never lowered or met by a new exclusion without a dated note here).
- **Exclusion:** `toString` methods are not mutated. They are debug text;
  killing their mutants would mean asserting formatting, not behaviour.
- **Baseline:** 37/37 mutants killed (100 %), after `MoneyTest` proved that
  zero is not positive (the one real survivor of the 2026-10-13 on-demand
  run). Removing two domain test classes drops the score to 78 % and the
  profile fails, which confirms the gate.
