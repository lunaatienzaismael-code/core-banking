# T-033 pilot log

- 2026-11-20T20:22:56Z session start (branch fix/T-033-ledger-constraint-violation-400)
- free -m at start: used=474 free=15589; df: /dev/vda        252G  8.9G   61G  13% /
- 2026-11-20T20:23:33Z test-runner run 1 start (first ./mvnw verify, cold deps)
- 2026-11-20T20:24:08Z test-runner run 1 end: status build_error (cold deps; log .agents/tasks/T-033/logs/maven-20261120-202335.log)
  - verbatim: `Could not transfer artifact io.confluent:kafka-avro-serializer:pom:8.2.4 from/to confluent (https://packages.confluent.io/maven/): packages.confluent.io`
- 2026-11-20T20:24:12Z STOP: dependency host packages.confluent.io unreachable through the agent proxy; per pilot rules no workaround. docker build, scripts/e2e.sh and evaluator NOT run.
- peak mem/disk at stop: used=488 MB; /dev/vda        252G  8.9G   60G  13% /
- total: 00:~15 min wall clock (session start 2026-11-20T20:22:53Z)
- 2026-11-20T20:30:42Z git push -u origin fix/T-033-ledger-constraint-violation-400 REFUSED, verbatim:
  - `remote: Claude doesn't have GitHub access to lunaatienzaismael-code/core-banking for your organization. An org admin can install the Claude GitHub App at https://github.com/apps/claude/installations/select_target, or reconnect GitHub from claude.ai settings (https://claude.ai/customize/connectors?auth_start=github&auth_start_force=1) to re-link an existing installation`
  - `fatal: unable to access 'https://github.com/lunaatienzaismael-code/core-banking/': The requested URL returned error: 403`
- 2026-11-20T20:34:24Z test-runner run 2 start (after user changed env network; session NOT restarted)
- 2026-11-20T20:35:17Z test-runner run 2 end: build_error. Network change DID apply live without session restart: curl https://packages.confluent.io/maven/ via the agent proxy -> HTTP 200.
  - but Maven still fails, verbatim: `Could not transfer artifact io.confluent:kafka-avro-serializer:pom:8.2.4 from/to confluent (https://packages.confluent.io/maven/): packages.confluent.io: Name or service not known`
  - same from my own shell (./mvnw -pl messaging-contracts dependency:resolve). getent hosts packages.confluent.io -> rc=2 (no local DNS; only the proxy resolves). Maven's transport apparently resolves DNS itself instead of going through the proxy (JAVA_TOOL_OPTIONS sets https.proxyHost=127.0.0.1, port 42695, which differs from run 1's 33303: the proxy port changed with the network update).
  - STOP: no workaround attempted. ConstraintViolationTest not executed yet. e2e.sh and evaluator not run.
- 2026-11-20T20:37:57Z probe push `git push origin HEAD:claude/t-033-pilot-probe` REFUSED, verbatim:
  - `remote: Claude doesn't have GitHub access to lunaatienzaismael-code/core-banking for your organization. An org admin can install the Claude GitHub App at https://github.com/apps/claude/installations/select_target, or reconnect GitHub from claude.ai settings (https://claude.ai/customize/connectors?auth_start=github&auth_start_force=1) to re-link an existing installation`
  - `fatal: unable to access 'https://github.com/lunaatienzaismael-code/core-banking/': The requested URL returned error: 403`
  - Same 403 as for the task branch: not branch-specific; nothing was created on the remote.
- 2026-11-20T20:38:56Z Maven proxy fix WORKS (took ~1 min): a generated ~/.m2/settings.xml with <proxy> entries from HTTPS_PROXY makes `./mvnw -pl messaging-contracts dependency:resolve` BUILD SUCCESS (the JVM props from JAVA_TOOL_OPTIONS are not honoured by Maven's resolver). Candidate environment setup script (not the repo's pom.xml/.mvn):

```bash
#!/usr/bin/env bash
# Generates ~/.m2/settings.xml with a Maven proxy taken from HTTPS_PROXY.
set -euo pipefail
python3 - <<'PY'
import os, urllib.parse as u
p = u.urlparse(os.environ["HTTPS_PROXY"])
auth = ""
if p.username:
    auth = f"<username>{u.unquote(p.username)}</username><password>{u.unquote(p.password or '')}</password>"
nonproxy = "localhost|127.0.0.1"
xml = f"""<settings>
  <proxies>
    <proxy><id>agent-https</id><active>true</active><protocol>https</protocol>
      <host>{p.hostname}</host><port>{p.port}</port>{auth}<nonProxyHosts>{nonproxy}</nonProxyHosts></proxy>
    <proxy><id>agent-http</id><active>true</active><protocol>http</protocol>
      <host>{p.hostname}</host><port>{p.port}</port>{auth}<nonProxyHosts>{nonproxy}</nonProxyHosts></proxy>
  </proxies>
</settings>
"""
os.makedirs(os.path.expanduser("~/.m2"), exist_ok=True)
open(os.path.expanduser("~/.m2/settings.xml"), "w").write(xml)
PY
```

  - Caveat: the proxy port changes between sessions/network updates, so the script must read HTTPS_PROXY at session start, not at image build.
- 2026-11-20T20:39:03Z test-runner run 3 start (full verify with generated settings.xml)
- 2026-11-20T20:39:43Z test-runner run 3 end: fail (35 tests, 1 error): SchemaCompatibilityTest: `No release tag found; a CI checkout needs its tags (fetch-depth: 0): fatal: No names found, cannot describe anything.` Cause: cloud clone has no tags. Fix: `git fetch --tags origin` works (read access OK, v0.2.0..v0.4.0). Candidate for the setup script too.
- 2026-11-20T20:39:43Z test-runner run 4 start
- 2026-11-20T20:40:29Z test-runner run 4 end: fail, AvroRoundTripIT ExceptionInInitializerError: `Could not find a valid Docker environment` (/var/run/docker.sock NoSuchFileException). Docker CLI present but dockerd NOT running in the cloud session; started it with `nohup dockerd &` (up in <10 s, overlayfs). ConstraintViolationTest not reached (build stopped in messaging-contracts).
- 2026-11-20T20:40:29Z test-runner run 5 start (dockerd up)
- 2026-11-20T20:45:27Z test-runner run 5 end: PASS 446/446 (317 unit, 129 IT); ConstraintViolationTest 1/1 pass
- 2026-11-20T20:45:27Z docker build ledger-service start
- 2026-11-20T20:45:28Z docker build ledger-service end rc=0
- 2026-11-20T20:45:28Z docker build payment-service start
- 2026-11-20T20:45:29Z docker build payment-service end rc=0
- 2026-11-20T20:45:33Z scripts/e2e.sh start
- 2026-11-20T20:45:33Z scripts/e2e.sh end rc=1
  - CORRECTION: the 'rc=0' above are wrong ($? was clobbered by $(date)); both docker builds FAILED at 20:45:28/29, verbatim:
    - `ERROR: failed to build: failed to solve: eclipse-temurin:21-jdk: failed to resolve source metadata for docker.io/library/eclipse-temurin:21-jdk: unexpected status from HEAD request to https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/21-jdk: 429 Too Many Requests`
    - `ERROR: failed to build: failed to solve: eclipse-temurin:21-jre: failed to resolve source metadata for docker.io/library/eclipse-temurin:21-jre: unexpected status from HEAD request to https://registry-1.docker.io/v2/library/eclipse-temurin/manifests/21-jre: 429 Too Many Requests`
  - scripts/e2e.sh failed identically in 0 s (rc=1). Docker Hub rate-limits (429, anonymous pulls from the shared egress IP). Per pilot rules: STOP, no workaround. evaluator NOT run.
- peak at stop: mem used ~670 MB of 16087; disk /dev/vda        252G   12G   58G  17% /
- total: session start 2026-11-20T20:22:53Z, stop 2026-11-20T20:45:45Z
- Findings for the cloud environment: (1) Maven needs ~/.m2/settings.xml proxy generated from HTTPS_PROXY; (2) git tags must be fetched (git fetch --tags origin); (3) dockerd must be started (nohup dockerd &); (4) Docker Hub 429 blocks e2e image builds; (5) GitHub push 403 (Claude app not installed on the repo).
- 2026-11-20T20:54:12Z push of 4 commits to fix/T-033-... OK after GitHub App install (3df6597..8234c4c). No claude/* branch forced.
- 2026-11-20T20:54:12Z dockerd restarted with /etc/docker/daemon.json registry-mirrors [https://mirror.gcr.io]; docker pull eclipse-temurin:21-jre OK
- 2026-11-20T20:54:12Z scripts/e2e.sh start (includes docker builds)
- 2026-11-20T20:54:24Z scripts/e2e.sh end rc=1
- 2026-11-20T20:54:46Z e2e.sh rc=1 (corrects the line above). The mirror fixed the 429 (base images resolved: `resolve docker.io/library/eclipse-temurin:21-jdk@sha256:8ad1... done`), but the build then failed inside the container, verbatim:
  - `#19 0.270 wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz`
  - `Dockerfile:18 RUN ./mvnw -B -q -pl ledger-service -am dependency:go-offline` -> `exit code: 1`
  - Cause: the build container has no route to the internet except through the agent proxy (HTTPS_PROXY is not passed to docker build, and the proxy CA is not in the image). Fixing it means build-args + CA in the image or Dockerfile changes: beyond the agreed limits. PILOT CLOSED here. Evaluator NOT run; no PR proposed.
- peak mem used seen: ~670 MB of 16087 MB; disk: /dev/vda        252G   13G   56G  19% /
- total: 2026-11-20T20:22:53Z -> 2026-11-20T20:54:46Z

## Setup script candidato

Everything that was needed in the cloud session (items 1-4 verified; 5 is not enough for e2e):

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
# 1. Maven proxy from HTTPS_PROXY (read at session start: the port changes)
# Generates ~/.m2/settings.xml with a Maven proxy taken from HTTPS_PROXY.
set -euo pipefail
python3 - <<'PY'
import os, urllib.parse as u
p = u.urlparse(os.environ["HTTPS_PROXY"])
auth = ""
if p.username:
    auth = f"<username>{u.unquote(p.username)}</username><password>{u.unquote(p.password or '')}</password>"
nonproxy = "localhost|127.0.0.1"
xml = f"""<settings>
  <proxies>
    <proxy><id>agent-https</id><active>true</active><protocol>https</protocol>
      <host>{p.hostname}</host><port>{p.port}</port>{auth}<nonProxyHosts>{nonproxy}</nonProxyHosts></proxy>
    <proxy><id>agent-http</id><active>true</active><protocol>http</protocol>
      <host>{p.hostname}</host><port>{p.port}</port>{auth}<nonProxyHosts>{nonproxy}</nonProxyHosts></proxy>
  </proxies>
</settings>
"""
os.makedirs(os.path.expanduser("~/.m2"), exist_ok=True)
open(os.path.expanduser("~/.m2/settings.xml"), "w").write(xml)
PY
# 2. Tags (SchemaCompatibilityTest needs git describe)
git fetch --tags origin
# 3. Docker daemon with a Docker Hub mirror (avoids 429 on anonymous pulls)
mkdir -p /etc/docker
echo '{"registry-mirrors":["https://mirror.gcr.io"]}' > /etc/docker/daemon.json
nohup dockerd > /tmp/dockerd.log 2>&1 &
for i in $(seq 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
```

Still missing for scripts/e2e.sh: network inside `docker build` (Maven Central download in the Dockerfile fails: no proxy/CA in the build container). Options to decide: pass HTTPS_PROXY + CA as build args, or build the jars outside Docker and COPY them.
- 2026-11-20T20:58:02Z evaluator round 1 start (e2e delegated to CI by user decision; SHA 8fac83b)
- 2026-11-20T20:58:56Z evaluator round 1: approve (0/0/0, 2 nits), review-1.json. PR creation by REST (`gh api -X POST repos/.../pulls`) WORKED: PR #75 at SHA 8fac83b; verdict comment via REST issues/75/comments OK; GraphQL not used.
- 2026-11-20T20:59:30Z scripts/check-task-sync.sh fails in the cloud: `HTTP 403: GitHub GraphQL is not available from Claude Code sessions; use the REST API` (needs a REST port). Label needs-human created and applied to #76 via REST.
