# Test Report: TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT

> Created at: `2026-10-10T16:05:56+09:00`
> GitHub Issue: `#350`
> Branch: `test/gh-350-share-spring-context`
> Commit: `68f530fa`(최종 로컬 실행은 커밋 전 작업 트리에서 했다)

## 1. Executive summary

- Result: `PARTIAL`. 로컬 시나리오 INT-001~005는 통과했다. CI Benchmark(INT-006)는 아직 실행하지 않았다.
- Tested scope:
  - `@DirtiesContext`를 모두 빼서 설정이 같은 클래스끼리 Spring 컨텍스트를 공유하게 했다.
  - 클래스 시작 전마다 DB를 Flyway를 막 적용한 상태로 되돌리는 초기화를 넣었다.
  - 통합 테스트 전체를 기본 순서와 무작위 클래스 순서(seed 2개)로 실행했고, `performanceTest`도 1회 실행했다.
- Unverified scope:
  - CI Benchmark 측정.
  - 캐시 크기를 32보다 줄였을 때의 효과(D3 조건에 해당하지 않아 바꾸지 않았다).
- Release recommendation: CI Benchmark에서 두 설정의 테스트 목록이 같고 실패가 0이면 병합 가능.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain Java 21(Temurin) |
| Spring Boot | 3.5.16(Spring Framework 6.2) |
| Database | 로컬 Docker의 Testcontainers `postgis/postgis:16-3.5-alpine`(linux/amd64) 1개, DB 1개 |
| External double | Testcontainers `localstack/localstack:3.8`(S3) 1개 |
| Test runner | JUnit 5, Gradle 8.14.3, `integrationTest` fork 1개, heap 기본 상한 512MB |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Unit(`./harness test-run`의 `test`) | PASS(up-to-date) | 1,381건, 실패 0 | 1초 | 운영·단위 테스트 코드가 바뀌지 않아 Gradle이 같은 입력의 직전 결과를 썼다 |
| Integration, main 코드(기준, `--rerun`) | PASS | 852건, 실패 0 | 706초 | 로컬 결과 XML(저장소 밖) |
| Integration, 첫 구현 | FAIL | 852건 중 16건 실패(2개 클래스) | 197초 | 5.1절 |
| Integration, 커넥션 교체 추가 후 기본 순서·seed 350·seed 20261010 | PASS | 852건, 실패 0 | 172·177·192초 | 로컬 결과 XML |
| `performanceTest`, 위 코드 | FAIL | 17건 중 1건 실패 | 305초 | 5.2절 |
| `performanceTest`, main 코드(별도 worktree) | PASS | 17건, 실패 0 | 325초 | 로컬 결과 XML |
| Integration, 최종(통계 삭제 추가) `./harness test-run` 기본 순서 | PASS | 852건, 실패 0 | 223초 | 로컬 결과 XML |
| Integration, 최종 무작위 순서 seed 350 | PASS | 852건, 실패 0 | 190초 | 로컬 결과 XML |
| Integration, 최종 무작위 순서 seed 20261010 | PASS | 852건, 실패 0 | 166초 | 로컬 결과 XML |
| `performanceTest`, 최종 | PASS | 17건, 실패 0 | 212초 | 로컬 결과 XML |

- 시간은 Gradle 명령의 벽시계 시간이고 같은 Mac에서 1회씩 쟀다.
- main 기준 실행 때 Mac의 load average가 10~15로 높았다. #347 때 같은 코드의 로컬 시간은 241~288초였다. 그래서 로컬 시간은 비교
  근거로 쓰지 않고, 판정은 CI Benchmark로 한다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-001 | PASS | 통합 테스트 전체 | 아래 표 |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-002 | PASS | main 결과와 비교 | `classname#name` 목록 852개 동일, 건너뜀 0 동일 |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-003 | PASS | 무작위 클래스 순서 seed 350, 20261010 | 첫 클래스가 각각 `InboxDetailScope`(A 그룹), `OperatorLogin`(C 그룹)이다. C 그룹 컨텍스트가 먼저 떠도 Flyway 적용과 스냅샷이 맞았다. missCount는 44·45(LRU 축출 후 재생성 1번) |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-004 | PASS | GC 로그, 결과 XML | OOM·연결 오류 없음. GC 멈춤 합계는 main 13.8초 → 4.3초. 다만 Full GC 직후 남은 heap이 크게 늘었다(6절) |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-005 | PASS | `performanceTest` 5개 클래스 | 첫 실행에서 1건 실패, 초기화 보완 후 통과(5.2절) |
| TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT-INT-006 | 미실행 | CI Benchmark | push와 측정 전 사용자 승인 필요 |

INT-001 내부 지표(최종 기본 순서, 결과 XML과 GC 로그에서 추출):

| 지표 | main | 이 브랜치 |
| --- | --- | --- |
| 컨텍스트 캐시 missCount | 110 | 44 |
| Flyway `Successfully applied` 횟수(합계 시간) | 123번(162.2초) | 14번(6.1초) |
| PostGIS·LocalStack 기동 | 1번·1번 | 1번·1번 |
| heap 최고치 | 510MB | 512MB |
| Full GC 직후 | 없음 | 302~391MB(2번) |
| GC 멈춤 합계 | 13.8초 | 4.3초 |

- Flyway 14번은 첫 컨텍스트 1번과 마이그레이션 테스트가 자기 스키마에 직접 실행한 13번이다. 나머지 43개 컨텍스트의 Flyway는
  이력을 검증만 했다.
- missCount 44는 정적 분석으로 센 캐시 키 수와 같다.

초기화가 맞는지는 기존 단언으로 확인했다. 아래 테스트가 기준 데이터를 바꾸는 클래스 뒤에 돌아도 통과했다.

| 확인한 것 | 단언한 기존 테스트 |
| --- | --- |
| 기준 데이터 복원 | FlywayMigration(OCTANT 1행·segment 8행), CountrySeedMigration(COUNTRY 249행, non-COUNTRY 0행, KR '대한민국') |
| 별도 스키마 삭제 | FlywayMigration의 `pg_indexes` 조회, ProfileImage·AppealCase·OperatorActionAudit의 카탈로그 개수 |
| identity 초기화 | DeviceRegistrationTransaction |

## 5. Failures and diagnostics

### 5.1 커넥션에 남은 `search_path`(첫 구현, 2개 클래스 16건)

| 클래스(실패/전체) | 증상 |
| --- | --- |
| FlywayMigration(11/12) | `relation "direction_scheme" does not exist` 등. public 테이블을 찾지 못함 |
| PushDispatchGroupMigration(5/5) | `relation "region_code" does not exist`, `relation "push_dispatch_group" does not exist` |

- 원인:
  - 두 클래스 모두 flyway-migration 그룹(6개 클래스가 한 컨텍스트를 공유)이다.
  - 같은 그룹의 `CountrySeedMigration`(실행 순서상 앞)과 `SchemaRevisionMigration`은 풀에서 빌린 커넥션에
    `SET search_path TO <별도 스키마>`를 하고 그대로 돌려준다. 세션 설정이라 커넥션이 풀에 돌아가도 남는다.
  - 예전에는 클래스가 끝나면 컨텍스트와 풀이 닫혀서 드러나지 않았다. 컨텍스트를 공유하자 그 커넥션을 같은 그룹의 다음 클래스가
    받았다. 초기화는 그 별도 스키마를 지우므로, search_path가 존재하지 않는 스키마만 가리키게 되어 public 테이블 이름을 찾지
    못했다.
- 조치: 테스트 클래스는 고치지 않았다. base의 초기화가 클래스 시작 전에 그 클래스 컨텍스트의 Hikari 풀에
  `softEvictConnections()`를 호출해, 클래스마다 새 커넥션을 받게 했다. 세션 상태(search_path, 세션 설정, 지운 스키마를 가리키는
  준비된 문장)가 클래스 사이로 이어지지 않는다.

### 5.2 TRUNCATE 뒤에 남은 열 통계(`performanceTest` 1건)

- 증상: `DirectionMatchingPerformanceIntegrationTest` PERF-001에서 "preview candidate count query: active_user_presence에
  Seq Scan이 없어야 한다" 단언이 실패했다. 이 클래스만 돌리면 통과하고, `DirectionMatchingIndexPlanPerformanceIntegrationTest`
  뒤에 돌리면 재현된다. main에서는 5개 클래스가 모두 통과했다.
- 원인:
  - IndexPlan 클래스는 대량 데이터를 넣고 `ANALYZE user_account, active_user_presence`를 한다.
  - 초기화의 TRUNCATE는 행은 지워도 `pg_statistic`의 열 통계는 남긴다.
  - 다음 클래스는 데이터를 새로 넣고 `active_user_presence`만 `ANALYZE`한다. 그래서 `user_account`는 지워진 데이터의 통계로
    계획이 세워졌다. 막 마이그레이션한 DB에는 통계가 없다.
- 조치:
  - 초기화에서 TRUNCATE 직후 public 테이블(유지하는 두 테이블 제외)의 `pg_statistic` 행을 지운다. 컨테이너 계정은 superuser다.
  - 재현 조합(IndexPlan + Performance)과 `performanceTest` 전체, 통합 테스트 세 순서를 다시 돌려 모두 통과했다.
- 통합 테스트(`integrationTest`)에도 실행 계획 단언이 3곳 있다(NotificationInboxQuery, PushDispatchGrouping,
  PushDeliveryLease). 첫 구현에서 통과했지만 같은 원인으로 순서에 따라 깨질 수 있었다. 이 조치로 함께 막힌다.

### 5.3 최종 변경 범위

| 파일 | 변경 |
| --- | --- |
| `PostgisContainerIntegrationTestSupport` | `@DirtiesContext`와 클래스마다 DB를 다시 만드는 확장을 빼고, 클래스 시작 전 초기화 확장으로 바꿈 |
| `@DirtiesContext`를 다시 선언한 11개 클래스 | 선언과 import 제거, 헤더에 원본 시나리오 줄 추가 |
| `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest` | 위 변경에 더해, 원래 포매터 형식이 아니어서 손대면 spotless ratchet이 파일 전체를 다시 정렬함(형식 변경만, 커밋 분리) |

초기화 확장(`SharedDatabaseResetExtension`)이 클래스 시작 전에 하는 일:

1. `SpringExtension.getApplicationContext()`로 컨텍스트를 먼저 얻는다. 처음 뜨는 컨텍스트의 Flyway가 스키마를 만든 뒤에
   초기화가 돈다.
2. 그 컨텍스트의 Hikari 풀 커넥션을 새로 받게 한다(5.1).
3. 처음 한 번은 기준 데이터(`region_code`, `direction_scheme`, `direction_segment`)를 JSON으로 JVM 메모리에 스냅샷하고, 스키마
   이름 목록을 저장한다. 첫 클래스의 테스트가 돌기 전이라 Flyway를 막 적용한 상태다.
4. 이후 클래스마다 다음을 한 트랜잭션(`lock_timeout` 10초)으로 실행한다.
   - `flyway_schema_history`·`spatial_ref_sys`를 뺀 public 테이블 전체 `TRUNCATE ... RESTART IDENTITY CASCADE`
   - 열 통계 삭제(5.2)
   - 스냅샷에 없는 스키마 `DROP SCHEMA ... CASCADE`(`pg_` 계열 제외)
   - 기준 데이터를 원래 id로 다시 넣고 identity 시퀀스를 최대 id 뒤로 옮긴다

## 6. Potential issues

### Application code

- 운영 코드는 바꾸지 않았다. 운영 코드에서 상태를 가진 빈은 `FixedWindowRateLimiter`뿐이다. test profile 한도가 100000이라 공유
  컨텍스트에서 요청이 쌓여도 영향이 없다. 한도를 낮춘 `AuthRateLimit`·`NicknameChangeLimit`은 각자 단독 컨텍스트다.

### Infrastructure and resource limits

- heap:
  - 캐시된 컨텍스트 수만큼 남는 heap이 늘었다. 캐시가 32개에 차서 축출이 시작된 뒤에는 400~470MB에서 머문다.
  - Full GC 직후 남은 heap은 main 약 80MB → 이 브랜치 300~400MB이고, 상한 512MB까지 여유가 약 110MB다.
  - 테스트 클래스나 고유 설정이 늘면 OOM에 가까워질 수 있다. 그때는 `spring.test.context.cache.maxSize`를 줄인다(16에서
    컨텍스트 생성 46번 예상, 시뮬레이션). heap 상한은 이번 이슈 범위 밖이다.
- DB 연결:
  - 캐시된 컨텍스트마다 Hikari 풀이 열려 있다(최소 1, 최대 4).
  - 이번 실행에서 연결 오류는 없었다. 캐시 32개 × 최대 4는 PostgreSQL 기본 `max_connections` 100을 넘을 수 있으므로, 동시성
    테스트를 쓰는 고유 설정이 늘면 확인이 필요하다.

### Database and migrations

- 컨텍스트가 공유되면서 Flyway는 JVM에서 한 번만 public에 적용된다. FlywayMigration의 "빈 PostGIS 데이터베이스의 startup에서
  V1부터 최신 버전까지 migration을 적용한다"는 이제 첫 컨텍스트의 적용 결과(이력 1..33)를 확인한다. 이 테스트의 단언은 원래
  적용 시점을 보지 않았다(표시 이름 유지).
- 초기화는 public의 테이블 밖 객체(테스트용 트리거·함수·시퀀스)를 지우지 않는다. 각 테스트가 `finally`·`@AfterEach`에서
  지운다. 테스트가 그 정리 전에 중단되면 다음 클래스에 남을 수 있다.
- `pg_statistic` 직접 삭제는 superuser 권한에 기대는 테스트 전용 처리다. 컨테이너 계정이 superuser가 아니게 바뀌면 초기화가
  실패로 드러난다.

### Concurrency and idempotency

- 클래스끼리 동시에 돌지 않는다(fork 1개). 나중에 fork 병렬 실행을 켜면 fork마다 컨테이너·DB가 따로여야 한다. 지금 초기화는
  JVM 안에서만 순서를 보장한다.

### Transactions and event ordering

- 캐시된 다른 컨텍스트에 트랜잭션이 남아 TRUNCATE가 기다리면 10초 뒤 실패로 드러난다. 이번 실행에서는 없었다.

### External APIs

- LocalStack은 #347 그대로다. 객체 key가 UUID라 정리하지 않는다. FCM은 상태 없는 fake다.

### Failure recovery and reconciliation

- 초기화가 실패하면 그 클래스가 실패로 드러나고 다음 클래스에서 다시 시도한다.
- 클래스 중간 실패로 남은 데이터는 다음 클래스 시작 전에 지워진다.
- revert하면 #347 상태(클래스마다 새 컨텍스트와 새 DB)로 돌아가며 데이터 정리가 필요 없다.

## 7. Regression and residual risk

- 새 통합 테스트 클래스를 더할 때 고유한 `@Import`·중첩 설정·`@MockitoSpyBean`을 쓰면 캐시 키가 늘어 heap·연결 여유가 준다.
  가능하면 기존 설정을 재사용한다.
- 풀 커넥션에 세션 설정을 남기는 테스트는 같은 클래스 안에서는 여전히 다음 테스트에 영향을 줄 수 있다. 이 변경 전과 같다.
- 테스트 정리 코드는 여전히 "클래스 시작 시 빈 DB"를 전제한다. base 초기화를 우회하는 클래스(base를 상속하지 않는 DB 테스트)를
  더하면 이 전제가 깨진다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-350-TEST-PLAN-GH-350-SHARE-SPRING-CONTEXT.md`
- CI run: 미실행(INT-006)
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(INT-006)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨(heap 여유는 측정 후 판단)
- [ ] 실행 결과와 PR 설명이 일치함
