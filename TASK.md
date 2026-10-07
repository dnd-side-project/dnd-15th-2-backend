# GitHub Issue #330 Task Contract

> Generated at: `2026-10-08T00:48:09+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `CI 통합 테스트 측정 기록과 같은 커밋 반복 측정`
- GitHub Issue: `#330`
- Branch: `ci/gh-330-test-measurement`
- Base branch: `main`

## Objective

Harness Policy `test` job은 Gradle task 경계를 job 로그 줄 간격으로만 남겨 통합 테스트의 클래스별 시간,
컨텍스트 생성 수, heap 사용량을 알 수 없다. 통합 테스트 lifecycle, Gradle 캐시, 중복 task 변경의 효과를
같은 커밋에서 비교할 수 있도록 측정 기록과 반복 측정 절차를 만든다. 테스트 시간을 줄이는 변경은 하지 않는다.

## Scope

| 대상 | 변경 |
| --- | --- |
| `build.gradle` `integrationTest` | `org.springframework.test.context.cache` DEBUG 로그(JUnit XML의 system-out에 남는다), 테스트 JVM 가비지 컬렉션 로그(`build/gc/`) |
| `.github/workflows/harness-policy.yml` `test` job | `./gradlew check --profile`, JUnit XML과 가비지 컬렉션 로그와 profile 리포트를 artifact로 올림(취소되지 않은 run은 실패해도 올림) |
| `.github/workflows/ci-benchmark.yml`(신규) | `workflow_dispatch`로 기준 ref와 비교 ref를 받아 ref마다 10회, job 20개를 동시에 실행 |
| `scripts/experiments/ci-benchmark-compare.py`(신규) | 내려받은 artifact로 테스트 목록 일치, task 시간 중앙값 차이, Mann-Whitney U 단측 p값 출력, `--self-test` |

- 측정 방식(사용자 결정, 2026-10-08): 같은 커밋에서 두 설정을 10회씩 동시에 돌리고 순위 검정(Mann-Whitney U
  단측 p<0.05)으로 판정한다. 5회씩 돌려 시간 범위가 겹치지 않는지 보는 방식은 실제 차이가 표준편차의 4배쯤은
  돼야 확실히 잡고, 횟수를 늘리면 더 엄격해져서 쓰지 않는다. 기존 기록에서 같은 커밋 test job 시간의 표준편차는
  최근(통합 테스트 클래스 90개 이상) 약 64초다.
- task 시간 기록(사용자 결정, 2026-10-08): Gradle 내장 `--profile` 리포트를 쓴다. 빌드 스캔(`--scan`)은 외부
  서비스로 빌드 정보를 보내고 약관 동의가 필요해서 쓰지 않는다.
- 반복 측정을 Harness Policy가 아니라 별도 workflow로 둔다. #313(PR #322)의 `concurrency`가 `workflow_dispatch`에도
  적용돼 같은 브랜치에서 연달아 실행하면 앞 run이 취소된다.
- `workflow_dispatch`는 workflow 파일이 default 브랜치에 있어야 실행할 수 있다. PR 검증 동안에만 이 브랜치 push로
  `CI Benchmark`가 돌게 하는 임시 트리거를 두고, 검증 run ID를 남긴 뒤 머지 전에 지운다.

## Explicit exclusions

- 테스트 시간을 줄이는 변경(컨테이너 공유, `@DirtiesContext` 제거, Gradle 캐시, 중복 task)
- Harness Policy 트리거와 `concurrency`(#313 범위)
- `main-ruleset` 활성화와 required check 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 측정 방식(반복 횟수, 판정 규칙, `--profile`) | 사용자 | 2026-10-08 결정 |
| `build.gradle`, workflow 2개, 비교 스크립트 변경 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- `origin/main`(0be1e92)에서 만든 별도 worktree(`~/Desktop/dnd-worktrees/gh-330-test-measurement`)라 시작 시
  `git status --short`가 깨끗했다. `~/Desktop/dnd`의 #137 작업 브랜치는 건드리지 않았다.

## Validation

```bash
python3 scripts/validate-workflows.py
python3 scripts/experiments/ci-benchmark-compare.py --self-test
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 이 PR의 Harness Policy `test` job artifact에 `test`와 `integrationTest`의 JUnit XML, 가비지 컬렉션 로그,
  profile 리포트가 있고 `integrationTest` XML에 `org.springframework.test.context.cache` DEBUG 로그가 있다.
- `CI Benchmark`를 두 ref로 실행한 run에서 job 20개가 모두 artifact를 남긴다. run ID를 PR 본문에 기록한다.
- 비교 스크립트가 그 run의 artifact로 테스트 목록 일치 여부, `integrationTest`와 `test` task 시간의 중앙값 차이,
  p값을 출력한다.
- 변경 전 main run과 이 PR run의 `integrationTest` 실행, 건너뜀, 실패 수가 같다.
- `validate-workflows.py`, `./harness check`, `./harness pr-ready --project-tests`가 통과한다.
