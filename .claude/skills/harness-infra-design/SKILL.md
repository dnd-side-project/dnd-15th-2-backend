---
name: harness-infra-design
description: 승인 전 AWS 인프라 요구사항, 아키텍처 대안, 보안, 비용, 복구와 변경 위험을 분석하고 Infrastructure Design Report를 생성한다.
argument-hint: "--id <DESIGN-ID>"
disable-model-invocation: true
allowed-tools:
   - Read
   - Glob
   - Grep
   - Bash(./harness infra-design *)
---

# Infrastructure Design Workflow

`AGENTS.md`, `CLAUDE.md`, `TASK.md`와
`agents/infrastructure-orchestrator.md`를 읽는다.

## 실행 전 게이트

다음을 확인한다.

- 현재 브랜치가 `infra/gh-<ISSUE>-<slug>` 형식이다.
- 현재 브랜치와 연결된 GitHub Issue가 존재한다.
- `TASK.md`의 Issue 번호가 브랜치와 일치한다.
- `$ARGUMENTS`에 유효한 `DESIGN-ID`가 포함되어 있다.
- 기존 작업 트리에 다른 사용자의 변경이 있으면 보존한다.

조건이 충족되지 않으면 구현하거나 설계를 확정하지 않고 `BLOCKED`로 반환한다.

## Skill 실행 순서

다음 내부 Skill을 순서대로 사용한다.

1. `infra-intake`
2. `infra-architecture`
3. `infra-security-review`
4. `infra-cost-review`
5. `infra-change-risk`

보안 검토와 비용 검토는 동일한 설계 초안을 대상으로 독립적으로 수행한다.

## 하네스 실행

```bash
./harness infra-design $ARGUMENTS
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