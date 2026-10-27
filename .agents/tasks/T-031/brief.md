# T-031 — Bounded retries and dead-letter topics

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: feat
- Issue: #61 · Branch: `feat/T-031-retries-dlt`
- Depends on: T-029

## Goal

A message that keeps failing is retried a bounded number of times and then
parked on a dead-letter topic, in both services, without blocking the
partition (ADR 0013).

## Acceptance criteria

- **AC4.4** — Given a poison message (e.g. undeserialisable payload), when it
  is consumed, then after the configured retries it is on the dead-letter
  topic with the original key and failure headers, and the next message on
  the same partition is processed — named IT per service.
- **B1** — Non-retryable errors (deserialisation, validation) skip retries
  and go straight to the DLT — named test.
- **B2** — Retry count and backoff are configuration; defaults documented in
  ADR 0013 or the skill `spring-boot-conventions`.

## Scope

- In: Kafka error handling config in both services, ITs.
- Out: DLT replay tooling.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
