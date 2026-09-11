---
name: "harness-infra-design"
description: "\uc2b9\uc778 \uc804 AWS \uc778\ud504\ub77c \uc694\uad6c\uc0ac\ud56d, \uc544\ud0a4\ud14d\ucc98 \ub300\uc548, \ubcf4\uc548, \ube44\uc6a9, \ubcf5\uad6c\uc640 \ubcc0\uacbd \uc704\ud5d8\uc744 \ubd84\uc11d\ud558\uace0 Infrastructure Design Report\ub97c \uc0dd\uc131\ud55c\ub2e4."
---

# Infrastructure Design Workflow

`AGENTS.md`, 존재하는 경우 `TASK.md`,
`agents/infrastructure-orchestrator.md`를 읽는다.

## 설계 진입과 게이트

다음을 확인한다.

- 현재 브랜치, GitHub Issue, `TASK.md`의 일치 여부를 확인한다.
- 유효한 `DESIGN-ID`는 Issue와 연결해 생성할 수 있지만, ID 생성을 사람의
  설계 승인 증거로 간주하지 않는다.
- 기존 작업 트리에 다른 사용자의 변경이 있으면 보존한다.

Issue·`TASK.md`·Issue 번호 branch가 없어도 읽기, 요구사항 분석과 Project draft
계획·작업 분해는 할 수 있다. 이 상태에서는 설계를 승인 상태로 확정하거나
Terraform 구현을 시작하지 않는다. 설계 보고서를 완성하려면 유효한 Issue, 일치하는
branch·`TASK.md`·`DESIGN-ID`가 필요하다.

## Reference 적용 순서

다음 reference를 순서대로 읽어 설계에 적용한다.

1. `references/intake.md`
2. `references/architecture.md`
3. `references/security-review.md`
4. `references/cost-review.md`
5. `../harness-infra-build/references/change-risk.md`

이 reference를 읽는 것은 에이전트 위임이 아니다. 보안·비용 검토는 동일한
설계 초안을 각 독립 검토 에이전트에 인계하고, 각 결과를 구분해 통합한다.
독립 인계를 실행할 수 없으면 `BLOCKED`로 반환하고 독립 검토를 수행했다고
보고하지 않는다.

## 하네스 실행

```bash
./harness infra-design --id <DESIGN-ID>
```

## 필수 산출물

templates/infrastructure-design-report.md 형식을 사용해 다음을 작성한다.

- Design ID
- GitHub Issue
- 요구사항과 제약
- CONFIRMED, ASSUMED, UNKNOWN, BLOCKED
- 선택한 아키텍처
- 비교한 대안과 탈락 이유
- 네트워크
- 컴퓨팅
- 데이터베이스
- 저장소
- 관측성
- 백업과 복구
- IAM과 GitHub OIDC 경계
- Terraform State 설계
- 비용 가정과 계산
- 보안 검토 결과
- 변경 위험도
- 실패 모드
- 롤백 또는 복구 절차
- Terraform 소유 파일
- 검증 계획
- 사람의 결정이 필요한 항목

## 종료 조건

설계 상태는 다음 중 하나로 반환한다.

- READY_FOR_DESIGN_REVIEW
- BLOCKED
- FAIL

사람이 APPROVED_FOR_BUILD로 승인하기 전에는 Terraform 구현을 시작하지 않는다.
terraform apply, AWS CLI 변경 명령 또는 배포를 실행하지 않는다.
승인 후에도 설계 역할이 직접 Terraform을 구현하지 않고
`harness-infra-build`의 실행·독립 검증 단계로 인계한다.
