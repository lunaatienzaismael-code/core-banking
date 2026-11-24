# T-036 — Risk assessment with amount rule

- Use case: UC5 (`docs/spec.md`) · Phase: 8 · Type: feat
- Issue: #TBD · Branch: `feat/T-036-risk-amount-rule`
- Depends on: T-035

## Goal

Risk assessment with amount rule (ADR 0014).

## Acceptance criteria

- **AC5.1** — Given a configured threshold, When a payment above it is assessed, Then `RiskRejected` is replied; at or below it, `RiskApproved`.
- **B1** — `AssessRisk` goes through inbox; reply through outbox (ADR 0013).

## Scope

- In: risk domain, use case, Kafka adapters, changesets
- Out: velocity (T-037), saga (T-038)

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
