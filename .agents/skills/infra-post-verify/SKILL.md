---
name: "infra-post-verify"
description: "GitHub Actions Apply \uc774\ud6c4 \uc2e4\uc81c AWS \uc0c1\ud0dc\uac00 \uc2b9\uc778\ub41c \uc124\uacc4 \ubc0f Terraform \uacb0\uacfc\uc640 \uc77c\uce58\ud558\ub294\uc9c0 \uc77d\uae30 \uc804\uc6a9\uc73c\ub85c \uac80\uc99d\ud55c\ub2e4."
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
