# GitHub Issue #390 Task Contract

> Generated at: `2026-11-25T13:14:29+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `test-server 좌표 암호화 key SSM 주입`
- GitHub Issue: `#390`
- Branch: `infra/gh-390-dev-coordinate-key-ssm`
- Base branch: `main`

## Objective

- #388(PR #389)이 dev 프로필에서 요구하는 좌표 암호화 key를 test-server 컨테이너에 주입하는 경로를 추가한다. #364(D-5)의 push token key 주입 경로를 따른다.
- DESIGN-ID: `D-6` (예정)
- Design report: `docs/reports/infrastructure/gh-390-D-6.md` (예정)
- Design status: 미작성. `APPROVED_FOR_BUILD` 전에는 Terraform을 구현하지 않는다.
- 확인된 사실(2026-11-25):
  - PR #389의 `application-dev.yml`은 `QELLO_COORDINATE_CURRENT_KEY_ID`, `QELLO_COORDINATE_CURRENT_KEY_BASE64`를 기본값 없이 요구하고, `QELLO_COORDINATE_PREVIOUS_KEY_ID`, `QELLO_COORDINATE_PREVIOUS_KEY_BASE64`는 빈 값을 허용한다.
  - D-5는 SSM SecureString + `value_wo`, `ssm_parameter_arns`로 한정한 EC2 role, 허용 목록 밖 파라미터 조회·복호화 명시적 Deny(`infra/modules/app-server/main.tf`), 배포 SSM 명령에서 `.env` 갱신(H-2 I안), `infrastructure-apply.yml`의 TF_VAR 입력 binding(H-3)을 사용했다.
  - 좌표 key는 push token key와 공유하지 않는다(ADR-0009 D4).
- 결정(2026-11-25, @Byuntil): 유형 인프라, Priority P0. #389보다 먼저 apply한다.

## Scope

- Infrastructure Design Report D-6 작성: 새 SecureString 파라미터, previous key 슬롯 포함 여부, IAM 허용·Deny 목록 갱신, 배포 런타임 `.env`·compose 매핑, apply workflow TF_VAR binding, key 생성·등록·회전 절차와 담당, 비용, 위험, 롤백.
- 설계 승인 후 D-6 §9에 명시된 Terraform·배포 런타임 파일만 구현하고 정적 검사와 fixture 테스트를 실행한다.

## Explicit exclusions

- 운영(prod) key 주입(I2), key 자동 회전, 애플리케이션 코드 변경.
- 설계 승인 전 Terraform 구현.
- terraform apply·State 명령·AWS 리소스 직접 변경. 실제 key 생성과 등록은 secret owner가 수행한다.
- `.github/workflows/*apply*` 변경은 설계 승인에 별도로 명시된 TF_VAR binding 외에는 하지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 범위 | @Byuntil | 사람의 범위 승인 |
| D-6 설계 보고서 | 설계 오케스트레이터(omo) | 독립 보안·비용 검토 후 사람의 설계 승인 |
| D-6 §9 구현 파일 | 실행 에이전트(omo) | 정적 검사 후 오케스트레이터의 독립 검증, PR에서 `@Byuntil`·`@tkv00` 리뷰 |
| key 발급·보호된 입력·apply | 사람 | PR·Environment 게이트 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```

## Completion criteria

- D-6 보고서가 `APPROVED_FOR_BUILD`이고 사람 승인 기록이 있다.
- 새 파라미터가 `SecureString`이고 `value_wo`를 써서 State에 값이 남지 않는다.
- EC2 role의 SSM·KMS 허용·Deny 범위가 기존 파라미터와 새 파라미터로만 한정된다.
- terraform 정적 검사와 배포 런타임 fixture 테스트가 통과한다.
- 승인된 Plan 환경의 plan 증거(plan SHA-256, 리소스 수)가 있고 인스턴스 교체·삭제가 없다.
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 통과한다.
