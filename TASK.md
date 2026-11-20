# GitHub Issue #364 Task Contract

> Generated at: `2026-11-20T18:01:08+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `test-server push token 보호 key와 FCM credential SSM 주입`
- GitHub Issue: `#364`
- Branch: `infra/gh-364-test-server-push-secrets`
- Base branch: `main`

## Objective

- Issue #364의 승인된 D-5에 따라 test-server push secret 주입을 구현하고 정적 검증 증거를 보고한다.
- DESIGN-ID: `D-5`
- TASK-ID: `GH-364-DESIGN`
- Design report: `docs/reports/infrastructure/gh-364-D-5.md`
- Design status: `APPROVED_FOR_BUILD` — 승인자 `@Byuntil`, `2026-11-20`.
- Build gate: 구현 진행 가능. 승인된 9개 구현 파일로 한정하며 apply·배포는 승인 범위 밖이다.
- Decision and approval evidence: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7036145201
- Fixture test plan: `docs/test-plans/gh-364-TEST-PLAN-GH-364-PUSH-SECRET-DEPLOY.md` — `APPROVED`, 가짜 fixture 테스트 작성·실행 승인.
- Additional approval evidence: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7037762767
- 추가 승인: 기존 ami와 함께 user_data·user_data_base64를 ignore_changes에 지정한다. 기존 호스트는 H-2 deploy workflow가 갱신하며 인스턴스 교체 별도 Issue에서 예외 제거를 재검토한다.
- 테스트 실행 환경 승인: 단위 fixture는 외부 프로그램 없이, 통합 fixture는 환경변수 없이 PATH의 Compose로 실행하며 skip하지 않는다 — https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7039501730
- CI 도구 승인: `harness-policy.yml` test job과 `ci-benchmark.yml`에 고정 버전 Python·PyYAML·Terraform 설치 단계만 추가한다 — https://github.com/dnd-side-project/dnd-15th-2-backend/issues/364#issuecomment-7039900124

## Scope

- H-1: A 선택. 서비스 계정 JSON은 SSM SecureString Standard에 저장하고 WIF는 후속 별도 Issue로 분리한다.
- H-2: I 선택. 인스턴스 교체 없이 deploy workflow의 SSM 명령에서 `.env`와 `compose.push.yaml`을 staging·검증·게시한다. 해당 workflow 변경 범위 승인 완료.
- H-3: infrastructure-apply workflow의 새 5개 secret TF_VAR 입력 binding만 변경 범위 승인. Environment·확인 문구·승인 게이트·트리거는 유지한다.
- H-4: secret owner·장애 담당 `@Byuntil`, 백업 담당 `@tkv00`. 발급 후 JSON 바이트 수만 기록하며 4 KiB 초과 시 credential만 Advanced 비용(월 약 0.05 USD)을 승인했다. 회전 재검토일은 `2027-05-20`이다.
- H-4a: 롤백용 이전 `.env`·override는 직전 성공 세트 1개만 보관하고 다음 성공 배포 때 교체한다. 별도 기간 보존은 두지 않는다.
- H-6: 기존 관리형 정책은 유지하고 runtime role의 7개 밖 GetParameter/GetParameters와 SSM key의 7개 밖 encryption context 복호화를 명시적 Deny한다. S3 key에는 영향을 주지 않는다. app-server/main.tf 정책 변경 범위 승인 완료.
- H-7: plan 역할 권한은 늘리지 않는다. 사람이 기존 MFA 인프라 역할로 plan을 직접 실행해 SHA-256과 리소스 수 증거를 만든다. bootstrap 변경과 refresh 생략은 금지한다.
- 새 SecureString 5개, 기존 2개를 보존하는 IAM 경계, 비밀 입력·복구 경로를 구현하고 정적 검사한다.
- 초기 독립 보안·비용 검토 기록을 보존한다. 구현의 독립 검증은 사용자 오케스트레이터에게 인계한다.
- #364 보호 workflow apply 및 기존 인스턴스 주입 검증이 #365 main 병합보다 앞서야 한다.
- H-6 구현 단계에서 SSM Agent의 Run Command 배포 경로와 7개 밖 접근 거부를 검증한다. 결정 반영만으로 실제 권한 통제나 plan 실행이 완료된 것은 아니다.
- WIF, 전체 서버 예산 재확인, 현재 plan 역할의 기존 SecureString refresh 실패 가능성은 후속 별도 Issue 또는 후보로 남긴다. 이번에 Issue를 생성하지 않는다.

## Explicit exclusions

- 승인된 9개 파일 밖의 구현, 애플리케이션 변경과 승인 없는 테스트 작성은 하지 않는다.
- terraform plan은 H-7에 따라 사람이 실행한다. apply·State/import/taint/force-unlock과 AWS 리소스 변경은 수행하지 않는다.
- 전체 서버 예산 재확인과 `infra/bootstrap` 변경은 이번 Issue 범위 밖이다.
- prod Firebase 프로젝트, prod 인프라, previous key 파라미터, provider 활성화와 worker scheduling은 제외한다.
- 서비스 계정 key 발급과 AES/HMAC key 생성·등록은 secret owner가 수행한다. 이번 세션에서는 생성하지 않는다.
- 커밋, push, PR 생성과 리뷰 요청 메시지를 보내지 않는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, D-5 보고서 | 설계 오케스트레이터 | 독립 보안·비용 검토 후 사람의 설계 승인 |
| D-5 §9의 9개 구현 파일 | 실행 에이전트 | 정적 자체 검사 후 사용자 오케스트레이터의 독립 검증 |
| secret 발급·보호된 입력 경로·실제 apply | 사람 | 설계 승인과 별개인 PR·Environment 게이트 |

Secret owner와 장애 담당은 `@Byuntil`, 백업 담당은 `@tkv00`로 확정했다.
H-2·H-3·H-6의 파일 범위와 D-5 전체 승인을 위 GitHub 코멘트에서 확인했다.
독립 검증은 사용자 오케스트레이터가 수행하며 자체 정적 검사를 독립 검증으로 보고하지 않는다.

## Existing user-owned changes

- 재착수 시 `git status --short` 출력이 없었다. 사용자가 기존 `.gitignore` 변경을 로컬 exclude로 옮겼다고 알렸다.
- `.gitignore`와 `.git/info/exclude`는 이 세션에서 수정하지 않는다.
- 구현 재개 시 기존 변경은 `TASK.md`와 미추적 D-5 보고서였으며 보존했다.
- `harness start`가 최신 `origin/main`에서 현재 브랜치를 생성했다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.

## Validation

```bash
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
GIT_MASTER=1 git diff --check
env -u SSL_CERT_FILE -u SSL_CERT_DIR terraform fmt -check -recursive infra
env -u SSL_CERT_FILE -u SSL_CERT_DIR terraform -chdir=infra/environments/dev/test-server init -backend=false
env -u SSL_CERT_FILE -u SSL_CERT_DIR terraform -chdir=infra/environments/dev/test-server validate
```

JUnit 작성·실행은 승인된 테스트 계획 이후다. `./harness pr-ready --project-tests`와
Terraform 정적 검사·실제 plan·live 주입 검증은 설계 승인 후 실행 단계의 필수 항목으로 남긴다.
구성된 TFLint·Checkov 등도 실행한다. 승인된 fixture 테스트와 `./harness pr-ready --project-tests`를 실행한다.
AI 세션은 plan을 실행하지 않고 사람의 plan 증거, live IAM·Run Command·FCM 검증 미실행을 보고한다.

## Completion criteria

- branch, Issue #364, TASK와 DESIGN-ID가 일치한다.
- D-5에 CONFIRMED/ASSUMED/UNKNOWN/BLOCKED, 두 인증 대안과 두 반영 대안, 비용·보안·복구·파일 소유 범위를 기록한다.
- 독립 보안·비용 검토 결과와 미검증 항목을 숨기지 않는다.
- 비밀값, 실제 계정·프로젝트 식별자, ARN을 새 문서에 기록하지 않는다.
- D-5 §9의 9개 구현 파일과 명시된 문서, 추가 승인된 두 fixture 테스트 파일 및 test-run 보고서 범위만 수정한다.
- 구현과 정적 검증 결과를 AGENTS.md §11 형식으로 보고하고 커밋·push·PR 없이 멈춘다.
