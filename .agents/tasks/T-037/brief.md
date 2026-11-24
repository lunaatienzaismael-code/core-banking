# T-037 — Velocity rule backed by Redis

- Use case: UC5 (`docs/spec.md`) · Phase: 8 · Type: feat
- Issue: #TBD · Branch: `feat/T-037-risk-velocity-rule`
- Depends on: T-036

## Goal

Velocity rule backed by Redis (ADR 0014).

## Acceptance criteria

- **AC5.2** — Given N transfers by one debtor within the window, When another is assessed, Then it is rejected.
- **B1** — Atomic Lua increment; redelivered `AssessRisk` does not count twice.
- **B2** — Redis down → assessment fails and retries, never approves.

## Scope

- In: Redis adapter, velocity rule, config
- Out: sliding window

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
