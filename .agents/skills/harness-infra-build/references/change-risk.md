---
name: infra-change-risk
description: AWS와 Terraform 변경의 삭제, 교체, 데이터 손실, 권한 확대와 복구 난이도를 평가한다.
user-invocable: false
---

# Infrastructure Change Risk

변경을 다음 등급 중 하나로 분류한다.

## LOW

- 태그 변경
- 설명 변경
- 영향 없는 Output 추가
- 검증 규칙 강화

## MEDIUM

- 비운영 리소스 추가
- 로그와 Alarm 추가
- 비파괴적인 스케일 조정
- 제한적인 Security Group 축소

## HIGH

- 운영 리소스 추가 또는 변경
- IAM 권한 확대
- 네트워크 경로 변경
- Backup 설정 변경
- Provider 주요 버전 변경
- 비용이 지속적으로 증가하는 변경

## CRITICAL

- 리소스 삭제
- 리소스 교체
- 데이터베이스 변경
- State Backend 변경
- 암호화 제거
- 공개 접근 확대
- 백업 보존 기간 축소
- 복구 불가능한 변경
- 운영 데이터 이전

## 필수 출력

```text
risk_level:
risk_reasons:
affected_resources:
possible_data_loss:
downtime:
rollback_available:
recovery_procedure:
required_approvals:
required_tests:
```

HIGH 또는 CRITICAL 변경은 롤백·복구 계획 없이 승인 가능 상태로 반환하지 않는다