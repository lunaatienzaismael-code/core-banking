# T-XXX — <title>

- Use case: UCn (`docs/spec.md`) · Phase: n · Type: feat
- Issue: #N · Branch: `<type>/T-XXX-<slug>`
- Depends on: T-YYY (or none)

## Goal

<!-- One or two sentences: the observable outcome when this task is done. -->

## Acceptance criteria

<!-- Copy the spec ACs this task delivers (Given/When/Then), plus brief-local
     criteria B1, B2... for enabling work. Each must be provable by a test. -->

- **ACn.m** — Given … When … Then …
- **B1** — …

## Scope

- In: <!-- packages / endpoints / tables touched -->
- Out: <!-- explicitly deferred to other tasks -->

## Protected paths

<!-- Optional. Only when the task must edit a path in
     .agents/protected-paths.txt (e.g. the root pom.xml for a new module).
     One backticked path or glob per bullet; the PR then needs the owner's
     approval (AUTONOMY.md). Delete this section otherwise. -->

- `pom.xml`

## Design notes

<!-- Only what the skills and ADRs do not already say: port names, error cases,
     edge cases worth a named test. Link ADRs, do not repeat them. -->

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
