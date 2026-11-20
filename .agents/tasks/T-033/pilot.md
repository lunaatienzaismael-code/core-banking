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
