#!/usr/bin/env bash
# Runs the Maven build and prints ONE compact JSON summary of the test results
# on stdout. The full Maven log is written to a file whose path is in the JSON.
#
# Usage:
#   scripts/test-summary.sh                       # ./mvnw -B verify
#   scripts/test-summary.sh -- -pl ledger-service  # extra Maven args after --
#   scripts/test-summary.sh --log-dir DIR          # where to write the log
#   scripts/test-summary.sh --no-build             # only summarise existing reports
#
# Output contract (stable; consumed by the test-runner subagent and CI):
#   {
#     "status": "pass" | "fail" | "build_error",
#     "exit_code": <maven exit code, or null with --no-build>,
#     "tests": {"total": n, "passed": n, "failed": n, "errors": n, "skipped": n},
#     "suites": {"unit": {...same counters...}, "integration": {...}},
#     "failures": [{"suite": "unit|integration", "class": "...", "test": "...",
#                   "type": "...", "message": "<= 300 chars"}],   # at most 20
#     "build_errors": ["<= 20 [ERROR] lines, only when no test failed"],
#     "log": "<path to the full Maven log, or null>"
#   }
# Exit code: 0 when status is "pass", 1 otherwise.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/target/test-summary"
BUILD=true
MVN_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) BUILD=false; shift ;;
    --log-dir) LOG_DIR="$2"; shift 2 ;;
    --) shift; MVN_ARGS=("$@"); break ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

LOG=""
EXIT_CODE="null"
if $BUILD; then
  mkdir -p "$LOG_DIR"
  LOG="$LOG_DIR/maven-$(date +%Y%m%d-%H%M%S).log"
  set +e
  (cd "$ROOT" && ./mvnw -B verify ${MVN_ARGS[@]+"${MVN_ARGS[@]}"}) >"$LOG" 2>&1
  EXIT_CODE=$?
  set -e
fi

python3 - "$ROOT" "$EXIT_CODE" "$LOG" <<'PY'
import glob, json, os, sys
import xml.etree.ElementTree as ET

root, exit_code, log = sys.argv[1], sys.argv[2], sys.argv[3] or None
exit_code = None if exit_code == "null" else int(exit_code)

def empty():
    return {"total": 0, "passed": 0, "failed": 0, "errors": 0, "skipped": 0}

suites = {"unit": empty(), "integration": empty()}
failures = []

for suite, folder in (("unit", "surefire-reports"), ("integration", "failsafe-reports")):
    for path in sorted(glob.glob(os.path.join(root, "**", "target", folder, "TEST-*.xml"), recursive=True)):
        for case in ET.parse(path).getroot().iter("testcase"):
            counters = suites[suite]
            counters["total"] += 1
            problem = case.find("failure")
            kind = "failed"
            if problem is None:
                problem = case.find("error")
                kind = "errors"
            if problem is not None:
                counters[kind] += 1
                if len(failures) < 20:
                    failures.append({
                        "suite": suite,
                        "class": case.get("classname"),
                        "test": case.get("name"),
                        "type": problem.get("type"),
                        "message": (problem.get("message") or "").strip()[:300],
                    })
            elif case.find("skipped") is not None:
                counters["skipped"] += 1
            else:
                counters["passed"] += 1

tests = empty()
for counters in suites.values():
    for key in tests:
        tests[key] += counters[key]

build_errors = []
if log and not failures and exit_code not in (None, 0):
    with open(log, errors="replace") as f:
        for line in f:
            if "To see the full stack trace" in line:
                break
            if line.startswith("[ERROR]") and line.strip() != "[ERROR]":
                build_errors.append(line.rstrip()[:300])
        build_errors = build_errors[:20]

if failures or tests["failed"] or tests["errors"]:
    status = "fail"
elif exit_code not in (None, 0):
    status = "build_error"
else:
    status = "pass"

print(json.dumps({
    "status": status,
    "exit_code": exit_code,
    "tests": tests,
    "suites": suites,
    "failures": failures,
    "build_errors": build_errors,
    "log": log,
}, indent=None, separators=(",", ":")))
sys.exit(0 if status == "pass" else 1)
PY
