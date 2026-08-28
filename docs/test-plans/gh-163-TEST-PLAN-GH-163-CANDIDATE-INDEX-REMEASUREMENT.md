# Test Plan: TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT

> Created at: `2026-08-28T15:07:30+09:00`
> GitHub Issue: `#163`
> Status: Approved for execution

## 1. Objective

`user_account`가 `active_user_presence`보다 충분히 크고 presence 좌표가 조회 반경
밖까지 분산된 PostgreSQL/PostGIS 데이터에서 방향 매칭의 두 후보 조회 SQL을 다시
측정한다. 부분 GIST 인덱스 `active_user_presence_position_gix`가 선택되는지 쿼리별로
결론을 내리고, 선택되지 않을 때는 쿼리 형태 변경 검토 여부를 근거와 함께 남긴다.

이 계획은 인덱스 사용을 성공 조건으로 미리 고정하지 않는다. 인덱스 사용과 미사용은
모두 가능한 실측 결과이며, 성공 조건은 데이터 전제가 검증되고 두 실행계획의 접근
경로가 재현 가능하게 관측되어 결과별 후속 판단이 기록되는 것이다.

## 2. Scope

### Included

- 전용 `@Tag("performance")` JUnit 5 통합 테스트에서 Testcontainers
  PostgreSQL 16/PostGIS 3.5 사용
- 합성 후보 계정 100,000개와 presence 10,000개 생성
- presence를 원점에서 최대 100km 원 내부에 결정적으로 분산
- 현재 소스 기본값인 `GLOBAL / 20,100km`로 실제 정책 충실도 측정
- 같은 데이터에 5km를 적용해 공간 조건이 선택적일 때의 planner 경로를 별도 진단
- 계정:presence 10:1, 20,100km·5km의 실제 선택도와 시간·수신 predicate 유효성 검증
- `ANALYZE user_account, active_user_presence` 후 preview 집계와 matching 후보 조회
  SQL에 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` 실행
- relation별 node type, index name, actual rows/loops와 shared buffer 요약 관측
- 쿼리별 부분 GIST 인덱스 사용 여부 및 결과에 따른 후속 판단을 테스트 보고서에 기록
- 기존 `performanceTest` 분리 게이트가 유지되는지 확인

### Excluded

- `ActiveUserPresenceSql`과 repository production 코드 수정
- GIST·B-tree 인덱스 또는 Flyway migration 변경
- planner hint, `enable_seqscan` 등 PostgreSQL planner 설정 강제
- 운영 데이터 복제, 실제 운영 부하·동시 사용자·장시간 soak test
- `delivery-scope` 정책 변경
- 5km를 제품 정책이나 운영 설정으로 채택
- 실행 시간의 PASS/FAIL 임계값 설정
- 쿼리 재작성 또는 후속 최적화 구현
- API, 권한, 외부 연동, 배포와 인프라 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #163 목적 | #127의 1:1 계정:presence 및 반경 내부 집중이라는 데이터 왜곡을 제거하고 재측정한다. |
| GitHub Issue #163 범위 | 계정 수가 presence 수보다 큰 합성 데이터와 조회 반경보다 넓은 좌표 분포에서 같은 두 SQL을 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`으로 측정한다. |
| GitHub Issue #163 완료 조건 | 두 SQL의 인덱스 사용 여부를 결론으로 남기고, 미사용이면 쿼리 형태 변경 검토 여부를 기록하며, 사용이면 #127 미충족 조건과 연결한다. |
| GitHub Issue #163 제외 | 쿼리·인덱스·migration 수정, 실제 운영 부하 테스트와 `delivery-scope` 변경은 하지 않는다. |
| GitHub Issue #127 보고서 §6 | 기존 데이터는 계정 10,000개 모두에 presence가 있고 좌표가 50~5,000m 안에 있어 공간 선택도를 검증하지 못했다. |
| `ActiveUserPresenceSql` | `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`과 `FIND_CANDIDATES_SQL` 원문을 변경하지 않고 직접 설명한다. |
| `application.properties` | 현재 저장소 기본 정책은 `delivery-scope=GLOBAL`, 최소 거리 0m, 최대 거리 20,100,000m다. |
| `DirectionPostPolicy`·`DirectionMatchingWorker` | preview는 정책값을 직접 전달하고 matching은 제출 시 저장한 같은 거리 snapshot을 사용한다. |
| `V1__create_direction_communication_schema.sql` | `active_user_presence_position_gix`는 `position`에 대한 `receive_allowed = TRUE` 부분 GIST 인덱스다. |
| `AGENTS.md` §3 | JUnit 5, 모든 테스트의 `@DisplayName`, 테스트 클래스 생성 시각·source scenario, 테스트 보고서와 잠재 위험 분석이 필요하다. |

## 4. Approved decisions

1. **10:1 cardinality:** 합성 후보 계정 100,000개 중 10,000개만 presence를 갖게 한다.
   Issue의 예시를 그대로 사용하되 운영 실측 비율이라고 주장하지 않는다.
2. **Controlled distribution, not a product model:** presence는 테스트 원점을 중심으로
   최대 100km 원 내부에 면적 균등에 가까운 결정적 거리와 golden-angle 방위로
   배치한다. 대한민국 실제 사용자 분포라고 주장하지 않고 planner 변수를 통제하는
   합성 데이터로만 사용한다.
3. **Dual-radius comparison:** 두 SQL을 현재 정책 기본값 20,100km와 선택도 진단값
   5km로 각각 측정한다. 20,100km는 합성 presence 100%인 10,000개, 5km는 약
   0.25%인 25개를 선택하도록 생성 전제를 검증한다. 정책 기본값은 테스트에 숫자를
   중복하지 않고 `DirectionPostPolicy`에서 읽으며, 5km는 운영 정책이 아니다.
4. **Observation, not forced success:** GIST 사용 여부는 단언하지 않는다. 테스트는
   데이터 모양과 실행계획 증거의 완전성을 단언하고, `USED`/`NOT_USED` 판정은
   쿼리와 반경별 관측 결과로 보고한다.
5. **Historical isolation:** #127의 기존 10,000행 성능 클래스를 수정하지 않고 #163
   전용 클래스를 추가한다. 기존 회귀 근거를 보존하고 두 데이터 모양을 분리한다.
6. **No planner coercion:** 통계는 `ANALYZE`로 갱신하지만 planner GUC를 바꾸거나
   hint로 GIST를 강제하지 않는다.
7. **Result handoff:** 20,100km 결과는 현재 서비스 계획으로, 5km 결과는 공간
   선택도가 생겼을 때의 진단으로 명시한다. #127은 현재 정책값에서 GIST 사용이
   확인된 경우에만 원래 조건을 그대로 충족했다고 표현한다. 5km에서만 사용되면
   “선택적 거리 정책에서 인덱스 유효성 확인”으로 범위를 제한한다. 5km에서도
   미사용이면 presence-first 쿼리 재작성 검토를 `RECOMMENDED` 또는
   `NOT_RECOMMENDED`로 판정하고, 별도 개선 Issue 생성은 사람 결정으로 남긴다.

## 5. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 계정:presence 비율이 여전히 1:1이거나 seed 일부가 누락됨 | #127과 같은 왜곡을 반복해 결론이 무효가 됨 | Medium | P0 | 전용 region에서 합성 계정 100,000, presence 10,000을 SQL로 단언 |
| 좌표가 반경 안에 다시 집중되거나 무작위 분포로 실행마다 달라짐 | 공간 선택도와 planner 선택이 재현되지 않음 | Medium | P0 | 결정적 좌표식, 20,100km 내부 10,000개와 5km 내부 25개 단언 |
| 5km 진단값을 현재 서비스 정책으로 해석함 | 실제 GLOBAL/20,100km 동작과 다른 결론을 제품 판단에 사용함 | High | P0 | 모든 scenario·보고서에 `POLICY_BASELINE`과 `SELECTIVITY_PROBE` 표기 |
| 5km에서의 GIST 사용만으로 #127 원래 조건을 완전 충족 처리함 | 운영 기본값에서 미사용이라는 사실이 가려짐 | High | P0 | #127 연결은 반경별 결과를 구분하고 정책 기본값 결과를 우선 기록 |
| `user_account` 통계가 갱신되지 않음 | planner가 10:1 cardinality를 모른 채 잘못된 계획을 고름 | Medium | P0 | 두 테이블 모두 `ANALYZE`, `pg_stat_all_tables.last_analyze` 또는 계획 row estimate 확인 |
| 테스트가 GIST 사용을 성공으로 단언함 | 허용된 “여전히 미사용” 결과가 테스트 실패로 가려짐 | High | P0 | 접근 경로 존재만 단언하고 사용 여부는 관측값으로 분리 |
| 실행계획에 relation 접근 경로가 누락되거나 잘못 파싱됨 | 사용 여부 결론을 낼 직접 증거가 없음 | Low | P0 | JSON plan을 재귀 순회해 두 relation의 node type/index name/actual rows 수집 |
| Testcontainers 머신의 지연 편차를 회귀로 오인함 | flaky performance gate가 됨 | High | P1 | 실행 시간은 증거만 기록하고 임계값을 단언하지 않음 |
| 100,000행 적재가 기본 `check`에 포함됨 | 모든 개발·CI 검증이 느려짐 | Low | P0 | `@Tag("performance")`, focused `performanceTest`, `integrationTest` 제외 확인 |
| 결과를 근거로 같은 브랜치에서 SQL·인덱스를 수정함 | Issue 제외 범위와 독립 검증 원칙 위반 | Medium | P0 | production SQL·migration diff가 비어 있음을 최종 검증 |
| raw plan 또는 테스트 데이터 식별자가 보고서에 과다 노출됨 | 내부 정보·민감정보 기록 위험 | Low | P1 | 보고서에는 relation/index 논리명, 행 수, 시간, buffer 요약만 기록 |

## 6. Unit scenarios

신규 단위 시나리오는 없다. 이 이슈의 대상은 PostgreSQL planner, 실제 GIST 인덱스,
PostGIS `ST_DWithin` 선택도와 `ANALYZE` 통계의 결합이므로 mock 또는 문자열 단위
테스트로 유효하게 검증할 수 없다. SQL 상수의 정적 경계는 기존
`DirectionRecipientSelectionBoundaryTest`와 `DirectionPreviewPersistenceBoundaryTest`가
소유한다.

## 7. Integration and performance scenarios

공통 전제: `@SpringBootTest`, `@ActiveProfiles("test")`,
`PostgisContainerIntegrationTestSupport` 상속, `@Tag("performance")`, 고정 시각
`NOW`, 고유 region `TEST-DIRECTION-PERF-163`.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-001` | `user_account`, `active_user_presence`, `DirectionPostPolicy`, PostGIS | 후보 계정 100,000개를 만들고 그중 10,000개에 `receive_allowed=TRUE`, 유효 시간, 결정적 위치를 부여 | region·nickname prefix로 계정/presence 수와 `ST_DWithin`의 policy max·5km count를 조회 | 합성 계정 100,000, presence 10,000, 현재 policy max 20,100km 내부 10,000, 5km 내부 25/외부 9,975이며 위치·시간·수신 predicate가 모두 유효함 | `@AfterAll`에서 전용 region의 presence→account→region 순서 삭제 |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-002` | `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`, `DirectionPostPolicy`, `NamedParameterJdbcTemplate`, PostgreSQL JSON plan | PERF-001 데이터, 제외 사용자 1명, OCTANT scheme, policy에서 읽은 min/max(현재 0~20,100km), region filter 없음, 두 테이블 `ANALYZE` | preview 집계 SQL에 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` 실행 | `POLICY_BASELINE`으로 plan 파싱 성공, 두 relation 접근 node 및 실제 선택도 존재. 접근 경로·buffer 요약과 GIST `USED`/`NOT_USED` 기록 | 공통 cleanup |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-003` | `FIND_CANDIDATES_SQL`, `DirectionPostPolicy`, `NamedParameterJdbcTemplate`, PostgreSQL JSON plan | PERF-001 데이터, 제외 사용자 1명, policy에서 읽은 min/max(현재 0~20,100km), 북쪽 wrap-around sector 337.5°~22.5°, region filter 없음, 두 테이블 `ANALYZE` | matching 후보 SQL에 같은 `EXPLAIN` 실행 | `POLICY_BASELINE`으로 plan 파싱 성공, 두 relation 접근 node 및 실제 선택도 존재. 동일 증거와 GIST `USED`/`NOT_USED` 기록 | 공통 cleanup |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-004` | `FIND_CANDIDATE_COUNTS_BY_SEGMENT_SQL`, `NamedParameterJdbcTemplate`, PostgreSQL JSON plan | PERF-001과 동일 데이터·scheme·GLOBAL 조건, 반경만 0~5km로 변경 | preview 집계 SQL에 같은 `EXPLAIN` 실행 | `SELECTIVITY_PROBE`로 plan 파싱 성공, 반경 후보 25개에 대한 접근 경로와 GIST `USED`/`NOT_USED` 기록 | 공통 cleanup |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-005` | `FIND_CANDIDATES_SQL`, `NamedParameterJdbcTemplate`, PostgreSQL JSON plan | PERF-001과 동일 데이터·북쪽 sector·GLOBAL 조건, 반경만 0~5km로 변경 | matching 후보 SQL에 같은 `EXPLAIN` 실행 | `SELECTIVITY_PROBE`로 plan 파싱 성공, 실제 후보가 1개 이상이며 접근 경로와 GIST `USED`/`NOT_USED` 기록 | 공통 cleanup |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-PERF-006` | Gradle `performanceTest`, 기존 `integrationTest` | 신규 클래스에 performance tag 적용 | focused 및 전체 performance suite를 실행하고 기본 `integrationTest` 구성을 점검 | 신규 스위트는 `performanceTest`에서만 실행되며 `check`의 기본 경로에는 포함되지 않음 | 없음 |
| `TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT-REPORT-001` | 테스트 보고서, #127/#163 추적성 | PERF-002~005의 실제 출력 | 쿼리·반경별 접근 경로와 GIST 사용 여부를 비교하고 결과별 후속 결정을 작성 | 정책 기본값과 선택도 진단을 분리해 #127 충족 범위를 기록. 5km에서도 미사용이면 presence-first 재작성 검토를 `RECOMMENDED`/`NOT_RECOMMENDED`와 이유로 기록 | 없음 |

P0: PERF-001~005, REPORT-001. P1: PERF-006의 전체 performance suite 회귀 실행.

## 8. Cross-cutting scenarios

### Database and transactions

- 성능 데이터는 운영 DB가 아닌 일회성 Testcontainers PostgreSQL/PostGIS에만 넣는다.
- 합성 데이터 적재는 `INSERT ... SELECT generate_series(...)` 배치 SQL로 수행한다.
- 100,000개 계정 중 ID 순서가 앞선 10,000개에만 presence를 생성한다.
- 거리는 `sqrt((series - 0.5) / 10000) * 100000` 형태로 만들고 방위는 고정
  golden-angle 수열을 써 면적 균등에 가까운 재현 가능한 분포를 만든다.
- 실행계획 전 `ANALYZE user_account, active_user_presence`를 실행한다. transaction을
  검증하는 이슈가 아니므로 테스트에 `@Transactional`을 붙이지 않는다.
- `regionCode=null`로 기존 GLOBAL 조회 형태를 유지한다. region predicate를 추가해
  `active_user_presence_region_idx`가 결과를 대신 좌우하지 않게 한다.
- 20,100km 측정은 `application.properties`의 현재 정책 충실도 시나리오다. 5km
  측정은 다른 파라미터를 모두 동일하게 둔 planner 선택도 진단이며 제품 정책을
  표현하지 않는다.

### Concurrency and idempotency

- 대상 SQL은 읽기 전용이고 데이터 적재 후 단일 스레드로 실행한다. worker claim,
  동시성, 멱등성은 #163 범위가 아니며 기존 #127 및 worker 통합 테스트가 소유한다.

### External APIs

- 외부 API, FCM/APNs, S3와 AWS 자격 증명을 사용하지 않는다.

### Failure recovery and reconciliation

- assertion 실패 여부와 무관하게 `@AfterAll`이 전용 region의 데이터를 FK 안전 순서로
  삭제한다. 프로세스 강제 종료로 cleanup이 실행되지 않아도 컨테이너 폐기로 격리된다.
- Docker/Testcontainers 또는 image pull 실패는 구현 실패가 아니라 `BLOCKED`로
  분류하고 실패 명령, 오류 요약, 재현 조건, 미검증 범위와 남은 위험을 보고한다.
- GIST 미사용은 테스트 환경 실패가 아니다. 유효한 관측 결과로 보고서의 후속 판단
  분기를 실행한다.

## 9. Test data and isolation

- **Fixtures:** 원점 `(37.5000, 127.0000)`, 고정 `NOW`, region
  `TEST-DIRECTION-PERF-163`, nickname prefix `perf-163-account-`와
  `perf-163-excluded`를 쓴다.
- **Cardinality:** 후보 계정 100,000개, presence 10,000개, 별도 제외 사용자 1명.
- **Spatial distribution:** 10,000개 presence를 100km 원 내부에 결정적으로 배치한다.
  생성식과 직접 count가 20,100km에는 10,000개 전부, 5km에는 정확히 25개가
  들어감을 보장한다. 이는 실제 대한민국 사용자 분포 모델이 아니라 선택도 통제값이다.
- **Temporal predicates:** 모든 presence는 `location_at=NOW-10s`,
  `expires_at=NOW+1h`, `receive_allowed=TRUE`, `position IS NOT NULL`이다.
- **Database isolation:** 전용 region과 nickname prefix로 기존 seed 및 다른 테스트와
  분리한다. 쿼리 자체에는 GLOBAL 조건을 재현하기 위해 region filter를 주지 않는다.
- **Clock/randomness:** `random()`을 사용하지 않는다. 동일 commit·동일 PostgreSQL
  버전에서 cardinality와 좌표 분포가 동일해야 한다.
- **External API doubles:** 외부 호출 자체가 없다.
- **Cleanup:** `active_user_presence` → `user_account` → `region_code` 순서로 삭제한다.

실제 자격 증명이나 `.env` 값, 계정 식별자, 서버 주소와 전체 raw plan을 기록하지 않는다.

## 10. Result interpretation

각 쿼리의 `POLICY_BASELINE(20,100km)`과 `SELECTIVITY_PROBE(5km)` plan node를
재귀 순회해 다음 요약만 기록한다.

- relation: `active_user_presence`, `user_account`
- node type과 index name
- plan rows, actual rows, actual loops, rows removed by filter
- shared hit/read blocks
- 전체 planning time과 execution time
- `active_user_presence_position_gix` 존재 여부

판정 규칙:

| Policy baseline | Selectivity probe | Conclusion | Required follow-up record |
| --- | --- | --- | --- |
| GIST 사용 | GIST 사용 | 현재 GLOBAL 기본값에서도 인덱스가 선택됨 | #127의 원래 미충족 조건을 충족한 근거로 연결 |
| GIST 미사용 | GIST 사용 | 현재 전지구 반경에서는 비선택적이라 미사용하지만 선택적 거리에서는 인덱스가 유효함 | #127을 “현재 기본값에서 사용”으로 처리하지 않고 조건부 유효성을 기록; 쿼리 재작성은 기본적으로 `NOT_RECOMMENDED` |
| GIST 미사용 | GIST 미사용 | 선택도가 생겨도 현재 쿼리 형태가 GIST를 선택하지 않음 | relation별 actual rows·rows removed·buffer를 근거로 presence-first 재작성 검토를 `RECOMMENDED`/`NOT_RECOMMENDED` 판정 |
| GIST 사용 | GIST 미사용 | 예상과 반대인 비정상적 planner 차이 | 데이터 전제·통계·plan 파싱을 재검증하고 결론을 `BLOCKED`로 보류 |

표의 “GIST 사용”은 각 반경에서 preview와 matching을 개별 판정한 뒤 둘 다 사용한 경우를
말한다. 두 쿼리 결과가 다르면 쿼리별로 같은 규칙을 적용한다. 정책 기본값에서 미사용한
것만으로 쿼리를 바꾸지 않는다. 5km에서도 미사용하더라도 PK/다른 인덱스로 충분히 작은
행만 읽는다면 `NOT_RECOMMENDED`일 수 있으며, 넓은 scan/filter와 높은 제거 행 수가
관측되면 별도 성능 개선 Issue를 `RECOMMENDED`할 수 있다. 이 판단은 테스트 보고서에
남기고 실제 Issue 생성은 사람 승인 후 수행한다.

## 11. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Test Orchestrator | `TASK.md`, `docs/test-plans/gh-163-TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT.md` | 계획 전체 | Human approval; placeholder·범위·추적성 검토 |
| 2 | Test Executor | `src/integrationTest/java/com/dnd/qello/DirectionMatchingIndexPlanPerformanceIntegrationTest.java` | PERF-001~006 | `./gradlew performanceTest --tests '*DirectionMatchingIndexPlanPerformanceIntegrationTest'` |
| 3 | Test Executor | `docs/reports/tests/gh-163-TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT.md` | REPORT-001 | `templates/test-report.md` 전체 섹션 및 결과 판정 표 확인 |
| 4 | Independent verifier | 소스 수정 없음 | 전체 | 실제 diff, focused/full 결과, production SQL·migration 무변경과 민감정보 검토 |
| 5 | PM reviewer | 저장소 파일 수정 없음; 필요 시 #127 근거 연결 제안 | 결과별 handoff | Issue #163 완료 조건과 보고서 결론 일치 확인 |

한 실행자는 자신에게 배정된 파일만 수정한다. 검증자는 검증 통과를 위해 소스나 테스트를
수정하지 않는다.

## 12. Verification commands and failure classification

Focused performance evidence:

```bash
./gradlew performanceTest --tests '*DirectionMatchingIndexPlanPerformanceIntegrationTest'
```

Report scaffold and repository regression:

```bash
./harness test-run --id TEST-PLAN-GH-163-CANDIDATE-INDEX-REMEASUREMENT
./gradlew performanceTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- **PASS:** 데이터 모양 전제와 plan evidence 단언이 통과하고 두 SQL의 실제 결과 및
  후속 판단이 보고서에 기록되며 필수 검증 실패가 없다.
- **FAIL:** 합성 cardinality/분포가 계획과 다르거나 plan 파싱·relation 증거가 없거나,
  production SQL·migration이 변경되었거나 정책 검증이 실패한다.
- **BLOCKED:** Docker/Testcontainers, image pull 또는 필수 도구 문제로 실제
  PostgreSQL/PostGIS 측정을 실행할 수 없다. 실패 명령·오류·미검증 범위와 위험을 남긴다.
- GIST `NOT_USED` 자체는 FAIL이 아니다. 이 이슈가 허용한 관측 결과다.

## 13. Completion criteria

- [x] 사람이 §4 Approved decisions를 승인했다. (`2026-08-28T15:53:41+09:00`)
- [ ] PERF-001~006 및 REPORT-001 구현
- [ ] 합성 계정 100,000, presence 10,000, 20,100km 내부 10,000과 5km 내부 25/외부 9,975 검증
- [ ] 두 테이블 통계 갱신 후 두 원본 SQL을 정책 기본값·선택도 진단값으로 각각 측정
- [ ] 쿼리·반경별 relation access path, index name, actual rows/loops·filter와 buffer 요약 기록
- [ ] 쿼리·반경별 GIST `USED`/`NOT_USED` 및 §10 최종 판정 기록
- [ ] 정책 기본값과 진단값을 구분한 #127 근거 연결 또는 쿼리 형태 변경 검토 여부 기록
- [ ] production source·SQL·migration·인덱스 무변경
- [ ] 모든 신규 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 정확한 ISO 8601 생성 시각과 source scenario 검증
- [ ] focused performance test와 전체 `performanceTest` 결과 기록
- [ ] 단위·통합·harness·hook·공백 검증 결과 기록
- [ ] 애플리케이션, DB, 동시성, 트랜잭션, 외부 API와 장애 복구 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성

## 14. Human approval

- Reviewer: Human partner
- Decision: `APPROVED`
- Approved at: `2026-08-28T15:53:41+09:00`
