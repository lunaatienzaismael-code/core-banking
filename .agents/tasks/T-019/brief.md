# T-019 — payment-service module skeleton

- Use case: UC3 (`docs/spec.md`) · Phase: 6 · Type: build
- Issue: #43 · Branch: `build/T-019-payment-service-skeleton`
- Depends on: T-018

## Goal

A second, empty service module `payment-service` builds, tests, starts against
its own PostgreSQL and is held to the same architecture, coverage and
packaging rules as `ledger-service`.

## Acceptance criteria

- **B1** — Maven module `payment-service` (base package
  `com.ledgerflow.payment`), registered in the parent POM; Spring Boot app
  starts in a Testcontainers context IT against PostgreSQL with Liquibase
  (empty master changelog) and `ddl-auto: validate`.
- **B2** — `HexagonalArchitectureTest` applies
  `HexagonalArchitecture.of("com.ledgerflow.payment")` from
  `architecture-rules` (reuse, no copy).
- **B3** — OpenAPI-first tooling as in ADR 0008 with an initial contract
  `payment-service/src/main/openapi/payment-api.yaml` (info, Problem schema,
  no paths yet); Problem Details fallback handler so the
  `webAdapterHasProblemDetailsFallback` rule holds.
- **B4** — `infra/docker-compose.yml` gains `payment-db` (own database, user
  and volume, a port other than 5432); multi-stage `payment-service/Dockerfile`
  mirroring the ledger one; CI builds the image and runs the module's tests
  (JaCoCo and PIT gates apply as in the ledger, or the brief records why a
  threshold is relaxed while the module is empty).
- **B5** — Docs: `README`/roadmap/CLAUDE.md commands mention the new module
  where they list `ledger-service`.

## Scope

- In: parent POM, `payment-service/**`, `infra/docker-compose.yml`, CI
  workflow, docs listing modules.
- Out: any payment domain or endpoint (T-020, T-021).

## Design notes

- `Money` is not shared with the ledger (ADR from T-018); it arrives with T-020.
- If JaCoCo/PIT fail only because there is no code yet, prefer a minimal real
  class over lowering a gate; record the choice in the PR.

## Definition of done

- [ ] Every criterion above has a test or a CI step proving it
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
