# Autonomy migration plan

> Amended on 2026-11-27 by [record 0003](decisions/0003-owner-delegates-all-approvals.md):
> the staff engineer approves everything the owner used to approve (D1, D3, D5).

Plan to move LedgerFlow's working flow to an autonomous mode that runs in
Claude Code cloud sessions with scheduled routines, on the Pro plan. Agreed
with the user on 2026-11-20. **Only the flow changes**: no change to the
architecture or the domain code.

Rule for this migration and after it: nothing the flow needs may live only in
`.session/` or in a session's local memory. Cloud sessions start from a fresh
clone and do not see either.

Status: **step 0 (pilot) done** on 2026-11-20 — T-033 merged as PR #75.
PRs 1–5 opened as #77–#80 and the PR 5 code part, stacked; the owner's
switch-on follows once they are on `main`. Update this line as steps
complete.

## 1. Facts verified before planning (2026-11-20)

From the Claude Code docs on cloud sessions, cloud environments and scheduled
tasks (links at the end):

1. **`gh pr` and `gh issue` do not work in cloud sessions.** The GitHub proxy
   rejects GraphQL with a 403; `gh api` with REST works. Enabling auto-merge
   is GraphQL-only, so it must happen in GitHub Actions (section 3.7). The
   flow's PR creation, `/status`, `/next-task` step 0 and
   `scripts/check-task-sync.sh` must move to REST. Confirmed by the pilot:
   PR creation, comments and labels through `gh api` REST work;
   `check-task-sync.sh` fails with `HTTP 403: GitHub GraphQL is not available
   from Claude Code sessions`.
2. **The agent acts as the owner's GitHub account (admin).** With
   `enforce_admins` off, a run could push to `main`; a required code-owner
   review is unusable because the owner cannot approve "their own" PR.
3. **Every session starts from a fresh clone.** Bookkeeping can no longer
   "ride uncommitted" to the next branch; it is committed inside the PR. The
   post-merge `done` mark cannot go in the task's own PR, and a direct commit
   on `main` would bypass the PR rule: it is committed by the **next task's
   reconciliation** (section 3.2, step 0).
4. **The VM** has Docker, 4 vCPU, 16 GB RAM, 30 GB disk. Commands run up to
   10 min in the foreground and 30 more in the background.
   `packages.confluent.io` is not on the default allowlist. The pilot found
   that `docker build` has **no network inside the build container** (the
   agent proxy and its CA are not passed in), so the e2e suite cannot run in
   the VM (section 4, issue #76).
5. **Routines** start in auto mode, push to `claude/…` branches unless the
   prompt names another branch, support a GitHub trigger
   `pull_request.closed` filtered to merged PRs, and have hourly caps. On Pro
   the real limit is subscription usage.
6. **User-level memory (`~/.claude`) does not reach cloud sessions.**
7. A `Stop` hook cannot run the full suite: it fires at every turn end and
   hooks time out after 600 s by default.

## 2. Decisions

| Id | Decision | Choice |
|----|----------|--------|
| D1 | GitHub identity of the agent | **B now** (same account as the owner); move to **A** (bot account) once the pilot works |
| D2 | How the evaluator proves the full suite ran | It **verifies runs bound to the commit SHA**: the local `verify` (unit + ITs) and the CI **End-to-end tests** job on the PR head SHA (read through REST); it does not re-run the suite. Commits after the reviewed SHA that touch only `.agents/` keep it valid |
| D3 | Edits to protected paths | Allowed only when the task brief declares them under `Protected paths:`; the PR then needs human approval |
| D4 | Protect the flow files too | **Yes** (`AUTONOMY.md`, `.claude/**`, guard scripts, `gh-rest.sh`, `test-summary.sh`, `autonomy-gate.sh`, `protected-paths.txt`, `CODEOWNERS`) |
| D5 | Phase gate | The owner's merge of the next-phase plan PR approves the phase |

D1 details (owner's adjustments):

- In B, **`Autonomy gate` is a required status check** on `main`, not just a
  switch for auto-merge. It fails when a PR touches protected paths without
  human approval. With `enforce_admins` on, a manual REST merge is blocked
  too.
- **Human approval = the label `human-approved`**, added by the owner in the
  GitHub UI. Every new push (`synchronize`) removes it, so an approval only
  covers the code that was reviewed.
- `AUTONOMY.md` must state that in B this mark is **soft**: the agent shares
  the owner's identity, so GitHub cannot tell who added the label. It is
  backed only by deny rules, `scripts/gh-rest.sh` refusing that label, and
  the policy. In A it is replaced by a code-owner review the bot cannot give.

Order and rollout:

- **Pilot first**, before PR 1 (section 4) — done.
- **Routine: schedule trigger only** at first, 1–2 runs a day. Add
  `pull_request.closed` once the usage per task is known and it is confirmed
  that the trigger fires for merges done by `GITHUB_TOKEN`.

## 3. Target design

### 3.1 `AUTONOMY.md` — single source of the autonomy policy

- What the agent does without stopping: implement, commit, push to
  `<type>/T-xxx-slug`, open the PR; merge happens through the gate workflow
  when the PR carries `Verdict: APPROVE` and CI is green.
- Reversible ADRs: the agent decides, status `Proposed by agent`, with a
  trade-off table and how banks / production ledgers do it.
- ADRs that stop the run (domain, security, irreversible): money
  representation, ledger invariants or posting semantics; security and
  authentication; changes to applied changesets or data migrations; breaking
  public contracts (OpenAPI, Avro without BACKWARD compatibility); new
  infrastructure or dependencies.
- Stop conditions: 3 review rounds without approval; brief wrong or too big;
  undeclared protected path; CI red after 2 fix attempts; secrets or repo
  settings; real data; end of phase.
- How to stop: comment on the issue, label it `needs-human`, regenerate
  `STATE.md`.
- End of phase: the agent opens a phase-close PR (roadmap, STATE) and a PR
  with the next phase's plan (ADRs, briefs, `tasks.json`), both labelled
  `needs-human`. Merging the plan approves the phase (D5); the first run after
  it creates the issues and the milestone.
- Always forbidden: merging directly, pushing to `main`, force-pushing,
  creating tags (the owner tags at phase close).
- Identity mode (B or A) and the soft nature of `human-approved` in B.
- Remove the contradicting rules and point to `AUTONOMY.md`: `CLAUDE.md`
  ("Working rules", "Per-task flow"), `plan-feature` steps 2 and 4,
  `.agents/README.md`; `/next-task` is replaced by `/run-task`.

### 3.2 Skill `/run-task` (model-invocable)

`.claude/skills/run-task/SKILL.md`, without `disable-model-invocation`:

0. Lock and reconcile through REST: if a task PR is open, exit "waiting" (or
   fix it if its CI is red); label the issue `agent:running`; mark merged
   tasks `done` in `tasks.json` and `progress.md`. This post-merge
   bookkeeping is committed on the new task's branch, in its last
   `chore(agents)` commit (step 6), never directly on `main`.
1. Select the first `todo` task of the phase whose dependencies are `done`;
   none left → end-of-phase procedure, stop.
2. Branch `<type>/<id>-<slug>` (the routine prompt names it, to avoid
   `claude/`).
3. Implement with the skills.
4. `test-runner` (unit + ITs; e2e runs in CI, section 3.4).
5. `evaluator`, at most 3 rounds.
6. Atomic commits plus a last `chore(agents)` commit with `tasks.json`
   (`in_review`), `progress.md` and `STATE.md`.
7. PR through REST with the template and an exact line `Verdict: APPROVE` or
   `Verdict: CHANGES`, plus the verdict comment. After 3 rounds without
   approval: draft PR with `CHANGES` and `needs-human`.
8. On any stop: comment on the issue with `needs-human` and the reason;
   regenerate `STATE.md`.

Support: `scripts/gh-rest.sh` with closed subcommands (`open-pr`, `comment`,
`label`, `list-prs`, `pr-state`, plus what `check-task-sync.sh` needs to
list issues and PRs, and `checks` for the CI runs of a SHA), so permissions
cover the wrapper instead of all of `gh api`. **Every** use of `gh pr` and
`gh issue` moves to it: `scripts/check-task-sync.sh`, the `/status` and
`/next-task` skills and the `gh pr`/`gh issue` permissions in
`.claude/settings.json`.

### 3.3 `.agents/STATE.md` replaces `.session/HANDOFF.md`

- Generated by `scripts/state.sh` from `tasks.json`, the tail of
  `progress.md` and GitHub (open PRs, `needs-human` issues). At most ~30
  lines: phase, active task, open PR, blocks, open questions (= `needs-human`
  issues, no free text that goes stale).
- From `.session/`: the three pending proposals become issues or are folded
  into this plan. Ledger `ConstraintViolationException` → 400 is done
  (T-033); agent tuning (`test-runner` quoting errors verbatim, a minimum
  review depth for the evaluator) is folded into section 3.4; the Confluent
  repository order in the root POM becomes an issue; local and cloud environment
  notes go to `docs/development.md`; decision history is already in the ADRs
  and `progress.md`; `ORCHESTRATION-PLAN.md` duplicates `CLAUDE.md` and
  `.agents/README.md` and is dropped.

### 3.4 `evaluator` and `test-runner`

- `scripts/test-summary.sh` adds `commit` (SHA) and `dirty`. No local
  `full` profile for now: the pilot showed `docker build` has no network in
  the cloud VM, so **e2e is validated in CI on the PR head SHA**. Running it
  in the VM (build the jars outside Docker, or pass proxy + CA to
  `docker build`) is issue #76, for later.
- The evaluator approves only with `status=pass`, `dirty=false`, the local
  run's `commit` equal to the reviewed SHA, and the CI **End-to-end tests**
  job on that SHA not red (read through REST); it reads every changed file in
  full.
- **SHA rule:** commits after the reviewed SHA are accepted only when
  `git diff --name-only <reviewed>..HEAD` lists paths under `.agents/` alone
  (the bookkeeping commit and `review-N.json`, which cannot exist before its
  own review). Anything else needs a new review round. The gate script
  (section 3.7) checks it, since the evaluator never sees the final HEAD.
- `review.schema.json` gains `evidence[]`: per brief criterion
  `{criterion, file, line, test}`; missing evidence = `not_met`.
- `test-runner`, when it builds the JSON itself, copies the last 20
  `[ERROR]` / `Caused by` lines **verbatim** into `verbatim` and never
  diagnoses.

### 3.5 Hooks and permissions (`.claude/settings.json`)

- `Stop` → `scripts/verify.sh`: exit 0 when `stop_hook_active` is true or
  nothing changed versus `origin/main`; otherwise `spotless:check` and
  `verify -DskipITs` on the touched modules (unit + ArchUnit); on failure a
  summary on stderr and exit 2.
- `PreToolUse` (Edit, Write, NotebookEdit) → `scripts/guard-paths.sh`,
  reading `.agents/protected-paths.txt` (single list for the hook, the gate
  workflow and `CODEOWNERS`; `scripts/guard-paths.sh --check-codeowners`
  detects drift, not yet wired into `verify.sh` or CI):
  - `.github/workflows/**`;
  - every `pom.xml` that already exists on `origin/main` (new module POMs
    allowed);
  - the Maven wrapper: `mvnw`, `.mvn/**`;
  - `architecture-rules/**`, `**/HexagonalArchitectureTest.java`;
  - `**/db/changelog/changes/*.yaml` already on `main` (new changesets and
    the master changelog allowed);
  - the flow files (D4).

  Exception (D3): paths declared in the task brief under `Protected paths:`.
  Phase 8 will need it for the root POM (new modules): expect 1–2 stops. The
  hook does not see edits made through Bash; the gate workflow is the real
  barrier.
- `allow`: `git add`, `git commit`,
  `git push -u origin {feat,fix,test,build,docs,chore,refactor,ci}/*`,
  `scripts/gh-rest.sh:*`, `scripts/state.sh`.
- `deny`: `git push --force*`, `git push -f*`, `git push origin +*`,
  `git push --delete*`, `git push origin main*`, `git push origin HEAD:main*`,
  `gh pr merge*`, `gh api*merge*`, `gh api*human-approved*`, `git tag*`.

### 3.6 `CODEOWNERS` and GitHub settings (done by the owner)

`.github/CODEOWNERS` assigns the protected paths to the owner. Settings:

- General: enable **Allow auto-merge**; keep rebase-only merges and branch
  auto-delete.
- `main` protection: required checks **Build and test**, **End-to-end
  tests**, **Mutation testing**, **Docker image**, and — only after PR 4 is
  merged, or no check is ever reported — **Autonomy gate**; enable
  **`enforce_admins`**; keep `strict` and linear history.
- **Prerequisite:** the **Claude GitHub App installed** on the repository,
  not only authorized on the account. Without the installation every push
  from a cloud session fails with `403 … Claude doesn't have GitHub access`
  (seen in the pilot). Also needed for routine GitHub triggers and PR
  auto-fix. Done on 2026-11-20.
- Tag ruleset `v*`: owner only.
- B: "Require review from Code Owners" **off** (the owner cannot approve
  their own PR); the gate enforces protected paths. A: a bot account with
  Write (not admin) connected with `/web-setup`, and "Require review from
  Code Owners" **on**. To verify in A: which identity routines use when the
  App and `/web-setup` coexist.
- Accepted cost: an auto-merge done with `GITHUB_TOKEN` does not trigger the
  `push: main` CI. With `strict` and rebase merges the tree that lands is the
  one tested in the PR; a fine-grained PAT secret would restore CI on `main`.
- Escape hatch if the gate itself breaks: the owner disables the protection
  temporarily.

### 3.7 Workflow `.github/workflows/autonomy-gate.yml`

- Job name **`Autonomy gate`**, a required check.
- Runs on **`pull_request_target`**, not `pull_request` (changed while
  writing PR 4). With `pull_request` the workflow and the scripts come from
  the PR itself, so a PR could rewrite the gate that judges it.
  `pull_request_target` runs them from `main` and reads the PR only as data
  through the API. The cost is that the gate acts only once it is on `main`,
  which matches making it required after PR 4 is merged. Logic in
  `scripts/autonomy-gate.sh` (protected); `--dry-run` runs it locally.
- Runs on **every** event of those types: `opened`, `edited`, `synchronize`,
  `reopened`, `labeled`, `unlabeled`, `ready_for_review`,
  `converted_to_draft`; no `paths` filter, so the required check never stays
  pending.
- `permissions: contents: write` (enabling auto-merge needs it) and
  `pull-requests: write` (to remove `human-approved` and add `needs-human`).
- On `synchronize`: remove `human-approved` and evaluate as if it were
  already gone. A label removed with `GITHUB_TOKEN` does not start a new run.
- **Passes** when either:
  - the PR touches no protected path, the branch matches
    `<type>/T-\d{3}-…` from this repository, the PR is not a draft, has no
    `needs-human`, the body has `^Verdict: APPROVE$`, the latest
    `review-N.json` on the branch says `approve`, and the SHA rule holds
    (section 3.4: only `.agents/` paths changed since its reviewed SHA, so
    `review.schema.json` gains `commit`) → it also runs
    `gh pr merge --auto --rebase`;
  - or the PR carries `human-approved`.
- **Fails** otherwise, including protected paths without the label; it
  disables auto-merge and adds `needs-human` when the cause is protected
  paths.
- Known limit: the verdict is written by the same agent; the real guarantees
  are the required CI, the protected paths and the owner's review at phase
  end.

### 3.8 Cloud environment and routine

- Environment `ledgerflow` (created on 2026-11-20; network and variables set
  by the owner): network **Limited** with "Allow package
  managers" plus `packages.confluent.io`; `BASH_DEFAULT_TIMEOUT_MS=600000`,
  `BASH_MAX_TIMEOUT_MS=1800000`.
- Per-session preparation: **a `SessionStart` hook, not the setup script**
  (changed while writing PR 5). The setup script is cached as a filesystem
  snapshot: files survive but running processes do not, and the proxy port
  changes between sessions. `scripts/cloud-session-start.sh` does the
  pilot's three fixes at every cloud session start (Maven proxy from
  `HTTPS_PROXY`, `git fetch --tags`, `dockerd` with the `mirror.gcr.io`
  mirror). It acts only when `CLAUDE_CODE_REMOTE` is `true`, which the docs
  define as set in remote web environments and never in the local CLI, so it
  leaves a developer's `~/.m2` and Docker alone.
- Setup script: none for now. Later, once timings are known, it can warm the
  cache (`./mvnw dependency:go-offline`, `docker pull` of the Testcontainers
  images), kept under 5 min so it is cached.
- Routine "LedgerFlow run-task": prompt "Run /run-task; follow AUTONOMY.md;
  push only to the branch it names". Schedule only at first:
  `CRON_TZ=Europe/Madrid 7 9,20 * * *` (09:07 and 20:07, off the hour as the
  docs advise).
- Later: GitHub trigger `pull_request.closed`, merged, base `main`. Check
  first (in PR 4) that it fires for a merge done by the gate with
  `GITHUB_TOKEN`: a routine with only that trigger and a trivial prompt, one
  test merge, then its History. Expected to fire (the `GITHUB_TOKEN` rule
  applies to Actions workflows, not App webhooks), but unverified.

## 4. Step 0 — pilot (done, 2026-11-20)

Task **T-033** (issue #74, PR #75), run with the current flow in a cloud
session started by hand on `fix/T-033-ledger-constraint-violation-400`.
Full log: `.agents/tasks/T-033/pilot.md`.

Result:

- `./mvnw verify` **passed in the VM** (446/446: 317 unit, 129 IT) once the
  three setup fixes in section 3.8 were applied by hand. Peak memory ~670 MB
  of 16 GB; disk use under 20 %.
- `docker build` (and so `scripts/e2e.sh`) **fails in the VM**: the build
  container has no route out except through the agent proxy, which is not
  passed in, and its CA is not in the image. Fixing it means build args or
  Dockerfile changes — issue #76, `needs-human`, for later.
- Evaluator round 1 approved. PR, verdict comment and labels **worked
  through REST**; `check-task-sync.sh` failed on GraphQL (403).
- CI on the PR head: 4/4 checks green in ~14 min, **e2e included**.
- The PR was merged by the agent through REST (`PUT …/pulls/75/merge`,
  `merge_method=rebase`, `sha` guard matching HEAD). It proves a REST merge
  works and that `enforce_admins` is still off; it is a one-off of the
  pilot, and the target policy is unchanged: merges happen only through the
  gate's auto-merge (section 3.7).
- The bookkeeping commit after the review moved HEAD past the reviewed SHA.
  Resolved by the SHA rule in section 3.4.
- Usage: **not measured**. The first figure will come from the first routine
  run.

Decisions taken from it (applied above):

| Finding | Decision |
|---|---|
| `docker build` has no network in the VM | e2e is validated in CI on the PR head SHA, not in the VM (D2, section 3.4); #76 later |
| Maven proxy, missing tags, `dockerd` off, Docker Hub 429 | Setup script from the pilot's candidate (section 3.8) |
| Push 403 until the App was installed | GitHub App installed on the repo is a prerequisite (section 3.6) |
| Bookkeeping commit after the review | The SHA check accepts later commits that touch only `.agents/` (section 3.4) |
| `done` cannot be marked inside the merged PR | Post-merge bookkeeping goes in the next task's reconciliation (section 3.2, step 0) |
| `check-task-sync.sh` and `gh pr`/`gh issue` fail on GraphQL | Port all of them to REST through `scripts/gh-rest.sh` (PR 2) |

## 5. PR order

Each PR is merged by the owner: all of them touch paths that become
protected.

1. **Policy and state**: this file; `AUTONOMY.md`; remove contradicting rules
   from `CLAUDE.md`, the skills and `.agents/README.md`; `STATE.md` and
   `scripts/state.sh`; move what is still needed out of `.session/`
   (section 3.3); decisions D1–D5, the pilot-first order and the
   schedule-only routine recorded in the repo (this file, plus an ADR if the
   owner wants one).
2. **Flow**: `/run-task`, `scripts/gh-rest.sh`, `check-task-sync.sh` and
   every `gh pr`/`gh issue` use (`/status`, `/next-task`, settings) on
   REST, `test-summary.sh` fields (`commit`, `dirty`), the SHA rule,
   evaluator and test-runner changes, `review.schema.json` `evidence` and
   `commit`.
3. **Barriers**: hooks, `scripts/guard-paths.sh`, `scripts/verify.sh`,
   permissions, `.agents/protected-paths.txt`, `CODEOWNERS`.
4. **Gate**: `autonomy-gate.yml` (section 3.7); then the
   `pull_request.closed`-with-`GITHUB_TOKEN` check (section 3.8).
5. **Switch-on**: in the repo, the `SessionStart` hook, the routine and
   environment settings in `docs/development.md`, and T-034 as the first
   task the routine picks. Then the owner: GitHub settings (section 3.6), make
   `Autonomy gate` required, and the schedule-only routine; later move to
   D1 = A when it is worth it.

## Sources

- [Use Claude Code in the cloud](https://code.claude.com/docs/en/claude-code-on-the-web)
- [Configure cloud environments](https://code.claude.com/docs/en/cloud-environments.md)
- [Scheduled tasks (routines)](https://code.claude.com/docs/en/routines.md)
