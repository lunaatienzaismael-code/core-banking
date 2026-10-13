#!/usr/bin/env bash
# Compares .agents/tasks.json with GitHub issues and PRs and prints ONE compact
# JSON report on stdout. Read-only: changes nothing on either side.
#
# Source of truth (CLAUDE.md, .agents/README.md): tasks.json owns working state,
# dependencies and ACs; GitHub (a merged PR) owns "done". This script reports
# where the two disagree; the orchestrator decides how to reconcile.
#
# Usage:
#   scripts/check-task-sync.sh
#
# Output contract (stable; consumed by /status):
#   {
#     "status": "in_sync" | "drift",
#     "checked": <number of tasks with an issue>,
#     "discrepancies": [{"task": "T-xxx", "field": "...",
#                        "tasks_json": "...", "github": "..."}]
#   }
# Fields checked: issue (exists), title ("T-xxx — <title>"), milestone
# ("Phase <n> — …"), state (done ↔ issue closed and PR merged; in_review ↔ PR
# open; other ↔ issue open), depends_on (issue footer "Depends on: …").
# Exit code: 0 when in sync, 1 on drift, 2 when GitHub cannot be read.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

issues="$(gh issue list --state all --limit 500 --json number,title,state,body,milestone)" || exit 2
prs="$(gh pr list --state all --limit 500 --json number,state)" || exit 2

ISSUES="$issues" PRS="$prs" python3 - "$ROOT/.agents/tasks.json" <<'PY'
import json, os, re, sys

tasks = json.load(open(sys.argv[1]))["tasks"]
issues = {i["number"]: i for i in json.loads(os.environ["ISSUES"])}
prs = {p["number"]: p["state"] for p in json.loads(os.environ["PRS"])}
out = []


def drift(task, field, local, remote):
    out.append({"task": task, "field": field, "tasks_json": local, "github": remote})


checked = 0
for t in tasks:
    tid, n = t["id"], t["issue"]
    if n is None:
        if t["status"] != "todo":
            drift(tid, "issue", "null", "task started without an issue")
        continue
    issue = issues.get(n)
    if issue is None:
        drift(tid, "issue", f"#{n}", "not found")
        continue
    checked += 1

    expected_title = f"{tid} — {t['title']}"
    if issue["title"] != expected_title:
        drift(tid, "title", expected_title, issue["title"])

    milestone = (issue.get("milestone") or {}).get("title") or ""
    if not milestone.startswith(f"Phase {t['phase']} "):
        drift(tid, "milestone", f"Phase {t['phase']}", milestone or "none")

    pr_state = prs.get(t["pr"]) if t["pr"] else None
    if t["status"] == "done":
        if issue["state"] != "CLOSED":
            drift(tid, "state", "done", f"issue {issue['state']}")
        if pr_state != "MERGED":
            drift(tid, "state", "done", f"PR {pr_state or 'missing'}")
    elif t["status"] == "in_review":
        if pr_state != "OPEN":
            drift(tid, "state", "in_review", f"PR {pr_state or 'missing'}")
    elif issue["state"] != "OPEN":
        drift(tid, "state", t["status"], f"issue {issue['state']}")

    match = re.search(r"Depends on: (.+?)(?: · |\s*$)", issue["body"] or "", re.M)
    remote = match.group(1).strip() if match else "missing"
    local = ", ".join(t["depends_on"]) or "none"
    if remote != local:
        drift(tid, "depends_on", local, remote)

print(json.dumps({"status": "drift" if out else "in_sync", "checked": checked, "discrepancies": out}))
sys.exit(1 if out else 0)
PY
