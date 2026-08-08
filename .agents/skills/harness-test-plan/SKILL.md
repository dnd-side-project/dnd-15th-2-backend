---
name: "harness-test-plan"
description: "Design a risk-based JUnit 5 test plan before implementation."
---

# Test Plan Orchestration

1. Read `AGENTS.md`, `TASK.md`, `agents/test-orchestrator.md`, and the GitHub Issue.
2. Inspect relevant source, tests, schema, transactions, and external API
   boundaries without changing implementation.
3. Run `./harness test-plan --id <TEST-PLAN-ID>` to scaffold the plan.
4. Complete every applicable section in `templates/test-plan.md`.
5. Split unit and integration scenarios and include database, concurrency,
   transaction, external API, and failure-recovery risks.
6. Assign non-overlapping files and scenario IDs to execution agents.
7. Stop for human approval. Do not implement tests or change production code.

Never include `.env` values, tokens, URLs, account IDs, or IAM identifiers.
