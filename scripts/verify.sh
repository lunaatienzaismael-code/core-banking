#!/usr/bin/env bash
# Stop hook (AUTONOMY.md): a turn may not end on code that does not format or
# does not pass its unit and ArchUnit tests. The full suite (ITs, e2e) is the
# test-runner's and CI's job; hooks time out and fire at every turn end.
#
# Reads the hook JSON on stdin. Exit 0 lets the turn end; exit 2 keeps it
# going with the failure summary on stderr.
#
# - exit 0 when `stop_hook_active` is true (a previous block already made the
#   agent continue; never loop);
# - exit 0 when nothing outside .agents/, docs/ and *.md differs from
#   origin/main;
# - exit 0 when this exact tree already passed (stamp in target/);
# - otherwise `spotless:check` and `verify -DskipITs` on the touched modules
#   (all modules when the root POM changed).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

input="$(cat)"
active="$(python3 -c 'import json,sys; print(str(json.loads(sys.argv[1] or "{}").get("stop_hook_active", False)).lower())' "$input")"
[[ "$active" == "true" ]] && exit 0

base="$(git merge-base origin/main HEAD 2>/dev/null || echo origin/main)"
changed="$( { git diff --name-only "$base"; git ls-files --others --exclude-standard; } \
  | grep -vE '^(\.agents/|docs/)|\.md$' | sort -u || true)"
[[ -z "$changed" ]] && exit 0

modules=()
all=false
while IFS= read -r path; do
  [[ "$path" == pom.xml ]] && all=true
  top="${path%%/*}"
  [[ "$top" != "$path" && -f "$top/pom.xml" ]] && modules+=("$top")
done <<< "$changed"
if ! $all && [[ ${#modules[@]} -eq 0 ]]; then
  exit 0   # only scripts, CI or other files outside the Maven modules
fi

# The tree being checked: HEAD plus every uncommitted change.
stamp_dir="$ROOT/target/verify-hook"
tree="$( { git rev-parse HEAD; git diff HEAD; git ls-files --others --exclude-standard | xargs -I{} sh -c 'echo {}; cat "{}"' 2>/dev/null; } | git hash-object --stdin)"
[[ -f "$stamp_dir/$tree" ]] && exit 0

args=(-B -q)
if ! $all; then
  pl="$(printf '%s\n' "${modules[@]}" | sort -u | paste -sd, -)"
  args+=(-pl "$pl" -am)
fi

mkdir -p "$stamp_dir"
log="$stamp_dir/last.log"
if ./mvnw "${args[@]}" spotless:check verify -DskipITs > "$log" 2>&1; then
  rm -f "$stamp_dir"/[0-9a-f]*
  touch "$stamp_dir/$tree"
  exit 0
fi

{
  echo "verify.sh: spotless:check / unit + ArchUnit tests fail on the touched modules. Fix before ending the turn."
  echo "Full log: ${log#"$ROOT"/}"
  grep -E '^\[ERROR\]|Tests run:.*(Failures: [1-9]|Errors: [1-9])' "$log" | grep -v '^\[ERROR\] *$' | head -20
} >&2
exit 2
