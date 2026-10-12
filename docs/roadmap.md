# LedgerFlow — Roadmap

High-level checklist of phases. Each feature phase follows the same cycle:
`/plan-feature` → `/next-task` (one task per branch/PR) → extract conventions →
close milestone → tag. ADRs needed by a phase are proposed and decided before it
starts.

- [x] **Phase 0 — Definition** (no code)
  - [x] Confirm current Claude Code formats (agents / skills / commands / model)
  - [x] ADRs 0001–0007 (stack, hexagonal, testing, persistence+money, git flow,
        API/errors, deployment)
  - [x] `docs/spec.md` (purpose, actors, use cases with Given/When/Then, scope)
  - [x] `docs/roadmap.md`

- [ ] **Phase 1 — Walking skeleton**
  - [x] Maven multi-module parent + `ledger-service`, hexagonal packages, health
        endpoint (no business logic)
  - [x] ArchUnit dependency rules (reusable across services)
  - [x] Test base: JUnit 5, AssertJ, Mockito, Testcontainers + PostgreSQL, empty
        Liquibase changelog integration test
  - [x] Quality: Spotless, JaCoCo (low threshold), PIT on demand
  - [x] `infra/docker-compose.yml` (Postgres only) + multi-stage `Dockerfile`
  - [x] GitHub Actions CI (build, Spotless, unit + integration, ArchUnit, JaCoCo)
  - [x] `scripts/test-summary.sh` (compact JSON from Surefire/Failsafe)
  - [ ] Git/GitHub: `.gitignore`, PR template, milestones, minimal README, branch
        protection on `main`

- [ ] **Phase 2 — Minimal agent infrastructure**
  - [ ] `CLAUDE.md` (<120 lines)
  - [ ] `.agents/` state (progress, tasks.json, templates, per-task folders)
  - [ ] Subagents `test-runner` (Haiku) and `evaluator` (Sonnet)
  - [ ] Seed skills: `java-hexagonal`, `java-testing`, `task-planning`
  - [ ] Commands: `/plan-feature`, `/next-task`, `/status`

- [ ] **Phase 3 — First vertical (UC1)**: open account, query balance and
      movements, end to end across all layers with tests. Reference implementation.

- [ ] **Phase 4 — Extract conventions from real code**
  - [ ] Fill skills' "Reference examples" with paths to real code
  - [ ] Propose new rules (skill vs ArchUnit)
  - [ ] New skill `persistence-postgres-liquibase`
  - [ ] Raise JaCoCo threshold to real coverage
  - [ ] Close milestone, tag `v0.1.0`

- [ ] **Phase 5 — Full ledger (UC2)**: double-entry transactions, domain
      invariants, optimistic locking, concurrent-postings Testcontainers test.
      ADR: balance derived vs materialised. Tag `v0.2.0`.

- [ ] **Phase 6 — payment-service (UC3)**: second module reusing ArchUnit rules,
      `Idempotency-Key` persistence, payment state machine. Consider
      `spring-boot-conventions` skill. Tag `v0.3.0`.

- [ ] **Phase 7 — Events and saga (UC4)**: Kafka (KRaft) + Schema Registry in
      compose, Avro contracts with BACKWARD compatibility checked in CI,
      transactional outbox, idempotent consumers, orchestrated saga with
      compensation, retries + DLT. ADRs: orchestration vs choreography, outbox vs
      dual-write, topics/partition keys. New skill `kafka-events`. Tag `v0.4.0`.

- [ ] **Phase 8 — Risk and notifications (UC5)**: `risk-service` with Redis,
      `notification-service` as a second consumer group. Tag `v0.5.0`.

- [ ] **Phase 9 — Operations and resilience (UC6)**: OpenTelemetry with context
      propagated over Kafka headers, business metrics + versioned Grafana
      dashboard, reconciliation job, chaos test (kill the ledger mid-saga), k6
      load test with results in the README. Tag `v0.6.0`.

- [ ] **Phase 10 — Showcase**: final README (problem, architecture diagram,
      payment sequence diagram, decisions linked to ADRs, one-command start, demo
      GIF, "how it was built with agents" section). Tag `v1.0.0`.
