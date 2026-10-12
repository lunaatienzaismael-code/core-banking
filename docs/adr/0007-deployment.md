# ADR 0007: Deployment and Local Environment

- Status: Accepted
- Date: 2026-10-12
- Deciders: Project owner

## Context

A reviewer should be able to run the system locally with a single command, and
each service should ship as a reproducible artifact. We want the minimum infra
that serves the current phase, growing only as use cases require it.

## Options Considered

- **Single fat JAR, run by hand** — simplest, but not reproducible across
  environments and far from how banks ship.
- **Dockerfile per service + `docker compose` for local** — reproducible
  images, one-command local stack, mirrors real deployment shape.
- **Kubernetes from day one** — realistic at scale but heavy, premature and
  noisy for the early phases.

## Decision

- **Multi-stage Dockerfile per service** (build stage + slim runtime stage).
- **`docker compose` for local development**, under `infra/`. It contains only
  **PostgreSQL** for now; Kafka (KRaft) + Schema Registry, Redis and
  observability are added in the phases that introduce them.
- **Kubernetes is optional and only at the end** (showcase phase), if at all.

## Consequences

- `docker compose up` gives a working local dependency stack with one command.
- Each service's image is reproducible and CI-buildable.
- The compose file grows incrementally; each addition arrives with the phase
  that needs it, keeping earlier phases simple.
