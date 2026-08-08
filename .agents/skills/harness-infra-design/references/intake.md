---
name: infra-intake
description: AWS 인프라 설계 전에 Issue와 TASK.md에서 요구사항, 제약, 누락 정보와 가정을 추출한다.
user-invocable: false
---

# Infrastructure Intake

아키텍처를 선택하기 전에 요구사항을 구조화한다.

## 입력

다음을 읽는다.

- `AGENTS.md`
- `TASK.md`
- 현재 GitHub Issue
- 관련 ADR
- 기존 인프라 문서
- 기존 Terraform 코드

## 필수 확인 항목

- 대상 환경
- AWS Region
- 월 예산 상한
- 예상 요청량
- 예상 동시 사용자
- 외부 트래픽
- 저장 용량과 증가율
- 데이터 민감도
- 공개 접근 범위
- 가용성 목표
- RTO
- RPO
- 배포 빈도
- 운영 인력
- 운영 가능 시간
- 서비스 예상 수명
- 기존 AWS 리소스
- 기존 도메인과 인증서 관리 주체
- 외부 연동
- 법적 또는 보안 제약

## 상태 분류

각 항목을 다음 중 하나로 분류한다.

- `CONFIRMED`: Issue 또는 승인 문서에 명시됨
- `ASSUMED`: 설계를 위해 임시 가정함
- `UNKNOWN`: 확인할 수 없음
- `BLOCKED`: 확인 전에는 안전한 설계가 불가능함

## 규칙

- 누락된 값을 사실처럼 생성하지 않는다.
- 비용과 트래픽 값을 임의로 확정하지 않는다.
- 현재 코드에서 발견한 값을 승인된 요구사항으로 간주하지 않는다.
- 아키텍처 대안을 이 단계에서 선택하지 않는다.
- 보안 관련 정보가 누락되어도 위험을 축소하지 않는다.

## 출력

다음 형식으로 오케스트레이터에 반환한다.

```text
status:
confirmed:
assumed:
unknown:
blocked:
constraints:
required_human_decisions:
evidence:
```
필수 입력이 부족하면 BLOCKED로 반환한다.
