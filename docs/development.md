# Development environments

How to get the build green on a developer machine and in a Claude Code cloud
session. Commands are in [`CLAUDE.md`](../CLAUDE.md); the agent flow is in
[`AUTONOMY.md`](../AUTONOMY.md).

## Local (macOS, Rancher Desktop)

- Integration tests need Docker running: `rdctl start`.
- Testcontainers with Rancher Desktop needs
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` (Ryuk). Set it
  in your shell or in `.claude/settings.local.json` under `env`.
- If the VM shows "Broken" ("vz driver is running but host agent is not"),
  delete the stale `lima/0/vz.pid` under
  `~/Library/Application Support/rancher-desktop/`, then
  `rdctl shutdown && rdctl start`.
- `scripts/e2e.sh` has stalled locally while `docker build` ran
  `dependency:go-offline`. CI is the reference for the e2e suite.
- zsh does not word-split `$var` in `for` loops; pass explicit arguments.

## Cloud session (Claude Code on the web)

Found in the T-033 pilot (`.agents/tasks/T-033/pilot.md`). Each item broke the
build until it was fixed:

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Could not transfer artifact io.confluent:… packages.confluent.io` | Host not on the default allowlist | Environment network: allow `packages.confluent.io` |
| `packages.confluent.io: Name or service not known` | Maven's resolver ignores the JVM proxy properties in `JAVA_TOOL_OPTIONS`; only the proxy resolves DNS | Generate `~/.m2/settings.xml` with a `<proxy>` from `HTTPS_PROXY` at session start (the port changes) |
| `SchemaCompatibilityTest`: `No release tag found` | The cloud clone has no tags | `git fetch --tags origin` |
| `Could not find a valid Docker environment` | `dockerd` is not running | Start `dockerd` |
| `429 Too Many Requests` from Docker Hub | Anonymous pulls from a shared egress IP | Registry mirror `mirror.gcr.io` in `/etc/docker/daemon.json` |
| `docker build` cannot download Maven | The build container gets neither the proxy nor its CA | Not fixed: e2e runs in CI (issue #76) |
| `git push` → `403 … Claude doesn't have GitHub access` | Claude GitHub App not installed on the repository | Install the App on the repository |
| `gh pr …`, `gh issue …`: `GitHub GraphQL is not available` | The GitHub proxy blocks GraphQL | Use `gh api` with REST endpoints |

Rows 2–5 are handled per session by the `SessionStart` hook
`scripts/cloud-session-start.sh`. It acts only when `CLAUDE_CODE_REMOTE` is
`true` (set in cloud sessions, never in the local CLI), so it never touches a
local `~/.m2/settings.xml` or Docker. It cannot be the environment's setup
script, because that script is cached as a filesystem snapshot: the proxy port
changes between sessions and `dockerd` would not be running. Row 1 is an
environment network setting. With these fixes, `./mvnw verify` passed in the
VM (446 tests, T-033 pilot).

## Cloud environment and routine (configured by the owner)

- Environment `ledgerflow`: network **Limited** with "Allow package
  managers" plus `packages.confluent.io`; `BASH_DEFAULT_TIMEOUT_MS=600000`,
  `BASH_MAX_TIMEOUT_MS=1800000`. No setup script is needed: the session hook
  does the per-session work.
- Routine "LedgerFlow run-task": repository `core-banking`, environment
  `ledgerflow`, prompt
  `Run /run-task; follow AUTONOMY.md; push only to the branch it names`,
  schedule trigger only, `7 9,20 * * *` in Europe/Madrid. The GitHub trigger
  `pull_request.closed` comes later (`.agents/autonomy-migration.md`, 3.8).
- Labels the flow uses: `needs-human`, `agent:running` and `human-approved`
  (the last one is only ever set by the owner).
