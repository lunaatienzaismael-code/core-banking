# T-033 — Ledger ConstraintViolationException as 400 Problem Details

- Use case: none (enabling work; error model per ADR 0006) · Phase: 8 · Type: fix
- Issue: #74 · Branch: `fix/T-033-ledger-constraint-violation-400`
- Depends on: none

## Goal

A request to ledger-service that fails Bean Validation's method validation
answers 400 with Problem Details, as payment-service already does, instead of
falling through to the 500 fallback. This task is also the first run of the
current flow in a cloud session (pilot), so the full suite including the
end-to-end tests must run there.

## Acceptance criteria

- **B1** — Given a request whose parameter breaks a constraint checked by
  method validation (`jakarta.validation.ConstraintViolationException`), when
  ledger-service handles it, then it answers 400 with a Problem Details body
  whose `title` is "Invalid request" and whose `detail` names the parameter
  and the violated constraint — named web-layer test.
- **B2** — The 500 fallback keeps answering for other unexpected exceptions —
  the existing fallback test stays green, no change to its assertions.

## Scope

- In: `ledger-service` `adapter.in.web.ProblemDetailsHandler` and its web
  test.
- Out: sharing the handler between services; payment-service (already maps
  it, `payment-service/.../ProblemDetailsHandler.java:55`); changes to the
  OpenAPI contract.

## Design notes

- Mirror payment-service's mapping (sorted `<last path node>: <message>`
  pairs joined by `; `). The generated interfaces are `@Validated`, but the
  ledger contract has no header/path/query constraint today (path ids are
  `format: uuid`, which fails as a type mismatch, not as a constraint), so no
  production endpoint triggers this exception yet. Prove B1 with a test-scope
  `@Validated` controller in the web slice test, and say so in the test's
  `@DisplayName`; do not add a constraint to the contract only to test it.
- Origin: T-022 evaluator nit (latent ConstraintViolationException → 500 in
  the ledger).

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `scripts/e2e.sh` green in the cloud session (pilot)
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
