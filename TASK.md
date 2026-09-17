# GitHub Issue #274 Task Contract

> Generated at: `2026-09-17T22:59:27+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Docker 빌드에서 git 의존 테스트 실행 제거`
- GitHub Issue: `#274`
- Branch: `fix/gh-274-dockerfile-skip-tests`
- Base branch: `main`

## Objective

- `Deploy Test Server`의 `build-and-push` 잡이 Docker 빌드 단계에서 실패하는
  원인을 없앤다. Dockerfile이 이미지 빌드 안에서 전체 `test` task를 실행하는데,
  하네스 규약 테스트가 git 저장소와 `origin/main`을 읽고 `.dockerignore`가
  `.git`을 제외해 구조적으로 통과할 수 없다.

## Scope

- `Dockerfile`의 build 스테이지에서 `test`를 제거하고 `bootJar`만 실행한다.
- 제거 근거를 주석으로 남긴다.

## Explicit exclusions

- 테스트 코드, 테스트 설정, CI 워크플로는 바꾸지 않는다. 테스트 게이트는
  CI에 그대로 남는다.
- `.dockerignore`에서 `.git` 제외를 해제하지 않는다(이미지 레이어에 git
  메타데이터를 넣지 않는 것이 의도된 설계다).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 컨테이너 빌드 | `tkv00` | PR 승인(1인, #251 기준) |

## Existing user-owned changes

- 브랜치 생성 직전 `git status --short` 결과가 비어 있었다. 보존할 사용자
  변경이 없다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] build 스테이지가 `test` 없이 `bootJar`만 실행한다
- [x] 제거 근거가 주석으로 남는다
- [x] 로컬 `docker build --platform linux/amd64` 성공, `/app/app.jar` 생성 확인
- [x] `./harness pr-ready --project-tests` 통과

## 참고

- 발견 경위: 실제 `Deploy Test Server` 실행에서 발견(2026-09-17, run 35229804009).
  OIDC assume과 ECR 로그인은 통과했고 Docker 빌드에서 멈췄다.
- 실패한 테스트: `ChangedJavaTypesTest`, `ProductionConventionRatchetTest`,
  `ProductionConventionAuditTest`, `JavaConventionBaselineTest`,
  `JavaSourceConventionTest`, `JavaStaticAnalysisRuleTest` — 전부
  `java.io.IOException`.
- 관련 이슈: #229/#231/#233.
