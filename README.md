# LedgerFlow

Event-driven, SEPA-style payments platform built around a **double-entry
ledger**. A backend portfolio project: reasoned decisions, correct distributed
consistency, serious tests and a readable Git history.

> Status: **walking skeleton + agent infrastructure** (Phase 2). See the [roadmap](docs/roadmap.md).

## Stack

Java 21 · Spring Boot 4 · Maven multi-module · PostgreSQL + Liquibase ·
hexagonal architecture enforced by ArchUnit · Testcontainers · Docker.

## Run it

Requirements: JDK 21 and a running Docker daemon.

```bash
./mvnw -B verify                                  # format check, unit + ArchUnit, Testcontainers ITs, coverage
docker compose -f infra/docker-compose.yml up -d  # local PostgreSQL
./mvnw -pl ledger-service spring-boot:run         # http://localhost:8080/actuator/health
```

HTTP contract (OpenAPI-first, [ADR 0008](docs/adr/0008-openapi-tooling.md)): `ledger-service/src/main/openapi/ledger-api.yaml`;
Spring interfaces and DTOs are generated from it at build time.
Compact test report (JSON): `scripts/test-summary.sh`.
Mutation testing (PIT, PR gate, [ADR 0003](docs/adr/0003-testing-strategy.md)): `./mvnw -B verify -Pmutation -DskipITs -Djacoco.skip=true`; report in `<module>/target/pit-reports/`.

## Layout

| Path | Purpose |
|------|---------|
| `architecture-rules/` | Reusable ArchUnit rules for the hexagonal layout (ADR 0002) |
| `ledger-service/` | Double-entry ledger service |
| `infra/` | Local dependencies (`docker compose`) |
| `CLAUDE.md`, `.claude/`, `.agents/` | Agent workflow: guide, subagents, skills and file-based task state |
| `docs/` | [Specification](docs/spec.md), [roadmap](docs/roadmap.md), [ADRs](docs/adr/README.md) |

All example data (IBANs, names, amounts) is fictitious.
