# Infrastructure Build Report: D-3

> Created at: `2026-09-16T17:17:55+09:00`
> GitHub Issue: `#229`(설계), `#233`(구현)
> DESIGN-ID: `D-3` (`docs/reports/infrastructure/gh-229-D-3.md`, `APPROVED_FOR_BUILD` —
> `tkv00` 단독 승인, 2026-09-16)
> Status: 정적 검증 완료 / `terraform plan`(실제 Backend) 미실행 / apply 미실행

이 문서는 `/harness-infra-build` 산출물의 검증 증거다. 실제 값(계정 ID, ARN,
버킷 이름, State, plan 원문)은 기록하지 않는다.

## 0. 최종 상태 (AGENTS.md 11절)

```text
status: BLOCKED
issue_number: 233 (원본 구현), 237 (이 리뷰 수정)
task_id: GH-237
design_id: D-3
```

**`BLOCKED`인 이유**: TASK.md 완료 조건이 요구하는 `terraform plan`(실제
Backend)이 아직 없다. `PASS`가 아니라 `BLOCKED`로 표기한다 — 정적
검증과 코드 리뷰 대응은 끝났지만, 실제 계정 대상 plan/apply 전까지는
전체 완료로 볼 수 없다(§3 참고). §4의 IAM 조합처럼 정적 검사가 잡지
못하는 apply 시점 오류가 남아 있을 수 있다.

TASK.md가 요구하는 4개 필수 검증의 실행 결과:

| 명령 | 결과 |
| --- | --- |
| `./harness check` | PASS |
| `./harness pr-ready --project-tests` | PASS (`BUILD SUCCESSFUL`) |
| `npm run hooks:validate` | PASS (`Husky validation passed.`) |
| `git diff --check` | PASS (공백 오류 없음) |

executed_checks: fmt, validate(4 root), tflint(4 root), checkov(전체),
harness check, harness pr-ready, npm run hooks:validate, git diff --check
passed_checks: 위 전부
failed_checks: 없음
blocked_checks: `terraform plan`(실제 Backend) — §3 참고

## 1. 구현 범위

설계 §13 Terraform ownership에 선언된 경로를 전부 수정했다(두 보고서 파일
포함, §13에 이미 소유 파일로 명시되어 있다). 이 PR은 추가로 작업 계약
문서 `TASK.md`도 변경한다 — §13의 Terraform 소유 파일 목록에는 없지만
이슈 #233의 작업 계약을 기록하는 일반적인 PR 구성 요소다.

| 경로 | 상태 |
| --- | --- |
| `infra/modules/network/**` | 신규. VPC, 퍼블릭 서브넷 1개, Internet Gateway, 라우트 테이블, 기본 보안 그룹 제한 |
| `infra/modules/app-server/**` | 신규. EC2, Elastic IP, 보안 그룹, 인스턴스 Role/Instance Profile, 데이터 EBS 볼륨, EventBridge Scheduler 자동 정지(H-1) |
| `infra/environments/dev/test-server/**` | 신규. 위 두 모듈 조합, ECR 리포지토리, SSM SecureString 파라미터(`value_wo`) |
| `infra/bootstrap/oidc.tf` | 기존 파일 수정. `infra-plan`/`infra-apply` Role 정책에 EC2·ECR·SSM·IAM·EventBridge Scheduler 액션 추가, 공유 정책 데이터소스 신설 |
| `infra/bootstrap/deployer.tf` | 기존 파일 수정. `infra-deployer` Role에 위와 동일한 apply 권한을 `source_policy_documents`로 합성 |

**결정 항목 반영**: 설계 §15/§16의 확정 사항을 그대로 구현했다.

- H-1(자동 정지) 포함 — `app-server` 모듈에 `aws_scheduler_schedule` 신설
- H-3(x86_64) — AMI를 `data "aws_ami"` 필터로 조회(하드코딩 없음)
- H-5(EIP 유지), H-6(데이터 볼륨 10 GiB) — 변수 기본값에 반영
- H-9(`value_wo`) — `aws_ssm_parameter`에 `value_wo`/`value_wo_version` 적용,
  `test-server` 스택만 `required_version >= 1.11.0`으로 상향

## 2. 실행한 검증

| 명령 | 대상 | 결과 |
| --- | --- | --- |
| `terraform fmt -check -recursive infra` | 전체 | PASS (exit 0) |
| `terraform init -backend=false` + `terraform validate` | 신규 3개 root + `infra/bootstrap` | PASS (4/4 `Success! The configuration is valid.`) |
| `tflint --chdir=<root>` | 위 4개 root | PASS (exit 0, 0 issues) |
| `checkov --directory infra --framework terraform --skip-check "CKV_AWS_144,CKV2_AWS_62,CKV_AWS_145"` | 전체(기존 storage/bootstrap 포함) | PASS — Passed 408 / Failed 0 / Skipped 9 |
| `templatefile()` 렌더링 확인(`terraform console`) + `shellcheck` | `user_data.sh.tftpl` | PASS — Terraform 이스케이프(`$${...}`) 정상 렌더링, shellcheck 경고 0건 |
| `python scripts/preflight.py` | 저장소 전체 | PASS — 1348개 파일 검사, 민감정보 없음 |
| `python scripts/validate-conventions.py` | 저장소 전체 | PASS |

```text
terraform_version: 1.15.8
tflint_version: 0.64.0
checkov_version: 3.3.17 (로컬 실행. CI는 3.3.9 고정 — 버전 차이로 인한 결과
  차이는 관찰되지 않았다)
aws_provider_version: 6.57.1 (신규 test-server, 기존 bootstrap/storage와 동일 고정)
  network/app-server 모듈은 기존 s3-private-bucket 모듈과 동일하게
  ">= 6.0.0, < 7.0.0" 범위를 쓰며 로컬에서 6.64.0으로 해석되었다.
provider_lock_sha256 (h1, darwin_arm64):
  infra/environments/dev/test-server: Mz2BVjntgeXCYLdhPCpgTBvGNPmxJBJxqq5M4r27Hc8=
  infra/bootstrap:                    (기존 값 유지, 이번 변경으로 provider 버전 변경 없음)
```

`terraform init -backend=false`는 신규 3개 root(`network`, `app-server`,
`test-server`)에서 새 `.terraform.lock.hcl`을 생성해 커밋했다.
`infra/bootstrap`은 기존 lock 파일을 그대로 두었다(provider 버전 변경 없음).

### bootstrap 검증 방법

`infra/bootstrap`은 이 로컬 환경에 이미 실제 `terraform.tfstate`(과거 사람이
직접 적용한 결과)가 존재해, 그 디렉터리에서 직접 `init`/`validate`를 실행하면
해당 State의 원격 Backend 참조를 건드릴 위험이 있었다. `infra/` 트리 전체를
`.terraform/`·`*.tfstate*`·`tfplan`을 제외하고 격리된 임시 디렉터리로 복사한
뒤 그 사본에서 `init -backend=false`/`validate`/`tflint`/`checkov`를
실행했다. 복사본과 실제 저장소 파일이 동일함을 `diff -rq`로 확인했다(`.tf`
파일 차이 없음, `.gitignore` 대상 파일만 차이). 실제 State나 원격 Backend에는
쓰기는커녕 어떤 API 호출도 발생시키지 않았다.

## 3. 실행하지 못한 검증

| 명령 | 이유 | 영향 범위 | 남은 위험 | 후속 방법 |
| --- | --- | --- | --- | --- |
| `terraform plan`(`infra/environments/dev/test-server`, 실제 Backend) | 이 세션에는 `infra-plan` OIDC Role을 assume할 수단이 없다. 로컬 개인 IAM 자격 증명으로 실행하면 역할 분리 원칙(AGENTS.md 4.9)을 어기고, 이 자격 증명이 State 버킷에 접근 권한이 없어 어차피 403으로 실패한다(bootstrap 디렉터리에서 실측 확인) | test-server 스택 전체 | 정적 검사가 잡지 못하는 apply 시점 오류(IAM 정책의 실제 리소스 타입 조합 오류, ECR/EIP 계정 한도, AMI 필터가 실제로 매치하는지)가 남아 있다 | PR 워크플로의 `infra-plan` OIDC Role로 실제 `plan`을 생성하고 그 SHA-256을 이 문서에 추가한다 |
| `terraform plan`(`infra/bootstrap`, IAM 정책 변경분) | 위와 동일한 자격 증명 제약 | bootstrap의 기존 3개 Role 정책 | 새로 추가한 EC2/ECR/SSM/IAM/Scheduler 액션 40여 개 중 일부가 실제 계정에서 `AccessDenied`를 낼 가능성이 있다(특히 다중 리소스 타입에 걸친 action의 조합) | 동일. PR CI의 `terraform` job이 4개 root 전체를 `-backend=false`로 validate하지만 실제 plan은 별도 workflow가 다룬다 |
| Infracost | 저장소에 구성되어 있지 않다 | 비용 자동 회귀 검증 | 코드 변경이 비용에 주는 영향이 PR에서 자동으로 드러나지 않는다 | 설계 §6에서 AWS Price List API 공식 단가로 수동 계산을 이미 완료했다. 자동화는 후속 이슈 |
| tflint AWS ruleset(플러그인) | 번들 terraform ruleset만 사용했다(gh-63-D-1-build.md와 동일한 기존 제약) | AWS 리소스 인자 수준 검사 | AWS 전용 규칙이 잡는 오류는 미검증 | 후속 이슈에서 `.tflint.hcl`과 플러그인 도입 검토 |

**실행하지 않은 검증을 통과로 표기하지 않았다.**

## 4. IAM 정책 설계 변경 (당초 설계 §5 대비)

설계 보고서 §5는 EC2 관련 다수 action이 "AWS 제약으로 리소스 수준 권한을
지원하지 않는다"고 전제하고 `resources = ["*"]`를 태그 조건으로만 좁히는
안을 제시했다. 구현 단계에서 `policy_sentry`(AWS 서비스 권한 부여 참조
데이터 기반)로 각 action을 실제 조회한 결과 **이 전제가 대부분 틀렸다** —
`ec2:CreateVpc`, `ec2:RunInstances`를 포함한 대상 action 대부분이 리소스
타입별 ARN(`vpc/*`, `instance/*` 등)을 지원한다.

- 변경: `resources = ["*"]` + 태그 조건 방식을 버리고, action별로 실제
  지원하는 리소스 타입 ARN(`arn:aws:ec2:*:*:<type>/*`)을 명시했다. 태그
  조건(`aws:RequestTag`/`aws:ResourceTag`)은 생성 시점 조건으로는 유지했다.
- 예외: `ec2:Describe*` 계열 중 일부(`DescribeInstanceAttribute` 등 소수)는
  리소스 수준 권한을 지원하지만, 이 스택은 그런 세부 action을 쓰지 않는다.
  와일드카드 `Describe*` 대신 이 스택이 실제로 필요한 조회 action만 나열해
  Client VPN·Spot Fleet 등 무관한 하위 action까지 광범위하게 허용하는
  문제를 함께 없앴다.
- 검증: `cloudsplaining`(checkov `CKV_AWS_356`가 내부적으로 사용) 라이브러리로
  각 action·Resource 조합을 개별 검증해 반영했다.
- `infra-apply`(oidc.tf)와 `infra-deployer`(deployer.tf)의 중복을 줄이기
  위해 공유 `data "aws_iam_policy_document" "test_server_shared_permissions"`를
  신설하고 `source_policy_documents`로 양쪽에 합성했다. plan 역할의 조회
  권한은 별도로 유지한다(쓰기 권한이 없어 문서를 공유하지 않는다).

이 변경은 설계 §5의 의도(least privilege)를 더 정확히 구현한 것이며, 승인된
아키텍처·비용·비밀값 처리 방식은 바꾸지 않았다.

## 5. 알려진 미검증 위험

- **IAM 리소스 타입 조합의 완전성**: `policy_sentry` 데이터로 각 action이
  지원하는 리소스 타입을 확인했지만, 실제 계정에서 `terraform plan/apply`를
  실행해 보지 않았다. 특히 여러 리소스 타입에 걸친 action
  (`AssociateRouteTable`, `RunInstances` 등)에서 실제 호출 시 예상 밖의
  리소스 타입 검증이 추가로 필요할 가능성이 있다. §3의 후속 방법대로
  실제 `plan`에서 확인한다.
- **애플리케이션 부팅 가능성**: 설계 §5·보고서 §"알려진 제약"에 이미 기록한
  대로, `application.yml`의 알림(FCM) 관련 속성처럼 기본값이 없는 설정이
  남아 있으면 컨테이너가 크래시 루프에 빠질 수 있다. dev 프로파일로 이를
  완화하는 작업은 #230 범위이며 이 PR에 포함하지 않는다.
- **Docker Compose 다운로드 버전**: `compose_version` 기본값(`v2.29.7`)이
  GitHub Releases에서 실제로 내려받아지는지는 이 세션에서 검증하지 못했다
  (외부 네트워크 다운로드를 인스턴스 부팅 시점에만 수행하도록 설계했기
  때문). 최초 apply 후 SSM Session Manager로 접속해 확인한다.

## 6. 대상 AWS 계정 전환 (2026-09-16, 사람 확인)

이 저장소가 앞으로 쓸 AWS 계정이 로컬 `~/.aws/config`의 `qello-new` 프로파일이
가리키는 계정으로 바뀌었다(사람 확인). 이 세션에서 State나 실제 AWS 리소스를
직접 수정하지는 않았지만, 이후 작업자에게 다음 사실을 남긴다.

- **순서 의존성**: `infra/bootstrap`(#63)이 새 계정에 아직 적용되지
  않았다. 이 스택이 만드는 State 버킷·GitHub OIDC Provider·
  `infra-plan`/`infra-apply`/`infra-deployer` Role이 새 계정에는 없다.
  `infra/environments/dev/storage`(#63 S3 이미지 버킷)도 마찬가지다.
  이 이슈(#233)의 `test-server` 스택은 storage 스택의 출력값
  (`media_bucket_kms_key_arn` 등)에 의존하므로, **bootstrap → storage →
  test-server 순서로 새 계정에 먼저 적용해야** 이 스택을 plan/apply할 수
  있다.
- **로컬 State 파일**: 로컬 `infra/bootstrap/terraform.tfstate`(2026-08-06
  자)는 이전 계정의 실제 상태를 담고 있다. 새 계정에서 bootstrap을 처음
  적용할 때 이 로컬 State 파일을 재사용하지 않는다 — 서로 다른 계정의
  실제 리소스를 같은 State에 뒤섞으면 다음 apply에서 존재하지 않는
  리소스를 삭제하려 시도하거나, 실제로 존재하는 리소스를 Terraform이
  인식하지 못하는 위험이 생긴다. 이 파일은 이 세션에서 건드리지 않았고
  (`terraform state`, `import`, `force-unlock`은 금지 명령이라 실행하지
  않는다), 처리 방법은 사람이 별도로 결정한다.
- **코드 자체는 영향 없음**: 이번에 작성한 Terraform 코드는 계정 ID를
  하드코딩하지 않는다. IAM 정책은 전부
  `data.aws_caller_identity.current.account_id`로 동적 참조하고, 리전·
  `project_prefix`는 변수다. 계정이 바뀌어도 코드 수정 없이 새 계정에
  적용할 수 있다.

## 7. Terraform Ownership 준수 확인

- `terraform.tfvars`, `*.tfstate*`, `tfplan`은 커밋하지 않았다(`git status`로
  확인, `.gitignore`가 이미 차단).
- `.terraform.lock.hcl`은 신규 3개 root에서 커밋 대상에 포함했다.
- `CLAUDE.md`, `AGENTS.md`, `.claude/**`, `agents/**`, `CODEOWNERS`,
  `.github/workflows/*apply*`, `scripts/guard-*`는 수정하지 않았다.
- `terraform apply`, `destroy`, `import`, `state`, `force-unlock`, `taint`,
  AWS CLI를 이용한 리소스 생성·변경·삭제는 이 세션에서 실행하지 않았다.
  AWS CLI 호출은 `sts get-caller-identity`(신원 확인)와 `pricing
  get-products`(읽기 전용 가격 조회, 설계 단계)뿐이다.
