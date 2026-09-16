---
name: "harness-infra-build"
description: "\uc0ac\ub78c\uc5d0\uac8c \uc2b9\uc778\ub41c Infrastructure Design Report\ub97c Terraform\uc73c\ub85c \uad6c\ud604\ud558\uace0 \ub3c5\ub9bd \uac80\uc99d\uacfc plan \uc99d\uac70\ub97c \uc0dd\uc131\ud55c\ub2e4."
---

# Infrastructure Build Workflow

`AGENTS.md`, `TASK.md`,
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

## Reference와 역할 분리

구현 에이전트는 다음 reference를 순서대로 읽는다.

1. `references/module-conventions.md`
2. `references/terraform-build.md`

독립 검증 에이전트에게 실제 변경 파일과 다음 reference를 인계한다.

1. `references/terraform-verify.md`
2. `references/change-risk.md`

독립 검증 에이전트는 Terraform 소스를 수정하지 않는다. 구현과 검증을 동일한
판단으로 처리하지 않고, 실제 위임을 수행하지 않았다면 독립 검증으로
보고하지 않는다.

## 실행

```bash
./harness infra-build --id <DESIGN-ID>
```

구현 후 필수 정적 검사는 다음 문서 예시를 따른다. 승인된 Plan 환경이
준비된 경우에만 plan 증거를 생성한다.

```text
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
승인된 Plan 환경에서만 plan 증거 생성. AI apply/state 조작 금지.
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
- terraform state *
- terraform force-unlock
- terraform taint
- AWS 리소스 변경 명령
- 프로덕션 배포

승인된 Plan 환경이 없으면 plan을 시도하지 않고 미실행 범위와 남은 위험을
보고한다. 승인 여부와 관계없이 AI 에이전트는 apply와 State 조작·복구를
실행하지 않는다.
