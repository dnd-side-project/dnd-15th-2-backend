---
name: terraform-build
description: 승인된 Infrastructure Design Report의 요구사항만 Terraform 코드로 구현한다.
user-invocable: false
---

# Terraform Build

## 입력

- 승인된 Infrastructure Design Report
- `TASK.md`
- 변경 대상 Terraform 파일
- `terraform-module-conventions`
- 현재 Terraform State 구조
- 기존 `.terraform.lock.hcl`

## 구현 규칙

1. 설계 요구사항과 Terraform 파일의 추적표를 작성한다.
2. 승인된 범위의 파일만 수정한다.
3. 승인되지 않은 리소스를 추가하지 않는다.
4. 기존 이름과 State 주소를 불필요하게 변경하지 않는다.
5. 삭제 또는 교체 가능성이 있으면 구현 전에 보고한다.
6. IAM은 최소 권한으로 작성한다.
7. 암호화, 로그, 백업, 태그와 삭제 보호를 코드로 표현한다.
8. 환경별 값은 변수로 전달한다.
9. 실제 계정 정보와 주소를 작성하지 않는다.
10. 주석 정책을 준수한다.

## 구현 후 자체 검사

```bash
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```
자체 검사는 독립 검증을 대체하지 않는다.

## 출력
status:
design_requirements:
changed_files:
implemented_requirements:
not_implemented:
assumptions:
destructive_risks:
verification_required: