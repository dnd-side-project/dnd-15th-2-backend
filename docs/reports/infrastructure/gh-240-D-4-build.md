# Infrastructure Build Report: D-4

> Created at: `2026-09-17T02:46:24+09:00`
> GitHub Issue: `#240`
> DESIGN-ID: `D-4` (`docs/reports/infrastructure/gh-240-D-4.md`, `APPROVED_FOR_BUILD` —
> `tkv00` 단독 승인, 2026-09-17)
> Status: 정적 검증 완료 / `terraform plan`(실제 Backend) 미실행 / apply 미실행

이 문서는 `/harness-infra-build` 산출물의 검증 증거다. 실제 값(계정 ID, ARN,
버킷 이름, State, plan 원문)은 기록하지 않는다.

## 0. 최종 상태 (AGENTS.md 11절)

```text
status: BLOCKED
issue_number: 240
task_id: GH-240
design_id: D-4
```

**`BLOCKED`인 이유**: `infra/bootstrap`은 자기 자신이 Terraform State
Backend(S3 버킷)를 만드는 스택이라 `.tf` 코드 자체에는 `backend "s3"`
블록이 없다(`versions.tf` 주석 참고). 최초 apply 이후 사람이
`terraform init -migrate-state`로 로컬 State를 그 S3 버킷으로 옮겨두었고,
로컬 `.terraform/terraform.tfstate`(backend 포인터)는 그 이관 결과를
캐시하고 있다. 이 세션에서 `terraform init`(실제 Backend, `qello-new`
자격 증명)을 실행하자 "Backend configuration changed"가 발생했다 —
`.tf` 코드가 선언하는 backend(없음)와 캐시된 backend(S3)가 다르기
때문이다. 해결하려면 `-migrate-state` 또는 `-reconfigure`가 필요한데
둘 다 실제 State 처리 방식을 바꾸는 명령이라(전자는 마이그레이션을
수행하고, 후자는 기존 backend 설정을 버릴 수 있다) 원래 이관 절차의
정확한 `-backend-config` 인자를 알지 못한 채 실행하면 실제 State
포인터를 훼손할 위험이 있다. AGENTS.md는 Terraform State를 직접
수정하지 않도록 요구하므로, 이 재구성은 사람이 원래 이관 절차의 정확한
값으로 직접 판단해 실행해야 한다고 보고 이 세션에서는 중단했다. 정적
검증(fmt/validate/tflint/checkov)은 모두 완료했다.

TASK.md가 요구하는 필수 검증의 실행 결과:

| 명령 | 결과 |
| --- | --- |
| `terraform fmt -check -recursive infra` | PASS |
| `terraform validate`(격리된 임시 복사본, 실제 Backend 미사용) | PASS |
| `tflint --recursive` | PASS |
| `checkov -d infra --framework terraform` | PASS(이 PR이 건드린 리소스 기준 — 전체 결과는 §3 참고) |

executed_checks: fmt, validate(격리 복사본), tflint, checkov, policy_sentry
리소스 수준 권한 조회
passed_checks: 위 전부
failed_checks: 없음(이 PR이 변경한 파일 기준)
blocked_checks: `terraform plan`(실제 Backend) — 위 사유 참고

## 1. 구현 범위

설계 §13 Terraform ownership에 선언된 파일만 수정했다.

| 경로 | 상태 |
| --- | --- |
| `infra/bootstrap/oidc.tf` | 기존 파일 수정. `test_server_deploy_trust`(OIDC 신뢰 정책), `aws_iam_role.test_server_deploy`, `test_server_deploy_permissions`(ECR push + SSM SendCommand 최소 권한), `aws_iam_role_policy.test_server_deploy` 추가 |
| `infra/bootstrap/outputs.tf` | 기존 파일 수정. `test_server_deploy_role_arn` output 추가 |

`infra/environments/dev/test-server/**`, `infra/modules/**`,
`.github/workflows/**`는 수정하지 않았다(설계 §13 범위 제외와 일치).

## 2. 설계 요구사항 추적표

| 설계 요구사항(D-4) | 구현 위치 | 상태 |
| --- | --- | --- |
| §4/§15: `sub` claim을 `ref:refs/heads/main`으로만 한정 | `test_server_deploy_trust` 두 번째 condition | 구현됨 |
| §5: ECR push 권한을 리포지토리 ARN으로 한정 | `test_server_deploy_permissions`의 `EcrPush` statement | 구현됨 |
| §5: `ecr:GetAuthorizationToken`은 AWS 제약상 `Resource = "*"` | `EcrAuth` statement | 구현됨(policy_sentry로 리소스 수준 미지원 확인, §3) |
| §5: SSM `SendCommand`를 인스턴스 태그 + `AWS-RunShellScript` 문서로 한정 | `SendCommandToTestServerInstance`, `SendCommandRunShellScriptDocument` statement | 구현됨 |
| §5 SEC-2: SSM 조회 action의 리소스 수준 권한 지원 여부 빌드 단계 검증 | `ReadCommandResults` statement + §3 | 검증 완료 — `Resource = "*"` 불가피 확인 |
| §15: GitHub Environment 게이트 미적용 | 신뢰 정책에 Environment 조건 없음(`infra_apply_trust`와 다름) | 구현됨(설계 결정대로) |
| §15: Role 이름 `${project_prefix}-test-server-deploy` | `aws_iam_role.test_server_deploy.name` | 구현됨 |
| §9: `infra/bootstrap`의 기존 S3 Backend 재사용, 새 모듈/environment 없음 | 파일 범위(§1) | 구현됨 |

## 3. 정적 검증 상세

### 3.1 `terraform fmt`

```text
terraform fmt -check -recursive infra
```

차이 없음(통과).

### 3.2 `terraform validate`

`infra/bootstrap`은 로컬에 실제 S3 Backend를 가리키는 `.terraform/terraform.tfstate`
캐시가 있어(§0) `-backend=false`로도 그 backend에 접근을 시도해 403을
반환했다. 사용자의 로컬 State를 건드리지 않기 위해 `infra/` 전체를
스크래치 디렉터리로 복사(`.terraform`, `*.tfstate*`, `tfplan` 제외)해
격리된 환경에서 검증했다.

```text
terraform -chdir=<격리 복사본>/bootstrap init -backend=false
terraform -chdir=<격리 복사본>/bootstrap validate
```

결과: `Success! The configuration is valid.`(기존에도 있던
`manage_github_oidc_provider` 미선언 변수 경고 1건은 이 PR과 무관한
기존 상태 — `terraform.tfvars`에는 있지만 `variables.tf`에 선언되지
않은 값이며, 이 PR이 추가하거나 악화시키지 않았다.)

### 3.3 `tflint --recursive`

경고 없음(통과).

### 3.4 `checkov -d infra --framework terraform`

```text
Passed checks: 438, Failed checks: 11, Skipped checks: 9
```

11건의 실패는 전부 `aws_s3_bucket`(`bootstrap/state_backend.tf`,
`modules/s3-private-bucket/main.tf`) 리소스에 대한 기존 실패(이벤트 알림
미설정, 교차 리전 복제 미설정, KMS 기본 암호화 미설정)이며, 이 PR이
수정한 `oidc.tf`/`outputs.tf`나 신규 IAM 리소스와 무관하다. 이 PR은
`--skip-check` 목록을 추가하거나 변경하지 않았다.

### 3.5 `policy_sentry`로 IAM action 리소스 수준 권한 확인

`uvx --from policy_sentry policy_sentry query action-table`로 다음을
확인했다(공식 IAM 서비스 권한 부여 참조 데이터 기반).

| Action | 리소스 수준 지원 | 결론 |
| --- | --- | --- |
| `ecr:GetAuthorizationToken` | 미지원(`*`) | `EcrAuth` statement에서 `Resource = "*"` 유지(AWS 제약) |
| `ecr:PutImage`, `InitiateLayerUpload`, `UploadLayerPart`, `CompleteLayerUpload`, `BatchCheckLayerAvailability`, `BatchGetImage`, `DescribeRepositories` | `repository/${RepositoryName}` | 리포지토리 ARN으로 한정(구현됨) |
| `ssm:SendCommand` | `document/${DocumentName}`, `instance/${InstanceId}` 등 | 문서 ARN + 인스턴스(태그 조건)로 한정(구현됨) |
| `ssm:GetCommandInvocation`, `ssm:ListCommandInvocations` | 미지원(`*`) | `ReadCommandResults` statement에서 `Resource = "*"` 유지(AWS 제약, D-4 §5 SEC-2 검증 완료) |

## 4. 변경 위험도 재확인 (Change risk)

```text
risk_level: MEDIUM
risk_reasons:
  - 신규 IAM Role/OIDC Trust Policy 생성(권한 확대에 해당)
  - 쓰기 권한(ECR push, SSM 명령 실행)을 GitHub Actions에 위임
affected_resources: IAM Role 1개, IAM Policy 1개(기존 OIDC Provider 재사용)
possible_data_loss: 없음(신규 생성)
downtime: 없음(신규 생성, 기존 리소스 변경 없음)
rollback_available: 예 — 신규 리소스이므로 보호된 workflow에서 terraform destroy로 롤백 가능(사람 승인 필요, 이 세션에서는 미실행)
required_approvals: "@Byuntil", "@tkv00", GitHub infrastructure-apply Environment
required_tests: terraform fmt/validate/tflint/checkov(완료), terraform plan(사람이 backend 재구성 후 실행 필요)
```

D-4 설계 문서의 위험도 평가(MEDIUM)와 동일하게 유지한다. 구현 중 새로
발견된 위험은 없다.

## 5. 남은 작업(빌드가 해결하지 못한 항목)

- **`terraform plan`(실제 Backend) 미실행**: §0 사유 참고. 사람이
  `infra/bootstrap`의 원래 `-backend-config` 인자(버킷·키·리전)를 확인해
  `terraform init -reconfigure -backend-config=...`로 재구성한 뒤
  `terraform plan`을 실행해야 한다. 이 값들은 State/backend 설정 자체라
  이 문서에는 기록하지 않는다.
- **GitHub repository secret 미생성**: `test_server_deploy_role_arn`
  output 값을 apply 이후 사람이 GitHub repository secret(이름은 #231
  구현 시 정함)으로 등록해야 한다.
