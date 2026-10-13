#!/usr/bin/env bash
# Counts evaluator findings by defect_type across every
# .agents/tasks/*/review-*.json and prints ONE compact JSON report on stdout.
# Makes the growth rule (CLAUDE.md) deterministic: a defect type seen in two or
# more tasks must become a skill rule, an ArchUnit rule or an evaluator check,
# recorded in .agents/growth-rules.json.
#
# Usage:
#   scripts/review-defects.sh
#
# Output contract (stable; consumed by /status and /next-task):
#   {
#     "status": "ok" | "alert",
#     "reviews": <number of review files read>,
#     "defects": {"<defect_type>": {"tasks": ["T-xxx", ...], "findings": n,
#                                    "rule": "<where it is enforced>" | null}},
#     "alerts": [{"defect_type": "...", "tasks": [...]}],   # 2+ tasks, no rule
#     "unclassified": ["<review path>#<finding index>", ...]
#   }
# Exit code: 0 when status is "ok", 1 on alert or unclassified findings.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT" <<'PY'
import glob, json, os, sys

root = sys.argv[1]
rules = json.load(open(os.path.join(root, ".agents/growth-rules.json")))["rules"]
files = sorted(glob.glob(os.path.join(root, ".agents/tasks/*/review-*.json")))

defects, unclassified = {}, []
for path in files:
    review = json.load(open(path))
    for index, finding in enumerate(review.get("findings", [])):
        kind = finding.get("defect_type")
        if not kind:
            unclassified.append(f"{os.path.relpath(path, root)}#{index}")
            continue
        entry = defects.setdefault(kind, {"tasks": [], "findings": 0, "rule": rules.get(kind)})
        entry["findings"] += 1
        if review["task_id"] not in entry["tasks"]:
            entry["tasks"].append(review["task_id"])

alerts = [
    {"defect_type": kind, "tasks": entry["tasks"]}
    for kind, entry in sorted(defects.items())
    if len(entry["tasks"]) >= 2 and entry["rule"] is None
]
status = "alert" if alerts or unclassified else "ok"
print(json.dumps({
    "status": status,
    "reviews": len(files),
    "defects": dict(sorted(defects.items())),
    "alerts": alerts,
    "unclassified": unclassified,
}))
sys.exit(0 if status == "ok" else 1)
PY
