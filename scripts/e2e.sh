#!/usr/bin/env bash
# Builds both service images from their Dockerfiles, then runs the end-to-end
# tests (module e2e-tests, profile e2e) against them. `docker build` honours
# .dockerignore and reuses BuildKit's layer cache, so a rebuild after a code
# change only recompiles; CI builds the same tags with its own cache.
#
# Usage:
#   scripts/e2e.sh [extra mvn args]
set -euo pipefail

cd "$(dirname "$0")/.."

for service in ledger-service payment-service; do
  docker build -q -f "$service/Dockerfile" -t "ledgerflow/$service:e2e" . > /dev/null
done

./mvnw -B verify -Pe2e -pl e2e-tests -am "$@"
