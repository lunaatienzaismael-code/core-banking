# T-039 — End-to-end risk smoke test

- Use case: UC5 (`docs/spec.md`) · Phase: 8 · Type: test
- Issue: #TBD · Branch: `test/T-039-e2e-risk-smoke`
- Depends on: T-037, T-038

## Goal

End-to-end risk smoke test (ADR 0014).

## Acceptance criteria

- **B1** — e2e: approved payment completes; over-threshold payment is rejected with no money moved.

## Scope

- In: e2e-tests
- Out: production code

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
