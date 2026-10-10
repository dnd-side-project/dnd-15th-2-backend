# Test Plan: TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT

> Created at: `2026-10-10T15:15:10+09:00`
> GitHub Issue: `#350`
> Status: Approved — 사람 승인 완료(2026-10-10, D1~D4 권장안)

## 1. Objective

통합 테스트에서 `@DirtiesContext`를 빼 설정이 같은 클래스끼리 Spring 컨텍스트를 공유하게 하고, 클래스끼리 한 DB를 이어
써도 모든 통합 테스트가 같은 결과를 내는지 검증한다. 줄어든 시간은 CI Benchmark로 잰다.
실패하면 캐시된 컨텍스트가 빈 DB를 보거나, 앞 클래스가 남긴 데이터·스키마 때문에 뒤 클래스가 깨지거나, 캐시된 컨텍스트가
쌓여 heap이나 DB 연결 한도를 넘는다.

## 2. Scope

### Included

- `PostgisContainerIntegrationTestSupport`
  - `@DirtiesContext(AFTER_CLASS)`를 뺀다.
  - 클래스마다 DB를 지우고 다시 만드는 `ClassDatabaseExtension`을 클래스 시작 전 DB 초기화로 바꾼다(결정 D1).
- `@DirtiesContext(AFTER_CLASS)`를 다시 선언한 클래스 11개에서 그 선언을 뺀다(결정 D2).
- 컨텍스트 캐시 크기: 기본값(32)으로 먼저 재고, 문제가 보일 때만 바꾼다(결정 D3).
- 깨진 테스트 기록(클래스, 증상, 원인, 조치), 검토한 대안과 선택 이유.
- 무작위 클래스 순서 실행, CI Benchmark 측정과 내부 지표 추출.

### Excluded

- fork 병렬 실행, 샤딩.
- 운영 코드와 Flyway 마이그레이션 변경.
- heap 상한 변경(측정만 한다).
- 새 기능 테스트 추가. 테스트 이름 목록이 main과 같아야 한다.
- 컨텍스트 설정을 하나로 합치는 작업. 예를 들어 클래스별 `@Import`나 `@MockitoSpyBean`을 없애 단독 컨텍스트 34개를 줄이는
  일이다. 필요하면 별도 이슈로 한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #350 | 이름 목록·건너뜀 수 동일, 실패 0, 컨테이너 기동 1번 유지, 무작위 순서 seed 2개 실패 0, CI Benchmark 비교 출력, miss 수·Flyway 횟수·heap 기록 |
| CI Benchmark run 38023671076 (#347 head) | `integrationTest` 중앙값 315.5초. missCount 110, Flyway `Successfully applied` 123번(시간 중앙값 63.1초), heap 최고치 511~512MB, Full GC 직후 73~88MB |
| `build.gradle` `integrationTest` | fork 1개, `performance` 태그 제외, 컨텍스트 캐시 DEBUG 로그, GC 로그. `maxHeapSize`·캐시 크기 설정 없음 |
| `application-test.yml` | Hikari `maximum-pool-size: 4`, `minimum-idle: 1` |
| `scripts/experiments/ci-benchmark-compare.py` | 테스트 식별자는 `classname#name`(`name`은 `@DisplayName`) |
| `src/integrationTest/AGENTS.md`, test-policy | `@DisplayName`, 클래스 헤더 생성 시각·원본 시나리오, 보고서 템플릿 |

조사 결과(main `68f530fa`, 정적 분석):

- **컨텍스트 구성:** `integrationTest`에서 Spring을 쓰는 클래스는 110개다. `@DirtiesContext`를 모두 빼면 캐시 키는 44개다.
  - 여러 클래스가 공유하는 키: 10개.
    - A: `@ActiveProfiles("test")`만 있는 클래스 47개
    - B: MockMvc 6개
    - C: MockMvc + account-persistence 4개
    - F: flyway-migration 6개
    - PD: push 정책 properties 3개
    - local profile 2개, observability 2개, DirectionFlow127 Clock 2개, Inbox124 Clock 2개, LocalStack 2개
  - 클래스 하나만 쓰는 키: 34개. 클래스마다 고유한 `@Import`, 중첩 `@TestConfiguration`, `@TestPropertySource`,
    `@MockitoSpyBean` 등이 있어서다.
- **캐시 크기 시뮬레이션:** 실제 클래스 실행 순서(#347 CI 결과의 클래스 시작 시각)에 LRU를 적용했다. 컨텍스트 생성 수는
  캐시 32에서 44번, 16에서 46번, 8에서 52번, 4에서 62번이다.
- **상태가 남는 테스트 빈:** 대부분 한 클래스만 쓰는 컨텍스트 안에 있다(`MutableClock` 계열,
  `FaultInjectingOutboxEventRepository`, race hook, 빈으로 등록한 Mockito mock).
  - 여러 클래스가 공유하는 Inbox124·DirectionFlow127 Clock은 `@BeforeEach`에서 초기화한다.
  - 운영 코드에서 상태를 가진 것은 `FixedWindowRateLimiter`뿐이다. test profile 한도가 100000이라 영향이 없다.
  - 캐시, `@Async`, 이벤트 리스너는 없고 worker 스케줄링은 꺼져 있다.
  - `@MockitoSpyBean`은 Spring이 테스트마다 reset한다.
- **DB:**
  - public 테이블은 54개다.
  - 기준 데이터는 V1의 `direction_scheme`(OCTANT)·`direction_segment` 8행과 V29의 `region_code` 249행뿐이다.
  - DELETE를 막는 트리거는 `report_content_snapshot`·`report_case_event` 두 테이블에 있다.
  - TRUNCATE 트리거는 없다.
- **기준 데이터를 바꾸는 테스트:**
  - 8개 클래스가 `direction_scheme`·`direction_segment`를 비운다(OCTANT도 지운다).
  - 6개 클래스가 KR을 지우고 'Korea'로 다시 넣는다.
  - 여러 클래스가 하위 지역·테스트용 국가 행을 남긴다.
  - OCTANT가 있어야 하는 클래스(FlywayMigration, VerticalFlow, InboxDirectionChip 등)와 non-COUNTRY 0행을 단언하는
    CountrySeedMigration이 있다.
- **고정 이름으로 남는 스키마:** `flyway_failure`, `country_seed_upgrade`, `v2_backfill`, `v2_backfill_conflict`,
  `v8_backfill`, `v8_backfill_conflict`.
  - 스키마 조건 없이 카탈로그를 조회하는 클래스가 있다. FlywayMigration의 `pg_indexes` 조회 4곳, AnswerSafetyNotificationPersistence,
    AppealCase, OperatorActionAudit, ReportSuppression, DirectionPostgisPersistence, DirectionPostModeration, ProfileImage다.
    이 스키마가 남으면 조회 결과가 2행 이상이 된다.
- **정리 코드:** 대부분 `@BeforeEach`에서 자기 테이블만 전체 삭제한다. 마지막 테스트의 데이터는 남는다.
  - fixture 기반 클래스(Answer125, Inbox124, Feed170, Notification176, Push*)는 범위를 좁혀 지운다.
  - 이 중 Push*·NotificationFanOutExpansion은 due 행을 전부 claim하는 worker를 부른다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 캐시된 컨텍스트가 다시 쓰일 때 DB가 지워지고 다시 만들어져 있음(지금 `ClassDatabaseExtension`) | 스키마 없는 DB, 커넥션 강제 종료로 그룹의 두 번째 클래스부터 전부 실패 | 확실(그대로 두면) | P0 | D1 적용 후 전체 실행 실패 0 |
| 앞 클래스가 남긴 행 때문에 정리 `DELETE`가 FK에 막힘(#347 첫 시도 42개 클래스) | 그 클래스 전체 실패 | 높음(초기화 없으면) | P0 | 전체 실행·무작위 순서 실패 0 |
| 기준 데이터가 바뀐 채 남음(OCTANT 삭제, KR 이름, 하위 지역) | OCTANT·non-COUNTRY 0행 단언 실패, 칩 조회 결과 변화 | 높음(복원 없으면) | P0 | FlywayMigration·CountrySeedMigration·InboxDirectionChip 통과 |
| 고정 이름 별도 스키마가 남음 | 스키마 조건 없는 카탈로그 조회가 2행 이상 | 높음(삭제 없으면) | P0 | FlywayMigration·ProfileImage·AppealCase 등 통과 |
| 첫 컨텍스트가 뜨기 전에 초기화가 돌아 스키마가 없음 | 첫 클래스 실패, 기준 데이터 스냅샷이 비어 있음 | 중간 | P0 | 초기화 전에 컨텍스트를 먼저 로드하는 순서(7절), 무작위 순서 통과 |
| identity 시퀀스가 이어짐 | 생성 id를 가정한 테스트 실패(예: `DeviceRegistrationTransaction` `findById(1L)`) | 중간 | P1 | `RESTART IDENTITY` 후 해당 클래스 통과 |
| 기준 데이터 복원 뒤 identity 시퀀스가 1로 돌아감 | 다음 `direction_scheme` INSERT의 PK 충돌 | 중간 | P1 | 복원 후 `setval`, DirectionMatching* 통과 |
| TRUNCATE가 다른 캐시 컨텍스트의 열린 트랜잭션에 막혀 멈춤 | 실행이 끝나지 않음 | 낮음 | P1 | `lock_timeout`을 걸어 멈추지 않고 실패로 드러남 |
| 캐시된 컨텍스트 32개가 heap을 채움 | OOM, GC 멈춤 증가로 이득 상쇄 | 중간~높음(지금도 512MB 상한에 닿음) | P0 | 로컬 GC 로그의 heap 최고치·Full GC 직후 값·멈춤 합계, 필요하면 D3 |
| 캐시 컨텍스트마다 커넥션이 남아 `max_connections`(기본 100)에 닿음 | 뒤 클래스 연결 실패 | 낮음~중간(32개 × 최소 1, 최대 4) | P1 | 전체 실행에 연결 오류 없음, 필요하면 D3 |
| worker가 다른 클래스 행을 claim(Push*, NotificationFanOutExpansion) | 처리 건수 단언 실패 | 낮음(D1 후 클래스 시작 시 비어 있음) | P1 | 해당 클래스 통과 |
| 테스트용 트리거·함수가 테스트 실패로 남음 | 뒤 클래스 INSERT 실패 | 낮음(모두 `finally`·`@AfterEach`에서 지움) | P2 | 보고서에 잔여 위험으로 기록 |
| EXPLAIN 단언이 통계에 따라 바뀜(NotificationInboxQuery, PushDispatchGrouping, PushDeliveryLease) | 실행 계획 단언 실패 | 낮음 | P2 | 해당 클래스 통과 |
| 공유된 rate limiter 창에 요청이 쌓임 | 429 | 낮음(test 한도 100000) | P2 | Auth 계열 통과 |
| `performanceTest` 클래스도 base를 상속해 같은 변경을 받음 | 성능 테스트 실패(CI에서는 안 돎) | 낮음 | P2 | 로컬 `performanceTest` 1회 실패 0 |
| 클래스 순서에 따라 결과가 바뀜 | CI Benchmark에서 처음 실패해 측정을 다시 함 | 중간 | P0 | 무작위 순서 seed 2개 실패 0 |

## 5. Unit scenarios

해당 없음. 운영 코드를 바꾸지 않고, 바뀌는 것은 통합 테스트 지원 클래스와 통합 테스트 선언뿐이다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-001 | 컨텍스트 공유 + 클래스 시작 전 DB 초기화 + 전체 통합 테스트 | D1·D2 적용 | `./gradlew integrationTest` | 실패 0. `missCount` 최종값 44 안팎(구성 분석과 다르면 원인 기록), Flyway `Successfully applied` 14번 안팎(첫 컨텍스트 1 + 마이그레이션 테스트 자체 실행 13), PostGIS·LocalStack 기동 각 1번 | 클래스 시작 전 초기화 |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-002 | 테스트 목록 비교 | 로컬 main(`68f530fa`) `integrationTest` 결과와 이 브랜치 결과 | `classname#name` 목록, 실행·건너뜀·실패 수 비교 | 목록·건너뜀 수 동일, 실패 0 | 없음 |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-003 | 무작위 클래스 순서 | 저장소 밖 init script(`random-order.init.gradle`, #347과 같은 것)로 `ClassOrderer$Random`과 seed 설정 | seed 2개로 각각 `./gradlew integrationTest` | 두 실행 모두 실패 0. 첫 클래스가 A 그룹이 아닌 순서도 하나 포함(첫 컨텍스트가 F·L 그룹이어도 초기화·스냅샷이 맞는지) | init script는 커밋하지 않음 |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-004 | heap·연결 | INT-001 실행의 GC 로그와 결과 XML | heap 최고치, Full GC 횟수·직후 값, 멈춤 합계, `OutOfMemoryError`·`too many clients` 검색 | OOM과 연결 오류가 없음. 멈춤 합계가 main 대비 크게 늘면(로컬 기준 2배 이상) D3 판단 자료로 보고 | 없음 |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-005 | `performanceTest` | D1·D2 적용 | `./gradlew performanceTest` 1회 | 실패 0(main에서 실패하던 것이 있으면 main 결과와 같음) | 없음 |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-006 | CI Benchmark | push 후, 사용자 승인 | `base_ref`=main SHA, `head_ref`=브랜치 SHA, task=`check`, 반복 10 | job 20개 성공, `integrationTest` 중앙값 차이·95% 구간·단측 p, 두 설정의 테스트 목록 동일, 실패 0 | 없음 |

INT-001의 초기화가 맞는지는 기존 단언으로 확인한다. 테스트를 새로 더하지 않는다.

| 확인할 것 | 그것을 단언하는 기존 테스트 |
| --- | --- |
| 기준 데이터 복원 | FlywayMigration(OCTANT 1행·segment 8행), CountrySeedMigration(COUNTRY 249행, non-COUNTRY 0행, KR '대한민국') |
| 별도 스키마 삭제 | FlywayMigration의 `pg_indexes` 조회, ProfileImage·AppealCase·OperatorActionAudit의 카탈로그 개수 |
| identity 초기화 | DeviceRegistrationTransaction |

이 클래스들은 알파벳순에서 기준 데이터를 바꾸는 클래스들보다 뒤에 돈다. 무작위 순서에서는 앞뒤가 섞인다.

INT-006에서 뽑을 내부 지표는 #347과 같다(`metrics.py`). 컨테이너 기동 횟수, Flyway 횟수·시간, missCount, heap 최고치,
Full GC 직후 값, 멈춤 합계다. 기대값은 missCount 110 → 44, Flyway 123 → 14 안팎, 컨테이너 1번 그대로다.

## 7. Cross-cutting scenarios

### Database and transactions

클래스 시작 전 초기화(D1)의 동작:

1. base의 JUnit `BeforeAllCallback`은 `SpringExtension.getApplicationContext(context)`로 그 클래스의 컨텍스트를 먼저 얻는다.
   캐시에 없으면 이때 컨텍스트가 뜨고, 처음 뜨는 컨텍스트가 빈 DB에 Flyway를 적용한다. 그래서 초기화는 항상 스키마가 있는
   DB에서 돈다. 마이그레이션 테스트의 static `@BeforeAll`보다 먼저 돈다.
2. 처음 한 번은 아직 어떤 테스트도 돌기 전이다. 이때 기준 데이터 스냅샷을 JVM 메모리에 저장한다(`direction_scheme`,
   `direction_segment`, `region_code`의 모든 행). public 밖 스키마 이름 목록도 함께 저장한다. 스냅샷은 DB에 테이블로 남기지
   않는다. 남기면 스키마 조건 없는 카탈로그 조회에 걸린다.
3. 매 클래스 시작 전에 다음을 한다.
   - `flyway_schema_history`·`spatial_ref_sys`를 뺀 public 테이블 전체를 `TRUNCATE ... RESTART IDENTITY CASCADE` 한 문장으로
     비운다. 트리거로 DELETE가 막힌 증거 테이블도 TRUNCATE는 통과한다.
   - 기준 데이터를 스냅샷 값 그대로(원래 id 포함) 다시 넣고, identity 시퀀스를 최대 id로 맞춘다.
   - 스냅샷에 없는 public 밖 스키마를 `DROP SCHEMA ... CASCADE`로 지운다.
   - 이 SQL은 `lock_timeout`을 걸고 컨테이너에 직접 JDBC로 연결해 실행한다.
   - 결과는 Flyway를 막 적용한 DB와 같은 상태다. #347까지의 "클래스마다 빈 DB" 전제가 그대로 유지되므로 테스트 클래스의 정리
     코드는 바꾸지 않는다.
4. 맨 처음 컨테이너의 기본 DB를 그대로 쓴다. 클래스마다 DB를 지우고 다시 만드는 `DROP DATABASE`/`CREATE DATABASE`는 없앤다.

- `flyway_schema_history`는 지우지 않는다. 두 번째 컨텍스트부터 Flyway는 이력을 검증만 한다.
- 테스트용 트리거·함수(6개 클래스)와 `gh123_transient_once_seq`는 각 테스트가 지운다. 초기화는 public의 테이블 밖 객체를
  건드리지 않는다.

### Concurrency and idempotency

- 클래스끼리 동시에 돌지 않는다(fork 1개, 병렬 설정 없음). 초기화는 직전 클래스가 끝난 뒤 돈다.
- 캐시된 다른 컨텍스트의 풀은 열린 채 idle 상태다. TRUNCATE의 `ACCESS EXCLUSIVE` 잠금은 idle 연결에 막히지 않는다. 트랜잭션이
  남은 연결이 있으면 `lock_timeout`으로 실패가 드러난다.
- 동시성 테스트의 advisory lock 대기 조회(`pg_locks`, key 조건 없음)는 순차 실행이라 다른 클래스와 겹치지 않는다.

### External APIs

- LocalStack은 #347 그대로다. 객체 key가 UUID라 정리하지 않는다.
- FCM은 test profile의 상태 없는 fake(`NoOpPushProvider`)다.

### Failure recovery and reconciliation

- 초기화가 실패하면 그 클래스 전체가 실패로 드러나고 다음 클래스에서 다시 시도한다.
- 클래스 중간에 실패해 남은 데이터는 다음 클래스 시작 전에 지워진다.
- 이 변경을 revert하면 #347 상태(컨테이너 하나, 클래스마다 새 DB, 클래스마다 새 컨텍스트)로 돌아간다. 데이터·스키마 정리는
  필요 없다.

## 8. Test data and isolation

- Fixtures: 각 클래스의 기존 fixture를 그대로 쓴다.
- Database isolation:
  - JVM당 컨테이너 하나, DB 하나.
  - 클래스 사이 격리는 base의 클래스 시작 전 초기화가 맡는다.
  - 클래스 안 테스트 사이 격리는 지금처럼 각 클래스의 정리 코드가 맡는다.
- Clock/randomness: 기존 고정·가변 Clock을 그대로 쓴다. 공유 컨텍스트의 가변 Clock은 `@BeforeEach`에서 초기화된다. INT-003의
  seed는 보고서에 기록한다.
- External API doubles: LocalStack 하나, FCM fake.
- Cleanup: 테스트 클래스 정리 코드는 바꾸지 않는다. D1 뒤에도 깨지는 클래스가 있으면 그 클래스 안에서만 최소로 고치고 보고서에
  기록한다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 실행 에이전트(이 세션) | 로컬 main 기준 결과(커밋하지 않음) | INT-002 기준, INT-004 기준 | main에서 `./gradlew integrationTest --rerun` |
| 2 | 실행 에이전트 | `PostgisContainerIntegrationTestSupport.java` | INT-001 | `@DirtiesContext`를 빼고 초기화로 바꾼 뒤 전체 실행 |
| 3 | 실행 에이전트 | `@DirtiesContext`를 다시 선언한 11개 클래스(선언 한 줄과 import만) | INT-001 | 전체 실행 |
| 4 | 실행 에이전트 | 저장소 밖 init script | INT-003 | seed 2개 전체 실행, 실패 시 2로 돌아감 |
| 5 | 실행 에이전트 | 없음 | INT-004, INT-005 | GC 로그 분석, `./gradlew performanceTest` |
| 6 | 실행 에이전트 | `docs/reports/tests/` 보고서 | INT-002 | `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |
| 7 | 실행 에이전트(사용자 승인 후) | 없음 | INT-006 | push, CI Benchmark dispatch, 비교 스크립트, 내부 지표 추출 |

- 메인 클론에서 Gradle을 돌리기 전에 다른 Gradle 실행이 없는지 확인한다.
- 3번에서 손대는 클래스는 spotless ratchet과 staged checkstyle이 파일 전체를 검사한다. 기존 형식·크기 위반이 드러나면
  suppression이나 훅 우회 없이 형식만 바꾼 커밋을 따로 두거나 사용자에게 묻는다.

## 10. Completion criteria

- [ ] INT-001~005 통과
- [ ] 수정한 테스트 클래스의 `@DisplayName` 유지, 헤더에 원본 시나리오 줄 추가
- [ ] 테스트를 지우거나 `@Disabled`로 건너뛰지 않음
- [ ] `./harness check`, `./harness pr-ready --project-tests` 통과
- [ ] INT-006 비교 출력과 내부 지표(base/head)를 보고서와 PR에 기록
- [ ] 깨진 테스트 목록, 대안과 선택 이유, 잠재 문제 분석을 담은 테스트 보고서 생성

## 11. Human approval

- Reviewer: 사용자(tkv00)
- Decision: 승인. 아래 D1~D4를 권장안대로 승인했다.
- Approved at: `2026-10-10T15:18:37+09:00`

승인받을 결정:

- **D1 데이터 정리 방식: 클래스 시작 전 base에서 전체 초기화(권장).**
  - 동작은 7절과 같다. 전체 TRUNCATE + 기준 데이터를 스냅샷 값으로 복원 + 남은 별도 스키마 삭제다.
  - 클래스마다 Flyway를 막 적용한 DB에서 시작한다는 #347까지의 전제를 유지해, 테스트 클래스 코드는 바꾸지 않는다.

  | 방안 | 장점 | 단점 |
  | --- | --- | --- |
  | 클래스 시작 전 전체 초기화(권장) | base 1개만 바뀐다. 기존 정리 코드·단언이 그대로 맞는다. 클래스 중간 실패의 잔여물도 다음 클래스 전에 지워진다. 테이블이 늘어도 고칠 곳이 없다 | base에 초기화 코드와 기준 데이터 스냅샷이 는다. 클래스마다 TRUNCATE 비용(54개 테이블, 110번)이 든다 |
  | 클래스가 끝난 뒤 초기화 | 위와 비슷하다 | 첫 클래스 전에는 돌지 않는다. 스냅샷을 오염 전에 잡기 어렵다. 실패가 끝난 클래스가 아니라 다음 클래스에 드러난다 |
  | 테스트마다 초기화(`@BeforeEach`) | 격리가 가장 강하다 | 852번 초기화해 비용이 크다. 마이그레이션 테스트가 `@BeforeAll`에서 만든 스키마가 테스트 사이에 지워진다. 클래스 단위로 데이터를 준비하는 테스트가 깨진다 |
  | 깨진 클래스마다 정리 보강(#347의 B) | base가 단순하다 | 약 50개 파일을 고친다. 각 클래스가 자기 밖 FK 자식까지 알아야 하고, 테이블이 늘면 다시 깨진다. 기준 데이터·스키마 잔여물은 따로 처리해야 한다 |
  | 컨텍스트 키마다 DB 따로 | 설정이 다른 컨텍스트끼리는 격리된다 | 같은 키를 공유하는 클래스끼리는 여전히 정리 방식이 필요하다. 키마다 DB 이름을 정하는 장치가 필요해 복잡하다 |

- **D2 다시 선언된 `@DirtiesContext` 11곳도 모두 뺀다(권장).**
  - 대상: DirectionMatching*, DirectionPost*, ReceiveStateReservation 등. DirectionMatchingPerformance는 `performanceTest`
    전용이다.
  - 모두 base와 같은 `AFTER_CLASS` 중복 선언이다. 남기면 A 그룹 컨텍스트가 그 클래스마다 닫혀 다시 뜬다.
  - D1 뒤에는 남길 이유가 없다(이 클래스들이 지우는 OCTANT 등은 초기화가 복원한다).
- **D3 캐시 크기는 기본 32로 먼저 잰다(권장).**
  - INT-004에서 OOM, 연결 오류, 또는 GC 멈춤이 main 대비 크게 늘면 그때 `integrationTest` task에
    `spring.test.context.cache.maxSize`를 16으로 넣는다. `build.gradle` 한 줄이며, 시뮬레이션상 컨텍스트 생성은 44번 → 46번이다.
  - 넣기 전에 결과를 보여주고 다시 묻는다. heap 상한은 바꾸지 않는다.
- **D4 무작위 순서 실행은 #347과 같이 저장소 밖 init script로 한다.**
