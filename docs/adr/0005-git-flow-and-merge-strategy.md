# ADR 0005: Git Flow and Merge Strategy

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner

## Context

Part of what this portfolio demonstrates is engineering discipline visible in
the Git history: small, reviewable PRs and a `main` that tells the real story
of how the system was built. The project also commits to **atomic Conventional
Commits** where every commit compiles and passes tests.

## Options Considered

### Merge strategy
- **Squash merge** — `main` gets one commit per PR. Very clean, but it
  **collapses the atomic commits**, so the meaningful step-by-step history
  survives only inside the (eventually stale) PR.
- **Rebase merge** — replays each atomic commit onto `main`, keeping history
  **linear and preserving the atomic commits**. Costs more commits on `main`
  and requires that every commit really is green.
- **Merge commit** — preserves branch topology but produces a non-linear
  history that is noisier to read.

## Decision

- **`main` is protected**: no direct pushes. Everything lands via PR with CI
  green.
- **One `tasks.json` task = one branch = one PR.** Branch naming:
  `<type>/<task-id>-<slug>`, e.g. `feat/T-012-account-balance-endpoint`.
- Inside a task, **atomic Conventional Commits** (`feat(ledger): …`,
  `test(ledger): …`, `docs(adr): …`). Each commit compiles and passes tests.
  No cosmetic commits to pad history.
- **Merge via rebase** (not squash). Rationale: it preserves the atomic commits
  on `main` **and** keeps history linear, which is exactly the "history tells
  the real story" goal. Squash is kept as a fallback only if commit noise ever
  becomes a problem.
- Each task has a **GitHub issue**; its PR closes it with `Closes #N`.
- Each phase is a **milestone**; closing it creates a `v0.X.0` tag with release
  notes.
- PR description uses a template: **What / Why / How / How to test / checklist**,
  plus the evaluator's verdict.

## Consequences

- `main` stays linear and each commit is independently meaningful and green.
- Rebase requires branches to stay reasonably up to date with `main`; small PRs
  (the one-task rule) keep rebases cheap.
- Branch protection must allow rebase merges and require the CI check.
- The branch/commit/PR conventions are summarised in `CLAUDE.md` (Phase 2) so
  every working session follows them.
