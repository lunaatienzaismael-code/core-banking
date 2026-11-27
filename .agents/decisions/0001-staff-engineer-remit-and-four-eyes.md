# 0001 — Staff engineer remit: what it takes over, and the four-eyes line it keeps

- Date: 2026-11-27
- Trigger: owner request to change the staff engineer's remit, relayed by the main agent ("dale más poderes al staff engineer, quiero que sea todo lo autónomo posible, que decida él")
- Task: none
- Status: Decided by staff engineer

## Question

Which of the current escalation limits and "Always forbidden" items in
`AUTONOMY.md` should the staff engineer take over, and how is each one
enforced, so the flow can run end to end without the owner?

Note on authority: the request reached me through the main agent, not from
the owner directly, and a relayed message is not the owner's consent. That
does not stop me deciding on the merits. It does fix how the decision takes
effect. Everything below changes `AUTONOMY.md`, `.claude/**`, the gate or
the workflows, which are all protected paths. So it takes effect only when
the owner adds `human-approved` to the PR that applies it and merges that
PR. That merge is the owner's real consent.

## Options

Each item had the same three options: keep as is (K), delegate to the staff
engineer (D), or delegate with a mechanical safeguard (S).

| # | Item | Option | Pros | Cons | Reversibility |
|---|------|--------|------|------|---------------|
| 1 | Merging PRs | K: owner merges all | Strongest control | Owner is the bottleneck on every task | High |
| | | D: staff engineer merges via REST | No owner needed | The same identity makes and checks the change; a REST merge bypasses the gate | Low (merged history stays) |
| | | **S: the gate auto-merges task PRs; no agent ever merges** | The evaluator (checker) is separate from the implementer (maker); the gate is deterministic and runs from `main` | Needs owner settings once | High |
| 2 | `human-approved` / protected paths | K: owner for every path | Simple | Phase 8 needs the root POM and per-module ArchUnit tests over and over | High |
| | | D: staff engineer adds `human-approved` | Fully autonomous | Self-approval: the flow would approve changes to its own guards. In mode B nobody could tell the owner's label from the agent's | Low |
| | | **S: split the paths into owner-only and `delegable`; the gate accepts a staff-engineer record for delegable paths only** | Routine build changes go ahead; guards stay four-eyes with a human | Gate and guard script change; still soft in mode B | High |
| 3 | Push to `main`, force-push, tags | D: agent pushes or tags | — | Rewrites or forges the audit trail | None |
| | | **S: push and force-push stay forbidden for everyone; phase tags are created by a release workflow from `main` after mechanical checks; tags are immutable** | Tag created by the pipeline, tied to a merged phase-close PR | One new workflow and a tag ruleset | High |
| 4 | Secrets, settings, paid services | D: agent changes them through the API as the owner | — | The agent would be editing the controls that bind it; paid services spend the owner's money | Low |
| | | **K, plus hardening: deny raw `gh api` and settings commands** | Controls stay with their owner | Owner stays in the loop for rare setup work | High |
| 5 | Phase end | K: owner merges plan PR | Owner sets scope | Owner needed once per phase | High |
| | | **S: staff engineer approves plan and close PRs for phases already in the roadmap; the gate merges them only within mechanical limits** | Runs on through roadmap phases | Gate gains two more branch types | High |
| 6 | 3 rounds / CI red twice | K: stop | Simple | One stuck task stops the whole run | High |
| | | **S: staff engineer re-scopes, grants a bounded extension, or blocks and continues; it never overrides CHANGES or red CI** | The run goes on; the merge criteria do not change | Caps needed | High |
| 7 | Possibly real data | **K** | GDPR and the project rule | — | — |
| 8 | Future changes to `AUTONOMY.md` and to this remit | D: staff engineer changes its own remit | — | An agent widening its own authority is the classic segregation-of-duties failure | Low |
| | | **K: staff engineer may propose; the owner approves** | The one person who holds the authority keeps it | Owner needed for policy PRs | High |

## How banks and production ledgers do it

- **Four-eyes / maker-checker.** In core banking, no change and no payment is
  both made and approved by the same principal. The EBA Guidelines on ICT and
  security risk management (EBA/GL/2019/04, change management and
  segregation of duties) and DORA (Regulation (EU) 2022/2554, Art. 9) require
  changes to ICT systems to be tested and approved by someone independent of
  whoever made them. Separate agents with separate contexts (implementer vs
  evaluator vs staff engineer) are a reasonable checker for product code.
  They are not independent from each other when the change is to the rules
  that govern them, and in mode B they all act as the owner's identity.
- **Controls are owned outside the controlled function.** Banks keep
  privileged access, secrets (HSM and vault custody), pipeline and
  branch-protection configuration, and spending authority with a separate
  function, usually Security or Platform with a CAB sign-off. Nobody approves
  changes to their own access rights.
- **Standard vs normal changes (ITIL change management).** Changes that are
  pre-authorised, low-risk and repeatable ("standard changes") go through an
  automated pipeline without a CAB. Anything that alters a control is a
  "normal" change that needs human approval. That is the `delegable` /
  owner-only split below.
- **Immutable audit trail.** Production releases are tagged by the release
  pipeline from an approved, merged change, not by hand, and the tags cannot
  be moved. History is never rewritten: no force-push, and protected `main`
  applies to admins too.
- **Data protection.** GDPR (Art. 5, data minimisation) and banking secrecy:
  real customer or employer data never enters a test or demo system.

A reviewer from an EU bank reading this history will check whether the
agent could approve its own controls. "The automation runs the standard path
end to end, and changes to the guardrails go to a human" reads as mature.
"The agent has every power" reads as missing segregation of duties.

## Decision

The guiding line: **the staff engineer takes over every judgment call on the
product and the delivery path. The owner keeps only the controls that bind
the agents (guards, settings, secrets, policy, remit) and anything that would
rewrite the audit trail.** Approvals become machine-checked records that the
gate reads, never labels that the agent adds.

1. **Merging: S.** The gate's auto-merge is the only way a PR merges. That
   holds for task PRs and for the phase PRs in item 5. No agent, the staff
   engineer included, ever merges through REST or `gh`. The four eyes are the
   implementer plus the evaluator, bound to a SHA and to green required
   checks. The owner does a one-time setup: enable Allow auto-merge, make
   `Autonomy gate` a required check, and turn on `enforce_admins`. Until
   then, the owner merges.
2. **Protected paths: S.** Each entry in `.agents/protected-paths.txt` gets a
   tier.
   - **`delegable`** (standard changes): `**/pom.xml existing`,
     `**/HexagonalArchitectureTest.java`, and the convention skills
     `.claude/skills/{java-hexagonal,java-testing,kafka-events,persistence-postgres-liquibase,spring-boot-conventions,task-planning}/**`.
   - **Owner-only**: `.github/workflows/**`, `mvnw`, `.mvn/**` (supply
     chain), `architecture-rules/**` (the shared guard), applied changesets
     (never edited; write a new changeset), and the flow itself
     (`AUTONOMY.md`, `.claude/agents/**`, `.claude/settings*.json`, the flow
     skills `run-task`, `plan-feature` and `status`, the
     `.agents/protected-paths.txt` list, `CODEOWNERS`, the guard, gate, REST
     and test scripts).

   The gate passes a PR whose protected paths are **all** `delegable` when
   every one of these holds:
   - the brief declares them under `## Protected paths`;
   - the PR adds a record in `.agents/decisions/` with `- Task: <its id>`,
     `- Status: Decided by staff engineer` and a
     `- Protected paths approved:` line that covers each of them;
   - the other conditions of the task path hold (evaluator approve on the
     SHA, the SHA rule).

   The evaluator gets a check: the record matches the diff, and a delegable
   POM change must not lower or remove the JaCoCo or PIT thresholds, the
   Spotless or ArchUnit wiring, or the enforcer rules. Weakening a guard
   through a delegable file is owner-only. `human-approved` stays the owner's
   alone, and no agent ever adds it. `CODEOWNERS` is generated from the
   owner-only entries only.
3. **Push to `main` and force-push: K, forbidden for everyone.** Branch
   protection with `enforce_admins` binds the owner too. **Tags: S.** The
   agent never runs `git tag`. A new `release.yml` workflow
   (`workflow_dispatch`, run from `main`) creates the annotated `v0.X.0` tag
   on the merge commit of the merged `chore/phase-<n>-close` PR, writes the
   release notes and closes the milestone. Before it does, it checks
   mechanically:
   - that PR is merged;
   - the milestone has no open issue;
   - the tag does not exist yet.

   The agent triggers the workflow through a new `scripts/gh-rest.sh release
   <n>` subcommand. `workflow_dispatch` is used because merges done with
   `GITHUB_TOKEN` do not trigger other workflows. A tag ruleset on `v*`
   blocks update and deletion for everyone and allows creation only by
   GitHub Actions. If the ruleset cannot exempt `GITHUB_TOKEN`, the fallback
   is a fine-grained PAT secret set by the owner.
4. **Secrets, settings, paid services: K, escalate.** Changing them means
   editing the controls themselves, and paid services spend the owner's
   money. Hardening: deny `Bash(gh api:*)`, `Bash(gh secret:*)`,
   `Bash(gh repo edit:*)`, `Bash(gh ruleset:*)` and
   `Bash(gh workflow enable/disable:*)`. GitHub access goes only through the
   closed set of `scripts/gh-rest.sh`, which has no settings, secrets or
   protection endpoints and must never get them.
5. **Phase end: S, for phases already listed in `docs/roadmap.md`.** The
   staff engineer reviews the next-phase plan PR (`docs/phase-<n>-plan`)
   against the roadmap entry and `docs/spec.md`, and writes a record that
   approves it. The gate merges a plan PR only when all of these hold:
   - it touches only `docs/adr/**` and `.agents/**`;
   - it adds an approving record with `- Phase: <n>`;
   - it is not a draft and has no `needs-human`.

   The gate merges a phase-close PR (`chore/phase-<n>-close`) only when all
   of these hold:
   - its milestone has no open issue;
   - its `docs/roadmap.md` diff only flips that phase's checkboxes;
   - the rest touches only `.agents/`.

   Changing `docs/spec.md` (scope and ACs), adding, removing or reordering
   phases, and closing the **last** phase (Phase 10, the `v1.0` showcase)
   stay with the owner (`needs-human`).
6. **Stuck task: S.** After round 3 with CHANGES, or CI still red after 2 fix
   attempts, the main agent asks the staff engineer
   (`trigger: review-exhausted | ci-red`). The staff engineer decides one of:
   - (a) amend or split the brief (new tasks);
   - (b) grant one extension of at most 2 more rounds or attempts, when the
     remaining defects are narrow and named;
   - (c) mark the task `blocked`, leave its draft PR with
     `Verdict: CHANGES`, and go on with the next ready task that does not
     depend on it.

   It never overrides a CHANGES verdict and never accepts red CI. It never
   quarantines or skips tests, and never lowers coverage or mutation
   thresholds. The merge criteria stay mechanical. The run stops with
   `needs-human` only when no ready task is left, or when a CI failure is in
   infrastructure that needs settings or secrets.
7. **Possibly real data: K.** If fictitious data is enough and nothing has
   been committed yet, the agent just uses fictitious data. That is not a
   decision. If possibly real data is already committed or pushed, or the
   task requires it, the run stops: cleaning it up may need a history
   rewrite, and that is the owner's.
8. **Future changes to `AUTONOMY.md` and to the staff engineer's remit:
   K.** The staff engineer may write a record that proposes a change (status
   `Proposed by staff engineer`). The PR that applies it is labelled
   `needs-human` and needs `human-approved`, as these files are owner-only
   paths. This record is the precedent: owner-level policy follows the same
   route.

Against the goal: money is not affected (1). Items 1, 2, 3 and 5 follow
standard-change automation with a human four-eyes check on controls, and
pipeline-made immutable tags (2). Approvals bound to a SHA and to records
keep the delivery path consistent and replayable (3). Every delegated
approval is a file in the history and every rule is checked by the gate, so
it is deterministic and reviewable (4). Each tier can be moved later by
editing one list, and mode A turns the soft checks into hard ones (5). Only
the paths, triggers and gate branches the current phases already hit are
added (6).

## Consequences

The caller now prepares **one PR**, `chore/T-xxx-staff-engineer-remit` or the
flow branch, that touches protected paths. It needs `human-approved` from the
owner. Split it into atomic commits:

- `AUTONOMY.md`:
  - "What the agent does without stopping": the gate merges both task PRs and
    phase PRs;
  - a new "Delegated approvals" section (records, not labels; the delegable
    tier);
  - "Stop conditions": only no ready task left, settings or secrets, real
    data, escalation, and the last phase;
  - "End of phase": items 3 and 5;
  - "Always forbidden": add "changing settings, secrets or this policy" and
    "granting oneself authority"; tags now come only from `release.yml`;
  - the rollout table.
- `.claude/agents/staff-engineer.md`: new triggers `review-exhausted`,
  `ci-red`, `phase-plan`, `phase-close`, and the `delegable-path` approval
  together with the `- Protected paths approved:` and `- Phase:` lines.
  Hard limits: settings, secrets, paid services, real data, owner-only paths,
  its own remit or the policy (propose only), Phase 10 close.
- `.agents/protected-paths.txt`: the ` delegable` suffix. `.claude/**`
  becomes explicit entries.
- `scripts/guard-paths.sh`: parse the tier; `--check` reports which matched
  paths are delegable; `--codeowners` lists only owner-only entries.
- `scripts/autonomy-gate.sh`: the delegable-record path, the
  `docs/phase-<n>-plan` and `chore/phase-<n>-close` branch rules with their
  mechanical checks, and the header.
- `.github/workflows/release.yml` (new) and `scripts/gh-rest.sh`: a
  `release <n>` subcommand that dispatches it. Still no merge, settings or
  label subcommand.
- `.claude/settings.json`: the deny rules from item 4.
- `.claude/skills/run-task/SKILL.md` and `.claude/skills/plan-feature/SKILL.md`:
  call the staff engineer at review exhaustion, CI red and phase end.
- `.claude/agents/evaluator.md`: the record-vs-diff check and the
  guard-weakening check from item 2.
- `.github/CODEOWNERS`: regenerate.
- `.agents/autonomy-migration.md`: a note that D3 and D5 are amended by this
  record.

Owner actions:

- `human-approved` and merge on that PR. This is what makes the decision take
  effect.
- One-time settings: Allow auto-merge, `Autonomy gate` required,
  `enforce_admins`, and the `v*` tag ruleset (no update or delete; creation
  only by Actions, or set a PAT secret if Actions cannot be exempted).
- Recommended next, not required: move to mode A (bot account, Write not
  admin), so that `human-approved` and the delegated records are hard
  controls rather than soft ones.
