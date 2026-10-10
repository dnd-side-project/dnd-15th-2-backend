# GitHub Issue #347 Task Contract

> Generated at: `2026-10-10T04:28:44+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `통합 테스트 PostGIS·LocalStack 컨테이너를 한 번만 띄워 공유`
- GitHub Issue: `#347`
- Branch: `test/gh-347-share-test-containers`
- Base branch: `main`

## Objective

통합 테스트는 클래스마다 PostGIS 컨테이너를 새로 띄운다. 기준 측정(CI Benchmark run 37884006551, 같은 커밋
20회)에서 컨테이너 기동이 run당 106번, 355초로 `integrationTest` 중앙값 688초의 절반이었다. 컨테이너를
JVM당 한 번만 띄워 공유하고, 줄어든 시간을 같은 방식으로 잰다.

## Scope

- `PostgisContainerIntegrationTestSupport`: `@Testcontainers`·`@Container`를 빼고 static 블록에서 컨테이너를 한 번만
  start한다. 테스트 클래스가 시작될 때마다 같은 이름의 DB(`qello_test`)를 `DROP DATABASE ... WITH (FORCE)`로 지우고
  `template_postgis`로 다시 만든다. `@ServiceConnection`과 `@DirtiesContext(AFTER_CLASS)`는 그대로 둔다.
- `LocalStackContainerIntegrationTestSupport`: LocalStack도 static 블록에서 한 번만 띄운다.
- 한 DB를 공유한 첫 시도의 실패(51개 클래스)는 트러블슈팅으로 기록한다. 테스트를 지우거나 건너뛰게 하지 않는다.
- CI Benchmark(base=main, head=이 브랜치, task=check, 10회씩)로 잰다. 실행 전 사용자 승인을 받는다.
- 위 변경의 테스트 계획은 `/harness-test-plan`으로 먼저 승인받는다.

## Explicit exclusions

- `@DirtiesContext` 제거와 클래스 간 데이터 정리 시점(다음 단계, 별도 이슈)
- fork 병렬 실행과 샤딩
- 운영 코드, Flyway 마이그레이션 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 통합 테스트 지원 클래스·깨진 통합 테스트 수정·측정 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`994bc0a1`)에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 로컬 `integrationTest`에서 PostGIS·LocalStack 컨테이너 기동이 각각 1번이고, 테스트 이름 목록과 건너뜀 수가 main과
  같고 실패 0이다.
- 클래스 실행 순서를 무작위로 바꾼 실행(seed 2개)에서도 실패 0이다.
- CI Benchmark 비교 출력에 `integrationTest` 중앙값 차이, 95% 구간, 단측 p가 있고, 두 설정의 테스트 목록이 같고
  실패 0이다. run ID와 출력을 PR에 기록한다.
- `./harness check`, `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-10 사용자 승인: 이슈 유형은 test, Sprint Week 10, Priority P1, Status In Progress. LocalStack도 PostGIS와
  같은 방식으로 한 번만 띄운다. 메인 클론에서 작업한다.
- 2026-10-10 사용자 승인: `docs/test-plans/gh-347-TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS.md`. 깨진 테스트는 그 클래스 안에서만
  최소로 고치고 base 클래스에 공통 정리를 넣지 않는다. `FlywayMigrationIntegrationTest`의 표시 이름은 바꾸지 않는다.
  무작위 클래스 순서 실행은 저장소 밖 Gradle init script로 한다.
- 2026-10-10 사용자 결정: 한 DB를 공유한 첫 시도에서 51개 클래스(438건)가 실패했다. 깨진 클래스마다 정리를 고치는 안(D1),
  base에서 클래스 시작 전 TRUNCATE하는 안 대신, 컨테이너 하나 안에서 클래스마다 새 DB를 만드는 안을 택했다. D1·D2는
  이 결정으로 대체한다. 실패 내용, 대안별 트레이드오프, 변경 범위와 전후 코드, 선택 이유를 기록한다.
- 2026-10-10 구현 변경(위 결정 범위 안): 처음에는 번호 붙은 DB(`qello_test_class_<n>`)를 만들고 `@DynamicPropertySource`로
  접속 정보를 넘겼다. 이 방식은 마이그레이션 테스트 5개와 `QelloLocalProfileIntegrationTest`도 고쳐야 했고, 고친
  `SchemaRevisionMigrationIntegrationTest`의 기존 긴 메서드 4개가 pre-commit의 staged checkstyle(QELLO-JAVA-SIZE-001)에
  걸렸다. 같은 이름의 DB를 다시 만들면 접속 정보가 그대로라 base 클래스 2개만 바꾸면 된다.
