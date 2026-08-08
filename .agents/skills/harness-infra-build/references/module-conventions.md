---
name: terraform-module-conventions
description: Qello Terraform의 디렉터리, 모듈, 변수, 출력, 버전, 태그와 주석 작성 규칙을 제공한다.
user-invocable: false
---

# Terraform Module Conventions

## 기본 구조

```text
infra/
├── bootstrap/
├── modules/
│   ├── network/
│   ├── compute/
│   ├── database/
│   └── observability/
└── environments/
    ├── dev/
    ├── staging/
    └── production/
```
## 규칙
- 재사용 가능한 리소스 집합은 modules/에 둔다.
- 환경 조합은 environments/에 둔다.
- Backend bootstrap과 실제 workload를 분리한다.
- Module이 환경 이름을 직접 추론하지 않게 한다.
- Provider 버전을 고정한다.
- 외부 Module 버전을 고정한다.
- .terraform.lock.hcl을 커밋한다.
- 변수에는 type과 description을 작성한다.
- 가능한 경우 validation을 작성한다.
- Output에는 description을 작성한다.
- 민감 Output에는 sensitive = true를 작성한다.
- 공통 태그를 일관되게 적용한다.
- local-exec, remote-exec, provisioner는 사용하지 않는다.
- 비밀값을 기본값에 작성하지 않는다.

## 주석
- 코드가 무엇을 하는지 반복하지 않는다.
- 설계 의도, AWS 제약과 예외 제거 조건을 설명한다.
- depends_on에는 명시적인 이유가 필요하다.
- ignore_changes에는 관리 주체와 제거 조건이 필요하다.
- TODO에는 Issue와 재검토 날짜가 필요하다.
- 장문 근거는 ADR로 이동한다.