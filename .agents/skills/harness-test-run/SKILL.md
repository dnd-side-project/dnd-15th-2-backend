---
name: "harness-test-run"
description: "Implement and run approved JUnit 5 scenarios and create a safe report."
---

# Test Execution

1. Read `AGENTS.md`, `TASK.md`, `agents/test-executor.md`, and the approved test
   plan.
2. Confirm the current branch includes the GitHub Issue number.
3. Modify only assigned test/report files. Ask before changing production code.
4. Use JUnit 5, `@DisplayName` on every test, and a class header containing the
   exact ISO 8601 creation timestamp and source scenario ID.
5. Run `./harness test-run --id <TEST-PLAN-ID>`.
6. Complete the generated report, including potential issues across code,
   infrastructure, database, concurrency, transactions, external APIs, and
   failure recovery.
7. Run `./harness pr-ready --project-tests`.
8. Report completed changes and verification. If a commit is needed, use
   `/harness-commit` to present the complete purpose-based draft and obtain human
   approval before committing.

Do not commit, push, or create a PR automatically from this role. Do not report
unexecuted tests as passing. Never copy secrets or `.env` values.
