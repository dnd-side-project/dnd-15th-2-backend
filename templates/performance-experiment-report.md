# Performance Experiment Report

> Warning: do not copy raw query text, EXPLAIN JSON, IDs, coordinates,
> credentials, URLs, tokens, `.env` values, or complete logs into this
> report. Record only allowed aggregate fields, logical labels, and
> sanitized plan summaries.

## Contract
- Experiment ID
- GitHub Issue
- Test plan ID
- Before commit and condition
- After commit and condition
- Fixture seed and logical distribution
- Primary metric
- Correctness, policy, resource, and cost guardrails
- Success and stop criteria

## Environment
- Java, Spring Boot, PostgreSQL, PostGIS, Docker Desktop versions
- Host resource summary without account, address, or device identifiers
- Container CPU and memory limits, or an explicit statement that Testcontainers limits were not fixed
- JVM, Hikari, and worker settings
- Dirty-worktree status excluding unrelated file contents

## Method
- Warm-up count
- Measured-call count
- Cold or warm state
- Statistics target
- Query/radius combinations
- Representative-value rule

## Results
- Client p50, p95, p99
- PostgreSQL calls, total/mean execution time, rows
- Shared and temp blocks
- Plan estimates, actual rows, access paths, GiST state
- Sort method, space type, and spill state

## Guardrails
- Preview count equality
- Matching order/set equality
- Persisted recipient equality
- Duplicate, missing, block, status, expiry, fairness, and capacity checks

## Interpretation
- Supported or rejected hypothesis
- Rejected alternatives
- Local-only applicability limits
- Follow-up decision requiring a separate Issue

## Verification
- Commands and results
- Failed or blocked checks
- Residual risks
