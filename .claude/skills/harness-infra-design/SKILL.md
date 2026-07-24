---
name: harness-infra-design
description: Design a minimal-cost AWS baseline and prepare a plan-only PR.
---

# Infrastructure Design Orchestration

1. Read `AGENTS.md`, `TASK.md`, and
   `agents/infrastructure-orchestrator.md`.
2. Confirm Jira and GitHub Issue links.
3. Run `./harness infra-design --id <DESIGN-ID>`.
4. Compare the lowest practical single EC2 workload with ECS, and managed RDS
   with self-hosting.
5. Document architecture, least-privilege IAM, OIDC, availability, operations,
   recovery, alternatives, and risks.
6. Estimate one month using dated AWS official pricing assumptions and show the
   formula.
7. Define Terraform or AWS CDK module ownership and plan commands.
8. Stop for human review. Do not apply, deploy, or change production.

Never record endpoints, account IDs, IAM IDs, tokens, keys, or `.env` values.
