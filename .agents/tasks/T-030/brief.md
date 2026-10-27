# T-030 — Saga compensation

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: feat
- Issue: #60 · Branch: `feat/T-030-saga-compensation`
- Depends on: T-029

## Goal

When posting to the creditor fails after funds were reserved, the saga
releases them and the payment ends `FAILED`; no money is left reserved or
lost.

## Acceptance criteria

- **AC4.3** — Given a payment with funds reserved, when posting to the
  creditor is refused, then a release-funds command is sent, the debtor's
  balance returns to its value before the payment, the transit account is back
  to zero for that payment, and the payment is `FAILED` with the reason.
- **B1** — A redelivered posting-refused reply sends no second release —
  named test.
- **B2** — ADR 0011 updated only if ADR 0012 introduced `RELEASING`.

## Scope

- In: saga compensation step, its tests.
- Out: retries/DLT (T-031); timeouts for replies that never arrive (UC6
  stuck-payment report).

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
