# T-028 — Ledger posts and releases reserved transfers

- Use case: UC4 (`docs/spec.md`) · Phase: 7 · Type: feat
- Issue: #58 · Branch: `feat/T-028-ledger-post-release`
- Depends on: T-027

## Goal

The ledger completes a reserved transfer (transit → creditor) or releases it
(transit → debtor) on command, idempotently, and replies with the outcome.

## Acceptance criteria

- **B1** — Post-transfer command: transit → creditor transaction; replies
  transfer posted, or posting refused (unknown creditor, currency mismatch)
  with nothing changed — named tests.
- **B2** — Release-funds command: transit → debtor transaction; replies funds
  released. Release cannot fail for a valid reservation — named test.
- **B3** — Both commands go through the inbox (AC4.2 behaviour); a
  redelivered post or release has no second effect — named test each.
- **B4** — Invariant test: after reserve + post, or reserve + release, the
  transit account balance for that currency is back to its previous value.

## Scope

- In: ledger inbound adapter and use cases for both commands.
- Out: saga orchestration (T-029, T-030).

## Design notes

- Reserve, post and release share one consumer and inbox path from T-027;
  avoid a second listener per command type unless ADR 0013 says otherwise.

## Definition of done

- [ ] Every criterion above has a test named after the behaviour
- [ ] `test-runner` reports `pass`; ArchUnit and Spotless green
- [ ] `-Pmutation` green locally
- [ ] `evaluator` verdict `approve` (latest `review-N.json`)
- [ ] Docs/ADR/OpenAPI updated if behaviour or a decision changed
