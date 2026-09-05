# Test Plan: TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE

> Created at: `2026-09-05T01:08:43+09:00`
> GitHub Issue: `#214`
> Status: Approved for execution

## 1. Objective

GitHub Issue #163가 확립한 PostGIS 후보 조회 실행계획 재측정 증거를 확장해,
동일한 합성 데이터·쿼리·반경·반복 횟수를 유지한 상태에서 PostgreSQL 통계
설정(`default_statistics_target`) 변경 전후의 실행계획과 SQL 성능을
`pg_stat_statements`와 클라이언트 percentile로 비교하고, 결과 정합성 회귀가
없는지 검증한다.

이 계획은 통계 target 1000이 planner 추정치나 access path를 반드시 바꿔야
한다고 미리 단정하지 않는다. `SUPPORTED`(변경됨)와 `REJECTED`(변경되지 않음)
모두 유효한 실험 결과이며, 모든 정합성·정책 가드레일이 유지된 상태에서만 둘 중
하나로 판정한다. 가드레일이 하나라도 깨지면 `INVALID`로 판정하고 이는 실패로
취급한다. 실패 시 위험은 로컬 실험 결론을 근거 없이 운영 성능 개선 주장으로
확대 해석하거나, 통계 변경 전후 후보·수신자 집합이 실제로는 달라졌는데 이를
누락해 잘못된 결론을 내리는 것이다.

## 2. Scope

### Included

- performanceTest 전용 pg_stat_statements preload와 extension
- 10K:1K, 50K:5K, 100K:10K 결정적 cardinality sweep
- statistics target 100과 1000의 고정 100K:10K before/after
- preview counts, matching candidate order/set, persisted recipient set guardrails
- sanitized experiment report

### Excluded

- production SQL, index, migration, policy 변경
- Compose, Prometheus, Grafana, k6
- 운영 임계값 또는 운영 개선 주장
- `DirectionMatchingIndexPlanPerformanceIntegrationTest`(#163) 수정
- `ActiveUserPresenceSql`, repository 코드, `delivery-scope` 정책, 20,100km
  운영 반경 변경
- planner GUC나 hint를 이용한 인덱스 사용 강제
- 실제 운영 데이터, 운영 부하 테스트
- 관측 결과에 따른 후속 성능 개선 구현이나 별도 Issue 자동 생성

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #214 작업 내용 | performanceTest 전용 `pg_stat_statements` 구성, 세 cardinality sweep, 고정 100K:10K fixture에서 통계 target 100/1000 비교, 실행계획·SQL 성능·정합성 결과를 수집한다. |
| GitHub Issue #214 완료 조건 | `pg_stat_statements`는 performanceTest 전용이고 일반 integrationTest에 영향이 없어야 하며, 세 규모 측정, 통계 target 전후 비교, preview/matching/recipient 논리적 동일성, 정책 유지, 비식별 보고서와 필수 검증 통과가 모두 필요하다. |
| GitHub Issue #214 제외 범위 | 운영 SQL·인덱스·migration·방향 정책 변경, Compose/Prometheus/Grafana/k6 구성, 로컬 결과의 운영 성능 개선 단정, 관측성·부하 테스트의 다른 경로 구현은 이 작업에 포함하지 않는다. |
| GitHub Issue #163 (선행 근거) | 100,000 계정:10,000 presence 합성 데이터, 100km 결정적 분포, `GLOBAL/20,100km` 정책값과 5km 진단값 이중 반경 측정 방식을 그대로 재사용한다. |
| 구현 계획 `docs/superpowers/plans/2026-09-04-postgis-e3-evidence.md` | Global Constraints와 Task 1~6의 정확한 파일·인터페이스·단계·커밋 문구를 실행 순서의 근거로 삼는다. |
| `ActiveUserPresenceSql` | `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`과 `FIND_CANDIDATES_SQL` 원문을 변경하지 않고 측정만 한다. |
| `DirectionPostPolicy` | preview·matching 정책 반경(`GLOBAL`, 최소 0m, 최대 20,100,000m)을 실험 상수로 다시 적지 않고 정책에서 읽는다. |
| `PostgisContainerIntegrationTestSupport` | 일반 `integrationTest`는 성능 전용 preload flag 없이 기존과 동일하게 시작해야 한다. |
| `AGENTS.md` §3 | JUnit 5, 모든 테스트의 `@DisplayName`, 테스트 클래스 생성 시각·source scenario, 테스트 보고서와 잠재 위험 분석이 필요하다. |
| `AGENTS.md` §4.10 / 계획 Global Constraints | 원문 SQL, raw EXPLAIN JSON, 실제/합성 사용자 ID, 닉네임, 좌표, 자격 증명, URL, 토큰, `.env` 값을 로그·보고서에 남기지 않는다. |

## 4. Approved decisions

아래 결정은 이 테스트 계획의 §11 인간 승인 이전 초안이며, `Implementation
gate`가 `APPROVED_FOR_BUILD`로 바뀌기 전에는 구현(Task 2 이후)에 사용하지
않는다. 승인되면 이 절의 각 항목이 그대로 구현의 근거가 된다.

1. **Fixed three-point sweep, 10:1 ratio:** 정확히 10,000:1,000, 50,000:5,000,
   100,000:10,000 세 규모만 사용한다. Issue의 예시와 #163의 10:1 비율을
   유지하며, 추가 규모나 임의 비율은 도입하지 않는다.
2. **Reuse the GH-163 deterministic distribution:** presence는 원점 기준
   100km 원 내부에 제곱근 반경식과 golden-angle 방위로 결정적으로 분산한다.
   `GLOBAL/0..20,100km` 정책 반경과 5km 진단 반경을 분리 측정하는 #163 방식을
   그대로 재사용하고 5km를 운영 정책으로 주장하지 않는다.
3. **Statistics-target-only before/after:** 고정 100K:10K fixture에서
   재시딩 없이 같은 connection에서 `SET default_statistics_target`→
   `ANALYZE user_account, active_user_presence`→`RESET`을 100과 1000
   각각에 적용한다. 변경하는 변수는 통계 target 하나뿐이다.
4. **performance-only pg_stat_statements:** `pg_stat_statements`는
   Gradle system property로 선택된 하드코딩 안전 명령을 통해서만 preload되며,
   이 property는 `performanceTest`에서만 설정한다. 일반 `integrationTest`는
   property나 preload 명령을 받지 않는다.
5. **Correctness guardrails over latency:** preview segment 집계, matching
   후보 order/set, 영속화된 최종 수신자 집합의 논리적 동일성과 양방향 차단,
   비활성 계정, 만료 presence, 공정성, 수신 한도 정책이 통계 target 100과
   1000 사이에서 동일해야 한다. 지연시간이나 access path는 pass/fail
   임계값으로 쓰지 않는다.
6. **No production or index change, historical isolation:** production SQL,
   repository, 인덱스, migration, `delivery-scope` 정책, 20,100km 운영 반경을
   바꾸지 않는다. planner GUC나 hint로 인덱스 사용을 강제하지 않는다. #163의
   `DirectionMatchingIndexPlanPerformanceIntegrationTest`는 수정하지 않고
   별도 클래스로 이 실험을 추가한다.
7. **Result handoff:** 실험 결론은 `SUPPORTED`(통계 target 1000이 추정치나
   access path를 바꿨고 모든 가드레일이 동일), `REJECTED`(의미 있는 변화가
   없었고 모든 가드레일이 동일), `INVALID`(가드레일 실패 또는 통제 조건 훼손)
   중 하나로만 기록한다. `INVALID`는 실패로 취급한다. production 권고나 후속
   Issue 생성은 이 작업의 범위가 아니며 별도 사람 승인이 필요하다.

## 5. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| performance preload flag가 일반 `integrationTest`에도 적용됨 | 모든 개발·CI 통합 테스트가 불필요하게 느려지거나 컨테이너 시작 방식이 바뀜 | Medium | P0 | PERF-002에서 `integrationTest` 컨테이너 시작 커맨드에 preload 옵션이 없음을 확인 |
| `pg_stat_statements` fingerprint가 다른 쿼리에 잘못 귀속됨 | 실행시간·호출수 증거가 실제로 측정 대상이 아닌 SQL을 가리킴 | Medium | P0 | 각 measure 호출에서 fingerprint LIKE 매치가 정확히 1행인지 단언하고 0행/2행 이상이면 실패시킴 |
| cardinality sweep에서 규모별 계정:presence 비율이나 확률 반경 카운트가 계획과 다름 | 전환 관찰이 무효화되고 이후 고정 fixture 해석에 영향 | Medium | P1 | 각 스케일에서 계정/presence/5km 확률 카운트를 시딩 직후 단언 |
| 통계 target 변경 전후 재시딩하거나 다른 connection을 사용함 | 통계 target 외 다른 변수가 섞여 before/after 비교가 무효화됨 | Medium | P0 | 같은 connection에서 SET→ANALYZE→RESET을 수행하고 재시딩 없이 두 스냅샷을 캡처 |
| preview count, matching order/set 또는 영속화된 수신자 집합이 통계 target 사이에서 실제로 달라짐 | 정합성 회귀가 존재하는데 `SUPPORTED`/`REJECTED`로 잘못 판정됨 | High | P0 | `LogicalQuerySnapshot`/`LogicalRecipientSnapshot` 동등성 단언, 다르면 `INVALID`로 보고 |
| 양방향 차단, 비활성 계정, 만료 presence, 공정성, 수신 한도 정책이 통계 target 변경으로 우회됨 | 정책 위반이 조용히 통과함 | High | P0 | guardrail 전용 논리 행이 두 조건 모두에서 제외/포함 규칙을 만족하는지 단언 |
| 중복되거나 누락된 최종 수신자가 발생함 | 실제 알림 정합성이 깨졌는데 실험이 이를 놓침 | High | P0 | 중복/누락/차단/full-slot 카운트를 0으로 단언 |
| 보고서나 로그에 원문 SQL, raw EXPLAIN JSON, ID, 닉네임, 좌표, 자격 증명이 남음 | 민감정보 노출 및 저장소 정책 위반 | Medium | P0 | 허용된 aggregate 필드만 있는 sanitized 한 줄 출력만 캡처하고 diff·rg 스캔으로 재검증 |
| 이 실험 결과를 근거로 같은 브랜치에서 SQL·인덱스·정책을 수정함 | Issue 제외 범위와 독립 검증 원칙 위반 | Medium | P0 | 최종 diff에 production SQL·migration·인덱스 변경이 없음을 확인 |
| Testcontainers/Docker 가용성 문제 또는 100K:10K 적재 시간으로 로컬 실행이 실패함 | 실험 자체를 실행할 수 없음 | Low | P1 | 실패 시 명령·오류·재현 조건·미검증 범위·남은 위험을 `BLOCKED`로 기록 |
| 호스트 자원 편차(고정되지 않은 Testcontainers CPU/메모리)를 실제 개선/회귀로 오인함 | 결론의 재현성·신뢰도 저하 | Medium | P1 | 보고서에 Testcontainers 자원이 고정되지 않았음을 명시하고 지연시간을 관찰값으로만 기록 |
| 100,000:10,000 적재가 기본 `check` 경로에 섞여 들어감 | 모든 개발·CI 검증이 느려짐 | Low | P0 | `@Tag("performance")`, focused `performanceTest`, `integrationTest` 제외 확인 |

## 6. Unit scenarios

신규 순수 단위 시나리오는 없다. 이 Issue의 대상은 PostgreSQL planner, 실제
`pg_stat_statements` 통계, PostGIS 선택도, `ANALYZE`/`default_statistics_target`
동작과 실제 애플리케이션 흐름(`DirectionPostApplicationService`,
`DirectionMatchingWorker`)의 결합이므로 mock이나 문자열 단위 테스트로 유효하게
검증할 수 없다. SQL 상수의 정적 경계는 기존
`DirectionRecipientSelectionBoundaryTest`와 `DirectionPreviewPersistenceBoundaryTest`가
계속 소유한다. `DirectionMatchingPerformanceProbe`의 API 계약(PERF-010)은
7절의 통합/성능 시나리오로 분류한다. 실제 PostgreSQL 컨테이너와
`pg_stat_statements` 확장에 대해 실행되기 때문이다.

## 7. Integration and performance scenarios

공통 전제: `@SpringBootTest`, `@ActiveProfiles("test")`,
`PostgisContainerIntegrationTestSupport` 상속, `@Tag("performance")`(PERF-002는
일반 `integrationTest` 구성 검사), 고정 `Instant.parse("2026-09-04T12:00:00Z")`,
전용 region `TEST-DIRECTION-PERF-E3`.

각 시나리오의 "Approved wording" 열은 구현 계획이 정의한 승인 문구를 그대로
옮긴 것이며, 어떤 실행자도 이 문구를 바꾸지 않는다.

| Scenario ID | Approved wording | Components | Setup | Action | Expected result | Cleanup | Priority |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-001` | performanceTest JVM에서 pg_stat_statements preload와 extension 조회가 성공한다. | `PostgisContainerIntegrationTestSupport`, `pg_stat_statements` extension, `JdbcTemplate` | performanceTest JVM, `qello.test.postgres.pg-stat-statements-enabled=true`, `CREATE EXTENSION IF NOT EXISTS pg_stat_statements` | `SHOW shared_preload_libraries` 조회 후 쿼리 1회 실행, `pg_stat_statements_reset()`, `pg_stat_statements` 행수 조회 | preload 목록에 `pg_stat_statements`가 포함되고 확장 조회가 성공하며 원문 `query` 컬럼은 출력하지 않음 | 컨테이너 폐기(Testcontainers) | P0 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-002` | 일반 integrationTest JVM은 performance preload flag를 받지 않는다. | `PostgisContainerIntegrationTestSupport`, 일반 `integrationTest` 구성 | 일반 `integrationTest` JVM(performance 전용 flag 미설정) | 컨테이너 시작 커맨드 구성을 확인 | preload flag가 설정되지 않았을 때 `shared_preload_libraries=pg_stat_statements` 명령이 추가되지 않고 기존 시작 방식이 유지됨 | 컨테이너 폐기 | P0 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-003` | 10K:1K cardinality의 preview·matching 네 query/radius 조합을 측정한다. | `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`, `FIND_CANDIDATES_SQL`, `DirectionMatchingPerformanceProbe`, `DirectionPostPolicy` | 10,000 계정 중 1,000 presence, 100km 결정적 분포, 통계 target 100으로 `ANALYZE` | preview·matching을 정책 반경과 5km 반경으로 각각 warm-up 1회 + 측정 20회 실행, EXPLAIN 파싱 | 네 조합 모두 20회 결과가 동일하고 `pg_stat_statements` 요약·client p50/p95/p99·plan node 증거가 존재함(access path는 관찰값으로만 기록) | 전용 region FK-safe 삭제 | P1 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-004` | 50K:5K cardinality의 preview·matching 네 query/radius 조합을 측정한다. | 위와 동일 | 50,000 계정 중 5,000 presence, 100km 결정적 분포, 통계 target 100으로 `ANALYZE` | PERF-003과 동일 절차 | PERF-003과 동일 형태의 증거, 5km 확률 후보 13개 확인 | 전용 region FK-safe 삭제 | P1 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-005` | 100K:10K cardinality의 preview·matching 네 query/radius 조합을 측정한다. | 위와 동일 | 100,000 계정 중 10,000 presence, 100km 결정적 분포, 통계 target 100으로 `ANALYZE` | PERF-003과 동일 절차 | PERF-003과 동일 형태의 증거, 5km 확률 후보 25개 확인 | 전용 region FK-safe 삭제 | P1 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-006` | 고정 100K:10K fixture에서 statistics target 100과 1000 결과를 비교한다. | 고정 100K:10K fixture, `analyzeWithStatisticsTarget` | PERF-005와 동일 fixture를 재사용하고 재시딩 없이 통계 target만 100→1000으로 변경 | 같은 connection에서 SET→ANALYZE→RESET 후 네 조합을 다시 측정 | 두 통계 target의 plan 추정치·access path·`pg_stat_statements` 요약을 나란히 비교 기록(변화 여부는 관찰값) | 전용 region FK-safe 삭제 | P0 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-007` | before/after preview segment counts와 matching logical candidate order/set이 같다. | `LogicalQuerySnapshot` | PERF-006과 같은 두 조건의 preview 집계·matching 후보 결과 | 통계 target 100/1000의 `previewCounts`, `matchingOrder`, `matchingSet`을 비교 | 세 값 모두 두 조건에서 완전히 동일함 | 전용 region FK-safe 삭제 | P0 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-008` | before/after persisted recipient logical set이 같고 중복·누락이 0이다. | `LogicalRecipientSnapshot`, `DirectionPostApplicationService`, `DirectionMatchingWorker` | 통계 target 100/1000 각각에서 방향 글 제출→모더레이션 통과→매칭 배치 처리 | 영속화된 `post_recipient`를 논리 닉네임으로 정규화해 두 조건을 비교 | 두 조건의 수신자 논리 집합이 동일하고 duplicate/missing 카운트가 0 | 해당 run의 outbox·recipient·audience·post·receive-state만 정리 | P0 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-009` | 양방향 block, inactive account, expired presence와 receive-capacity 정책이 유지된다. | guardrail 전용 논리 행(차단, 비활성, 만료, 5km 밖, full-slot) | PERF-008과 동일 실행에 정책 adversary 행 포함 | 두 통계 target 조건 각각에서 정책별 포함/제외 결과 확인 | 양방향 차단·비활성 계정·만료 presence·5km 밖 제외, 공정성 순서와 수신 한도 초과 제외가 두 조건 모두에서 유지됨 | guardrail 행 포함 전용 region FK-safe 삭제 | P0 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-PERF-010` | evidence 출력에는 허용된 aggregate field만 존재한다. | `DirectionMatchingPerformanceProbe.sanitizedLine()`, plan 요약 출력 | PROBE-CONTRACT 쿼리(`SELECT 1 AS e3_probe_contract`) 20회 측정과 EXPLAIN 파싱 | sanitized 한 줄 출력과 plan 요약 필드 검사 | 출력에 `experiment=`, `condition=`, `calls=`, 집계 수치 필드만 있고 원문 SQL, `query=`, 사용자 ID, 닉네임, 좌표가 전혀 없음 | 없음 | P0 |
| `TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE-REPORT-001` | 최소 3회 이상의 fresh result, median/p95/p99, DB summary, plan summary, guardrail, 한계를 기록한다. | 성능 실험 보고서, `templates/performance-experiment-report.md` | PERF-001~010의 실제 신선한 출력 | 최소 3회 이상의 fresh result를 근거로 median/p95/p99, DB(`pg_stat_statements`) 요약, plan 요약, guardrail 결과, 로컬 실험 한계를 정리 | 보고서에 세 항목이 모두 존재하고 `SUPPORTED`/`REJECTED`/`INVALID` 중 하나가 신선한 증거로 판정되며 운영 영향을 과장하지 않음 | 없음 | P0 |

P0: PERF-001, PERF-002, PERF-006, PERF-007, PERF-008, PERF-009, PERF-010,
REPORT-001. P1: PERF-003, PERF-004, PERF-005 — 세 규모 sweep은 access path
전환을 설명하는 관찰 증거이지만, 고정 100K:10K fixture의 before/after
정합성 게이트 자체는 아니다.

## 8. Cross-cutting scenarios

### Database and transactions

- 모든 데이터는 운영 DB가 아닌 일회성 Testcontainers PostgreSQL 16/PostGIS
  3.5에만 적재한다.
- 합성 데이터 적재는 `INSERT ... SELECT generate_series(...)` 배치 SQL로
  수행하며, presence는 #163과 동일한 제곱근 반경식과 golden-angle 방위로
  결정적으로 배치한다.
- 통계 target 변경은 `SET default_statistics_target = <100|1000>` →
  `ANALYZE user_account, active_user_presence` → `RESET
  default_statistics_target`을 같은 connection에서 실행하며, 값은 내부
  allowlist 정수(100 또는 1000)만 허용한다.
- 정합성·plan 측정 시나리오는 트랜잭션 자체를 검증하는 것이 아니므로
  `@Transactional`을 붙이지 않는다. 매칭 배치 처리(PERF-008/009)는 기존
  `DirectionMatchingWorker` 트랜잭션 경계를 그대로 사용한다.
- `regionCode`는 GLOBAL 조회 형태를 유지하기 위해 조건절에 추가하지 않고
  전용 region 문자열은 데이터 격리에만 사용한다.

### Concurrency and idempotency

- 측정 대상 조회 SQL은 읽기 전용이며 데이터 적재 후 단일 스레드로 20회
  반복 실행한다.
- 매칭 배치 처리는 기존 #127 패턴을 재사용해 `limit 10`, 60초 lease,
  `OutboxRetryPolicy(3, attempt -> Duration.ofSeconds(1))`으로 idempotency
  키가 있는 방향 글 1건만 처리한다. worker 동시성 자체의 회귀 검증은 기존
  worker 통합 테스트가 소유하며 이 계획의 범위가 아니다.

### External APIs

- 외부 API, FCM/APNs, S3와 AWS 자격 증명을 사용하지 않는다.

### Failure recovery and reconciliation

- assertion 실패 여부와 무관하게 각 시나리오의 cleanup이 FK-safe 순서로
  전용 region의 데이터를 삭제한다. 프로세스 강제 종료로 cleanup이 실행되지
  않아도 Testcontainers 컨테이너 폐기로 격리된다.
- Docker/Testcontainers, image pull 또는 100,000행 적재 시간 문제로 실행이
  불가능하면 구현 실패가 아니라 `BLOCKED`로 분류하고 실패 명령, 오류 요약,
  재현 조건, 미검증 범위와 남은 위험을 보고한다.
- 통계 target 1000이 추정치나 access path를 바꾸지 않는 것(`REJECTED`) 자체는
  테스트 실패가 아니다. 이는 이 실험이 허용하는 관찰 결과다. 정합성·정책
  가드레일이 깨지는 경우만 `INVALID`(실패)로 분류한다.

## 9. Test data and isolation

- **Fixtures:** 원점 `(37.5000, 127.0000)`, 고정
  `Instant.parse("2026-09-04T12:00:00Z")`, region `TEST-DIRECTION-PERF-E3`,
  계정 prefix `perf-e3-account-`와 제외 계정 닉네임 `perf-e3-excluded`를
  사용한다.
- **Cardinality:** sweep 단계는 10,000:1,000, 50,000:5,000, 100,000:10,000
  세 규모만 사용한다. 고정 fixture 단계는 100,000:10,000만 재사용하며
  재시딩하지 않는다. guardrail 전용 논리 행은 측정 cardinality 카운트에
  포함되지 않도록 별도로 추가한다.
- **Spatial distribution:** presence는 100km 원 내부에 결정적으로 배치하고,
  정책 반경(`GLOBAL/0..20,100km`)과 5km 진단 반경의 예상 후보 수를 시딩
  직후 직접 카운트로 검증한다. 이는 실제 사용자 분포 모델이 아니라 #163에서
  검증된 선택도 통제값이다.
- **Statistics conditions:** `default_statistics_target` 100(baseline)과
  1000(experiment) 두 값만 사용한다.
- **Database isolation:** 전용 region과 nickname prefix로 기존 seed 및 다른
  테스트와 분리한다. 조회 SQL 자체에는 GLOBAL 조건을 재현하기 위해 region
  filter를 주지 않는다.
- **Clock/randomness:** `random()`을 사용하지 않는다. 고정 mutable
  test clock을 사용하고 각 시나리오 시작 시 `NOW`로 재설정한다.
- **External API doubles:** 외부 호출 자체가 없다.
- **Cleanup:** confirmed-event outbox 행, post recipient, matching outbox
  행, audience, post, receive state, approved question, presence, account,
  region 순으로 FK-safe 삭제한다.

실제 자격 증명이나 `.env` 값, 계정 식별자, 서버 주소, 원문 SQL과 raw plan을
기록하지 않는다.

## 10. Result interpretation

각 조건(`STATISTICS_TARGET=100`, `STATISTICS_TARGET=1000`)에서 다음만
기록한다.

- relation: `active_user_presence`, `user_account`
- node type과 index name
- plan rows, actual rows, actual loops, rows removed by filter
- shared hit/read, temp read/written block
- `pg_stat_statements` calls, total/mean execution time, rows
- client p50/p95/p99
- sort method, space type, spill 여부
- `active_user_presence_position_gix`(partial GiST) 존재 여부

판정 규칙:

| Preview/matching 논리 결과 | Access path/추정치 | Conclusion | Required follow-up record |
| --- | --- | --- | --- |
| 두 통계 target에서 동일 | 100→1000 사이에 access path 또는 plan 추정치가 바뀜 | `SUPPORTED` | 어떤 relation·쿼리·반경에서 무엇이 바뀌었는지 기록. production 변경은 별도 Issue로 남김 |
| 두 통계 target에서 동일 | 의미 있는 변화 없음 | `REJECTED` | 변화가 없었다는 관찰을 그대로 기록. 이는 실패가 아님 |
| 하나라도 다름(중복/누락/차단·정책 위반 포함) | 무관 | `INVALID` | 어떤 가드레일이 실패했는지와 재현 조건을 기록. 실험은 실패로 취급하고 production 판단에 사용하지 않음 |

`SUPPORTED`/`REJECTED` 어느 쪽도 통계 target 변경을 production에 적용하라는
권고로 직접 연결하지 않는다. 그러한 권고는 이 Issue의 범위 밖이며 별도 Issue와
사람 승인이 필요하다.

## 11. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Test Orchestrator | `TASK.md`, `docs/test-plans/gh-214-TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE.md` | 계획 전체 | Human approval; placeholder·범위·추적성 검토 |
| 2 | Test Executor | `build.gradle`, `src/integrationTest/java/com/dnd/qello/PostgisContainerIntegrationTestSupport.java`, `src/integrationTest/java/com/dnd/qello/PgStatStatementsPerformanceIntegrationTest.java` | PERF-001, PERF-002 | `./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'`; `./gradlew integrationTest --tests '*QelloApplicationIntegrationTest'` |
| 3 | Test Executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbe.java`, `src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceProbeIntegrationTest.java` | PERF-010 | `./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'` |
| 4 | Test Executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java` | PERF-003, PERF-004, PERF-005 | `./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest.cardinalitySweep*'` |
| 5 | Test Executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java`(동일 파일 확장) | PERF-006, PERF-007, PERF-008, PERF-009 | `./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest.statisticsTargetComparison*'`; `./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest.persistedRecipientGuardrail*'`; `./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'` |
| 6 | Test Executor | `templates/performance-experiment-report.md`, Issue-derived `docs/reports/tests/` 보고서, `TASK.md` | REPORT-001 | `./harness test-run --id TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE`; `./gradlew performanceTest`; `./harness check`; `./harness pr-ready --project-tests`; `npm run hooks:validate`; `git diff --check` |
| 7 | Independent verifier | 소스 수정 없음 | 전체 | 실제 diff, focused/full 결과, production SQL·migration 무변경, sanitization 스캔과 민감정보 검토 |
| 8 | PM reviewer | 저장소 파일 수정 없음 | 결과별 handoff | Issue #214 완료 조건과 보고서 결론(SUPPORTED/REJECTED/INVALID) 일치 확인 |

한 실행자는 자신에게 배정된 파일만 수정한다. 검증자는 검증 통과를 위해 소스나
테스트를 수정하지 않는다. 구현 계획의 정확한 단계·파일·커밋 문구는
`docs/superpowers/plans/2026-09-04-postgis-e3-evidence.md`의 Task 2~6이
그대로 소유하며, 이 표는 그 계획을 요약해 시나리오 ID와 연결하는 참조표다.

## 12. Verification commands and failure classification

Focused evidence per implementation task:

```bash
./gradlew performanceTest --tests '*PgStatStatementsPerformanceIntegrationTest'
./gradlew performanceTest --tests '*DirectionMatchingPerformanceProbeIntegrationTest'
./gradlew performanceTest --tests '*DirectionMatchingE3PerformanceIntegrationTest'
```

Report scaffold and repository regression:

```bash
./gradlew performanceTest
./harness test-run --id TEST-PLAN-GH-214-POSTGIS-E3-EVIDENCE
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- **PASS:** 세 규모 sweep과 고정 100K:10K before/after의 데이터 전제·plan
  evidence 단언이 통과하고, preview/matching/recipient 논리 동일성과 모든
  정책 가드레일이 유지되며, 보고서가 신선한 증거로 `SUPPORTED`/`REJECTED` 중
  하나를 판정하고 필수 검증 실패가 없다.
- **FAIL:** 합성 cardinality/분포가 계획과 다르거나, before/after 논리 결과가
  달라 `INVALID`가 되거나, production SQL·인덱스·migration이 변경되었거나
  필수 검증이 실패한다.
- **BLOCKED:** Docker/Testcontainers, image pull, 대용량 적재 시간 또는 필수
  도구 문제로 실제 PostgreSQL/PostGIS 측정을 실행할 수 없다. 실패 명령·오류·
  미검증 범위와 위험을 남긴다.
- 통계 target 1000이 access path나 추정치를 바꾸지 않는 것(`REJECTED`) 자체는
  FAIL이 아니다. 이 Issue가 허용한 관찰 결과다.

## 13. Completion criteria

- [ ] 사람이 §4 Approved decisions와 이 계획을 승인했다. (`PENDING`)
- [ ] PERF-001~010 및 REPORT-001 구현
- [ ] `pg_stat_statements`가 performanceTest에서만 활성화되고 일반
      `integrationTest`는 영향을 받지 않음을 확인
- [ ] 10K:1K, 50K:5K, 100K:10K 세 규모에서 preview·matching 네 query/radius
      조합을 측정
- [ ] 고정 100,000:10,000 fixture에서 재시딩 없이 통계 target 100→1000
      전후를 측정
- [ ] preview segment count, matching 후보 order/set, 영속화된 수신자 집합의
      논리적 동일성을 통계 target 전후에 확인
- [ ] 양방향 차단, 비활성 계정, 만료 presence, 공정성, 수신 한도 정책이
      통계 target 전후에 유지됨을 확인
- [ ] 중복/누락 수신자 카운트가 0임을 확인
- [ ] production source·SQL·migration·인덱스·정책 무변경
- [ ] 모든 신규 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 정확한 ISO 8601 생성 시각과 source scenario 검증
- [ ] focused performance test와 전체 `performanceTest` 결과 기록
- [ ] harness·hook·공백 검증 결과 기록
- [ ] 애플리케이션, DB, 동시성, 트랜잭션, 외부 API와 장애 복구 잠재 문제 분석
- [ ] `templates/performance-experiment-report.md` 기반 보고서에서
      `SUPPORTED`/`REJECTED`/`INVALID` 판정 기록

## 14. Human approval

- Reviewer: `Human partner (@Byuntil)`
- Decision: `APPROVED`
- Approved at: `2026-09-05T01:22:10+09:00`
