# T-009 — Raise JaCoCo thresholds to real coverage

- Use case: none (Phase 4 conventions) · Phase: 4 · Type: build
- Issue: #18 · Branch: `build/T-009-jacoco-thresholds`
- Depends on: T-007

## Goal

The coverage gate reflects the coverage UC1 actually reached, so a PR cannot
silently lower it (ADR 0003 ratchet).

## Acceptance criteria

- **B1** — JaCoCo line threshold goes from 0.30 to 0.90 and a branch threshold
  of 0.75 is added (measured after T-005: line 96.5 %, branch 83.3 %), for
  every module that has the check.
- **B2** — Generated OpenAPI code stays excluded; nothing else is newly
  excluded to reach the numbers.
- **B3** — `./mvnw -B verify` passes; a quick local check that lowering
  coverage below the threshold fails the build (not committed).
- **B4** — ADR 0003 records the new numbers and the ratchet rule (raise when
  real coverage stays comfortably above; never lower without an ADR note).

## Scope

- In: parent `pom.xml` properties/JaCoCo rules, `docs/adr/0003-*.md`.
- Out: new tests just to move the number.

## Design notes

- Re-measure on the branch before setting numbers; keep a few points of
  headroom so unrelated small PRs do not fail on rounding.
- `architecture-rules` coverage may differ; set per-module values only if
  needed.

## Definition of done

- [ ] Every criterion above has a check
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
