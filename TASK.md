# GitHub Issue #163 Task Contract

> Generated at: `2026-08-28T15:07:30+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `후보 조회 부분 GIST 인덱스 사용 여부 재측정`
- GitHub Issue: `#163`
- Branch: `perf/gh-163-candidate-index-remeasurement`
- Base branch: `main`
- Task ID: `GH-163-CANDIDATE-INDEX-REMEASUREMENT`
- Test plan: `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT`
- Test plan path:
  `docs/test-plans/gh-163-TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT.md`
- Implementation gate: `APPROVED_FOR_BUILD`
- Approval evidence: `2026-08-28T15:53:41+09:00` 사용자가 이중 반경 테스트 계획을 승인하고 구현 진행을 요청함

## Objective

- GitHub Issue #127의 10,000명 성능 측정이 만든 `user_account`와
  `active_user_presence`의 1:1 비율 및 반경 내부 집중 분포를 제거한다.
- 계정 수가 presence 수보다 충분히 많고 공간 조건의 선택도가 낮은 합성 데이터에서
  preview·matching 후보 조회 SQL의 실행계획을 다시 측정한다.
- 부분 GIST 인덱스 `active_user_presence_position_gix`의 사용 여부를 관측 근거와
  함께 결론 내리되, 결과에 따른 SQL·인덱스 변경은 이 작업에 포함하지 않는다.

## Scope

- 별도 `@Tag("performance")` PostgreSQL/PostGIS 통합 테스트 추가
- 합성 계정 100,000개와 그중 presence 10,000개를 생성하는 10:1 데이터 모양
- presence를 원점 기준 최대 100km에 결정적으로 분산
- 현재 정책과 같은 `GLOBAL / 20,100km` 및 선택도 진단용 5km 반경을 분리 측정
- 합성 데이터의 계정:presence 비율과 두 반경의 선택도를 실행계획 측정 전에 검증
- `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`과 `FIND_CANDIDATES_SQL`에
  `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` 실행
- `active_user_presence`·`user_account`의 접근 경로, 인덱스명, 실제 행 수와 buffer
  요약을 민감정보 없이 기록
- 두 쿼리별 GIST 사용 여부와 결과별 후속 판단을 테스트 보고서에 기록

100,000:10,000 비율과 100km 분포는 운영 실측값이 아니라 Issue의 예시와 공간
선택도를 통제하기 위한 합성 가정이다. 20,100km는 현재 저장소의 실제 기본 정책값이고,
5km는 정책 변경안이 아니라 planner 선택도 진단값으로만 사용한다.

## Explicit exclusions

- `ActiveUserPresenceSql` 쿼리 변경
- 인덱스·migration·production schema 변경
- 실제 운영 데이터 또는 운영 부하 테스트
- `delivery-scope` 정책 변경
- 5km를 운영 정책 또는 제품 요구사항으로 채택
- 실행 시간 임계값 단언
- 측정 결과에 따른 후속 성능 개선 구현 또는 별도 Issue 자동 생성
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Approved decisions

- `DEC-163-001`: 계정 100,000개 중 presence 10,000개인 10:1 합성 cardinality를 사용한다.
- `DEC-163-002`: presence는 100km 원 안에 결정적으로 분산하되 운영 분포라고 주장하지 않는다.
- `DEC-163-003`: 현재 정책값 20,100km와 선택도 진단값 5km를 분리 측정한다.
- `DEC-163-004`: GIST 사용 여부는 단언하지 않고 쿼리·반경별 관측값으로 보고한다.
- `DEC-163-005`: #127 성능 테스트는 보존하고 #163 전용 클래스를 추가한다.
- `DEC-163-006`: planner 설정이나 hint로 GIST 사용을 강제하지 않는다.
- `DEC-163-007`: #127 충족 범위와 쿼리 재작성 검토는 정책값·진단값 결과를 구분한다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·가정·테스트 계획 통합 | Test Orchestrator | Human partner |
| 합성 데이터·실행계획 성능 통합 테스트 | Test Executor | Independent verifier |
| 테스트 결과 보고서와 결과별 후속 판단 | Test Executor | PM reviewer |
| 전체 diff·정책·재현성 독립 검증 | Independent verifier | Human partner |

실행자는 승인된 테스트 계획에서 배정된 파일만 수정한다. 검증자는 테스트를 통과시키기
위해 production source나 테스트 소스를 수정하지 않는다.

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 비어 있었고 기존 사용자 변경은 없었다.
- 로컬 `main`과 `origin/main`은 `938046e`에서 일치했다.
- 이 브랜치에서 현재 생성한 변경은 이 `TASK.md`, 승인된 테스트 계획과 격리
  worktree용 `.gitignore` 항목뿐이다.

## Validation

Focused checks:

```bash
./gradlew performanceTest --tests '*DirectionMatchingIndexPlanPerformanceIntegrationTest'
```

Final checks:

```bash
./gradlew performanceTest
./harness test-run --id TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Task 2 execution evidence

- Measured commit: `c62f7620bf3010da6b7ac6cdc1f08e5f5cb957a2`
- Report: `docs/reports/tests/gh-163-TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT.md`
- Fresh measurement: the focused and full `performanceTest` commands passed. The approved fixture verified 100,000 synthetic accounts, 10,000 valid presences, 10,000 policy-baseline candidates, 25 selectivity-probe candidates, and 9,975 probe-external presences.
- Plan-rows revision: a later focused `performanceTest` of `DirectionMatchingIndexPlanPerformanceIntegrationTest` passed in 28s and confirmed `plan rows=1` for both target relations in all four observations. Access paths, actual rows, loops, filter counts, buffers, and GiST `USED`/`NOT_USED` matched the earlier full-suite evidence.
- Fresh conclusion: both preview and matching used no partial GiST at the actual `GLOBAL / 0..20,100km` policy baseline and used `active_user_presence_position_gix` at the diagnostic 5km probe. #127's operating-default claim remains unsatisfied; this task alone does not recommend a production query rewrite. Planner estimates stayed at 1 row per target-relation node after `ANALYZE` even when actual rows were much higher; that gap is recorded as a selectivity-estimate observation, not as a rewrite reason.
- Executed checks: focused performance test, full `performanceTest`, `harness test-run`, `harness check`, `harness pr-ready --project-tests`, `npm run hooks:validate`, and `git diff --check` all passed. The `pr-ready` optional local-main fast-forward helper could not update a `main` branch held by another worktree; it did not change this branch and did not block the successful readiness checks.
- Scope review through the measured commit found no production source, SQL, migration, or index definition change. Residual risk is that local synthetic cardinality and planner behavior do not represent production data distribution, cache state, or load.
- Final contract: `PASS`; issue `#163`; task ID `GH-163-CANDIDATE-INDEX-REMEASUREMENT`; design ID `N/A` (test-only task); no blocked or failed required checks; no additional human decision is required to record this evidence. Any production policy, SQL, index, or follow-up Issue requires separate human approval.

## Completion criteria

- [x] 사람이 100,000:10,000 비율, 100km 분포와 이중 반경 비교를 포함한 테스트 계획을 승인했다. (`2026-08-28T15:53:41+09:00`)
- [x] 합성 데이터가 계정 100,000개, presence 10,000개와 20,100km·5km 선택도를 결정적으로 만든다. (fresh performance evidence)
- [x] preview 집계 SQL을 정책 기본값과 선택도 진단값으로 각각 측정한다. (fresh performance evidence)
- [x] matching 후보 SQL을 정책 기본값과 선택도 진단값으로 각각 측정한다. (fresh performance evidence)
- [x] 각 실행계획에 두 대상 relation의 접근 경로가 존재하고 측정 데이터 전제 검증이 통과한다. (fresh performance evidence)
- [x] 정책 기본값과 선택도 진단값의 결과를 구분해 #127 충족 범위가 과장 없이 기록된다. (REPORT-001)
- [x] 선택도 진단에서 partial GiST가 사용되었고, 쿼리 형태 변경 `NOT_RECOMMENDED` 근거가 기록된다. (REPORT-001)
- [x] production source, query, migration과 인덱스 정의를 변경하지 않는다. (Issue-branch scope review)
- [x] 신규 JUnit 5 테스트가 `@DisplayName`, 정확한 생성 시각과 source scenario 규칙을 지킨다. (Task 1 class review and focused/full execution)
- [x] 테스트 보고서가 애플리케이션, DB, 동시성, 트랜잭션, 외부 API와 장애 복구 위험을 구분한다. (REPORT-001)
- [x] focused 및 final 검증이 통과하거나 실행 불가 항목이 `BLOCKED`로 기록된다. (all required commands passed)
