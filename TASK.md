# GitHub Issue #214 Task Contract

> Generated at: `2026-09-05T01:08:39+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `PostGIS 후보 조회 E3 증거 확장`
- GitHub Issue: `#214`
- Branch: `perf/gh-214-postgis-e3-evidence`
- Base branch: `main`
- Task ID: `GH-214-POSTGIS-E3-EVIDENCE`
- Test plan: `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE`
- Test plan path:
  `docs/test-plans/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE.md`
- Implementation plan path:
  `docs/superpowers/plans/2026-09-04-postgis-e3-evidence.md`
- Implementation gate: `APPROVED_FOR_BUILD`
- Approval evidence: Reviewer `Human partner (@Byuntil)`, Decision
  `approved for implementation`, Approved at `2026-09-05T01:22:10+09:00`.

## Objective

- GitHub Issue #163에서 확립한 PostGIS 실행계획 재측정 증거를 확장해, 동일한
  합성 데이터·쿼리·반경·반복 횟수를 유지한 채 PostgreSQL 통계 설정
  (`default_statistics_target`) 변경 전후의 실행계획과 SQL 성능을 재현 가능한
  로컬 실험(E3)으로 비교한다.
- performanceTest 전용 JVM에서만 `pg_stat_statements`를 활성화해 SQL 호출
  수·실행시간·행 수·buffer를 안전하게 수집하고, 10K:1K, 50K:5K, 100K:10K
  결정적 cardinality sweep으로 access path 전환 지점을 관찰한다.
- 고정 100K:10K fixture에서 통계 target 100과 1000만 다르게 적용해 preview
  집계, matching 후보 순서/집합, 영속화된 최종 수신자 집합의 논리적 동일성과
  정책 가드레일(양방향 차단, 비활성 계정, 만료 presence, 공정성, 수신 한도)이
  유지되는지 검증한다.
- 실험 결과를 `SUPPORTED`/`REJECTED`/`INVALID` 중 하나로 판정한 비식별
  보고서를 남긴다. production SQL·인덱스·migration·방향 정책 변경이나 운영
  성능 개선 주장은 이 작업 범위에 포함하지 않는다.

## Scope

Included:

- performanceTest 전용 pg_stat_statements preload와 extension
- 10K:1K, 50K:5K, 100K:10K 결정적 cardinality sweep
- statistics target 100과 1000의 고정 100K:10K before/after
- preview counts, matching candidate order/set, persisted recipient set guardrails
- sanitized experiment report

## Explicit exclusions

Excluded:

- production SQL, index, migration, policy 변경
- Compose, Prometheus, Grafana, k6
- 운영 임계값 또는 운영 개선 주장

추가로 다음도 이 작업 범위에서 제외한다.

- `DirectionMatchingIndexPlanPerformanceIntegrationTest`(#163) 수정. 원본
  증거를 재현 가능하게 그대로 보존한다.
- `ActiveUserPresenceSql`, repository 코드, `delivery-scope` 정책, `20,100km`
  운영 반경 변경.
- planner GUC나 hint를 이용한 인덱스 사용 강제.
- 실제 운영 데이터, 운영 부하 테스트, 관측 결과에 따른 후속 성능 개선
  구현이나 별도 Issue 자동 생성.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 요구사항·가정·테스트 계획 통합 | Test Orchestrator | Human partner |
| performanceTest 전용 preload/extension, 평가 probe, cardinality sweep, before/after 가드레일 구현 | Test Executor | Independent verifier |
| 성능 실험 보고서와 `SUPPORTED`/`REJECTED`/`INVALID` 판정 | Test Executor | PM reviewer |
| 전체 diff·정책·재현성 독립 검증 | Independent verifier | Human partner |

실행자는 승인된 테스트 계획에서 배정된 파일만 수정한다. 검증자는 테스트를
통과시키기 위해 production source나 테스트 소스를 수정하지 않는다.

## Existing user-owned changes

- 작업 시작 시 `git status --short`에는 untracked
  `docs/superpowers/plans/2026-09-04-postgis-e3-evidence.md`만 있었고 다른
  사용자 변경은 없었다.
- 이 브랜치는 `origin/main`의 `51e054b`에서 분기했다. 계획 문서 본문은
  `fff2b18` 기준으로 작성됐으나 실행 시점에 origin이 그 사이 진행되어
  `51e054b`에서 분기했으며, 이는 확인되고 기록된 결정이다.
- 계획 문서가 보존 대상으로 언급하는 untracked `scratch.py`는 이 worktree에
  존재하지 않는다.
- 이 작업(Task 1)에서 지금까지 생성·수정한 변경은 이 `TASK.md`와 신규 테스트
  계획 파일
  (`docs/test-plans/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE.md`)뿐이다.
  이미 존재하던 untracked 계획 문서
  (`docs/superpowers/plans/2026-09-04-postgis-e3-evidence.md`)는 Task 1의
  커밋 대상에 포함하되 내용은 수정하지 않는다.

## Validation

Focused checks (Task 2 이후 구현 완료 시):

```bash
./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'
./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'
./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'
```

Final checks:

```bash
./gradlew performanceTest
./harness test-run --id TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- [x] 사람이 테스트 계획 §11 Human approval과 이 `TASK.md`의 승인 상태를
      승인했다. (`APPROVED`, `2026-09-05T01:22:10+09:00`)
- [x] `pg_stat_statements`가 performanceTest에서만 활성화되고 일반
      integrationTest에는 영향을 주지 않는다.
- [x] 세 규모(10K:1K, 50K:5K, 100K:10K)에서 후보 조회 실행계획과 SQL 통계를
      측정한다.
- [x] 동일한 100,000:10,000 fixture에서 통계 설정(100→1000)만 변경한 전후
      비교를 수행한다.
- [x] preview segment count와 matching candidate 순서 및 집합이 전후
      동일하다.
- [x] 최종 수신자 집합이 동일하고 중복과 누락이 0이다.
- [x] 차단, 비활성 계정, 만료 presence, 공정성과 수신 한도 정책이 통계 변경
      전후에 유지된다.
- [x] 측정 결과는 로컬 합성 실험의 한계와 함께 비식별 보고서로 기록된다.
- [x] performanceTest와 저장소 필수 검증을 통과한다.
- [x] 신규 Issue, 일치하는 성능 브랜치, 승인된 `TASK.md`와 승인된 테스트
      계획이 존재한다. (Task 1)
- [x] 일반 integrationTest는 performance 전용 preload flag와 독립적으로
      유지된다.
- [x] 10K:1K, 50K:5K, 100K:10K 세 규모의 관측이 모두 신선하고
      비식별화되어 있다.
- [x] 지연시간이나 GiST access path를 깨지기 쉬운 pass/fail 임계값으로
      사용하지 않는다.
- [x] 보고서는 신선한 증거로부터 `SUPPORTED`/`REJECTED`/`INVALID`를 판정하고
      운영 영향을 과장하지 않는다.
- [x] production 소스, 쿼리, 인덱스, migration, Compose, Terraform, workflow,
      외부 서비스 변경이 없다.
- [x] 모든 필수 performance·저장소 검증 명령이 통과하거나 최종 상태가
      정확히 `FAIL`/`BLOCKED`로 기록된다.

## Final verification contract

```text
status: PASS
issue_number: 214
task_id: GH-214-POSTGIS-E3-EVIDENCE
design_id: N/A (local test-only experiment)
changed_files:
  TASK.md
  build.gradle
  docs/superpowers/plans/2026-09-04-postgis-e3-evidence.md
  docs/test-plans/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE.md
  docs/reports/tests/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE.md
  docs/reports/tests/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-sanitized-console.txt
  templates/performance-experiment-report.md
  src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java
  src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbe.java
  src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbeIntegrationTest.java
  src/integrationTest/java/com/dnd/qello/PgStatStatementsPerformanceIntegrationTest.java
  src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java
executed_checks:
  ./harness test-run --id TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE
  ./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'
  ./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'
  ./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'
  ./gradlew performanceTest
  ./harness check
  ./harness pr-ready --project-tests
  npm run hooks:validate
  git diff --check
  git diff --name-only origin/main...HEAD
  git diff --check origin/main...HEAD
  rg -n "SELECT .*query|raw.*EXPLAIN|userId=|nickname=|latitude=|longitude=|token=|password=" docs/reports/tests src/integrationTest/java/com/dnd/qello
passed_checks:
  ./harness test-run --id TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE
  ./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'
  ./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'
  ./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'
  ./gradlew performanceTest
  ./harness check
  ./harness pr-ready --project-tests
  npm run hooks:validate
  git diff --check
  git diff --name-only origin/main...HEAD
  git diff --check origin/main...HEAD
  rg -n "SELECT .*query|raw.*EXPLAIN|userId=|nickname=|latitude=|longitude=|token=|password=" docs/reports/tests src/integrationTest/java/com/dnd/qello
failed_checks: none
blocked_checks: none
assumptions: synthetic 10:1 100km disk; statistics targets 100 and 1000; ANALYZE limited to user_account and active_user_presence; PERF-006 fixture is not PERF-005
risks: local planner/host/cache differences and no fixed Testcontainers resources
required_human_decisions: production change remains a separate Issue
```

Experiment conclusion recorded in `docs/reports/tests/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE.md`:

```text
REJECTED: statistics target 1000 did not materially change estimates/access path, while all guardrails remained equal.
```
