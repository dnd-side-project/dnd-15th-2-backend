# Infrastructure Build Report: D-6

> Created at: `2026-11-25T07:22:21Z`
> GitHub Issue: `#390`
> Task: `GH-390-DESIGN`
> DESIGN-ID: `D-6`
> Branch: `infra/gh-390-dev-coordinate-key-ssm`
> Baseline commit: `4905f890`
> Status: 로컬 구현·필수 검사 PASS, 사람 plan·운영 인수 미실행

## 0. 최종 상태 (AGENTS.md §11)

```yaml
status: BLOCKED
local_validation_status: PASS
issue_number: 390
task_id: GH-390-DESIGN
design_id: D-6
changed_files:
  - infra/environments/dev/test-server/main.tf
  - infra/environments/dev/test-server/variables.tf
  - infra/environments/dev/test-server/outputs.tf
  - infra/environments/dev/test-server/README.md
  - infra/modules/app-server/variables.tf
  - infra/modules/app-server/main.tf
  - infra/modules/app-server/user_data.sh.tftpl
  - .github/workflows/infrastructure-apply.yml
  - src/test/java/com/dnd/qello/infrastructure/PushSecretEncodingTest.java
  - src/integrationTest/java/com/dnd/qello/infrastructure/PushSecretDeploymentIntegrationTest.java
  - docs/reports/infrastructure/gh-390-D-6-build.md
  - docs/reports/tests/gh-390-TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY.md
executed_checks:
  - Terraform fmt and backend-disabled init/validate for changed root/module
  - TFLint 0.64.0 across six Terraform directories
  - Checkov 3.3.9 across infra with existing CI exclusions
  - targeted unit and integration JUnit tasks with --rerun
  - harness check and hooks validation
  - exact IAM list and version guardrail static inspection
  - independent read-only diff audit
  - harness pr-ready --project-tests
  - git diff --check
passed_checks:
  - Terraform fmt/init/validate
  - TFLint six directories without findings
  - Checkov 494 passed / 0 failed / 9 existing exclusions
  - targeted unit 8 and integration 13; failures/errors/skipped 0
  - harness check, hooks validation and whitespace check
  - independent static code audit without actionable findings
  - full project unit 1609 / integration 902 / architecture 23 / source convention 3; failures/errors/skipped 0
failed_checks: []
blocked_checks:
  - human plan and live acceptance (explicitly outside AI execution scope)
assumptions:
  - local fixture behavior does not prove effective AWS permissions or running container state
  - current-only keys remain frozen until separately approved rotation design
risks:
  - HIGH runtime IAM expansion and existing administrator plaintext access
  - loss of key material cannot be repaired from DB backups alone
  - initial deployment requires coordinated merge/dispatch freeze
required_human_decisions:
  - PR review and protected Environment approvals
  - human MFA plan and actual scope/IAM/fingerprint evidence
terraform_version: 1.17.1
provider_lock_sha256: cae430f2d73b5801824589bbbaa93699ccbc93f57e2b150e959903f4a391713b
plan_sha256: NOT_GENERATED
created_resources: NOT_PLANNED (design target 2)
updated_resources: NOT_PLANNED (design target IAM in-place 1)
replaced_resources: NOT_PLANNED (design target 0)
deleted_resources: NOT_PLANNED (design target 0)
cost_delta: D-6 estimate unchanged; first-month subtotal 0.000186 USD plus additional usage; total UNKNOWN
security_findings: no new actionable static finding; live evidence remains unverified
```

BLOCKED는 구현 승인이 없다는 뜻이 아니다.
D-6과 fixture 계획은 승인되었으며, 사람이 수행할 plan·실환경 증거를 완료 처리하지 않는다.

## 1. 승인과 범위

2026-11-25에 @Byuntil의 Issue #390 댓글 `7153016641`(D-6/H-1~H-7)과
`7155537194`(fixture D1~D5)를 읽어 승인 원문을 확인했다.
작업 시작 status는 빈 출력이었고 branch/Issue/TASK가 일치했다.
TASK의 이전 문서 인계 단계 설명은 이번 명시적 구현 인계로 대체하되 승인된 파일 목록은 유지했다.

| 승인 요구 | 구현과 확인 |
| --- | --- |
| H-1 current-only 2개 | root main의 Standard SecureString 두 개, value_wo/value_wo_version 및 prevent_destroy |
| 값 비저장 | 기본값 없는 sensitive/ephemeral 값 변수 두 개, 이름 output만 추가 |
| 초기 version 고정 | 두 version 변수는 default=1, validation `== 1` |
| H-5 정확한 9개 | root ARN 참조 9개가 기존 module 네 Allow/Deny statement의 동일 변수로 전달됨 |
| H-2 두 전달 경로 | module/template 이름 전달과 boot map 확장, 공용 raw runtime의 조회·검증·env/overlay 비교 확장 |
| 목적별 분리 | strict canonical Base64 32 bytes, ID UTF-8 255-byte 한도, push AES/HMAC과 decoded material 중복 거부 |
| H-3 보호 workflow | TF_VAR binding 두 줄만 추가, Environment/트리거/확인 문구/승인 게이트 불변 |
| H-7 fixture | 기존 두 클래스에서 UNIT 5·INT 9 계약 구현, 기존 UNIT 4·INT 13 보존 |
| 운영 문서 | Environment-only 등록, 정상 dispatch, 동결·fingerprint·current-only·복구 경계 기록 |

추가 리소스·previous 슬롯·앱/DB/bootstrap/배포 workflow 변경은 없다.
권한 네 statement를 복제하지 않고 기존 공통 목록을 확장했다.
최초 부팅용 이름 보간은 공용 runtime 밖 map에만 추가했고 raw 추출 구간에 새 Terraform 보간을 넣지 않았다.

## 2. 실행한 검증

| 명령/검사 | 결과와 범위 |
| --- | --- |
| `terraform fmt -check -recursive` | PASS, exit 0 |
| `terraform init -backend=false -input=false` 및 `terraform validate` | 변경 root test-server와 app-server module 모두 PASS, exit 0, Backend/AWS 접근 없음 |
| TFLint 0.64.0 | CI와 같은 버전, infra의 여섯 Terraform 디렉터리 PASS, exit 0 |
| Checkov 3.3.9 | CI와 같은 버전·기존 제외 목록 사용, 494 PASS/0 FAIL/9 SKIPPED, exit 0 |
| 표적 unit `--rerun` | 8개, 실패·오류·skip 0, exit 0, Gradle 13초 |
| 표적 integration `--rerun` | 13개, 실패·오류·skip 0, exit 0, Gradle 2분 4초 |
| `./harness check` | PASS, exit 0 |
| `npm run hooks:validate` | PASS, exit 0 |
| `git diff --check` | PASS, exit 0 |
| `./harness pr-ready --project-tests` | PASS, exit 0, Gradle 7분 32초. 단위 1,609/통합 902/아키텍처 23/소스 관례 3, 실패·오류·skip 0 |
| 독립 검증 `st_01a21192` | 정적 감사 PASS, actionable finding 없음. 표적 XML 직접 확인, 전체/live 미완료는 BLOCKED로 구분 |

test-server root는 고정된 AWS provider 6.57.1을 사용했다. module 단독 검증은 기존
module lock의 6.64.0을 재사용했다. root/module 버전 제약과 lockfile은 변경하지 않았다.
실제 배포 대상 provider는 root의 6.57.1이다.
전체 XML은 단위 211 suites, 통합 116 suites, 아키텍처 4 suites, 소스 관례 1 suite다.
합계 2,537개이며 표적 8/13개는 이 안에 포함되어 중복 합산하지 않는다.

TFLint 최초 다운로드는 기존 CA 경로 문제로 exit 77이었고, 시스템 CA로 다시 실행해 통과했다.
Checkov는 외부 지침 mapping endpoint의 인증서 오류를 경고했으나 Terraform 검사는 완료했다.
BC ID mapping 조회가 실패한 것이며 검사에 사용한 기존 제외는 CKV ID다.
이 경고를 숨기거나 검사 항목을 새로 skip하지 않았다.
기존 CI 제외는 CKV_AWS_144/CKV2_AWS_62/CKV_AWS_145와 기존 resource별 승인 주석뿐이다.
Conftest/Infracost/terraform-docs/terraform test 구성은 발견하지 않았다.

terraform-ls/yaml-language-server/jdtls가 없어 LSP 진단은 실행하지 못했다.
승인 범위를 넓혀 설치하거나 설정을 수정하지 않고 실제 Terraform validate, workflow 정책 검사,
Java compile/JUnit/관례 검사로 해당 변경을 검증했다.

`harness infra-build`는 현재 CLI에 구현되어 있지 않다.
`harness test-run`은 테스트를 다시 실행한 뒤 보고 파일을 생성하므로, 중복 실행과 파일 편집
도구 제약을 피하고 지정 명령을 직접 실행한 후 선례/테스트 template 구조로 보고서를 작성했다.

## 3. Plan 증거와 독립 검증 경계

H-6에 따라 plan은 사람이 실행한다. 이 세션은 terraform plan/apply/State 명령,
AWS 접근, 실제 key 생성·조회·입력 등록·배포를 수행하지 않았다.
plan SHA-256·State serial·실제 변경 수는 없으며 예상 리소스 수를 관측 결과로 기록하지 않는다.
사람은 실제 MFA를 확인한 기존 역할과 합성 좌표 입력으로 정상 refresh plan을 수행한다.
저장 plan 해시와 workflow용 normalized resource_changes 해시를 구분하고,
입력/version 동결, drift, 삭제/교체/EC2 변경 0을 확인해야 한다.

독립 검증자는 열 파일의 실제 diff, 승인 문서/fixture 계획, runtime과 실제 deploy shell,
bootstrap 관리자 경계를 읽었고 표적 XML의 8/13 통과를 직접 확인했다.
정적 검사 도구는 구현자가 실행한 결과와 구분했으며 live 인수나 운영 승인을 대신하지 않았다.
독립 감사 시 대기 중이던 전체 pr-ready는 이후 리드가 exit 0과 위 XML 집계를 확인했다.

## 4. 사람의 적용·인수 순서

1. PR의 작성자가 아닌 승인자 리뷰와 Ruleset, 보호 Environment 게이트를 충족한다.
2. @Byuntil이 실제 MFA·secret Environment scope·접근자·실효 IAM을 확인하고
   원본과 @tkv00의 복구본을 D-6 §7의 길이 구분 fingerprint로 대조한다.
3. 인프라 PR 병합 직전 기존 배포 종료를 확인하고 자동 trigger 경로 병합·임의 dispatch를 동결한다.
4. 사람이 검토된 plan 조건과 commit/hash/State를 확인해 보호 workflow에서 apply한다.
5. `image_sha` 공란의 정상 dispatch를 실행한다. 비공란은 SSM을 재조회하지 않는 rollback이다.
6. 원본·복구본·SSM·실행 중 컨테이너의 fingerprint MATCH, health, DB/auth/push 보존,
   Agent와 범위 밖 read/decrypt 거부를 확인한다. 실제 key/ID/fingerprint는 보고하지 않는다.
7. #389 자체 gate와 실행 시점 head도 확인한 뒤 병합하고 동결을 해제한다.

## 5. 위험과 복구

IAM 확장은 HIGH이며 기존 prefix 관리자 권한과 Project 태그 EC2 root Run Command 경계는
H-5에서 수용한 잔여 위험이다. runtime의 정확한 목록이 모든 관리자를 제한하는 것은 아니다.
current-only version 고정/prevent_destroy도 외부 변경이나 config 제거를 완전히 막지 못한다.

배포 실패 시 env/override 파일 복구와 실행 중 컨테이너 복구를 구분한다.
승인된 수동 rollback은 요청 이미지와 맞는 pending/previous 세트를 선택해 SSM 재조회 없이
재생성한다. 새 ciphertext를 쓴 뒤에는 key를 제거하거나 pre-#389 이미지만으로 되돌리지 않는다.
DB 스키마 복구는 #388 담당과 별도 승인한다. Terraform rollback도 검토 PR과 보호 workflow를
통하며 좌표 파라미터를 즉시 삭제하지 않는다.

첫 회전은 previous 슬롯·reader-first·기술적 배포/부팅 동결을 별도 설계·승인한 뒤에만 수행한다.
키 원본은 ciphertext와 복구 대상 백업이 요구하는 동안 보호 보관한다.

## 6. 커밋과 인계

목적 단위 커밋 결과는 최종 외부 인계 보고서 `report-390-build.md`에 기록한다.
Co-Authored-By, push, PR 생성은 수행하지 않는다.
테스트별 상세 결과와 잠재 문제는
`docs/reports/tests/gh-390-TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY.md`에 기록한다.
