# GitHub Issue #350 Task Contract

> Generated at: `2026-10-10T14:55:47+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `통합 테스트 Spring 컨텍스트를 클래스끼리 공유하고 데이터 정리 방식 정하기`
- GitHub Issue: `#350`
- Branch: `test/gh-350-share-spring-context`
- Base branch: `main`

## Objective

#347로 컨테이너 기동은 run당 1번이 됐지만 클래스마다 Spring 컨텍스트를 새로 띄운다. CI Benchmark run 38023671076에서
컨텍스트 캐시 miss가 110번, Flyway 적용이 123번(합계 중앙값 약 63초)이었다. `@DirtiesContext`를 빼서 설정이 같은
클래스끼리 컨텍스트를 공유하고, 줄어든 시간을 같은 방식으로 잰다.

## Scope

- `@DirtiesContext(AFTER_CLASS)` 12곳(base 1, 클래스 11): 제거하거나 꼭 필요한 곳만 남긴다.
- `ClassDatabaseExtension`의 클래스별 DB 재생성: 컨텍스트를 공유하면 열린 커넥션 풀 아래의 DB를 지우게 되므로 공유 DB
  정리 방식으로 바꾼다.
- 데이터 정리 방식과 시점은 `/harness-test-plan`에서 대안을 비교해 사람 승인을 받는다.
- 깨진 테스트, 검토한 대안과 선택 이유를 테스트 보고서에 기록한다. 테스트를 지우거나 건너뛰게 하지 않는다.
- CI Benchmark(base=main, head=이 브랜치, task=check, 10회씩)로 잰다. 실행 전 사용자 승인을 받는다.

## Explicit exclusions

- fork 병렬 실행과 샤딩
- 운영 코드, Flyway 마이그레이션 변경
- heap 상한 변경(측정만 하고, 바꿀 필요가 보이면 별도 이슈)
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 통합 테스트 지원 클래스·깨진 통합 테스트 수정·측정 | 실행 에이전트 | 사용자 PR 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 깨끗했다. 최신 `origin/main`(`68f530fa`, #348 병합)에서 분기했다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- 로컬 `integrationTest`: 테스트 이름 목록·건너뜀 수가 main과 같고 실패 0, 컨테이너 기동 1번 유지.
- 클래스 실행 순서를 무작위로 바꾼 실행(seed 2개)에서도 실패 0.
- CI Benchmark 비교 출력에 `integrationTest` 중앙값 차이, 95% 구간, 단측 p가 있고 두 설정의 테스트 목록이 같고 실패 0.
- 컨텍스트 miss 수·Flyway 적용 횟수·heap 최고치를 base와 비교해 기록한다.
- `./harness check`, `./harness pr-ready --project-tests`가 통과한다.

## Decisions

- 2026-10-10 사용자 승인: 이슈 유형은 test, Sprint Week 10, Priority P1, Status In Progress.
- 2026-10-10 사용자 승인: `docs/test-plans/gh-350-TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT.md`. D1 클래스 시작 전 base에서 전체
  초기화(TRUNCATE ... RESTART IDENTITY CASCADE, 기준 데이터 스냅샷 복원, 남은 별도 스키마 삭제), 테스트 클래스 정리 코드는
  바꾸지 않는다. D2 `@DirtiesContext` 12곳 모두 제거. D3 캐시 크기는 기본 32로 먼저 재고, 문제가 보이면 결과를 보여주고
  `maxSize` 16을 다시 묻는다. D4 무작위 순서는 저장소 밖 init script.
