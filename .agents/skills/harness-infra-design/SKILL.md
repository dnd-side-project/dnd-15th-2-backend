---
name: harness-infra-design
description: Design a minimal-cost AWS baseline and prepare a plan-only PR.
---

# Infrastructure Design

Read `AGENTS.md`, `TASK.md`, and
`agents/infrastructure-orchestrator.md`. Confirm the GitHub Issue and run:

```bash
./harness infra-design --id <DESIGN-ID>
```

Compare EC2/ECS and RDS/self-hosting, document official monthly price
assumptions, IAM/OIDC, operations, risk, and recovery. Use Terraform or AWS CDK.
Stop before implementation approval and never apply or deploy.
