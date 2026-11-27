# 0002 — The staff engineer as approver in place of the owner for the "Always forbidden" actions

- Date: 2026-11-27
- Trigger: owner clarification of record 0001, relayed by the main agent: the staff engineer, not the owner, approves merging, pushing to `main`, force-push, tags and protected-path PRs
- Task: none
- Status: Decided by staff engineer
- Supersedes: record 0001, items 1, 2 and 3. Items 4 to 8 of 0001 stand.

## Question

For each action in `AUTONOMY.md` "Always forbidden" (merging PRs, pushing to
`main`, force-push, creating or moving tags, the `human-approved` approval
for protected paths), how does the staff engineer become the approver in
place of the owner, and with which safeguard? Fictitious data only stays a
project rule.

As in 0001: the request reached me through the main agent. It takes effect
when the owner merges the PR that applies it, with `human-approved`, because
that PR touches `AUTONOMY.md` and the gate.

## Options

| Action | Option | Pros | Cons | Reversibility |
|--------|--------|------|------|---------------|
| Merge | A: the staff engineer merges itself through REST | Simple | The gate is bypassed; nothing checks the approval mechanically | Low |
| | **B: the staff engineer approves with a record, and the gate executes the merge** | The approver and the executor are separate; the gate is deterministic and runs from `main` | Gate change | High |
| Push to `main` | A: allow direct pushes with staff-engineer approval | Fast | No PR, no CI, no review: the history gets commits nobody checked | Low |
| | **B: the staff engineer approves the change into `main`, always as a PR; direct pushes stay blocked for every identity** | Same outcome, auditable | One PR per change | High |
| Force-push / moving tags | A: the staff engineer approves case by case | Can "fix" history | Rewrites the audit trail. It is the Git version of editing a posted ledger entry | None |
| | **B: no approver can authorise it; corrections are forward-only (revert PR, new patch tag)** | History stays immutable | A mistake stays visible (on purpose) | — |
| Tag creation | **The staff engineer approves the phase close; `release.yml` creates the tag** | Tag made by the pipeline, tied to a merged PR | One workflow | High |
| `human-approved` / protected paths | A: the agent adds `human-approved` as the owner's account (mode B) | No changes | It forges the owner's signature: the history would claim a human approved | Low |
| | B: a staff-engineer approval for every protected path, the gate included | Full autonomy | One approved PR can rewrite the gate and remove every safeguard, this one included | Low |
| | **C: a staff-engineer approval (record and gate check, label `staff-approved`) for every protected path except the control core; risky paths get a veto window; the control core stays with the owner** | Covers almost every case; the approval mechanism cannot approve its own removal | One more tier and a timer in the gate | High |

## How banks and production ledgers do it

- **Maker-checker / four-eyes** (EBA/GL/2019/04 section 3.6; DORA Art. 9(4)(e)): the
  approver must be independent of the maker. The staff engineer (separate
  agent and context) checks the implementer and the evaluator. It can be the
  checker for changes to the product and the delivery path.
- **Nobody approves changes to their own entitlements.** In identity and
  access management, the access recertification and the role that grants
  approval rights are held outside the role itself. The rules that give the
  staff engineer its approval power (the gate, the tier list, its remit, the
  deny rules) are exactly those entitlements.
- **Immutable records.** A posted ledger entry is never edited; it is
  reversed by a new entry (ADR 0004 and 0009 apply the same idea to
  postings). Release tags and `main` history are the system's audit trail
  and are treated the same way: no force-push and no moved tags, by anyone.
  Corrections go through a revert and a new tag.
- **Attribution.** An approval must record who actually gave it. An agent
  setting a label as the owner is a misattributed approval, and an audit
  would flag it.
- **Notify-and-veto for high-risk standard changes.** Many change processes
  let an automated approval go ahead after a notice period, during which the
  control owner can object. That is the veto window below.

## Decision

1. **Merging: the staff engineer approves, and the gate executes.** For task
   PRs, the evaluator approve remains enough (0001). For every other PR
   (flow, phase, protected), the staff engineer writes a record bound to the
   PR head SHA (`- PR:`, `- Approved head: <sha>`). The gate checks the SHA
   and the required checks, then auto-merges. No agent calls a merge
   endpoint. Without that split, the approval would never be checked.
2. **Pushing to `main`: the staff engineer approves it as a PR.** It lands
   through 1. Direct pushes stay blocked for every identity,
   `enforce_admins` included. A push skips CI and review, so no approval can
   make it safe.
3. **Force-push and moving tags: forbidden for every approver.** Corrections
   go forward only: a revert PR approved by the staff engineer, or a new
   patch tag `v0.X.1`. This is principle 1 (postings are never edited)
   applied to the history.
4. **Creating tags: the staff engineer approves.** Its phase-close record is
   the approval. `release.yml` creates the tag and closes the milestone after
   the checks in 0001 item 3 pass, Phase 10 included.
5. **Protected paths: option C.** The label `human-approved` stays the
   owner's alone; no agent adds it. A new mark, `staff-approved`, is a label
   or a check that **only the gate sets**. The gate sets it when the PR adds
   a record with `- Protected paths approved:` covering every touched
   protected path, bound to the head SHA, and the brief declares those paths.
   The gate accepts either `human-approved` or `staff-approved`. The paths
   fall into three tiers:
   - **Standard** (`delegable` in 0001): the existing POMs,
     `HexagonalArchitectureTest`, the convention skills. The gate merges once
     the checks pass.
   - **Veto window** (new tier `staff-veto`): `.github/workflows/ci.yml` and
     the other non-gate workflows, `mvnw`, `.mvn/**`,
     `architecture-rules/**`, applied changesets,
     `.claude/agents/{evaluator,test-runner}.md`, the flow skills
     (`run-task`, `plan-feature`, `status`), `scripts/verify.sh`,
     `scripts/test-summary.sh`, `scripts/cloud-session-start.sh`,
     `scripts/guard-paths.sh` and `CODEOWNERS`. The gate merges only after
     the approved head SHA has been open for **24 h** with no `needs-human`,
     and the agent comments on the PR when the window starts. The owner
     vetoes by adding `needs-human`. The evaluator check from 0001 still
     rejects weakened thresholds or guards unless the record justifies the
     change by name.
   - **Control core** (owner only, `human-approved`): `AUTONOMY.md`,
     `.agents/protected-paths.txt`, `scripts/autonomy-gate.sh`,
     `.github/workflows/autonomy-gate.yml`, `.github/workflows/release.yml`,
     `.claude/agents/staff-engineer.md`, `.claude/settings*.json` and
     `scripts/gh-rest.sh`. These files define who may approve and what the
     gate checks. If the staff engineer could approve them, one PR could
     remove every other safeguard, and the four-eyes would be gone with it.
     Applied changesets are in the veto tier, not the core. Liquibase
     checksums still fail an edited changeset in CI, so editing one is in
     practice a new changeset.

Fictitious data only stays a fixed project rule. 0001 items 4 (secrets,
settings, paid services), 6, 7 and 8 are unchanged.

Against the goal: no option weakens the money invariants, and immutable
history is kept as their analogue (1). Standard practice is followed:
maker-checker, nobody approving their own entitlements, immutable records,
notify-and-veto (2). Approvals are bound to a SHA and executed by the gate
(3). Every approval is a record plus a deterministic gate rule (4). Each
tier is one suffix in the path list. In mode A, `staff-approved` becomes a
check that only the gate workflow can post, which makes it hard (5).

## Consequences

The caller extends the single PR from 0001. It touches the control core, so
it needs `human-approved` from the owner. The PR changes:

- `AUTONOMY.md`:
  - "Always forbidden" becomes: direct pushes to `main`, force-push and
    moving or deleting tags (for every identity); calling a merge endpoint;
    adding or removing `human-approved` or `staff-approved` by any agent;
    real data;
  - a new section "Approvals": the staff engineer's record, `staff-approved`
    set by the gate, the three tiers, the veto window, and the control core
    staying with the owner.
- `.agents/protected-paths.txt`: the suffixes ` delegable` and
  ` staff-veto`; no suffix means control core.
- `scripts/guard-paths.sh`: parse the three tiers; `--check` reports the tier
  of each path; `--codeowners` lists only the control core.
- `scripts/autonomy-gate.sh`:
  - accept `staff-approved`, or check the record directly (SHA, path
    coverage, brief declaration);
  - set and clear `staff-approved` itself;
  - the 24 h window based on the approved head commit or the record time
    (deterministic, read through the API);
  - non-task PRs merge on a record with `- PR:` and `- Approved head:`.
- `.github/workflows/autonomy-gate.yml`: a `schedule` trigger (hourly) so
  that PRs whose veto window has ended get re-checked.
- `scripts/gh-rest.sh`: refuse `staff-approved` as well as `human-approved`.
- `.claude/settings.json`: deny rules for any label call that names
  `staff-approved`.
- `.claude/agents/staff-engineer.md`:
  - the approval remit: merges via record, protected paths of the delegable
    and veto tiers, tag creation through phase close;
  - hard limits: the control core, force-push or tag moves (no approval
    exists), settings, secrets, paid services, real data;
  - the record fields `- PR:`, `- Approved head:`,
    `- Protected paths approved:`.
- `.claude/agents/evaluator.md`: the record-vs-diff and guard-weakening
  checks from 0001.
- `.agents/autonomy-migration.md`: note that D1 (the `human-approved` part),
  D3 and D5 are amended by 0001 and 0002.

Owner actions:

- `human-approved` and merge of that PR.
- One-time settings: auto-merge, `Autonomy gate` required,
  `enforce_admins`, the `v*` ruleset (no update or delete).
- From then on, an optional veto: add `needs-human` during a 24 h window.
- Recommended: mode A.
