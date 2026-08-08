---
name: "harness-review"
description: "Review GitHub Issue scope, evidence, safety gates, and PR readiness."
---

# PM and Safety Review

Read `AGENTS.md`, `TASK.md`, `agents/pm-reviewer.md`, the GitHub Issue, diff,
reports, and check results.

Return:

1. Scope match and unapproved changes.
2. Missing Issue/branch/commit/PR conventions.
3. Test or infrastructure evidence gaps.
4. Secret-handling and destructive-action risks.
5. Concrete required changes, each with owner and verification.
6. Final recommendation: approve, request changes, or blocked.

Do not edit implementation while acting as reviewer.
