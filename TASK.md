# GitHub Issue #338 Task Contract

> Generated at: `2026-10-09T22:43:41+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `test task의 컨벤션 테스트 중복 실행 제거와 측정 workflow의 task 선택`
- GitHub Issue: `#338`
- Branch: `ci/gh-338-dedupe-convention-tests`
- Base branch: `main`

## Objective

`./gradlew check`에서 컨벤션 테스트 5개 클래스(`JavaConventionArchitectureTest`, `ChangedJavaTypesTest`,
`ProductionConventionAuditTest`, `ProductionConventionRatchetTest`, `JavaSourceConventionTest`)가 `test`와
`javaConventionArchitectureTest`, `javaConventionSourceTest`에서 두 번 실행된다. 기준 측정(CI Benchmark, 같은 커밋
20회)에서 `test` 중앙값은 86.6초, 이 5개 클래스의 시간 합 중앙값은 10.85초였다. `test`에서 이 클래스를 빼고,
설정마다 10회 비교로는 이 차이를 잡을 확률이 약 58%라서 측정 workflow에 task와 반복 횟수 입력을 추가한다.

## Scope

- `build.gradle`: 두 컨벤션 task의 include 패턴 목록을 한 곳에 두고 `test`에서 같은 패턴을 exclude한다.
  `ApiResponseConventionTest`, `JavaConventionBaselineTest`, `JavaStaticAnalysisRuleTest`는 `test`에 남긴다.
- `.github/workflows/ci-benchmark.yml`: `workflow_dispatch` 입력에 Gradle task(`check`·`test`, 기본 `check`)와
  설정당 반복 횟수(기본 10)를 추가한다. 입력은 env로 넘기고 허용 값을 검사한다. matrix는 회차를 바깥,
  설정을 안쪽에 두어 base와 head job이 번갈아 시작하게 한다. 입력을 생략하면 지금과 같이 `check` 10회씩이다.
- `scripts/experiments/ci-benchmark-compare.py`: 결과가 있는 task만 비교하고, 두 설정의 테스트 이름 목록이
  다르면 빠지거나 추가된 클래스를 출력한다. 자체 검사에 `test`만 도는 경우를 추가한다.
- 측정: 브랜치를 push한 뒤 base=main, head=이 브랜치, task=`test`, 20회로 CI Benchmark를 실행하고 run과 비교
  출력을 PR에 기록한다.

## Explicit exclusions

- Harness Policy `test` job 명령을 `./gradlew check -x javaConventionCheck`로 바꾸는 방법
- java-conventions job이 다른 VM에서 한 번 더 실행하는 컨벤션 검사
- Gradle 캐시와 main 캐시 workflow(#339), #313의 트리거와 concurrency
- 컨벤션 테스트 코드 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `build.gradle` `test` 필터, 측정 workflow, 비교 스크립트 | 실행 에이전트 | 사용자 PR 리뷰 |
| CI Benchmark 실행(job 40개, 동시 job 상한 점유) | 사용자 승인 후 실행 에이전트 | 사용자 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(#341 병합 커밋)에서 별도 작업 폴더로 분기했다.
- 이 main은 #343(`DeviceTokenService` baseline 해시) 수정 전이라 `JavaConventionBaselineTest`가 실패한다. #343이
  병합되면 sync한 뒤 `pr-ready`와 측정을 진행한다.

## Validation

```bash
python3 scripts/experiments/ci-benchmark-compare.py --self-test
python3 scripts/validate-workflows.py
./gradlew test
./gradlew javaConventionCheck
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- 이 브랜치의 `test` JUnit XML에서 위 5개 클래스만 빠지고 나머지 테스트 이름 목록은 main과 같다.
- 로컬 `./gradlew javaConventionCheck` 후 `build/test-results/javaConventionArchitectureTest/`와
  `build/test-results/javaConventionSourceTest/`에 5개 클래스 결과가 있다.
- CI Benchmark(base=main, head=이 브랜치, task=`test`, 20회) run에서 job 40개가 모두 결과 파일을 남기고, 비교
  스크립트가 `test` 중앙값 차이, 95% 구간, 단측 p값을 출력한다. run과 출력을 PR에 기록한다.
- 같은 run의 job 시작 시각에서 base와 head job이 번갈아 시작했다.
- `python3 scripts/validate-workflows.py`, `./harness check`, `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-09 #338 이슈 작성 세션: 패턴은 `*Convention*`처럼 넓게 쓰지 않는다. 컨벤션 task에 없는 세 클래스까지
  빠지기 때문이다. 측정 판정 기준은 테스트 목록 차이가 5개 클래스뿐이고 실패 0, 단측 p<0.05다. p가 0.05를
  넘어도 결과를 그대로 기록하며, 이슈 완료 조건은 측정 기록까지다.
