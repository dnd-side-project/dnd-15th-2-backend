---
name: infra-security-review
description: AWS와 Terraform 설계의 IAM, 네트워크, 암호화, 비밀 관리, 상태 파일과 공급망 위험을 독립적으로 검토한다.
user-invocable: false
---

# Infrastructure Security Review

아키텍처 설계자를 신뢰하지 않고 설계 문서와 Terraform 변경을 독립적으로 검토한다.

## 검토 영역

### IAM

- 최소 권한
- `Action = "*"`
- `Resource = "*"`
- Trust policy
- Cross-account 접근
- Plan 역할과 Apply 역할 분리
- GitHub OIDC subject 제한
- 장기 Access Key 존재 여부

### Network

- Public Subnet 배치 필요성
- Security Group ingress와 egress
- `0.0.0.0/0`
- 관리 포트 공개
- 데이터베이스 공개 접근
- VPC Endpoint 필요성
- NAT와 egress 통제

### Data protection

- 저장 데이터 암호화
- 전송 데이터 암호화
- KMS Key 정책
- Secret Manager 또는 Parameter Store
- 로그의 민감정보
- 백업 암호화

### Terraform

- State Backend 보호
- plan 노출
- 민감 output
- Provider와 Module source
- 버전 고정
- `local-exec`
- `remote-exec`
- 과도한 `ignore_changes`
- 보안 검사 suppression

### Operations

- CloudTrail
- 로그 보존
- Alarm
- 백업
- 복구 권한
- 삭제 보호
- 감사 가능성

## Finding 형식

```text
id:
severity: CRITICAL | HIGH | MEDIUM | LOW | INFO
category:
evidence:
risk:
required_change:
exception_allowed:
exception_requirements:
```
## 규칙
- Finding을 구현 에이전트 대신 자동 수정하지 않는다.
- 근거 없는 보안 승인을 하지 않는다.
- 예외에는 소유자, 보완 통제, Issue와 만료일을 요구한다.
- 검증할 수 없는 항목은 BLOCKED로 기록한다.
