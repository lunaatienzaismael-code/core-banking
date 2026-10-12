## What

<!-- One or two sentences: the change, from the reviewer's point of view. -->

## Why

<!-- The task / use case / ADR this serves. -->

Closes #

## How

<!-- Key design points. Link ADRs instead of repeating them. -->

## How to test

```bash
./mvnw -B verify
```

## Checklist

- [ ] Each commit compiles, passes tests and follows Conventional Commits
- [ ] Hexagonal rules respected (ArchUnit green)
- [ ] Money as `long` minor units + ISO-4217; no `double`/`float`
- [ ] Financial invariants touched by this change have named tests
- [ ] OpenAPI contract and Problem Details updated if the HTTP API changed
- [ ] ADR added or updated if a significant decision was made
- [ ] Only fictitious data (IBANs, names, amounts)

## Evaluator verdict

<!-- Verdict and summary from .agents/tasks/<id>/review-N.json -->
