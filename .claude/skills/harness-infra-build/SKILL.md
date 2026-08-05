---
name: harness-infra-build
description: 사람에게 승인된 Infrastructure Design Report를 Terraform으로 구현하고 독립 검증과 plan 증거를 생성한다.
argument-hint: "--id <DESIGN-ID>"
disable-model-invocation: true
allowed-tools:
   - Read
   - Glob
   - Grep
   - Edit
   - Write
   - Bash(./harness infra-build *)
   - Bash(terraform fmt *)
   - Bash(terraform init *)
   - Bash(terraform validate *)
   - Bash(terraform plan *)
---

# Infrastructure Build Workflow

`AGENTS.md`, `CLAUDE.md`, `TASK.md`,
`agents/infrastructure-executor.md`와 승인된 Infrastructure Design Report를 읽는다.

## 실행 전 게이트

다음을 모두 확인한다.

- GitHub Issue가 존재한다.
- `TASK.md`와 브랜치의 Issue 번호가 일치한다.
- `DESIGN-ID`가 일치한다.
- 설계 상태가 `APPROVED_FOR_BUILD`이다.
- 승인된 Terraform 수정 범위가 기록되어 있다.
- 사람의 설계 승인 증거가 존재한다.
- 기존 작업 트리 변경을 보존할 수 있다.

하나라도 확인할 수 없으면 `BLOCKED`로 반환한다.

## Skill 실행 순서

다음 Skill을 사용한다.

1. `terraform-module-conventions`
2. `terraform-build`
3. `terraform-verify`
4. `infra-change-risk`

구현과 검증은 동일한 판단으로 처리하지 않는다.

## 실행

```bash
./harness infra-build $ARGUMENTS
```

## 종료 조건

다음 중 하나로 반환한다.

- PASS
- FAIL
- BLOCKED

다음을 실행하지 않는다.

- terraform apply
- terraform destroy
- terraform import
- terraform state
- AWS 리소스 변경 명령
- 프로덕션 배포

PR을 생성할 수 있는 plan 증거까지만 만든다.