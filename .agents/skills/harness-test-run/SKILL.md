---
name: harness-test-run
description: Implement and run approved JUnit 5 scenarios and create a safe report.
---

# Test Execution

Read `AGENTS.md`, `TASK.md`, `agents/test-executor.md`, and the approved plan.
Modify only assigned files. Use JUnit 5, `@DisplayName` on every test, and exact
ISO 8601 timestamp/source scenario headers on every test class.

Run:

```bash
./harness test-run --id <TEST-PLAN-ID>
./harness pr-ready --project-tests
```

Complete the report with cross-system potential issues. Never report unexecuted
tests as passing or copy `.env` values and secrets.
