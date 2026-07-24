---
name: harness-test-plan
description: Design a risk-based JUnit 5 test plan before implementation.
---

# Test Plan Orchestration

Read `AGENTS.md`, `TASK.md`, `agents/test-orchestrator.md`, the Jira ticket, and
linked GitHub Issue. Inspect the relevant code without changing it.

Run:

```bash
./harness test-plan --id <TEST-PLAN-ID>
```

Complete the plan with unit and integration scenarios, risks for database,
concurrency, transactions, external APIs and recovery, plus non-overlapping
executor ownership. Stop for human approval before implementation. Never expose
secrets or `.env` values.
