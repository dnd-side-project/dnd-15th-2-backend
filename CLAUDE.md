@AGENTS.md
@TASK.md

# Claude Code Entry Point

Claude Code는 작업 시작 시 이 파일을 진입점으로 사용한다.

공통 정책은 `AGENTS.md`에서 가져오고, 현재 작업의 범위와 상태는 `TASK.md`에서 가져온다. 이후 작업 유형에 맞는 Skill과 역할 문서를 읽는다.

이 파일은 Claude Code 전용 실행 규칙만 정의하며 `AGENTS.md`의 공통 정책을 재정의하거나 완화하지 않는다.

## 작업 시작 순서

모든 작업은 다음 순서로 시작한다.

1. `AGENTS.md`의 공통 정책을 확인한다.
2. `TASK.md`의 현재 작업 범위와 승인 상태를 확인한다.
3. 현재 branch에서 연결된 GitHub Issue를 확인한다.
4. 작업 유형에 맞는 Skill을 선택한다.
5. Skill이 지정한 역할 문서를 읽는다.
6. 허용된 범위와 금지된 작업을 확인한 뒤 작업을 시작한다.

`TASK.md`가 없거나 현재 branch 및 GitHub Issue와 일치하지 않으면 구현을 시작하지 않는다.

## 빠른 실행

```bash
./harness doctor
source ~/.config/qello-harness/env.zsh
h status
h context
```

GitHub Issue 번호는 저장소 설정에 고정하지 않고 현재 branch에서 파생한다.

새 작업마다 다음 순서로 작업 컨텍스트를 생성한다.

```bash
h start
h task-init
```

`h task-init`으로 생성된 `TASK.md`가 현재 작업의 범위, 승인 상태와 추적 정보를 나타낸다.

## 프로젝트 Skill

### 공통 워크플로

Issue 생성부터 커밋과 PR 생성까지의 흐름은 `docs/harness/WORKFLOW_SKILLS.md`를 따른다.

* `/harness-issue`

    * GitHub Issue 생성
    * GitHub Project 필드 연결
    * 작업 branch 생성
* `/harness-commit`

    * 변경 사항 검토
    * 검토 가능한 목적 단위로 커밋 분할
* `/harness-pr`

    * 로컬 검증
    * 변경 요약 작성
    * Pull Request 생성

### 테스트 역할

* `/harness-test-plan`

    * 테스트 범위와 전략을 설계하는 테스트 오케스트레이터
* `/harness-test-run`

    * 승인된 테스트 계획을 실행하고 증거를 생성하는 테스트 실행 에이전트

### 인프라 역할

* `/harness-infra-design`

    * AWS 인프라 요구사항과 대안을 분석한다.
    * `agents/infrastructure-orchestrator.md`를 역할 계약으로 사용한다.
    * Terraform 구현 전에 Infrastructure Design Report를 생성한다.
    * 설계 승인 전에는 Terraform 구현을 시작하지 않는다.
* `/harness-infra-build`

    * 승인된 설계에 포함된 Terraform만 구현한다.
    * `agents/infrastructure-executor.md`를 역할 계약으로 사용한다.
    * 정적 검사와 `terraform plan` 증거를 생성한다.
    * `terraform apply` 또는 AWS 배포를 실행하지 않는다.
* `/harness-review`

    * 설계, 구현, 검증 결과를 검토하는 PM 및 리뷰어 역할을 수행한다.

## 인프라 작업 진입 규칙

모든 인프라 변경은 다음 순서를 따라야 한다.

```text
GitHub Issue
→ TASK.md 초기화
→ /harness-infra-design
→ Infrastructure Design Report
→ 사람의 설계 승인
→ /harness-infra-build
→ Terraform 정적 검증
→ Terraform plan
→ Pull Request
→ 사람의 코드 승인
→ 보호된 GitHub Actions에서 apply
```

다음 조건을 모두 만족하기 전에는 `/harness-infra-build`를 실행하지 않는다.

* 현재 branch와 연결된 GitHub Issue가 존재한다.
* `TASK.md`에 유효한 `DESIGN-ID`가 기록되어 있다.
* Infrastructure Design Report가 존재한다.
* 설계 상태가 `APPROVED_FOR_BUILD`이다.
* 구현 범위와 Terraform 소유 파일이 명시되어 있다.

승인 증거를 확인할 수 없으면 구현하지 않고 `BLOCKED`로 보고한다.

## Terraform 전용 규칙

AWS 인프라는 Terraform으로만 관리한다.

다음 IaC 또는 직접 리소스 생성 방식을 추가하지 않는다.

* AWS CDK
* CloudFormation
* Pulumi
* AWS SDK를 이용한 인프라 생성
* AWS CLI 스크립트를 이용한 선언적 인프라 대체
* 애플리케이션 시작 시 AWS 리소스를 생성하는 방식

Terraform은 다음 원칙을 따른다.

* Provider 버전을 명시한다.
* Module 버전을 명시한다.
* `.terraform.lock.hcl`을 커밋한다.
* 비밀값을 변수 기본값 또는 코드에 기록하지 않는다.
* 계정별 값과 환경별 값은 변수 또는 CI 환경에서 주입한다.
* Terraform State와 plan을 민감 정보로 취급한다.
* 승인된 설계 범위를 벗어난 리소스를 추가하지 않는다.
* 리소스 변경 이유를 설계 문서와 추적할 수 있어야 한다.

## 인프라 주석 규칙

Terraform 주석은 코드의 동작을 번역하지 않고 코드만으로 알 수 없는 설계 의도와 제약을 설명한다.

다음 경우에는 주석을 작성한다.

* AWS 제약으로 일반적인 방식과 다르게 구현한 경우
* 보안 또는 컴플라이언스 요구사항이 있는 경우
* 비용 절감을 위해 가용성이나 기능을 제한한 경우
* 명시적 `depends_on`을 추가한 경우
* `lifecycle` 또는 `prevent_destroy`를 추가한 경우
* `ignore_changes`를 추가한 경우
* 외부 시스템이 특정 속성을 관리하는 경우
* 임시 예외 또는 호환성 설정을 추가한 경우

다음 주석은 작성하지 않는다.

```hcl
# VPC를 생성한다.
resource "aws_vpc" "main" {
}

# 버킷 이름
bucket = var.bucket_name

# Claude가 생성함
# 요청에 따라 수정함
```

`depends_on`, `ignore_changes`와 임시 예외에는 이유와 제거 조건을 작성한다.

```hcl
# ECS 배포 워크플로가 Task Definition revision을 갱신한다.
# 배포 주체가 Terraform으로 전환되면 이 예외를 제거한다.
lifecycle {
  ignore_changes = [
    task_definition
  ]
}
```

TODO에는 Issue ID와 재검토 날짜를 포함한다.

```hcl
# TODO(INFRA-142, 2026-10-31): Multi-AZ 전환 후 단일 NAT Gateway 예외를 제거한다.
```

다음 형태는 금지한다.

```hcl
# TODO: 나중에 수정
# FIXME
# 임시
```

주석에는 다음 정보를 기록하지 않는다.

* AWS Account ID
* 전체 ARN
* 실제 서버 주소
* 실제 IP 또는 도메인
* 토큰과 비밀값
* `.env` 값
* Terraform State 값
* Terraform plan 원문
* Claude의 추론 과정
* 사용자와 Claude의 대화 내용

장문의 설명은 Terraform 코드가 아니라 ADR 또는 Infrastructure Design Report에 기록한다.

## Claude Code 명령 제한

Claude Code 세션에서는 다음 명령을 실행하지 않는다.

```text
terraform apply
terraform destroy
terraform import
terraform state *
terraform force-unlock
terraform taint
terraform workspace new
terraform workspace delete
```

옵션이나 shell alias를 이용해 동일한 결과를 만드는 명령도 금지한다.

다음 작업도 수행하지 않는다.

* AWS CLI를 이용한 리소스 생성, 변경 또는 삭제
* AWS Console 조작을 대신하는 자동화 작성
* GitHub Environment 보호 규칙 변경
* Apply workflow의 승인 게이트 제거
* CODEOWNERS 변경을 통한 승인 우회
* IAM 장기 Access Key 생성
* 보호된 branch에 직접 push
* Terraform State 직접 편집
* 승인된 Terraform plan과 다른 구성을 적용

실제 AWS 리소스 변경은 보호된 GitHub Actions workflow만 수행한다.

## 파일 수정 범위

각 역할은 역할 문서에 선언된 파일만 수정한다.

인프라 실행 에이전트의 기본 수정 범위는 다음과 같다.

```text
infra/**
docs/infrastructure/**
승인된 Terraform 테스트 파일
자동 생성 대상 Terraform 문서
```

다음 파일은 명시적인 별도 승인 없이 수정하지 않는다.

```text
CLAUDE.md
AGENTS.md
.claude/**
agents/**
.github/workflows/*apply*
CODEOWNERS
scripts/guard-*
```

에이전트 자신의 권한, 금지 명령, 승인 게이트를 변경하는 작업은 수행하지 않는다.

## 모델 선택

역할 문서의 `model_profile`은 논리 이름이다.

실제 Claude 모델과 사용량은 다음 파일에서 개인 또는 CI 환경에 맞게 연결한다.

```text
agents/model-profiles.local.yml
```

저장소는 특정 API 모델 ID를 강제하지 않는다.

모델 변경은 역할의 권한, 작업 범위 또는 승인 조건을 변경하지 않는다.

## 공통 금지 사항

* GitHub Issue 게이트 없이 구현 시작
* 유효한 `TASK.md` 없이 작업 시작
* 승인된 설계 없이 Terraform 구현
* 승인 없이 인프라 적용 또는 프로덕션 변경
* Terraform 이외의 IaC 추가
* `.env`, 토큰, 계정 또는 서버 식별자를 대화, 파일이나 로그에 복사
* Terraform State 또는 plan 원문을 PR 본문이나 공개 로그에 기록
* 다른 사용자의 변경을 자동으로 정리하거나 되돌리기
* 테스트 실패를 숨기기
* 실행하지 않은 테스트를 통과로 보고하기
* 검증 오류를 자동으로 무시하거나 suppression 추가
* 승인 상태 또는 검증 결과를 추측해서 보고하기
* 자신이 작성한 설계나 구현을 자신이 승인한 것으로 처리하기

필수 정보나 승인 상태를 확인할 수 없으면 작업을 추측해서 진행하지 않고 `BLOCKED`로 반환한다.
