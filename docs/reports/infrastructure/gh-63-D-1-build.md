# Infrastructure Build Report: D-1

> Created at: `2026-08-05T18:52:16+09:00`
> GitHub Issue: `#63`
> DESIGN-ID: `D-1` (`docs/reports/infrastructure/gh-63-D-1.md`, `APPROVED_FOR_BUILD`)
> Status: 정적 검증 완료 / `terraform plan` 미실행 / apply 미실행

이 문서는 `/harness-infra-build` 산출물의 검증 증거다. 실제 값(계정 ID, ARN,
버킷 이름, State, plan 원문)은 기록하지 않는다.

## 1. 구현 범위

설계 §13 Terraform ownership에 선언된 경로만 수정했다. 예외적으로
`.github/workflows/**` 두 파일을 추가·수정했으며, 이는 §6에 별도로 기록한 사람
승인에 따른 것이다.

| 경로 | 상태 |
| --- | --- |
| `infra/bootstrap/**` | State Backend, State 접근 로그 버킷, GitHub OIDC Provider, `infra-plan`/`infra-apply` Role |
| `infra/modules/s3-private-bucket/**` | private 버킷 모듈(Public Access Block, versioning, SSE, lifecycle, 접근 로그, TLS 강제) |
| `infra/environments/dev/storage/**` | 이미지 버킷, 전용 KMS Key, 접근 로그 버킷, `dev-s3-tester` Role |
| `.github/workflows/infrastructure-static.yml` | tflint · Checkov 게이트 추가 |
| `.github/workflows/infrastructure-apply.yml` | apply 게이트 workflow 신규 |

## 2. 실행한 검증

| 명령 | 대상 | 결과 |
| --- | --- | --- |
| `terraform fmt -check -recursive infra` | 전체 | PASS (exit 0) |
| `terraform init -backend=false` + `terraform validate` | root 3개 | PASS (3/3 `Success! The configuration is valid.`) |
| `tflint --chdir=<root>` | root 3개 | PASS (exit 0, 0 issues) |
| `checkov --directory infra --framework terraform` | 전체 | PASS — Passed 257 / Failed 0 / Skipped 3 |
| `python scripts/validate-workflows.py` | workflow 4개 | PASS |
| `./harness check` | 저장소 전체 | PASS |

```text
terraform_version: 1.15.8
tflint_version: 0.64.0
checkov_version: 3.3.9
aws_provider_version: 6.57.1 (= 고정)
provider_lock_sha256:
  infra/bootstrap:                    e856b99b1460e4b58b47bf96ef57e016a4ab21a777aea11fbdb80b12207275e7
  infra/environments/dev/storage:     e856b99b1460e4b58b47bf96ef57e016a4ab21a777aea11fbdb80b12207275e7
  infra/modules/s3-private-bucket:    5753868d9f44adb0907bd66cdb58a16e213d046e95b18e39f4583bdb436d6cf5
```

`.terraform.lock.hcl`은 `terraform providers lock -platform=linux_amd64
-platform=darwin_arm64`로 CI(linux)와 로컬(macOS) 양쪽 해시를 명시했다.

## 3. 실행하지 못한 검증

| 명령 | 이유 | 영향 범위 | 남은 위험 | 후속 방법 |
| --- | --- | --- | --- | --- |
| `terraform plan` (실제 Backend) | `infra/bootstrap`이 아직 적용되지 않아 State Backend 버킷이 존재하지 않는다. `infra/environments/dev/storage`는 이 Backend 없이 `init`할 수 없다. | 두 스택 전체 | 정적 검사가 잡지 못하는 apply 시점 오류(버킷 이름 전역 충돌, IAM/KMS 정책 평가, S3 로그 배달 권한)가 남아 있다 | bootstrap을 사람이 적용한 뒤 `infra/environments/dev/storage`에서 plan을 생성하고, 그 SHA-256을 apply workflow 입력으로 사용한다 |
| `terraform plan` (`infra/bootstrap`) | AWS 자격 증명이 이 세션에 없고, AI 에이전트가 계정 자격 증명을 사용하지 않는다 | bootstrap | 동일 | `infra/bootstrap/README.md`의 최초 적용 절차에서 사람이 plan을 직접 검토한다 |
| Infracost | 저장소에 구성되어 있지 않다 | 비용 검증 | 월 10 USD 상한이 공식 단가로 검증되지 않았다(설계 §6에서 이미 BLOCKED) | 사람이 AWS Pricing Calculator로 재검증한다(설계 §10 체크리스트) |
| tflint AWS ruleset | 번들 terraform ruleset만 사용했다. AWS 플러그인은 `tflint --init` 네트워크 의존이 추가된다 | AWS 리소스 인자 수준 검사 | AWS 전용 규칙이 잡는 오류는 미검증 | 후속 이슈에서 `.tflint.hcl`과 플러그인 도입을 검토한다 |

**실행하지 않은 검증을 통과로 표기하지 않았다.**

## 4. 이번 빌드에서 수정한 결함

| 도구 | 검사 | 조치 |
| --- | --- | --- |
| tflint | `terraform_unused_declarations` | `infra/bootstrap/provider.tf`의 미사용 `data "aws_caller_identity"` 제거 |
| Checkov | `CKV_AWS_300` (3건) | 모든 lifecycle rule에 `abort_incomplete_multipart_upload` 추가 — 조회되지 않는 멀티파트 조각의 저장 비용 누적 방지 |
| Checkov | `CKV_AWS_18` (1건) | bootstrap State 버킷에 서버 접근 로그 추가 (§5 참고) |
| 코드 리뷰 | — | lifecycle rule에 `filter {}` 추가. S3 API는 rule마다 Filter 또는 Prefix를 요구하므로 없으면 apply 시 `MalformedXML`로 실패한다. 정적 검사로는 드러나지 않는 apply 시점 결함이었다 |
| 범위 대조 | — | 접근 로그 대상 버킷에 `depends_on` 추가. 로그를 보내는 버킷이 대상 버킷의 이름만 참조해 대상 버킷 정책까지 기다리는 의존성이 없었다. S3는 `PutBucketLogging` 시점에 쓰기 권한을 검증하므로 `InvalidTargetBucketForLogging`으로 실패할 수 있었다 |

## 5. 설계 보고서에 열거되지 않은 추가 리소스 (리뷰어 확인 필요)

`infra/bootstrap`에 **State 접근 로그 버킷**(`module.state_access_log_bucket`,
`aws_s3_bucket_logging.terraform_state`)을 추가했다.

- 근거: AGENTS.md 4.6이 원격 Backend 조건으로 "감사 가능한 접근 로그"를
  요구한다. 설계 §9의 bootstrap 리소스 열거에는 빠져 있어 정책과 설계가
  충돌했다.
- 결정: 사람이 2026-08-05에 "bootstrap에 로그 버킷 추가"를 선택했다.
- 영향: S3 버킷 1개 추가. 저장량이 매우 작아 월 10 USD 상한에 유의미한 영향은
  없을 것으로 판단하나, 이 판단은 공식 단가로 검증되지 않았다(§3).
- 설계 문서와의 관계: 승인된 설계 보고서 본문은 수정하지 않았다. 리뷰어는 이
  추가가 설계 범위 확장으로 수용 가능한지 PR에서 확인해야 한다.

## 6. 승인 범위 밖 파일 수정 (사람 승인 근거)

인프라 실행 에이전트의 기본 수정 범위(`infra/**`, `docs/infrastructure/**`)를
벗어나는 두 파일을 사람 승인(2026-08-05)에 따라 변경했다.

- `.github/workflows/infrastructure-static.yml` — 설계 §14가 빌드 단계 결정으로
  남겨 둔 tflint·Checkov 구성. 설계 §12의 "Public Access Block 누락이 리뷰에서
  빠질 위험"에 대한 보완 통제다.
- `.github/workflows/infrastructure-apply.yml` — 저장소에 apply 게이트
  workflow가 없어 설계 §10의 apply 경로 자체가 존재하지 않았다. 가드
  스크립트(`confirm-infra-apply.py`, `verify-infra-approvals.py`)와
  `scripts/validate-workflows.py`의 게이트 정의는 이미 있었으나 이를 호출하는
  workflow가 없었다.

두 파일 모두 CODEOWNERS에 의해 `@Byuntil`, `@tkv00` 리뷰 대상이다.

## 7. 정적 정책 검사 예외

`AGENTS.md` 10절에 따라 구성한 검사를 건너뛰지 않는다. 아래 예외만 사유와 함께
명시했으며, 그 외 모든 검사는 hard fail이다.

| 검사 | 범위 | 사유 |
| --- | --- | --- |
| `CKV_AWS_109`, `CKV_AWS_111`, `CKV_AWS_356` | `data.aws_iam_policy_document.post_image_kms_key` 블록 인라인 주석 | KMS **Key 정책**은 정책이 부착된 Key 자신만을 Resource로 가리키므로 `"*"` 외의 값을 쓸 수 없다(AWS 제약). Principal은 계정 root, `infra-apply`, `dev-s3-tester`로 한정되어 있다. identity 정책 전반의 검사는 그대로 유지하기 위해 전역 목록이 아니라 해당 블록에만 좁혀 적용했다 |
| `CKV_AWS_144` | 전역 | 교차 리전 복제. 설계 §2에서 dev/test·재생성 가능 데이터를 이유로 제외 승인됨 |
| `CKV2_AWS_62` | 전역 | S3 이벤트 알림. 알림을 소비할 컴퓨팅이 아직 없다. 업로드 API 이슈에서 재검토 |
| `CKV_AWS_145` | 전역 | KMS 기본 암호화. 로그 대상 버킷은 Customer-managed Key를 지원하지 않고(AWS 제약), State 버킷은 AGENTS.md 4.6의 서버 측 암호화를 SSE-S3로 충족한다 |

전역 예외 3건은 새 버킷에도 자동 적용되므로, 운영 환경 스택을 추가할 때
재검토해야 한다.

## 8. 변경 위험도

```text
risk_level: HIGH
risk_reasons:
  - 신규 IAM Role/OIDC Trust Policy 생성(권한 확대)
  - 저장소 최초의 Terraform State Backend 부트스트랩
  - 실제 AWS 변경 경로(apply workflow) 신규 도입
affected_resources: S3 버킷 4개(state, state access log, post image, access log),
  KMS Key 1개, IAM Role 3개, OIDC Provider 1개
possible_data_loss: 없음(전부 신규 생성, 기존 운영 데이터 없음)
downtime: 없음
rollback_available: 예 — 신규 리소스이므로 보호된 절차로 제거 가능(이 세션에서는 미실행)
recovery_procedure: State 버킷 Versioning으로 이전 State 복원
required_approvals: "@Byuntil", "@tkv00", infrastructure-apply Environment
```

## 9. apply 전에 사람이 해야 할 일

1. `INFRA_APPLY_ENABLED` 저장소 변수를 `true`로 설정(기본은 미설정 = 비활성).
2. 저장소 변수 `AWS_REGION`, `TF_STATE_BUCKET`과 시크릿
   `AWS_INFRA_APPLY_ROLE_ARN` 등록.
3. `infrastructure-apply` GitHub Environment 생성 및 보호 규칙(승인자) 설정.
4. `infra/bootstrap`을 README 절차대로 직접 적용하고 원격 State로 이전.
5. `infra/bootstrap/oidc.tf`의 `github_oidc_thumbprints` 기본값이 현재 값인지
   AWS 가이드에서 재확인(코드 내 TODO에 기록됨).
6. `github_repository` 변수 기본값이 실제 저장소와 일치하는지 확인.
7. AWS Pricing Calculator로 월 10 USD 상한 재검증(설계 §10 미완료 항목).
8. AWS Budgets 알림(설계 §7 권고: 8 USD) 구성 여부 결정.

## 10. 최종 상태

```text
status: PASS
issue_number: 63
task_id: GH-63
design_id: D-1
executed_checks: terraform fmt, terraform validate(3), tflint(3), checkov, validate-workflows, harness check
passed_checks: 위 전체
failed_checks: 없음
blocked_checks: terraform plan(실제 Backend), Infracost, tflint AWS ruleset
created_resources: 미확정 — plan 미실행
updated_resources: 미확정 — plan 미실행
replaced_resources: 미확정 — plan 미실행
deleted_resources: 미확정 — plan 미실행
cost_delta: 미확정 — 공식 단가 미확인(설계 §6에서 이미 BLOCKED)
security_findings: 0 (Checkov Failed 0, 예외는 §7)
assumptions:
  - S3 lifecycle rule에 filter가 없으면 apply가 실패한다는 전제로 filter {}를 추가했다
  - 로그 대상 버킷은 SSE-KMS CMK를 지원하지 않는다는 AWS 제약을 전제로 SSE-S3를 유지했다
required_human_decisions: §9의 8개 항목
```

`terraform apply`, `destroy`, `import`, `state`, AWS 리소스 변경 명령은 이
세션에서 실행하지 않았다.
