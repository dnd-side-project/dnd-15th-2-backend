---
name: harness-infra-build
description: Implement approved Terraform or AWS CDK and produce plan evidence.
---

# Infrastructure Execution

1. Read `AGENTS.md`, `TASK.md`,
   `agents/infrastructure-executor.md`, and the approved design.
2. Modify only assigned `infra/` and documentation paths.
3. Use Terraform or AWS CDK; do not replace IaC with raw SDK calls.
4. Implement least-privilege IAM, OIDC, encryption, logs, backups, and tags.
5. Run formatting, validation, static checks, and plan.
6. Redact or summarize plan output; do not paste sensitive identifiers.
7. Create a PR and request both `@Byuntil` and `@tkv00`.
8. Stop before apply/deploy. Apply requires both approvals, protected
   Environment review, and explicit human dispatch confirmation.
