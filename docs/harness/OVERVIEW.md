# Harness Engineering Overview

## 목적

백엔드 개발자 두 명이 Claude Code와 Codex를 병행하더라도 Jira 범위, 파일
소유권, 검증 증거, 사람 승인이 사라지지 않게 만드는 저장소 수준 운영 체계다.

하네스는 다섯 층으로 구성된다.

| 층 | 구성 | 역할 |
| --- | --- | --- |
| 업무 게이트 | Jira, GitHub Issue, 브랜치 규칙 | 왜·무엇을 하는지 고정 |
| 에이전트 계약 | `AGENTS.md`, `agents/`, 도구별 스킬 | 설계와 실행 분리 |
| 실행 명령 | `harness`, Mac 단축키 | 반복 작업 단순화 |
| 로컬 게이트 | Husky `pre-commit`, `prepare-commit-msg`, `commit-msg`, `pre-push` | 자동 조립과 규칙 위반 조기 차단 |
| 증거 | 테스트 계획/보고서, 인프라 설계 | 결과와 남은 위험 기록 |
| 강제 장치 | GitHub Actions, CODEOWNERS, Environment | 규칙 누락과 위험한 적용 차단 |

## 동적 작업 문맥

하네스에는 기본 Jira 키를 저장하지 않는다. 작업을 시작할 때 사용자가 전달한
Jira 키와 GitHub Issue 번호로 branch를 만들고, 이후 명령은 branch에서 두 값을
파생한다.

```text
feat/PAY-314-gh-42-refund-policy
     └ PAY-314  └ #42
```

commit, PR title, PR body가 이 문맥과 다르면 Husky 또는 CI가 거부한다.
`TASK.md`도 저장소 전역 설정이 아니라 현재 branch의 작업 계약이며 새 작업마다
`h task-init`으로 교체한다.

## 역할 모델

### 오케스트레이터

높은 성능과 넉넉한 사용량의 논리 프로필을 사용한다. 테스트 시나리오 또는 AWS
설계를 만들고, 실행 순서와 소유 파일을 정한다. 대량 구현은 하지 않는다.

### 실행 에이전트

비용 효율적인 Claude Sonnet 계열 또는 GPT-5.6 계열의 중간 사용량 논리
프로필을 사용한다. 승인된 시나리오와 파일만 구현하고 실행한다.

### PM/리뷰어

Jira 범위, 변경 증거, 민감정보, 복구 경로, 승인 상태를 검토한다. 실행 역할과
검토 역할을 한 턴에서 섞지 않는다.

실제 모델 ID는 `agents/model-profiles.local.yml`에만 설정한다. 팀 계정에 없는
모델명을 저장소가 강제하지 않는다.

## 테스트 하네스

테스트 오케스트레이터는 위험 기반 계획을 만들고, 실행 에이전트는 JUnit 5로
단위/통합 테스트를 구현한다.

자동 검증:

- 모든 테스트 메서드의 `@DisplayName`
- 모든 테스트 클래스의 정확한 생성 시각
- 원본 테스트 계획/시나리오 식별자
- 표준 보고서
- 비밀정보 패턴

실행 후 보고서는 코드, 인프라, DB, 동시성, 트랜잭션, 외부 API, 장애 복구를
별도 항목으로 분석한다.

## 인프라 하네스

초기 설계는 저비용 단일 애플리케이션 워크로드와 RDS를 기준으로 하되 EC2/ECS,
관리형/자체 운영 대안을 비교한다. Terraform 또는 AWS CDK만 사용한다.

설계와 plan은 PR에서 검토한다. apply는 별도 workflow이며 두 명의 정확한 PR
head 승인, protected Environment, OIDC, 명시적 확인 문구가 없으면 실행되지
않는다.

## 저장소에서 강제할 수 없는 것

다음은 GitHub 관리자 설정이 필요하다.

- 기본 브랜치 Ruleset
- 필수 status check
- 승인 수와 Code Owner review
- `infrastructure-apply` Environment reviewer
- Repository Variables
- AWS OIDC Role

파일이 존재한다고 위 설정까지 활성화된 것은 아니다.
