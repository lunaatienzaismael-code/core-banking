#!/usr/bin/env bash
# Protected-path barrier (AUTONOMY.md) over the single list in
# .agents/protected-paths.txt.
#
# Usage:
#   scripts/guard-paths.sh                       # PreToolUse hook: reads the hook JSON on stdin
#   scripts/guard-paths.sh --check [--base <ref>] [--brief <file>] [--] <path>...
#   scripts/guard-paths.sh --codeowners          # prints the expected .github/CODEOWNERS
#   scripts/guard-paths.sh --check-codeowners    # exit 1 when .github/CODEOWNERS drifted
#
# Hook mode: blocks (exit 2, reason on stderr) an Edit/Write/NotebookEdit of a
# protected path unless the current task's brief lists it under
# `## Protected paths`. The brief is .agents/tasks/<id>/brief.md, with <id>
# taken from the branch `<type>/T-xxx-<slug>`. The hook does not see edits made
# through Bash; the Autonomy gate is the real barrier.
#
# --check output contract (stable; consumed by the Autonomy gate):
#   {"protected": [...], "declared": [...], "undeclared": [...]}
# `protected` = given paths that match the list; `declared` = those the brief
# lists; `undeclared` = the rest. Exit code: 0 when nothing is protected, 1
# otherwise (declared paths still need the owner's approval, D3).
#
# `existing` entries are checked against <ref> (default origin/main).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# The heredoc below is python's stdin, so the hook JSON travels in the env.
HOOK_INPUT=""
[[ $# -eq 0 ]] && HOOK_INPUT="$(cat)"
export HOOK_INPUT

python3 - "$ROOT" "$@" <<'PY'
import json, os, re, subprocess, sys

root, args = sys.argv[1], sys.argv[2:]
LIST = os.path.join(root, ".agents/protected-paths.txt")
OWNERS = os.path.join(root, ".github/CODEOWNERS")
OWNER = "@lunaatienzaismael-code"


def git(*a):
    return subprocess.run(["git", "-C", root, *a], capture_output=True, text=True)


def glob_re(pattern):
    out, i = "", 0
    while i < len(pattern):
        if pattern.startswith("**/", i):
            out, i = out + "(?:.*/)?", i + 3
        elif pattern.startswith("**", i):
            out, i = out + ".*", i + 2
        elif pattern[i] == "*":
            out, i = out + "[^/]*", i + 1
        else:
            out, i = out + re.escape(pattern[i]), i + 1
    return re.compile(out + r"\Z")


def entries():
    for line in open(LIST):
        line = line.split("#", 1)[0].strip()
        if line:
            pattern, _, flag = line.partition(" ")
            yield pattern, flag.strip() == "existing"


def exists_on(ref, path):
    return git("cat-file", "-e", f"{ref}:{path}").returncode == 0


def is_protected(path, ref):
    for pattern, existing in entries():
        if glob_re(pattern).match(path) and (not existing or exists_on(ref, path)):
            return True
    return False


def current_brief():
    branch = git("branch", "--show-current").stdout.strip()
    m = re.match(r"[a-z]+/(T-\d{3})-", branch)
    return os.path.join(root, f".agents/tasks/{m.group(1)}/brief.md") if m else None


def declared_patterns(brief):
    if not brief or not os.path.exists(brief):
        return []
    text = open(brief).read()
    m = re.search(r"^## Protected paths\s*\n(.*?)(?=^## |\Z)", text, re.M | re.S)
    return re.findall(r"^\s*-\s*`([^`]+)`", m.group(1), re.M) if m else []


def declared(path, patterns):
    return any(glob_re(p).match(path) for p in patterns)


def codeowners():
    lines = [
        "# Generated from .agents/protected-paths.txt by scripts/guard-paths.sh --codeowners.",
        "# Do not edit by hand. `existing` entries are listed in full: CODEOWNERS",
        "# cannot tell a new file from an existing one.",
        "",
    ]
    for pattern, _ in entries():
        lines.append(f"/{pattern} {OWNER}" if not pattern.startswith("**") else f"{pattern} {OWNER}")
    return "\n".join(lines) + "\n"


def relative(path):
    path = os.path.realpath(os.path.join(os.getcwd(), path))
    real_root = os.path.realpath(root)
    if path != real_root and not path.startswith(real_root + os.sep):
        return None
    return os.path.relpath(path, real_root)


if not args:
    event = json.loads(os.environ["HOOK_INPUT"] or "{}")
    tool_input = event.get("tool_input") or {}
    target = tool_input.get("file_path") or tool_input.get("notebook_path")
    path = relative(target) if target else None
    if path is None or not is_protected(path, "origin/main"):
        sys.exit(0)
    brief = current_brief()
    if declared(path, declared_patterns(brief)):
        sys.exit(0)
    where = os.path.relpath(brief, root) if brief else "no task brief for this branch"
    print(
        f"{path} is a protected path (.agents/protected-paths.txt) and the task brief "
        f"({where}) does not list it under '## Protected paths'. Stop as AUTONOMY.md says: "
        "comment on the issue, label it needs-human.",
        file=sys.stderr,
    )
    sys.exit(2)

if args[0] == "--codeowners":
    sys.stdout.write(codeowners())
    sys.exit(0)

if args[0] == "--check-codeowners":
    current = open(OWNERS).read() if os.path.exists(OWNERS) else ""
    if current != codeowners():
        print(".github/CODEOWNERS is out of sync: run scripts/guard-paths.sh --codeowners > .github/CODEOWNERS", file=sys.stderr)
        sys.exit(1)
    sys.exit(0)

if args[0] == "--check":
    ref, brief, paths, rest = "origin/main", None, [], args[1:]
    while rest:
        if rest[0] == "--":
            paths, rest = paths + rest[1:], []
        elif rest[0] == "--base":
            ref, rest = rest[1], rest[2:]
        elif rest[0] == "--brief":
            brief, rest = rest[1], rest[2:]
        else:
            paths, rest = paths + [rest[0]], rest[1:]
    patterns = declared_patterns(brief)
    hit = [p for p in paths if is_protected(p, ref)]
    ok = [p for p in hit if declared(p, patterns)]
    print(json.dumps({"protected": hit, "declared": ok, "undeclared": [p for p in hit if p not in ok]}))
    sys.exit(1 if hit else 0)

print(f"guard-paths: unknown argument {args[0]}", file=sys.stderr)
sys.exit(2)
PY
