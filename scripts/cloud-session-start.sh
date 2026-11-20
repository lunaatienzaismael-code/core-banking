#!/usr/bin/env bash
# SessionStart hook: prepares a Claude Code CLOUD session for the build
# (docs/development.md, "Cloud session"). Locally it does nothing at all.
#
# Only when CLAUDE_CODE_REMOTE is "true", which the Claude Code docs define as
# set in remote web environments and not set in the local CLI
# (https://code.claude.com/docs/en/hooks). It never touches a developer's
# ~/.m2/settings.xml or Docker.
#
# Per session, because none of it survives the environment's cached setup
# script: the agent proxy's port changes between sessions, and a cached
# snapshot keeps files but no running processes.
#   1. ~/.m2/settings.xml with a <proxy> from HTTPS_PROXY (Maven's resolver
#      ignores the JVM proxy properties in JAVA_TOOL_OPTIONS);
#   2. git fetch --tags (SchemaCompatibilityTest needs `git describe`);
#   3. dockerd with the mirror.gcr.io registry mirror (anonymous Docker Hub
#      pulls hit 429), when it is not running yet.
# Each step reports one line; a failed step does not stop the others or the
# session (exit 0), so the agent sees what is missing and stops if it must.
set -uo pipefail

[[ "${CLAUDE_CODE_REMOTE:-}" == "true" ]] || exit 0

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/..}" || exit 0

# 1. Maven proxy
if [[ -n "${HTTPS_PROXY:-}" ]]; then
  if python3 - <<'PY'
import os, urllib.parse as u
from xml.sax.saxutils import escape as x

p = u.urlparse(os.environ["HTTPS_PROXY"])
auth = ""
if p.username:
    auth = f"<username>{x(u.unquote(p.username))}</username><password>{x(u.unquote(p.password or ''))}</password>"
proxies = "".join(
    f"""
    <proxy><id>agent-{scheme}</id><active>true</active><protocol>{scheme}</protocol>
      <host>{x(p.hostname)}</host><port>{p.port}</port>{auth}<nonProxyHosts>localhost|127.0.0.1</nonProxyHosts></proxy>"""
    for scheme in ("https", "http")
)
path = os.path.expanduser("~/.m2/settings.xml")
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "w") as f:
    f.write(f"<settings>\n  <proxies>{proxies}\n  </proxies>\n</settings>\n")
os.chmod(path, 0o600)
PY
  then echo "cloud-session-start: Maven proxy written to ~/.m2/settings.xml"
  else echo "cloud-session-start: FAILED to write the Maven proxy settings"
  fi
else
  echo "cloud-session-start: HTTPS_PROXY not set; Maven proxy skipped"
fi

# 2. Release tags
if git fetch --quiet --tags origin 2>/dev/null; then
  echo "cloud-session-start: tags fetched ($(git tag | wc -l | tr -d ' '))"
else
  echo "cloud-session-start: FAILED to fetch tags; SchemaCompatibilityTest will fail"
fi

# 3. Docker daemon with a registry mirror
if docker info > /dev/null 2>&1; then
  echo "cloud-session-start: dockerd already running"
elif command -v dockerd > /dev/null 2>&1; then
  sudo=""
  [[ "$(id -u)" -ne 0 ]] && command -v sudo > /dev/null 2>&1 && sudo="sudo -n"
  $sudo mkdir -p /etc/docker
  [[ -f /etc/docker/daemon.json ]] \
    || echo '{"registry-mirrors":["https://mirror.gcr.io"]}' | $sudo tee /etc/docker/daemon.json > /dev/null
  $sudo sh -c 'nohup dockerd > /tmp/dockerd.log 2>&1 &'
  for _ in $(seq 30); do docker info > /dev/null 2>&1 && break; sleep 1; done
  if docker info > /dev/null 2>&1; then
    echo "cloud-session-start: dockerd started (log /tmp/dockerd.log)"
  else
    echo "cloud-session-start: FAILED to start dockerd (log /tmp/dockerd.log); integration tests will fail"
  fi
else
  echo "cloud-session-start: dockerd not installed; integration tests will fail"
fi
exit 0
