# T-016 — Mutation testing gate (PIT)

- Use case: none (Phase 5 enabling work) · Phase: 5 · Type: build
- Issue: #30 · Branch: `build/T-016-pit-mutation-gate`
- Depends on: none

## Goal

PIT stops being on-demand-only: every PR runs mutation testing on `domain` and
`application` and fails below a ratcheted threshold, before UC2 adds ledger
logic.

## Acceptance criteria

- **B1** — Maven profile `mutation` binds `pitest-maven:mutationCoverage` to
  `verify` with `mutationThreshold` 90 and `excludedMethods` `toString`;
  plain `./mvnw -B verify` is unchanged locally.
- **B2** — CI job "Mutation testing" runs on every PR (`-Pmutation`, unit tests
  only, no Docker needed) and uploads the PIT HTML report as an artifact.
- **B3** — The surviving mutant found on 2026-10-13 is killed: `MoneyTest`
  proves zero is neither positive nor negative (java-testing rule 11).
- **B4** — Baseline recorded: mutation score after B3 with `toString`
  excluded (expected 37/37); a quick local check that lowering the score
  below 90 % fails the profile (not committed).
- **B5** — ADR 0003 amended with a dated note: PIT moves from on-demand to a
  PR gate (measured cost: seconds), threshold and ratchet rule as for JaCoCo,
  `toString` excluded with the reason; README command updated;
  `java-testing` "Running" section mentions `-Pmutation`.

## Scope

- In: parent `pom.xml`, `.github/workflows/ci.yml`, `MoneyTest`,
  `docs/adr/0003-*.md`, `README.md`, `java-testing` skill.
- Out: mutating adapters or ITs; new tests beyond B3.

## Design notes

- Keep the job separate from "Build and test" so a slow PIT run never hides a
  test failure; decide with the user whether it becomes a required check on
  `main` after it has run on a few PRs.
- Whether a branch-protection required check is added is a repo setting, not
  part of this PR.

## Definition of done

- [ ] Every criterion above has a check
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
