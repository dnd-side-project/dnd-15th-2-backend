---
name: terraform-verify
description: Terraform 변경을 독립적으로 정적 검사하고 plan, 보안, 비용과 위험 증거를 생성한다.
user-invocable: false
---

# Terraform Verification

Terraform 소스 코드를 수정하지 않고 검증한다.

## 필수 검사

```bash
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```

저장소에 구성되어 있으면 다음을 실행한다.

```text
tflint
terraform test
Checkov
Conftest
Infracost
terraform-docs 검증
```

승인된 Plan 환경이 준비된 경우:

```bash
terraform plan -lock-timeout=5m -out=tfplan
# .tfplan 바이너리는 실행별 메타데이터(생성 시각 등)를 담아 같은 State·설정에서
# 다시 만들어도 매번 다른 파일이 된다. infrastructure-apply.yml이 비교하는
# plan_sha256은 resource_changes만 정규화한 해시다(#264).
terraform show -json tfplan | jq -S '.resource_changes' > normalized-plan.json
sha256sum normalized-plan.json
sha256sum .terraform.lock.hcl
```

## Plan 분류
- 생성
- 수정
- 삭제
- 교체
- IAM 권한 변경
- Network 공개 범위 변경
- 데이터 계층 변경
- 백업 또는 암호화 변경

## 보안 규칙
- plan 원문을 PR 본문에 출력하지 않는다.
- plan JSON 전체를 공개 로그에 출력하지 않는다.
- 실제 Account ID, ARN, 주소와 민감값을 출력하지 않는다.
- scanner 결과를 임의로 suppress하지 않는다.
- 검증 실패를 자동 수정하지 않는다.

## 주석 검사

다음을 검출한다.

- Issue 없는 TODO
- 날짜 없는 TODO
- 이유 없는 depends_on
- 제거 조건 없는 ignore_changes
- ignore_changes = all
- 만료일 없는 보안 예외
- 민감정보가 포함된 주석
- 에이전트의 추론 과정을 기록한 주석

## 결과
```text
status: PASS | FAIL | BLOCKED
terraform_version:
provider_lock_sha256:
plan_sha256:
executed_checks:
passed_checks:
failed_checks:
blocked_checks:
created_resources:
updated_resources:
replaced_resources:
deleted_resources:
security_findings:
cost_delta:
remaining_risks:
```

필수 검사를 실행할 수 없으면 PASS가 아니라 BLOCKED를 반환한다.