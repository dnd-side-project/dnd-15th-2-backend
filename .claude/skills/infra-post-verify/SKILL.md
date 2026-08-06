---
name: infra-post-verify
description: GitHub Actions Apply 이후 실제 AWS 상태가 승인된 설계 및 Terraform 결과와 일치하는지 읽기 전용으로 검증한다.
argument-hint: "--id <DESIGN-ID>"
disable-model-invocation: true
---

# Post-deploy Infrastructure Verification

승인된 Infrastructure Design Report, 적용된 commit과 Apply 결과를 확인한다.

## 검증 영역

- 예상 리소스 존재 여부
- 삭제되거나 교체된 리소스
- Public 접근 범위
- Security Group
- IAM Role과 Trust policy
- 암호화
- 로그 수집
- 로그 보존 기간
- Alarm
- 백업
- 삭제 보호
- 애플리케이션 Health Check
- Terraform drift
- 예상 비용 증가 신호

## 금지

- AWS 리소스 생성
- AWS 리소스 수정
- AWS 리소스 삭제
- Terraform Apply
- State 변경
- 발견한 문제의 자동 수정

## 출력

```text
status: PASS | FAIL | BLOCKED
design_id:
commit_sha:
verified_resources:
configuration_mismatches:
health_findings:
security_findings:
drift_findings:
required_human_actions:
rollback_recommended:
```