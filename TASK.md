# GitHub Issue #382 Task Contract

> Generated at: `2026-11-23T22:10:18+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `PR 검사에 Gradle 의존성 취약점 스캔 추가`
- GitHub Issue: `#382`
- Branch: `ci/gh-382-vulnerability-scan`
- Base branch: `main`

## Objective

- 위치기반서비스 운영 배포 전 점검표의 작업 I8. 점검표의 이미지·의존성 취약점 점검 항목은 `일부`이며, 의존성 취약점은 OSV 수동 조회(#360)뿐이고 PR 검사에는 스캔 단계가 없다. Gradle 의존성 취약점 스캔을 PR 검사에 추가해 점검이 PR마다 실행되고 결과가 기록으로 남게 한다.
- 확인된 사실(2026-11-23):
  - `.github/workflows/`에 취약점 스캔 단계가 없다.
  - 기존 workflow: `branch-policy`, `ci-benchmark`, `deploy-api-docs`, `deploy-test-server`, `gradle-cache`, `harness-policy`, `infrastructure-apply`, `infrastructure-static`, `label-policy`, `pull-request-policy`.
- 결정(2026-11-23, @Byuntil):
  - 유형 CI 작업(`ci/` 브랜치, `type: chore`), Priority P1.
  - 범위는 Gradle 의존성 스캔만이다. 컨테이너 이미지 스캔은 운영 ECR push 스캔(I2)에 맡긴다.
- Test plan: `docs/test-plans/gh-382-TEST-PLAN-GH-382-DEPENDENCY-SCAN.md` — `APPROVED` — 시나리오 16개, D1 수정안(OSV-Scanner, CI 전용 lockfile 생성, 저장소 lockfile·build locking 없음), D2~D7 추천안대로. 승인자 @Byuntil, 2026-11-23. 승인 증거: https://github.com/dnd-side-project/dnd-15th-2-backend/issues/382#issuecomment-7102598858

## Scope

- PR 검사에 Gradle 의존성(런타임 클래스패스 포함) 취약점 스캔 job 추가.
- 실패 기준(심각도)과 예외 등록 방법 문서. 예외에는 사유, 추적 Issue, 만료일 필수.
- 외부 action 버전 고정, workflow 권한 `contents: read` 이하.
- 현재 `main`의 기준 초과 항목은 업그레이드 또는 만료일이 있는 예외로 처리.

## Explicit exclusions

- 컨테이너 이미지 스캔(I2의 ECR push 스캔).
- 레지스트리 스캔 설정 등 Terraform 인프라 변경.
- 이미 배포된 이미지의 재스캔.
- 소스 정적 분석(SAST).
- `.github/workflows/*apply*`, `CODEOWNERS`, `scripts/guard-*` 변경.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `TASK.md`, 범위 | @Byuntil | 사람의 범위 승인 |
| 스캔 설계·테스트 계획 | 계획 에이전트(omo) | 사람의 계획 승인 |
| workflow, 예외 파일, 문서, 의존성 업그레이드 | 실행 에이전트(omo) | PR에서 작성자가 아닌 백엔드 한 명 승인 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 출력은 `TASK.md`(task-init) 하나였다.
- `gh`와 `harness`는 `env -u SSL_CERT_FILE -u SSL_CERT_DIR`로 시스템 CA를 사용한다.
- C1(tkv00)·C6·C9가 같은 위치 처리 경로를 고치므로 착수 순서를 tkv00과 맞춘다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 실패 기준을 넘는 취약 버전 의존성을 넣은 재현 조건에서 검사가 실패한다.
- 현재 `main` 기준 스캔 결과와 기준 초과 항목의 처리 결과(업그레이드 또는 예외)를 기록한다.
- 새 job의 권한이 `contents: read` 이하이고 외부 action 버전이 고정돼 있다.
- 새 검사가 PR CI 시간에 더하는 소요 시간을 기록한다.
- `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`가 통과한다.
