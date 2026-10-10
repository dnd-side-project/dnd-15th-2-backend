# GitHub Issue #339 Task Contract

> Generated at: `2026-10-10T18:42:53+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `main Gradle 의존성 캐시 생성 workflow`
- GitHub Issue: `#339`
- Branch: `ci/gh-339-main-gradle-cache`
- Base branch: `main`

## Objective

PR run은 `actions/setup-java`의 `cache: gradle`로 Gradle 의존성을 복원하지만 main에 캐시를 만드는 workflow가 없어
새 브랜치의 첫 run이 캐시 미스로 시작한다. main ref에 Gradle 의존성 캐시를 만드는 workflow를 추가한다.

## Scope

- `.github/workflows/gradle-cache.yml` 신규: Harness Policy와 같은 `actions/setup-java@v5` 설정(`temurin`, `21`,
  `cache: gradle`)으로 test, java-conventions, sync-api-docs job이 쓰는 의존성을 받아 main ref에 캐시를 저장한다.

## Explicit exclusions

- `gradle/actions/setup-gradle`로 교체, Gradle 빌드 캐시(`org.gradle.caching`)
- Harness Policy 등 기존 workflow 변경, Gradle 빌드 파일 변경
- Apply workflow와 승인 게이트
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 캐시 생성 workflow·측정 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`e0210d6e`, #352 병합)에서 별도 작업 폴더로 분기했다.

## Validation

```bash
python scripts/validate-workflows.py
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 머지 후 새 workflow run이 main ref에 `setup-java-Linux-x64-gradle-<hash>` 캐시를 저장한다.
- Gradle 파일을 바꾸지 않은 새 브랜치의 첫 PR run에서 test, java-conventions, sync-api-docs job의 Set up Java 로그에
  `Cache restored from key`가 있다(run ID를 이슈에 기록).
- 같은 run의 test job에서 Gradle 시작부터 첫 task 실행까지 걸린 시간을 기록한다. 비교 기준은 1부의 캐시 미스 run이다.
- `python scripts/validate-workflows.py`, `./harness check`, `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-10 사용자 승인: 구현·PR 없이 닫혀 있던 이슈를 다시 열고 그 이유를 이슈 댓글로 남긴다.
- 2026-10-10 사용자 승인: Gradle 명령은 `./gradlew javaConventionCheck integrationTest --tests
  "*OpenApiSpecificationIntegrationTest"`. `check`의 의존성은 `javaConventionCheck`, `test`, `integrationTest`에서 오고
  `test`는 컨벤션 테스트와 같은 classpath를 쓴다. 컴파일까지만 하면 테스트 실행 classpath의 jar가 빠질 수 있고,
  `dependencies` 리포트는 jar를 내려받지 않는다.
- 2026-10-10 사용자 승인: 실행 조건은 setup-java가 key 계산에 쓰는 Gradle 파일이나 이 workflow가 바뀐 main push, 주 1회
  주기 실행(지워진 캐시 재생성), 수동 실행. `cache-hit`이면 Gradle 단계를 건너뛴다(key가 같으면 setup-java가 다시
  저장하지 않는다).
- 2026-10-10 사용자 승인: 지금 main 캐시와 key가 같아 첫 run이 저장을 건너뛰면, 머지 직후 별도 승인을 받아 main의 해당
  캐시 1개를 지우고 수동 실행해 저장을 기록한다.
