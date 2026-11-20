#!/usr/bin/env bash
# The only way the agent flow talks to GitHub: a closed set of subcommands over
# the REST API (`gh api`). GraphQL, and so `gh pr …` / `gh issue …`, is blocked
# in Claude Code cloud sessions. Permissions allow this wrapper, not all of
# `gh api` (AUTONOMY.md).
#
# Usage:
#   scripts/gh-rest.sh list-prs [open|closed|all]
#   scripts/gh-rest.sh pr-state <pr>
#   scripts/gh-rest.sh checks <sha>
#   scripts/gh-rest.sh list-issues [open|closed|all] [label]
#   scripts/gh-rest.sh open-pr --head <branch> --title <title> --body-file <file> [--draft] [--milestone <n>]
#   scripts/gh-rest.sh comment <issue-or-pr> --body-file <file>
#   scripts/gh-rest.sh label <issue-or-pr> <label>...
#   scripts/gh-rest.sh unlabel <issue-or-pr> <label>
#   scripts/gh-rest.sh create-milestone <title>
#   scripts/gh-rest.sh create-issue --title <title> --body-file <file> [--milestone <n>]
#
# Output contract (stable; consumed by /run-task, /status, check-task-sync.sh
# and state.sh): ONE compact JSON value on stdout.
#   list-prs     [{"number","title","state":"OPEN|CLOSED|MERGED","draft","head","head_sha","labels":[…]}]
#   pr-state     {"number","title","state","draft","head","head_sha","labels","body"}
#   checks       [{"name","status","conclusion"}]          # check runs of the commit
#   list-issues  [{"number","title","state":"OPEN|CLOSED","body","milestone","labels":[…]}]
#   open-pr      {"number","url"}
#   comment      {"id","url"}
#   label/unlabel {"number","labels":[…]}
#   create-*     {"number","url"}  (milestone: url is null)
# Exit code: 0 on success, 2 on GitHub or usage errors, 3 when refused.
#
# Refused on purpose (exit 3): adding or removing the label `human-approved`
# (only the owner sets it, AUTONOMY.md), and any base branch other than main.
# There is no merge subcommand: merges happen through the Autonomy gate.
set -euo pipefail

die() { echo "gh-rest: $*" >&2; exit 2; }
refuse() { echo "gh-rest: refused: $*" >&2; exit 3; }
api() { gh api -H "Accept: application/vnd.github+json" "$@"; }

# Joins the NDJSON lines that `gh api --paginate --jq` prints into one array.
array() { python3 -c 'import json,sys; print(json.dumps([json.loads(l) for l in sys.stdin if l.strip()], separators=(",",":")))'; }

PR_FIELDS='{number, title, state: (if .merged_at then "MERGED" else (.state|ascii_upcase) end), draft, head: .head.ref, head_sha: .head.sha, labels: [.labels[].name]}'

guard_label() {
  local l
  for l in "$@"; do
    [[ "$l" == "human-approved" ]] && refuse "the label human-approved is set only by the owner"
  done
  return 0
}

cmd="${1:-}"; shift || true
case "$cmd" in
  list-prs)
    state="${1:-open}"
    api --paginate "repos/{owner}/{repo}/pulls?state=$state&per_page=100" --jq ".[] | $PR_FIELDS" | array
    ;;
  pr-state)
    [[ $# -eq 1 ]] || die "usage: pr-state <pr>"
    api "repos/{owner}/{repo}/pulls/$1" --jq "$PR_FIELDS + {body}" | tr -d '\n'; echo
    ;;
  checks)
    [[ $# -eq 1 ]] || die "usage: checks <sha>"
    api --paginate "repos/{owner}/{repo}/commits/$1/check-runs?per_page=100" \
      --jq '.check_runs[] | {name, status, conclusion}' | array
    ;;
  list-issues)
    state="${1:-open}"; query="state=$state&per_page=100"
    [[ -n "${2:-}" ]] && query="$query&labels=$2"
    api --paginate "repos/{owner}/{repo}/issues?$query" \
      --jq '.[] | select(.pull_request | not) | {number, title, state: (.state|ascii_upcase), body, milestone: (if .milestone then {title: .milestone.title, number: .milestone.number} else null end), labels: [.labels[].name]}' | array
    ;;
  open-pr)
    head="" title="" body="" draft=false milestone=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --head) head="$2"; shift 2 ;;
        --title) title="$2"; shift 2 ;;
        --body-file) body="$2"; shift 2 ;;
        --draft) draft=true; shift ;;
        --milestone) milestone="$2"; shift 2 ;;
        --base) [[ "$2" == main ]] || refuse "base must be main"; shift 2 ;;
        *) die "unknown argument: $1" ;;
      esac
    done
    [[ -n "$head" && -n "$title" && -f "$body" ]] || die "open-pr needs --head, --title and --body-file"
    pr="$(api -X POST "repos/{owner}/{repo}/pulls" -f base=main -f head="$head" -f title="$title" \
      -F body=@"$body" -F draft="$draft" --jq '.number')"
    # A PR is an issue for milestones; the pulls endpoint does not take one.
    [[ -n "$milestone" ]] && api -X PATCH "repos/{owner}/{repo}/issues/$pr" -F milestone="$milestone" > /dev/null
    api "repos/{owner}/{repo}/pulls/$pr" --jq '{number, url: .html_url}'
    ;;
  comment)
    [[ $# -eq 3 && "$2" == --body-file && -f "$3" ]] || die "usage: comment <n> --body-file <file>"
    api -X POST "repos/{owner}/{repo}/issues/$1/comments" -F body=@"$3" --jq '{id, url: .html_url}'
    ;;
  label)
    [[ $# -ge 2 ]] || die "usage: label <n> <label>..."
    n="$1"; shift; guard_label "$@"
    args=(); for l in "$@"; do args+=(-f "labels[]=$l"); done
    api -X POST "repos/{owner}/{repo}/issues/$n/labels" "${args[@]}" > /dev/null
    api "repos/{owner}/{repo}/issues/$n" --jq '{number, labels: [.labels[].name]}'
    ;;
  unlabel)
    [[ $# -eq 2 ]] || die "usage: unlabel <n> <label>"
    guard_label "$2"
    label="$(python3 -c 'import sys,urllib.parse as u; print(u.quote(sys.argv[1], safe=""))' "$2")"
    # 404 = the label was not there; the result is the same.
    api -X DELETE "repos/{owner}/{repo}/issues/$1/labels/$label" > /dev/null 2>&1 || true
    api "repos/{owner}/{repo}/issues/$1" --jq '{number, labels: [.labels[].name]}'
    ;;
  create-milestone)
    [[ $# -eq 1 ]] || die "usage: create-milestone <title>"
    api -X POST "repos/{owner}/{repo}/milestones" -f title="$1" --jq '{number, url: null}'
    ;;
  create-issue)
    title="" body="" milestone=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --title) title="$2"; shift 2 ;;
        --body-file) body="$2"; shift 2 ;;
        --milestone) milestone="$2"; shift 2 ;;
        *) die "unknown argument: $1" ;;
      esac
    done
    [[ -n "$title" && -f "$body" ]] || die "create-issue needs --title and --body-file"
    args=(-f title="$title" -F body=@"$body")
    [[ -n "$milestone" ]] && args+=(-F milestone="$milestone")
    api -X POST "repos/{owner}/{repo}/issues" "${args[@]}" --jq '{number, url: .html_url}'
    ;;
  *)
    die "unknown subcommand '${cmd}'; see the header of $0"
    ;;
esac
