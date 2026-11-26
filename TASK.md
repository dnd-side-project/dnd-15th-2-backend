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
- DESIGN-ID: `D-6`
- Design report: `docs/reports/infrastructure/gh-390-D-6.md`
- Design status: `APPROVED_FOR_BUILD` — @Byuntil, 2026-11-25.
- 승인 증거: `https://github.com/dnd-side-project/dnd-15th-2-backend/issues/390#issuecomment-7153016641`
- 독립 보안 재검토는 새 HIGH/CRITICAL 없이 BLOCKED(실행 경로·사람 결정·live 증거 대기)였으며,
  위 승인이 H 결정과 fingerprint 경로를 확정했다. 실제 검증과 테스트 계획 승인을 대신하지 않는다.
- 확인된 사실(2026-11-25):
  - PR #389의 `application-dev.yml`은 `QELLO_COORDINATE_CURRENT_KEY_ID`, `QELLO_COORDINATE_CURRENT_KEY_BASE64`를 기본값 없이 요구하고, `QELLO_COORDINATE_PREVIOUS_KEY_ID`, `QELLO_COORDINATE_PREVIOUS_KEY_BASE64`는 빈 값을 허용한다.
  - D-5는 SSM SecureString + `value_wo`, `ssm_parameter_arns`로 한정한 EC2 role, 허용 목록 밖 파라미터 조회·복호화 명시적 Deny(`infra/modules/app-server/main.tf`), 배포 SSM 명령에서 `.env` 갱신(H-2 I안), `infrastructure-apply.yml`의 TF_VAR 입력 binding(H-3)을 사용했다.
  - 좌표 key는 push token key와 공유하지 않는다(ADR-0009 D4).
- 결정(2026-11-25, @Byuntil): 유형 인프라, Priority P0. #389보다 먼저 apply한다.
- Test plan: `docs/test-plans/gh-390-TEST-PLAN-GH-390-COORDINATE-KEY-DEPLOY.md` — `APPROVED`, 단위 5·통합 9, D1~D5 추천안. 승인자 @Byuntil, 2026-11-25, `https://github.com/dnd-side-project/dnd-15th-2-backend/issues/390#issuecomment-7155537194`.

## Scope

- 승인된 D-6 §9의 아래 10개 파일만 구현 범위로 둔다. fixture 두 파일은 별도 D-6 테스트 계획 승인 후 변경·실행한다.
- H-1: current SecureString 2개, version=1 validation, prevent_destroy. previous 2개는 첫 회전 전 별도 설계·승인.
- H-2: 기존 runtime 갱신(D-5 I안), 인스턴스 교체 및 `deploy-test-server.yml` 수정 없음.
- H-3: `infrastructure-apply.yml`의 새 TF_VAR binding 두 개만 추가. 새 secret은 `infrastructure-apply` Environment에만 등록하고 기존 게이트·확인 문구·Environment·트리거는 변경하지 않는다.
- H-4: @Byuntil key 발급·장애·병합 동결, @tkv00 복구본 보관. 회전 재검토일 2027-05-25.
- H-5: 네 Allow/Deny statement를 같은 9개 목록으로 확장하고 기존 관리자 접근 경계 수용.
  Run Command는 Project 태그로 제한된 EC2 전체에 허용되며 workflow는 그중 지정 인스턴스를 사용한다.
- H-6: 사람이 기존 MFA 인프라 역할로 plan. 새 좌표 입력은 합성 fixture만 사용하고 실제 key를 로컬 plan에 넣지 않는다.
- H-7: 아래 구현 파일 범위와 별도 fixture 계획 승인 경계를 지킨다.
- SEC-D6-06: D-6 §7의 길이 구분 ID/decoded material SHA-256 fingerprint로 원본·복구본·SSM·실행 중 컨테이너를 대조한다.
  담당자가 MFA 역할로 로컬 계산하고 Run Command는 기대 fingerprint를 받아 MATCH/MISMATCH만 반환한다.
  이 대조에서 에이전트와 CI runner에 실제 key를 전달하지 않는다.
- 순서: 인프라 PR 병합 → 보호 apply → `image_sha` 공란 정상 dispatch → fingerprint 대조 → #389 병합.
  해당 기간 자동 배포 경로 병합과 임의 dispatch를 동결하며, 실효 MFA·secret scope·IAM은 사람이 별도 확인한다.

### 승인된 구현 파일

1. `infra/environments/dev/test-server/main.tf`
2. `infra/environments/dev/test-server/variables.tf`
3. `infra/environments/dev/test-server/outputs.tf`
4. `infra/environments/dev/test-server/README.md`
5. `infra/modules/app-server/variables.tf`
6. `infra/modules/app-server/main.tf`
7. `infra/modules/app-server/user_data.sh.tftpl`
8. `.github/workflows/infrastructure-apply.yml`
9. `src/test/java/com/dnd/qello/infrastructure/PushSecretEncodingTest.java`
10. `src/integrationTest/java/com/dnd/qello/infrastructure/PushSecretDeploymentIntegrationTest.java`

이번 인계는 승인 문서 반영과 테스트 계획 작성만 수행한다. 위 파일의 구현·테스트 코드 변경은 하지 않는다.

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
