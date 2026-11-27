# LedgerFlow — agent guide

Event-driven, SEPA-style payments platform around a **double-entry ledger**.
Backend portfolio for EU banking/fintech: reasoned decisions, correct
consistency, serious tests, readable Git history.

- What and why: [`docs/spec.md`](docs/spec.md) (use cases + acceptance criteria)
- Where we are: [`docs/roadmap.md`](docs/roadmap.md)
- Decisions: [`docs/adr/`](docs/adr/README.md) — read the ADR before touching its area
- Agent state: [`.agents/`](.agents/README.md)

Stack: Java 21, Spring Boot 4.1, Maven multi-module, PostgreSQL + Liquibase,
hexagonal architecture enforced by ArchUnit (`architecture-rules`),
Testcontainers, Docker.

## Commands

```bash
./mvnw -B verify                              # Spotless check, unit + ArchUnit, ITs, JaCoCo
./mvnw -B verify -pl ledger-service -am       # one module
./mvnw spotless:apply                         # fix formatting
scripts/test-summary.sh [-- <mvn args>]       # same build, one-line JSON result
docker compose -f infra/docker-compose.yml up -d
```

Integration tests need Docker; setup in [`docs/development.md`](docs/development.md).

## Working rules

- **[`AUTONOMY.md`](AUTONOMY.md) is the autonomy policy**: what I do without
  stopping, when I stop, protected paths, end of phase and what is always
  forbidden. It wins over any older rule that contradicts it.
- Significant decisions become ADRs: a trade-off table, how banks and
  production ledgers do it, a recommendation on the merits.
- Talk to the user in **Spanish**; everything in the repo is in **English**.
- Only fictitious data (IBANs, names, amounts). Never real employer data.
- Deterministic first: test, ArchUnit or linter before relying on review.
- No speculative code: build what the current task's brief asks for.
- Nothing the flow needs lives only in `.session/` or in local memory: cloud
  sessions start from a fresh clone.

## Session start

1. Read `.agents/STATE.md` (regenerate it with `scripts/state.sh`), then
   `docs/roadmap.md`.
2. `/status` for tasks, branch and open PRs.
3. `/run-task`: it resumes the active task or starts the next one.

## Per-task flow

`/plan-feature UCn` once per use case, then per task `/run-task`:

read `brief.md` → branch `<type>/<id>-<slug>` → implement with skills and
atomic commits → `test-runner` → fix → `evaluator` → (fix → review again, max
3 rounds) → a `chore(agents)` bookkeeping commit → push + PR
`Closes #N` with the verdict → merge (the gate's auto-merge, on the evaluator verdict or the
staff engineer's approval; see `AUTONOMY.md`) → `done` in the next task's reconciliation.

- Skills: `java-hexagonal` (placement, layering, money), `java-testing`
  (what/how to test), `persistence-postgres-liquibase` (changesets, JPA,
  `JdbcClient`, persistence tests), `spring-boot-conventions` (config, beans,
  controllers, Problem Details), `kafka-events` (contracts, outbox, inbox,
  retries/DLT, messaging tests), `task-planning` (splitting and briefs).
- Subagents: `test-runner` (Haiku) runs the build, returns only JSON;
  `evaluator` (Sonnet) reviews diff vs brief with clean context, writes
  `review-N.json`, returns only the verdict; `staff-engineer` (Opus) takes
  every judgment call and approval that used to be the owner's, writes a
  record in `.agents/decisions/`, returns only the decision.

## State and contracts

- I am the **single writer** of `.agents/tasks.json` (edit field by field,
  never regenerate) and `.agents/progress.md` (append-only).
- **Source of truth:** `tasks.json` owns working state (`todo`,
  `in_progress`, `in_review`, `blocked`), dependencies and ACs; GitHub owns
  `done` (a task is done only when its PR is merged). Issues mirror
  `tasks.json`; `scripts/check-task-sync.sh` reports any drift, and `/status`
  runs it.
- Subagents write only inside `.agents/tasks/<id>/` (the `staff-engineer`,
  in `.agents/decisions/`) and return compact JSON plus paths.
- Contracts: test summary JSON (header of `scripts/test-summary.sh`),
  `.agents/templates/{brief.md,tasks.schema.json,review.schema.json}`.

## Token rules

- Never paste build logs; reference files by path.
- Run builds through `test-runner`, not directly, unless debugging one test.
- Read files in the slices you need; subagent replies stay compact JSON.
- Compact the context instead of restarting the session.

## Git (ADR 0005)

- `main` protected: PR only, CI "Build and test" green, linear history,
  **rebase merge** (no squash).
- One task = one GitHub issue = one branch = one PR (`Closes #N`).
- Atomic **Conventional Commits** (`feat(ledger): …`, `test(ledger): …`,
  `docs(adr): …`); each commit compiles and passes tests; no padding commits.
- PR body follows `.github/pull_request_template.md`, including the evaluator
  verdict. Each phase = milestone; closing it tags `v0.X.0`.

## Domain essentials (details in spec and ADRs)

- Money = `long` minor units + ISO-4217 code. Never `double`/`float`.
- Postings are signed (debit `+`, credit `−`); a transaction sums to zero per
  currency. Postings are the source of truth; the balance is read from
  per-account counters that always equal their sums (ADR 0009) and reported
  from the account's normal balance side.
- Domain is framework-free; use cases are the transaction boundary; errors
  leave the API as Problem Details (RFC 9457).

## Growth

Add agent infrastructure only when a trigger fires; propose it, do not just add it.

- **Growth rule:** when the evaluator flags the same `defect_type` in two
  tasks, propose a skill rule, an ArchUnit test if it is machine-checkable, or
  an evaluator check. `scripts/review-defects.sh` detects it (alerts for types
  missing from `.agents/growth-rules.json`); `/run-task` and `/status` run it.
- `code-explorer` subagent (Haiku, read-only): when code exceeds ~4–5
  modules/use cases.
- Agentic GitHub Actions (evaluator on PRs, CI doctor): once the local flow has
  run several tasks smoothly.
