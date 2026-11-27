# 0003 — The owner delegates every approval to the staff engineer

- Date: 2026-11-27
- Trigger: owner request
- Task: none
- Branch: chore/staff-engineer-agent
- Status: Decided by owner

## Question

Which approvals still need the owner once the staff engineer exists?

## Options

| Option | Pros | Cons | Reversibility |
|--------|------|------|---------------|
| Record 0001 (owner keeps guards, settings, history) | Four-eyes on the control core | Owner needed for every flow change | High |
| Record 0002 (veto windows, control core stays with owner) | Most work flows alone | Owner still needed for the control core | High |
| Staff engineer approves everything | The flow never waits for the owner | No human four-eyes; one model writes and approves | High: a later record or PR can restore any approval |

## How banks and production ledgers do it

Banks separate the author of a change from its approver (four-eyes) and keep
an audit trail. Here the trail stays (one record per approval, bound to a PR
and its head commit, and every push voids the approval), but the approver is
an independent subagent run, not a person.

## Decision

The owner decided it directly: the staff engineer approves everything the
owner used to approve. This supersedes records 0001 and 0002. Fixed rules
that are not approvals stay: only fictitious data; no direct merges outside
the gate; `human-approved` remains the owner's own optional mark; `main`
changes only through PRs (branch protection blocks direct pushes for every
account).

## Consequences

- `AUTONOMY.md`, the gate, the path guard, `gh-rest.sh`, the settings and the
  skills change as this branch does.
- This PR still needs the owner's `human-approved` once: the gate on `main`
  that judges it is the old one.

## Protected paths

- `AUTONOMY.md`
- `.claude/**`
- `.agents/protected-paths.txt`
- `.github/workflows/autonomy-gate.yml`
- `scripts/autonomy-gate.sh`
- `scripts/guard-paths.sh`
- `scripts/gh-rest.sh`
