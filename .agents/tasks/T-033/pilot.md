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
