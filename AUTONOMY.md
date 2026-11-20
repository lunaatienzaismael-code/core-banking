# Autonomy policy

Single source of what the agent may do without the owner, when it stops, and
what it may never do. `CLAUDE.md`, the skills and `.agents/README.md` point
here instead of repeating it. Rationale and rollout:
[`.agents/autonomy-migration.md`](.agents/autonomy-migration.md).

Everything the flow needs lives in the repository. Cloud sessions start from a
fresh clone and see neither `.session/` nor a user's local memory.

## Rollout status

The policy applies now. Some of the mechanisms that enforce it land in later
PRs of the migration (plan, section 5). Until they do, the rules in this file
are kept by the agent on its own.

| Mechanism | State |
|-----------|-------|
| This policy, `.agents/STATE.md` (`scripts/state.sh`) | in place (PR 1) |
| `/run-task`, `scripts/gh-rest.sh`, REST-only GitHub access, SHA-bound review evidence | in place (PR 2) |
| Hooks, protected-path guard, permissions, `CODEOWNERS` | in place (PR 3) |
| `Autonomy gate` workflow and auto-merge | written (PR 4); acts once it is on `main` and auto-merge is enabled (PR 5) — until then, the owner merges every PR |
| GitHub settings, cloud environment, scheduled routine | PR 5 (owner) |

## Identity mode

**Mode B** (current): the agent pushes and calls GitHub as the owner's
account. GitHub cannot tell the agent's actions from the owner's.

- Human approval of a PR is the label **`human-approved`**, added by the
  owner in the GitHub UI. Every new push removes it, so an approval covers
  only the code that was reviewed.
- In mode B this mark is **soft**. The only things that keep the agent from
  adding it are the deny rules, `scripts/gh-rest.sh` refusing that label,
  and this policy. The agent never adds, removes or asks a tool to add
  `human-approved`.

**Mode A** (later): a bot account with Write access, not admin. A code-owner
review that the bot cannot give replaces `human-approved`.

## What the agent does without stopping

- Pick the next ready task, branch `<type>/T-xxx-<slug>`, implement it with
  the skills, run `test-runner` and the `evaluator` (at most 3 rounds).
- Commit atomic Conventional Commits, ending with one `chore(agents)` commit
  that holds the bookkeeping (`tasks.json`, `progress.md`, `STATE.md`).
- Push the task branch and open the PR with
  `.github/pull_request_template.md`, `Closes #N`, one exact line
  `Verdict: APPROVE` or `Verdict: CHANGES`, and the verdict as a PR comment.
- Mark merged tasks `done` during the next task's reconciliation. That
  bookkeeping rides on the new branch, never on `main`.
- Merge happens only through the gate's auto-merge, when the PR carries
  `Verdict: APPROVE` and the required checks are green. Until the gate exists
  (PR 4), the owner merges.

### Review evidence

The evaluator approves only on evidence bound to a commit:

- the local run (`scripts/test-summary.sh`) passed on the reviewed SHA with a
  clean tree;
- the CI **End-to-end tests** job on the PR head SHA is not red. The e2e
  suite does not run in the cloud VM (`docker build` has no network there,
  issue #76), so CI is where it is validated;
- commits after the reviewed SHA may touch only `.agents/` (the bookkeeping
  commit and `review-N.json`). Anything else needs a new review round.

### Decisions (ADRs)

- **Reversible** decisions: the agent decides and writes the ADR with status
  `Proposed by agent`. The ADR has a trade-off table and says how banks and
  production ledgers do it, and it recommends on the merits, not on the
  smallest change.
- Decisions that **stop the run** and wait for the owner:
  - money representation, ledger invariants or posting semantics;
  - security and authentication;
  - changes to applied changesets or data migrations;
  - breaking public contracts (OpenAPI, or Avro without BACKWARD
    compatibility);
  - new infrastructure or new dependencies.

## Protected paths

The list is [`.agents/protected-paths.txt`](.agents/protected-paths.txt): CI
workflows, existing POMs, the architecture rules, applied changesets and the
flow files themselves (this file, `.claude/**`, the guard scripts, the list
and `CODEOWNERS`).

The agent edits a protected path only when the task brief lists it under
`## Protected paths`; the PR then needs `human-approved`. Three things read
the list:

- the `PreToolUse` hook (`scripts/guard-paths.sh`) blocks undeclared edits
  made with Edit/Write. It does not see edits made through Bash;
- the Autonomy gate (PR 4) is the real barrier: it fails any PR that touches
  a protected path without `human-approved`;
- `.github/CODEOWNERS` is generated from it
  (`scripts/guard-paths.sh --codeowners`); `--check-codeowners` detects drift.

The `Stop` hook (`scripts/verify.sh`) keeps a turn from ending on code that
fails `spotless:check` or the unit and ArchUnit tests of the touched modules.

## Stop conditions

The agent stops the run when:

- 3 review rounds end without approval (it opens a draft PR with
  `Verdict: CHANGES` and `needs-human`);
- the brief is wrong or the task is too big;
- the change needs a protected path the brief does not declare;
- CI is still red after 2 fix attempts;
- the work needs secrets or repository settings;
- the data involved might be real (only fictitious IBANs, names and amounts
  are allowed);
- a decision from the "stop the run" list comes up;
- the phase ends.

**How it stops:** a comment on the task's issue with the reason, the label
`needs-human` on the issue, and a regenerated `.agents/STATE.md`. Open
questions to the owner are `needs-human` issues, not free text in a file.

## End of phase

When no `todo` task of the phase is left:

1. The agent opens a **phase-close PR** (roadmap tick, `STATE.md`) and a
   **next-phase plan PR** (ADRs, briefs, `tasks.json` entries). Both are
   labelled `needs-human`.
2. The owner's merge of the plan PR approves the phase.
3. The first run after that merge creates the milestone and the issues of the
   new phase.

The owner closes the milestone and creates the tag.

## Always forbidden

- Merging a PR directly (outside the gate's auto-merge), pushing to `main`,
  force-pushing, creating or moving tags.
- Adding or removing `human-approved`.
- Using real personal or employer data.
