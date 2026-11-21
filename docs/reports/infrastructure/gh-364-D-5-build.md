# Infrastructure Build Report: D-5

> Created at: `2026-11-21`
> GitHub Issue: `#364`
> DESIGN-ID: `D-5` (`docs/reports/infrastructure/gh-364-D-5.md`, `APPROVED_FOR_BUILD` —
> `@Byuntil`, 2026-11-20)
> Implementation PR: #367 (merged)
> Status: 구현 머지 / 사람이 실행한 plan 증거 확보 / apply 미실행

이 문서는 D-5 구현의 plan 증거와 apply 승인 경로를 기록한다. 실제 값(계정 ID, ARN,
인스턴스·파라미터 경로, State, plan 원문, secret)은 기록하지 않는다.

## 0. 최종 상태 (AGENTS.md 11절)

```text
status: BLOCKED
issue_number: 364
task_id: GH-364-DESIGN
design_id: D-5
```

`BLOCKED`인 이유: apply는 사람이 보호된 `Infrastructure Apply` workflow로만 실행한다.
#367은 작성자가 아닌 소유자의 GitHub `APPROVED` 리뷰 없이 머지되어 승인 검사
(`scripts/verify-infra-approvals.py`)를 통과할 수 없다. 승인 경로는 §4를 따른다.

## 1. 구현 범위

| 항목 | 값 |
| --- | --- |
| 구현 PR | #367 |
| PR head commit | `13d4a27a7acfa5ff42f120c93b61b04321b2819f` |
| PR merge commit | `1a66db5ca7e29540145f556ad40badb4eed2ea53` |
| head와 merge commit의 트리 차이 | 없음 (`git diff --stat` 출력 없음) |

변경 파일은 D-5 §9의 9개 구현 파일, 승인된 두 fixture 테스트 파일, 테스트 계획·보고서,
`TASK.md`, 사용자가 승인한 CI 도구 설치 단계(`harness-policy.yml`, `ci-benchmark.yml`)다.

## 2. 실행한 검증

| 검증 | 결과 | 근거 |
| --- | --- | --- |
| 표적 fixture 테스트(단위 4개, 통합 13개) | PASS, 실패·오류·skip 0 | `docs/reports/tests/gh-364-TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY.md` |
| `./harness pr-ready --project-tests` | PASS (단위 1,403개, 통합 869개, 아키텍처 23개) | 같은 보고서 |
| #367 head의 GitHub Actions 필수 검사 | PASS (`terraform`, `test`, `policy`, `safety`, `lint`, `java-conventions` 포함) | PR #367 checks |
| 사람이 실제 Backend로 실행한 `terraform plan` | 실행 완료, 아래 §3 | H-7 |

`tflint`, Checkov 등 저장소 CI의 `terraform` job 밖에 있는 도구는 이 문서를 위해 따로
실행하지 않았다.

## 3. Plan 증거

H-7에 따라 plan 역할 권한을 늘리지 않고 사람이 기존 인프라 역할 profile로 plan을
직접 실행했다. `-refresh=false`는 쓰지 않았다. plan 원문과 plan 파일은 실행 직후 삭제했다.

```text
design_id: D-5
issue_number: 364
commit_sha: 13d4a27a7acfa5ff42f120c93b61b04321b2819f
terraform_version: 1.17.1
provider_lock_sha256: cae430f2d73b5801824589bbbaa93699ccbc93f57e2b150e959903f4a391713b
plan_sha256: c45d8ec5b650561b0420a6f9bdb82d71e2d3351b14b06b9e96ece1e82e2abb39
state_serial: 기록하지 않음
created_resources: 5
updated_resources: 1
replaced_resources: 0
deleted_resources: 0
aws_instance_changes: 0
```

- `plan_sha256`은 apply workflow와 같은 방식(`jq -S '.resource_changes'`의 SHA-256)으로
  계산했다. 실행마다 바뀌는 메타데이터는 포함하지 않는다.
- 생성 5건은 `aws_ssm_parameter.push_*` SecureString 5개다. 변경 1건은
  `module.app_server.aws_iam_role_policy.instance`의 in-place 갱신이다. 인스턴스 교체와
  삭제는 없다.
- 새 secret 입력은 write-only·ephemeral이라 plan에 값이 남지 않는다. plan은 가짜 입력값으로
  실행했다. apply workflow가 실제 입력으로 다시 만든 plan의 해시가 위 값과 다르면 apply가
  중단된다.
- `state_serial`은 기록하지 않았다. State가 바뀌면 apply workflow의 재계산 해시가 달라지므로
  같은 검사에서 중단된다.

## 4. Apply 승인 경로

#367은 작성자가 아닌 소유자의 GitHub `APPROVED` 리뷰 없이 머지되었다. 승인 스크립트는
머지된 PR head에 대한 `@Byuntil` 또는 `@tkv00`(작성자 제외)의 `APPROVED` 리뷰와, 적용
commit이 그 PR의 merge commit인지를 확인한다. 구두 승인은 이 검사를 통과하지 못하며
workflow나 스크립트를 바꿔 우회하지 않는다.

그래서 이 문서를 추가하는 PR을 승인 경로로 쓴다.

- 이 PR은 `infra/`를 바꾸지 않는다. 이 PR의 merge commit에는 #367의 구현이 그대로
  포함되므로 plan 해시는 §3 값과 같아야 한다.
- 승인자는 이 PR과 #367로 머지된 인프라 변경을 함께 검토하고 승인한다. PR 본문에 이
  범위를 적는다.
- AGENTS.md §4.8의 "apply용 임시 PR이 아니라 실제 인프라 PR에 승인을 둔다"는 규칙은 열린
  PR을 rebase할 때마다 승인이 무효가 되던 반복(#281)을 막으려는 것이다. 이 PR은 #364의
  증거 문서를 담아 머지하는 PR이며, 승인 게이트는 바꾸지 않는다.

Apply 입력값(머지 후 확정):

| 입력 | 값 |
| --- | --- |
| `pr_number` | 이 문서를 추가한 PR 번호 |
| `confirmation` | `APPLY PR-<위 PR 번호>` |
| `terraform_dir` | `infra/environments/dev/test-server` |
| `commit_sha` | 위 PR의 merge commit |
| `plan_sha256` | §3의 `plan_sha256` |

dispatch 직전에 #367 merge commit과 적용 commit 사이에 `infra/` 변경이 없는지 다시 확인한다.

## 5. Apply 후 검증

D-5 §10의 4·5번 항목을 따른다.

1. GitHub variable `TEST_SERVER_SSM_PARAMETER_PATH_PREFIX`를 등록한다.
2. test-server deploy workflow를 `workflow_dispatch`로 실행한다. 테스트 경로만 바뀐 push는
   자동 배포하지 않는다.
3. 기존 인스턴스의 컨테이너에 새 5개 변수가 도달했는지 boolean·형식 결과로 확인한다. 기존 DB·access
   token 환경변수와 볼륨이 보존됐는지도 확인한다.
4. app health, S3 presigned URL, FCM endpoint TLS 연결을 확인한다. provider·worker 활성화는
   #366 범위이며 live 발송 성공과 단순 주입 성공을 구분한다.
5. 그 뒤에만 #365를 main에 병합한다.

## 6. 위험과 남은 항목

- runtime role IAM 정책 갱신(HIGH, D-5 §8). 범위 밖 GetParameter·Decrypt 거부는 apply 후
  live로 검증해야 하며 아직 검증하지 않았다.
- H-7은 MFA 인프라 역할을 전제로 했다. 실제 plan에서는 역할 assume에 MFA가 요구되지 않았다.
  역할 신뢰 정책의 MFA 조건 여부는 이 Issue 범위 밖이며 후속 검토 후보로 남긴다.
- 롤백: deploy runtime이 직전 성공 `.env`·override 세트 1개(H-4a)로 복원한다. Terraform 변경을
  되돌릴 때는 revert PR을 만들어 같은 보호 workflow로 적용한다.
- 첫 부팅 재시도·health 포트·previous/pending 교체 원자성은 #371에서 다룬다.
