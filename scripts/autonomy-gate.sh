#!/usr/bin/env bash
# The Autonomy gate (AUTONOMY.md, plan section 3.7): decides whether a PR may
# merge without the owner, and turns auto-merge on or off accordingly.
#
# Run by .github/workflows/autonomy-gate.yml on pull_request_target, from the
# BASE branch checkout: the PR's code is never executed, only read as data
# (changed files, body, labels, its latest review-N.json).
#
# Usage:
#   scripts/autonomy-gate.sh <pr> [--action <event action>] [--base <ref>] [--dry-run]
#
# --base: where `existing` protected entries are looked up (default HEAD, the
# base branch checkout in the workflow; use origin/main when run locally).
#
# Passes (exit 0) when either:
#   - the PR carries `human-approved` (ignored on `synchronize`, which removes it);
#   - or all of: no protected path (scripts/guard-paths.sh --check), head branch
#     `<type>/T-xxx-<slug>` in this repository, not a draft, no `needs-human`,
#     a body line exactly `Verdict: APPROVE`, the latest review-N.json of that
#     task says `approve`, and every commit after its `head` touches only
#     .agents/ (the SHA rule). Then it enables auto-merge (rebase).
# Fails (exit 1) otherwise; it disables auto-merge, and adds `needs-human` when
# a protected path is the cause. --dry-run changes nothing on GitHub.
# Prints ONE JSON line: {"pr", "pass", "reasons": [...], "protected": [...]}
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 - "$ROOT" "$@" <<'PY'
import base64, json, os, re, subprocess, sys

root, args = sys.argv[1], sys.argv[2:]
pr_number = args[0]
action = args[args.index("--action") + 1] if "--action" in args else ""
dry_run = "--dry-run" in args
base_ref = args[args.index("--base") + 1] if "--base" in args else "HEAD"
TASK_BRANCH = re.compile(r"^(feat|fix|test|build|docs|chore|refactor|ci)/(T-\d{3})-[a-z0-9-]+$")


def gh(*a, check=True):
    r = subprocess.run(["gh", *a], capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit(f"autonomy-gate: gh {' '.join(a[:3])}: {r.stderr.strip()}")
    return r


def api(path, paginate=False, jq=None):
    cmd = ["api", "-H", "Accept: application/vnd.github+json", path]
    if paginate:
        cmd.insert(1, "--paginate")
    if jq:
        cmd += ["--jq", jq]
    return gh(*cmd).stdout


repo = os.environ.get("GITHUB_REPOSITORY") or json.loads(api("repos/{owner}/{repo}"))["full_name"]
pr = json.loads(api(f"repos/{repo}/pulls/{pr_number}"))
labels = {l["name"] for l in pr["labels"]}
head_sha, branch = pr["head"]["sha"], pr["head"]["ref"]
reasons = []


def effect(*a):
    if not dry_run:
        gh(*a, check=False)


if action == "synchronize" and "human-approved" in labels:
    # New code: the approval covered the old one. Removing it with
    # GITHUB_TOKEN starts no new run, so judge as if it were gone.
    effect("api", "-X", "DELETE", f"repos/{repo}/issues/{pr_number}/labels/human-approved")
    labels.discard("human-approved")

files = [l for l in api(f"repos/{repo}/pulls/{pr_number}/files?per_page=100", paginate=True, jq=".[].filename").splitlines() if l]
check = subprocess.run(
    [f"{root}/scripts/guard-paths.sh", "--check", "--base", base_ref, *files], capture_output=True, text=True
)
protected = json.loads(check.stdout)["protected"] if check.stdout else files

if "human-approved" in labels:
    passed = True
else:
    if protected:
        reasons.append("touches protected paths without human-approved")
    m = TASK_BRANCH.match(branch)
    if not m or pr["head"]["repo"] is None or pr["head"]["repo"]["full_name"] != repo:
        reasons.append(f"head branch '{branch}' is not a task branch of this repository")
    if pr["draft"]:
        reasons.append("draft")
    if "needs-human" in labels:
        reasons.append("labelled needs-human")
    if not re.search(r"^Verdict: APPROVE\r?$", pr["body"] or "", re.M):
        reasons.append("body has no exact 'Verdict: APPROVE' line")
    if m:
        folder = f".agents/tasks/{m.group(2)}"
        listing = gh("api", f"repos/{repo}/contents/{folder}?ref={head_sha}", check=False)
        names = [e["name"] for e in json.loads(listing.stdout)] if listing.returncode == 0 else []
        rounds = sorted(int(n[7:-5]) for n in names if re.fullmatch(r"review-\d+\.json", n))
        if not rounds:
            reasons.append(f"no review-N.json in {folder}")
        else:
            blob = json.loads(api(f"repos/{repo}/contents/{folder}/review-{rounds[-1]}.json?ref={head_sha}"))
            review = json.loads(base64.b64decode(blob["content"]))
            reviewed = review.get("head", "")
            if review.get("verdict") != "approve":
                reasons.append(f"review-{rounds[-1]}.json verdict is {review.get('verdict')}")
            elif not re.fullmatch(r"[0-9a-f]{7,40}", reviewed):
                reasons.append(f"review-{rounds[-1]}.json head is not a commit: {reviewed!r}")
            elif not head_sha.startswith(reviewed):
                after = json.loads(api(f"repos/{repo}/compare/{reviewed}...{head_sha}"))
                if after.get("status") not in ("ahead", "identical"):
                    reasons.append(f"reviewed commit {reviewed[:7]} is not an ancestor of the head")
                outside = [f["filename"] for f in after.get("files", []) if not f["filename"].startswith(".agents/")]
                if outside:
                    reasons.append(f"commits after the reviewed {reviewed[:7]} touch {', '.join(outside[:5])}")
    passed = not reasons

if passed and "human-approved" not in labels:
    effect("pr", "merge", pr_number, "--repo", repo, "--auto", "--rebase", "--match-head-commit", head_sha)
elif not passed:
    effect("pr", "merge", pr_number, "--repo", repo, "--disable-auto")
    if protected:
        effect("api", "-X", "POST", f"repos/{repo}/issues/{pr_number}/labels", "-f", "labels[]=needs-human")

result = {"pr": int(pr_number), "pass": passed, "reasons": reasons, "protected": protected}
print(json.dumps(result))
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary:
    with open(summary, "a") as f:
        f.write(("Autonomy gate: **pass**\n" if passed else "Autonomy gate: **fail**\n\n" + "\n".join(f"- {r}" for r in reasons)) + "\n")
sys.exit(0 if passed else 1)
PY
