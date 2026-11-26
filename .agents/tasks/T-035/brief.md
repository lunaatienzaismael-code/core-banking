# T-035 — risk-service module, Redis in compose and risk contracts

- Use case: UC5 (`docs/spec.md`) · Phase: 8 · Type: build
- Issue: #87 · Branch: `build/T-035-risk-service-skeleton`
- Depends on: none

## Goal

risk-service module, Redis in compose and risk contracts (ADR 0014).

## Acceptance criteria

- **B1** — `risk-service` module builds, reuses ArchUnit rules and boots with its own database.
- **B2** — Redis added to `infra/docker-compose.yml`; Testcontainers Redis available to tests.
- **B3** — Avro `AssessRisk`, `RiskApproved`, `RiskRejected` with BACKWARD check.

## Scope

- In: new module, compose, Avro contracts
- Out: any rule logic, saga changes

## Protected paths

- `pom.xml`
- `risk-service/src/test/java/com/ledgerflow/risk/HexagonalArchitectureTest.java` (new; B1 reuses the shared ArchUnit rules)

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
