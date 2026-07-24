---
name: harness-infra-build
description: Implement approved Terraform or AWS CDK and produce plan evidence.
---

# Infrastructure Execution

Read `AGENTS.md`, `TASK.md`, `agents/infrastructure-executor.md`, and the approved
design. Implement only assigned IaC, then format, validate, statically inspect,
and plan. Do not paste sensitive plan identifiers into PRs.

Create a reviewable PR and request both `@Byuntil` and `@tkv00`. Stop before
apply/deploy. Application requires both approvals, protected Environment review,
OIDC, and explicit human confirmation.
