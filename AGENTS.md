# Qello Repository Agent Contract

이 문서는 Qello 저장소에서 동작하는 사람과 AI 에이전트의 공통 계약이다.

도구별 지침보다 이 문서와 현재 브랜치의 `TASK.md`가 우선한다.
`CLAUDE.md`, 개별 Skill, 역할 문서와 자동화 스크립트는 이 문서의 정책을 완화하거나 우회할 수 없다.

## 1. 작업 시작 게이트

구현 전에 다음 항목을 모두 충족한다.

1. 백로그와 스프린트는 GitHub Project draft item으로 관리한다.
2. 실제 구현을 시작할 draft item만 Repository Issue로 변환한다.
3. Issue에서 범위와 완료 조건을 확인하고 `TASK.md`에 계약을 기록한다.
4. GitHub Issue 번호가 포함된 브랜치에서 작업한다.
5. 작업 시작 시 `git status --short`를 확인하고 기존 변경을 보존한다.
6. 브랜치는 로컬 `main`이 아니라 항상 최신 `origin/main`에서 분기한다
   (`./harness start`가 자동으로 `git fetch origin main`을 수행한다).
7. 현재 브랜치, Issue와 `TASK.md`의 작업 식별자가 일치하는지 확인한다.
8. 작업에 필요한 역할 문서와 Skill을 확인한다.

브랜치 형식:

```text
<type>/gh-<ISSUE-NUMBER>-<short-slug>
```

예:

```text
feat/gh-42-direction-post
infra/gh-77-aws-baseline
```

Issue가 없으면 GitHub Project draft item의 계획과 작업 분해까지만 허용한다.

다음 작업은 수행하지 않는다.

* 애플리케이션 구현
* Terraform 구현
* 인프라 변경
* 배포
* PR 생성

일정, 스프린트, 우선순위와 상태는 Jira가 아니라 GitHub Projects의 필드로 관리한다.

## 2. 역할 분리

역할은 계획, 구현, 검증과 승인을 분리한다.

### 2.1 오케스트레이터

오케스트레이터는 다음을 수행한다.

* 요구사항과 제약 확인
* 시나리오와 작업 분해
* 대안과 트레이드오프 분석
* 위험과 실패 모드 분석
* 인수 조건 정의
* 실행 에이전트와 검증 에이전트 호출
* 결과와 사람의 의사결정 항목 통합

오케스트레이터는 다음을 수행하지 않는다.

* 직접 애플리케이션 코드 구현
* 직접 Terraform 코드 구현
* 자신의 설계 승인
* 인프라 적용
* 검증 실패 자동 무시

### 2.2 실행 에이전트

실행 에이전트는 다음 원칙을 따른다.

* 승인된 계획에 포함된 파일만 수정한다.
* 승인된 요구사항 범위를 벗어나지 않는다.
* 임의로 리소스, 기능 또는 작업 범위를 추가하지 않는다.
* 자신이 수정하지 않은 기존 변경을 보존한다.
* 구현 결과와 실행한 검증을 구분해 보고한다.
* 실행하지 않은 검증을 성공했다고 보고하지 않는다.

### 2.3 검증 에이전트

검증 에이전트는 구현 에이전트와 독립적으로 동작한다.

* 구현 설명이 아니라 실제 변경 파일과 검증 결과를 확인한다.
* 검증을 통과시키기 위해 소스 코드를 임의로 수정하지 않는다.
* 검사 실패나 경고를 임의로 suppress하지 않는다.
* 검증할 수 없는 항목은 `BLOCKED`로 반환한다.
* 구현 에이전트가 만든 결과를 자동 승인하지 않는다.

### 2.4 PM 및 리뷰어

PM과 리뷰어는 다음을 확인한다.

* Issue 범위
* `TASK.md` 계약
* 설계 및 테스트 계획
* 구현 증거
* 검증 결과
* 승인 조건
* 비용 및 운영 위험
* 롤백과 복구 절차

상세 역할은 `agents/` 문서를 따른다.

## 3. 테스트 규칙

* JUnit 5를 사용한다.
* 단위 테스트와 통합 테스트를 분리한다.
* 모든 테스트 메서드에 `@DisplayName`을 작성한다.
* 모든 테스트 클래스 상단에 정확한 ISO 8601 생성 시각과 원본 테스트 계획 식별자를 기록한다.
* 테스트 후 애플리케이션, 인프라, DB, 동시성, 트랜잭션, 외부 API와 장애 복구 관점의 잠재 문제를 분석한다.
* 보고서는 `templates/test-report.md`에서 생성한다.
* `.env` 값, 토큰, URL, 계정 식별자 등 민감정보를 기록하지 않는다.

테스트 클래스 헤더 예:

```java
/**
 * Created at: 2026-08-03T12:00:00+09:00
 * Source scenario: TEST-PLAN-GH-42-DIRECTION-UNIT-001
 */
```

테스트 실패를 구현 문제와 테스트 환경 문제로 구분한다.

테스트 환경 문제라고 판단한 경우에도 다음을 기록한다.

* 실패한 명령
* 오류 요약
* 재현 조건
* 미검증 범위
* 남은 위험

## 4. 인프라 규칙

### 4.1 IaC 정책

AWS 인프라는 Terraform으로만 관리한다.

다음 도구 또는 방식을 추가하지 않는다.

* AWS CDK
* CloudFormation
* Pulumi
* AWS SDK를 이용한 인프라 생성
* AWS CLI 스크립트를 Terraform 대체재로 사용하는 방식
* 애플리케이션 시작 시 AWS 리소스를 생성하는 방식

기존 저장소에 별도의 IaC가 존재하는 경우 임의로 제거하거나 변환하지 않는다. Issue와 별도 마이그레이션 계획을 생성하고 사람의 승인을 받는다.

Terraform은 다음 원칙을 따른다.

* Terraform 버전 범위를 명시한다.
* AWS Provider 버전을 명시한다.
* 외부 Module 버전을 명시한다.
* `.terraform.lock.hcl`을 커밋한다.
* 비밀값을 변수 기본값에 작성하지 않는다.
* 환경별 값과 계정별 값은 변수 또는 CI 환경에서 주입한다.
* `provisioner`, `local-exec`, `remote-exec`는 기본적으로 사용하지 않는다.
* AWS CLI 또는 외부 스크립트를 이용한 리소스 변경은 금지한다.

예외가 필요한 경우 다음을 모두 기록한다.

* 사용 이유
* 대안이 불가능한 이유
* 영향 범위
* 보완 통제
* 제거 조건
* 추적 Issue
* 사람의 승인

### 4.2 인프라 설계 게이트

모든 인프라 구현은 `/harness-infra-design`에서 시작한다.

다음 조건을 모두 만족하기 전에는 `/harness-infra-build`를 실행하지 않는다.

1. 현재 브랜치에 연결된 GitHub Issue가 존재한다.
2. `TASK.md`에 `DESIGN-ID`가 기록되어 있다.
3. Infrastructure Design Report가 존재한다.
4. 설계 상태가 `APPROVED_FOR_BUILD`이다.
5. 구현 대상 Terraform 파일과 Module 범위가 명시되어 있다.
6. 비용, 보안, 복구와 운영 위험이 검토되었다.
7. 사람의 설계 승인 증거가 존재한다.

조건을 충족하지 못하면 구현하지 않고 `BLOCKED`로 반환한다.

### 4.3 설계 입력

인프라 설계 전에 다음 항목을 확인한다.

* 환경 구분
* AWS Region
* 예상 요청량과 동시 사용자
* 네트워크 트래픽
* 데이터 저장량과 증가율
* 월 예산 상한
* 가용성 목표
* RTO
* RPO
* 데이터 민감도
* 외부 공개 범위
* 배포 빈도
* 운영 인력과 운영 가능 시간
* 예상 서비스 수명
* 장애 발생 시 허용 가능한 영향

확인되지 않은 값은 임의로 확정하지 않는다.

다음 중 하나로 구분해 설계 보고서에 기록한다.

* `CONFIRMED`: Issue 또는 승인 문서에서 확인됨
* `ASSUMED`: 설계를 위해 임시 가정함
* `UNKNOWN`: 현재 확인할 수 없음
* `BLOCKED`: 확인 전에는 구현할 수 없음

### 4.4 아키텍처 대안 비교

초기 설계는 가장 낮은 실용 비용을 우선하지만, 특정 AWS 서비스를 사전에 정답으로 고정하지 않는다.

워크로드 요구사항을 분석한 뒤 필요한 후보만 비교한다.

컴퓨팅 후보 예:

* EC2
* ECS on Fargate
* ECS on EC2
* Lambda
* App Runner
* EKS

데이터베이스 후보 예:

* RDS
* Aurora
* DynamoDB
* 자체 운영 데이터베이스

설계 보고서에는 다음을 기록한다.

* 선택안
* 비교한 대안
* 각 대안의 탈락 이유
* 가용성
* 운영 부담
* 확장성
* 장애 복구
* 비용
* 서비스 종속성
* 예상되는 미래 전환 비용

EC2와 ECS, RDS와 자체 운영 DB 비교는 해당 후보가 실제 요구사항에 적합할 때 수행한다.

### 4.5 AWS 설계 검토 영역

모든 인프라 설계는 다음을 검토한다.

* 네트워크 경계
* 외부 공개 범위
* IAM 최소 권한
* 암호화
* 비밀 관리
* 로그
* 메트릭
* 알람
* 백업
* 복구
* 장애 모드
* 확장 방식
* 비용과 비용 증가 요인
* 배포 방식
* 롤백 방식
* 운영 Runbook
* Terraform State
* Terraform Locking
* 리소스 삭제 보호
* 태그 정책

### 4.6 Terraform State

Terraform State는 민감정보로 취급한다.

원격 Backend는 다음 조건을 만족해야 한다.

* S3 Backend 사용
* Versioning 활성화
* 서버 측 암호화
* Public Access 차단
* TLS가 아닌 접근 거부
* State Locking 활성화
* 개발과 운영 State 분리
* 최소 권한 IAM 적용
* State 삭제 권한 분리
* 감사 가능한 접근 로그

가능한 경우 S3 Backend의 lockfile 기반 잠금을 사용한다.

다음 작업은 AI 에이전트가 수행하지 않는다.

```text
terraform state *
terraform force-unlock
terraform import
terraform taint
```

State 수정, 복구, import와 강제 unlock은 별도의 Issue와 사람의 명시적 승인이 필요하다.

### 4.7 Terraform plan

Terraform plan은 민감정보로 취급한다.

* plan 원문을 PR 본문에 복사하지 않는다.
* plan JSON 전체를 공개 로그에 출력하지 않는다.
* 실제 주소, ARN, 계정 ID와 민감한 변수값을 PR에 기록하지 않는다.
* PR에는 변경 요약, 리소스 수와 위험도만 기록한다.
* 저장한 plan에는 SHA-256 해시를 생성한다.
* plan을 적용하는 경우 검토된 commit과 plan의 일치 여부를 검증한다.

plan 증거에는 가능한 범위에서 다음 정보를 기록한다.

```text
design_id
issue_number
commit_sha
terraform_version
provider_lock_sha256
plan_sha256
state_serial
created_resources
updated_resources
replaced_resources
deleted_resources
```

plan 파일을 저장해야 하는 경우 접근이 제한된 저장소에 암호화하여 보관하고 짧은 만료 기간을 적용한다.

### 4.8 Apply 게이트

AI 에이전트와 Claude Code 세션은 Terraform Apply를 실행하지 않는다.

실제 적용은 보호된 GitHub Actions workflow만 수행한다.

적용 전에 다음 조건을 모두 충족해야 한다.

1. 설계와 Terraform PR이 병합되었다.
2. GitHub Ruleset이 요구하는 PR 승인 수를 충족했다.
3. `@Byuntil`, `@tkv00`의 승인 증거가 존재한다.
4. 적용 대상 commit SHA가 승인된 commit과 일치한다.
5. 적용할 plan의 SHA-256이 검토된 값과 일치한다.
6. Terraform State가 변경되어 plan이 무효화되지 않았다.
7. `infrastructure-apply` Environment 승인을 통과했다.
8. workflow dispatch에서 사람이 정확한 확인 문구를 입력했다.
9. GitHub OIDC 단기 자격 증명을 사용한다.
10. 적용 후 검증 절차가 정의되어 있다.

PR 승인과 GitHub Environment 승인은 별개의 게이트로 취급한다.

`CODEOWNERS`는 리뷰어 요청만 수행한다. 실제 강제는 GitHub Ruleset과 `infrastructure-apply` Environment 보호 규칙으로 설정한다.

### 4.9 AWS 인증

* GitHub OIDC 기반 단기 자격 증명을 사용한다.
* 장기 AWS Access Key를 생성하거나 저장하지 않는다.
* Plan 역할과 Apply 역할을 분리한다.
* Apply 역할은 보호된 workflow와 Environment에서만 Assume할 수 있어야 한다.
* 개인 로컬 환경에 운영 Apply 권한을 부여하지 않는다.
* AI 에이전트에 운영 AWS 변경 권한을 제공하지 않는다.

### 4.10 민감정보

다음 정보를 코드, Issue, PR, 보고서, 테스트 결과, 로그, 주석 또는 예시에 기록하지 않는다.

* 비밀 키
* Access Key
* 토큰
* `.env` 값
* AWS Account ID
* 실제 IAM ID
* 전체 ARN
* 서버 주소
* 실제 IP
* 내부 도메인
* 데이터베이스 연결 정보
* Terraform State 값
* Terraform plan의 민감값

필요한 경우 실제 값 대신 논리 식별자나 명시적인 placeholder를 사용한다.

```text
<aws-account-id>
<private-subnet-cidr>
<database-endpoint>
```

## 5. Terraform 주석 규칙

Terraform 주석은 코드가 무엇을 하는지 번역하지 않고, 코드만으로 알 수 없는 설계 의도와 제약을 설명한다.

### 5.1 주석이 필요한 경우

다음 경우에는 주석을 작성한다.

* 일반적인 기본값과 다른 설정
* AWS 서비스 제약으로 인한 우회 구현
* 보안 또는 컴플라이언스 요구사항
* 비용 절감을 위해 가용성이나 기능을 제한한 결정
* 명시적 `depends_on`
* `lifecycle`
* `prevent_destroy`
* `ignore_changes`
* 조건부 리소스 생성
* 환경별 동작 차이
* 외부 시스템이 관리하는 속성
* eventual consistency 대응
* 보안 검사 예외
* 임시 호환성 설정

### 5.2 작성하지 않는 주석

코드에서 직접 알 수 있는 내용을 반복하지 않는다.

잘못된 예:

```hcl
# VPC를 생성한다.
resource "aws_vpc" "main" {
}

# 버킷 이름
bucket = var.bucket_name

# Claude가 생성한 코드
# 사용자 요청에 따라 수정
```

에이전트의 추론 과정, 프롬프트, 대화 내용과 작업 과정을 주석에 기록하지 않는다.

### 5.3 설계 의도 주석

짧은 설계 의도는 대상 블록 바로 위에 작성한다.

```hcl
# 개발 환경의 고정 비용을 줄이기 위해 NAT Gateway를 단일 AZ에만 생성한다.
# 운영 환경에서는 가용성 요구사항에 따라 AZ별로 생성한다.
resource "aws_nat_gateway" "this" {
}
```

복잡한 제약은 원인, 결정과 영향 순서로 작성한다.

```hcl
# 원인: ECS의 기존 Task가 배포 중 연결을 최대 10분 유지할 수 있다.
# 결정: ALB deregistration delay를 600초로 유지한다.
# 영향: 배포 완료 시간은 증가하지만 진행 중 요청의 강제 종료를 방지한다.
deregistration_delay = 600
```

상세 근거가 ADR 또는 설계 문서에 있으면 문서 식별자를 기록한다.

```hcl
# ADR-INFRA-004: 운영 데이터베이스의 우발적 삭제를 방지한다.
lifecycle {
  prevent_destroy = true
}
```

### 5.4 `depends_on`

Terraform이 참조를 통해 추론할 수 있는 의존성에는 `depends_on`을 사용하지 않는다.

명시적 의존성이 필요한 경우 이유를 작성한다.

```hcl
# IAM 정책 연결 직후 발생할 수 있는 권한 전파 지연으로
# ECS 최초 배포가 실패하지 않도록 명시적인 선행 조건을 둔다.
depends_on = [
  aws_iam_role_policy_attachment.ecs_execution
]
```

이유를 설명할 수 없는 `depends_on`은 추가하지 않는다.

### 5.5 `ignore_changes`

`ignore_changes`는 외부 시스템이 실제로 관리하는 속성에만 사용한다.

다음을 주석으로 기록한다.

* 외부 관리 주체
* Terraform이 변경을 무시해야 하는 이유
* 무시하는 속성
* 제거 조건

```hcl
lifecycle {
  # 배포 workflow가 Task Definition revision을 갱신한다.
  # 배포 주체가 Terraform으로 전환되면 이 예외를 제거한다.
  ignore_changes = [
    task_definition
  ]
}
```

다음 설정은 기본적으로 금지한다.

```hcl
lifecycle {
  ignore_changes = all
}
```

사용이 필요한 경우 ADR, 추적 Issue와 사람의 승인이 필요하다.

### 5.6 TODO와 임시 예외

`TODO`, `FIXME`, `TEMP`, `HACK`만 단독으로 작성하지 않는다.

TODO에는 다음을 포함한다.

* 추적 Issue
* 재검토 또는 만료 날짜
* 완료 조건

```hcl
# TODO(INFRA-142, 2026-10-31): Multi-AZ 전환 후 단일 NAT Gateway 예외를 제거한다.
```

금지 예:

```hcl
# TODO: 나중에 수정
# FIXME
# 임시
```

### 5.7 보안 예외

보안 예외에는 다음을 기록한다.

* 예외 이유
* 영향 범위
* 보완 통제
* 담당 팀
* 만료일
* 추적 Issue 또는 ADR

```hcl
# SECURITY-EXCEPTION
# 이유: 외부 시스템의 송신 IP가 고정되어 있지 않다.
# 범위: staging webhook listener의 443 포트
# 보완 통제: 요청 서명 검증과 WAF rate limit
# 소유자: platform-team
# 만료일: 2026-10-31
# 추적: INFRA-142
```

만료일과 추적 항목이 없는 보안 예외는 추가하지 않는다.

### 5.8 변수와 출력 설명

변수와 output의 설명은 별도 주석보다 `description`을 사용한다.

```hcl
variable "backup_retention_days" {
  description = "RDS 자동 백업 보존 기간. 운영 환경은 최소 7일이어야 한다."
  type        = number
}
```

민감한 output에는 `sensitive = true`를 지정한다.

### 5.9 주석 언어

* 기본 주석 언어는 한국어로 통일한다.
* AWS 서비스명, Terraform 속성명과 프로토콜명은 원문을 유지한다.
* 짧고 단정한 문장으로 작성한다.
* 추측성 표현을 사용하지 않는다.
* 장문 설명은 ADR 또는 Infrastructure Design Report로 이동한다.
* 주석과 구현이 달라지면 같은 변경에서 함께 수정한다.

## 6. 변경 안전성

* 사용자 또는 다른 에이전트가 수정한 파일을 덮어쓰지 않는다.
* 작업 시작 시 `git status --short`를 확인한다.
* 현재 작업 범위 밖의 변경을 자동 정리하지 않는다.
* 넓은 파일 변경, 삭제, 인프라 적용과 프로덕션 변경은 명시적인 사람 확인 없이 실행하지 않는다.
* 원본 원장, 마이그레이션 이력과 운영 감사 이력을 수정하거나 삭제하지 않는다.
* 명령은 재실행에 안전하게 만든다.
* 실패 시 부분 반영을 피한다.
* 기존 리소스의 삭제나 교체가 예상되면 구현 전에 위험을 보고한다.
* Terraform State를 직접 수정하지 않는다.
* 승인 게이트, CODEOWNERS, Ruleset과 Apply workflow를 작업 편의를 위해 수정하지 않는다.
* 에이전트 자신의 권한이나 금지 명령을 변경하지 않는다.

다음 변경은 고위험 변경으로 분류한다.

* 리소스 삭제
* 리소스 교체
* 데이터베이스 변경
* 네트워크 CIDR 변경
* 공개 접근 범위 확대
* IAM 권한 확대
* 암호화 설정 제거
* 백업 보존 기간 축소
* 로그 보존 기간 축소
* State Backend 변경
* Provider 주요 버전 변경
* 운영 리소스 이름 변경

고위험 변경은 설계 문서, 영향 범위, 롤백 또는 복구 절차와 사람의 승인이 필요하다.

## 7. 커밋과 PR

커밋 형식:

```text
<type>(<scope>): <summary> (#<ISSUE-NUMBER>)
```

`scope`는 선택이며 Issue 번호는 필수다.

예:

```text
feat(feed): add direction post endpoint (#42)
test(feed): add expiration scenarios (#42)
chore(harness): remove Jira integration (#6)
infra(network): add private subnets (#77)
```

PR 제목과 본문:

```text
<type>: <summary>
Closes #<ISSUE-NUMBER>
```

branch, commit과 PR body의 Issue 번호가 일치해야 한다.
branch와 commit 및 PR의 type도 일치해야 한다.

PR에는 다음을 연결하거나 기록한다.

* Issue
* `TASK.md`
* 설계 또는 테스트 계획
* 실행 증거
* 검증 결과
* 변경 위험
* 롤백 또는 복구 절차
* 실행하지 못한 검증
* 사람의 결정이 필요한 항목

하나의 커밋에는 하나의 검토 목적만 담는다.

PR을 올리기 전에는 `./harness sync`로 `origin/main`을 rebase해 반영한다.
충돌은 로컬에서 해결하며 자동으로 정리하지 않는다. 이미 push된 브랜치를
rebase한 직후 재push할 때는 `git push --force-with-lease`만 예외로
허용한다. `main`이나 공유 브랜치에는 force push를 쓰지 않는다.

새 작업은 다음처럼 시작한다.

```bash
./harness start --issue 42 --type feat --slug direction-post
./harness task-init --title "방향 글 API" --replace
```

## 8. Husky 로컬 게이트

`npm ci` 또는 `npm install`의 `prepare` 단계가 Husky Hook을 설치한다.

* `pre-commit`

  * branch 정책
  * staged 공백 검사
  * 민감정보 검사
  * 테스트 정책 검사
  * workflow 정책 검사
* `prepare-commit-msg`

  * branch의 type과 Issue 번호로 메시지 조립
* `commit-msg`

  * 커밋 형식과 branch 문맥 검사
* `pre-push`

  * 전체 하네스 실행
  * Gradle `check` 실행

개발자는 요약만 입력할 수 있다.

```bash
git commit -m "Qello 이름 변경"
```

`chore/gh-6-qello-project-migration` 브랜치에서는 다음과 같이 완성한다.

```text
chore: Qello 이름 변경 (#6)
```

scope가 필요하면 다음과 같이 입력한다.

```bash
git commit -m "chore(harness): Jira 연동 제거"
```

완성된 메시지는 그대로 유지하고 잘못된 Issue 번호는 `commit-msg`에서 차단한다.

Hook 우회는 PR에 다음을 기록한다.

* 우회 이유
* 우회한 Hook
* 수동 검증 명령
* 수동 검증 결과
* 남은 위험

GitHub Actions를 최종 강제 기준으로 둔다.

## 9. 라벨 정책

* Issue와 PR에는 `type: *` 라벨이 정확히 하나 필요하다.
* `area: *`는 필요한 경우에만 붙인다.
* `status: *`는 자동화 또는 예외 상태만 표현한다.
* 스프린트, 일정과 우선순위는 라벨을 만들지 않고 GitHub Project 필드로 관리한다.
* PR의 type 라벨은 branch 접두사에서 자동 산출한다.

정책 상세는 `docs/harness/LABELS.md`를 따른다.

## 10. 완료 전 검증

기본 검증 명령:

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

인프라 변경은 최소한 다음 검증을 추가로 수행한다.

```bash
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```

승인된 환경과 자격 증명이 준비된 경우 실제 Backend를 사용하는 plan을 별도로 실행한다.

```bash
terraform plan -lock-timeout=5m -out=tfplan
```

저장소에 해당 도구가 구성되어 있다면 다음 검증도 수행한다.

```text
tflint
terraform test
Checkov
Conftest
Infracost
terraform-docs
```

구성된 검증 도구를 임의로 건너뛰지 않는다.

실행하지 못한 검증은 다음을 PR에 기록한다.

* 실행하지 못한 명령
* 실행하지 못한 이유
* 영향을 받는 범위
* 남은 위험
* 후속 검증 방법

실행하지 않은 검증을 성공했다고 표현하지 않는다.

## 11. 검증 결과 계약

에이전트의 최종 상태는 다음 중 하나를 사용한다.

### PASS

* 요구된 구현을 완료했다.
* 필수 검증을 실행했다.
* 실패한 필수 검증이 없다.
* 차단된 필수 항목이 없다.

### FAIL

* 구현 또는 검증에서 재현 가능한 실패가 발견되었다.
* 코드, 테스트, Terraform 또는 정책 위반이 존재한다.
* 현재 변경 상태로는 병합할 수 없다.

### BLOCKED

* 승인이나 입력값이 부족하다.
* 필요한 환경 또는 권한이 없다.
* 필수 검증을 실행할 수 없다.
* 사람의 결정 없이는 안전하게 진행할 수 없다.

최종 보고에는 다음을 포함한다.

```text
status
issue_number
task_id
design_id
changed_files
executed_checks
passed_checks
failed_checks
blocked_checks
assumptions
risks
required_human_decisions
```

인프라 변경에는 가능한 경우 다음도 포함한다.

```text
terraform_version
provider_lock_sha256
plan_sha256
created_resources
updated_resources
replaced_resources
deleted_resources
cost_delta
security_findings
```

## 12. 공통 금지 명령과 행위

AI 에이전트와 Claude Code 세션에서는 다음 명령을 실행하지 않는다.

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

옵션, alias, shell script 또는 다른 도구를 이용해 동일한 결과를 만드는 것도 금지한다.

다음 행위도 금지한다.

* GitHub Issue 게이트 없이 구현 시작
* 유효한 `TASK.md` 없이 구현 시작
* 승인된 설계 없이 Terraform 구현
* 승인 없이 인프라 적용 또는 프로덕션 변경
* Terraform 이외의 IaC 추가
* AWS CLI를 이용한 리소스 생성, 변경 또는 삭제
* 장기 AWS Access Key 생성
* `.env`, 토큰, 계정 또는 서버 식별자를 파일이나 로그에 복사
* Terraform State 또는 plan 원문을 PR에 기록
* 다른 사용자의 변경 자동 정리 또는 되돌리기
* 테스트 실패 숨기기
* 실행하지 않은 테스트를 통과로 보고하기
* 검증 실패 자동 suppress
* 승인 상태 추측
* 자신이 만든 설계나 구현을 자신이 승인한 것으로 처리
* CODEOWNERS 또는 Ruleset을 통한 승인 우회
* Apply workflow의 승인 절차 제거
* 보호된 branch에 직접 push

필수 정보나 승인 상태를 확인할 수 없으면 추측해서 진행하지 않고 `BLOCKED`로 반환한다.
