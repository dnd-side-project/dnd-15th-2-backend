# Test Plan: TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS

> Created at: `2026-10-10T04:31:20+09:00`
> GitHub Issue: `#347`
> Status: Approved — 사람 승인 완료(2026-10-10 방식 변경 재승인, 12절)

## 1. Objective

통합 테스트의 PostGIS·LocalStack 컨테이너를 JVM당 한 번만 띄우고, PostGIS 안에서 테스트 클래스마다 새 데이터베이스를
만들어도 모든 통합 테스트가 같은 결과를 내는지 검증한다. 줄어든 시간은 CI Benchmark로 잰다.
실패하면 앞 클래스가 남긴 데이터 때문에 뒤 클래스가 깨지거나, 클래스 실행 순서에 따라 로컬에서는 통과하고 CI에서는
깨지는 테스트가 생긴다. 측정 1회는 job 20개라서 CI에서 처음 깨지면 측정을 다시 해야 한다.

## 2. Scope

### Included

- `PostgisContainerIntegrationTestSupport`: `@Testcontainers`·`@Container`를 빼고 static 블록에서 한 번만 start한다.
  JUnit `BeforeAllCallback`으로 클래스마다 `template_postgis`에서 새 DB를 만들고 직전 클래스의 DB를 지운다.
  `@ServiceConnection` 대신 `@DynamicPropertySource`로 그 DB의 접속 정보를 넘긴다. `@DirtiesContext(AFTER_CLASS)`와
  `pg_stat_statements` 옵션 분기는 그대로 둔다.
- `LocalStackContainerIntegrationTestSupport`: LocalStack도 static 블록에서 한 번만 띄운다. `@DynamicPropertySource`와
  `@BeforeAll` 버킷 생성은 그대로 둔다.
- 별도 스키마에 Flyway를 실행하는 마이그레이션 테스트 5개: Flyway 접속 URL을 클래스 DB로 바꾼다.
- `QelloLocalProfileIntegrationTest`: 연결된 DB 이름 기대값을 컨테이너 기본 DB `qello_test`에서 클래스 DB 이름으로 바꾼다.
  local 프로필 설정 대신 테스트 컨테이너에 연결되는지 보는 의도는 그대로다.
- 한 DB를 공유한 첫 시도의 실패 기록(클래스, 증상, 원인)과 방식 변경 기록(12절).
- 클래스 실행 순서를 무작위로 바꾼 로컬 실행.
- CI Benchmark 측정과 내부 지표 추출.

### Excluded

- `@DirtiesContext` 제거, 컨텍스트 재사용(다음 단계).
- 모든 클래스가 공통으로 쓰는 데이터 정리 장치(base 클래스의 전체 TRUNCATE 등). 정리 시점은 다음 단계에서 정한다.
- fork 병렬 실행, 샤딩, `performanceTest` task.
- 운영 코드와 Flyway 마이그레이션 변경.
- 새 기능 테스트 추가.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #347 | 컨테이너 기동 1번, 테스트 이름 목록·건너뜀 수 동일, 실패 0, 무작위 순서 seed 2개 실패 0, CI Benchmark 비교 출력 |
| CI Benchmark run 37884006551 (main `4c7bcef`, 같은 커밋 20회) | `integrationTest` 중앙값 688초, 표준편차 81초. 컨테이너 기동 106번 355초, 컨텍스트 기동 106번 190초, Flyway 적용 119번 65초 |
| Testcontainers singleton container 패턴 | 추상 base 클래스의 static 블록에서 `start()`, 종료는 Ryuk가 JVM 종료 때 맡는다 |
| `build.gradle` `integrationTest` | `maxParallelForks`·`forkEvery` 미설정(JVM 1개), `performance` 태그 제외, 컨텍스트 캐시 DEBUG 로그·GC 로그 |
| `scripts/experiments/ci-benchmark-compare.py` | 테스트 식별자는 `classname#name`이고 `name`은 `@DisplayName`이다 |
| `src/integrationTest/AGENTS.md`, test-policy | `@DisplayName`, 클래스 헤더 생성 시각·원본 시나리오, 보고서 템플릿 |

조사 결과(main `994bc0a1`):
- Testcontainers를 쓰는 파일은 두 base 클래스뿐이다. `LocalStackContainerIntegrationTestSupport`는
  `PostgisContainerIntegrationTestSupport`를 상속하고, 이를 상속하는 클래스는 4개다(`ExifStrip`, `FeedMediaViewUrl`,
  `MediaAssetStorage`, `ProfileImage`). base를 상속하지 않는 통합 테스트는 `StructuredLoggingProfileIntegrationTest`
  하나로, 자식 프로세스를 띄우고 DB를 쓰지 않는다.
- 별도 Flyway를 실행하는 마이그레이션 테스트 4개(`CountrySeed`, `SchemaRevision`, `NotificationPreference`,
  `QuestionProposalDeleteMute`)와 `FlywayMigrationIntegrationTest`의 실패 시나리오는 모두 자기 스키마를 따로 만든다.
  `public` 스키마를 비우거나 되돌리는 테스트는 없다.
- `FlywayMigrationIntegrationTest`는 `public`의 테이블·인덱스·함수·트리거 이름 목록이 기대 목록과 정확히 같은지 본다.
  테스트용 트리거·함수를 만드는 6개 클래스(`DirectionMatchingContract`, `DirectionMatchingWorker`, `PushDeliveryDispatch`,
  `QuestionProposalApi`, `RecipientNotificationFanOutWorker`, `RecipientNotificationFanOutWorkerConcurrency`)는 `finally`나
  `@AfterEach`에서 지운다.
- 데이터 정리는 클래스 안이나 fixture(`Answer125IntegrationFixtures.reset()`, `Inbox124IntegrationFixtures.reset()`,
  `Notification176IntegrationFixtures` 등)에 있다. 정리가 없는 클래스는 DB에 쓰지 않는 클래스(actuator, CORS,
  observability, OpenAPI, 기동 확인)와 별도 스키마를 쓰는 마이그레이션 테스트다.
- 테스트 profile은 스케줄러를 켜지 않는다(`qello.worker.scheduling.enabled` 기본 false, 켜는 통합 테스트 없음).
  worker는 테스트가 `processBatch` 등으로 직접 부른다.
- LocalStack 객체 key는 `MediaStorageKeys.uploadKey(ownerId, UUID)`로 매번 다르다.
- Gradle은 테스트 클래스를 클래스 파일 디렉터리 순서로 찾는다. 이 순서는 macOS와 CI(Linux)에서 다를 수 있다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 앞 클래스가 남긴 자식 테이블 행 때문에 뒤 클래스의 `DELETE FROM 부모`가 FK 위반 | 그 클래스 전체 실패 | 높음(첫 시도에서 발생) | P0 | 클래스마다 새 DB에서 전체 실행 실패 0 |
| 클래스 DB가 Spring 컨텍스트나 각 클래스의 `@BeforeAll`보다 늦게 만들어짐 | 컨텍스트가 직전 클래스 DB나 없는 DB에 연결, 마이그레이션 테스트의 `@BeforeAll` Flyway 실패 | 낮음 | P0 | 마이그레이션 테스트 5개와 전체 실행 통과 |
| 직전 클래스 DB를 지울 때 남은 연결 | `DROP DATABASE` 실패로 다음 클래스 시작 불가 | 낮음(`WITH (FORCE)`) | P1 | 전체 실행 실패 0 |
| `template_postgis`가 기본 DB와 다른 확장 구성 | PostGIS 함수·Flyway V1 결과가 달라짐 | 낮음(이미지 초기화 스크립트가 두 DB에 같은 확장 설치) | P1 | `FlywayMigrationIntegrationTest` catalog 단언 통과 |
| `CREATE DATABASE` 비용 | 줄어든 시간 일부 상쇄 | 확실(크기 미측정) | P2 | 로컬 전후 시간, CI Benchmark |
| 테이블 전체를 세는 `count(*)`·전체 목록 단언이 남은 행을 셈 | 단언 실패 | 중간 | P0 | 전체 실행 실패 0 |
| worker가 다른 클래스가 남긴 PENDING outbox·job을 claim | 처리 건수 단언 실패, 예상 밖 상태 변경 | 중간 | P0 | worker 테스트 클래스 통과, 무작위 순서 통과 |
| 남은 고정 값(닉네임, provider subject 등)과 유니크 제약 충돌 | INSERT 실패 | 중간 | P1 | 전체 실행 실패 0 |
| 로컬 클래스 순서에서만 통과 | CI Benchmark에서 실패해 측정 재실행 | 중간 | P0 | 무작위 순서 seed 2개 실패 0 |
| 테스트용 트리거·함수가 테스트 실패로 남음 | 뒤 클래스 INSERT 실패, `FlywayMigrationIntegrationTest` catalog 단언 실패 | 낮음 | P1 | 해당 6개 클래스와 Flyway 테스트 통과 |
| `@ServiceConnection`이 `@Container` 없는 static 필드에서 연결 정보를 못 만듦 | 모든 컨텍스트 기동 실패 | 낮음 | P0 | 첫 클래스 통과 |
| LocalStack에 `@Testcontainers`가 없어져 컨테이너가 안 뜸 | LocalStack 4개 클래스 실패 | 낮음(같은 방식으로 바꿈) | P0 | 4개 클래스 통과, LocalStack 기동 로그 1번 |
| 컨테이너가 중간에 죽으면 뒤 클래스가 모두 실패 | 원인 파악이 어려워짐 | 낮음 | P2 | 보고서에 기록 |
| 컨텍스트가 닫힐 때 커넥션이 남아 `max_connections` 초과 | 뒤 클래스 연결 실패 | 낮음(풀 최대 4, 클래스마다 닫힘) | P2 | 전체 실행 실패 0 |

## 5. Unit scenarios

해당 없음. 운영 코드를 바꾸지 않고, 바뀌는 것은 통합 테스트 지원 클래스와 통합 테스트뿐이다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-001 | PostGIS singleton + `@ServiceConnection` + 전체 통합 테스트 | base 클래스 변경 | `./gradlew integrationTest` | 실패 0. 모든 결과 XML에서 `Container postgis/postgis:16-3.5-alpine started in`이 1번 | 각 클래스 기존 정리 |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-002 | LocalStack singleton + 4개 클래스 | LocalStack base 변경 | INT-001과 같은 실행 | 4개 클래스 통과, LocalStack 기동 로그 1번 | 객체 key가 매번 달라 정리 불필요 |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-003 | 테스트 목록 비교 | main(`994bc0a1`)의 로컬 `integrationTest` 결과와 이 브랜치 결과 | `classname#name` 목록, 실행·건너뜀·실패 수 비교 | 목록과 건너뜀 수 동일, 실패 0 | 없음 |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-004 | 무작위 클래스 순서 | 저장소 밖 Gradle init script로 `integrationTest`에만 `junit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer$Random`과 seed 설정 | seed 2개로 각각 `./gradlew integrationTest` | 두 실행 모두 실패 0, 결과 XML의 클래스 시작 순서가 기본 실행과 다름 | init script는 커밋하지 않음 |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-005 | 한 DB 공유 첫 시도 기록 | 컨테이너만 한 번 띄우고 DB를 공유한 변경 | `./gradlew integrationTest` 1회 | 실패한 클래스·증상·원인을 보고서와 트러블슈팅 기록에 남기고 방식을 바꾼다(12절) | 없음 |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-006 | CI Benchmark | 브랜치 push 후, 사용자 승인 | `base_ref`=main SHA, `head_ref`=브랜치 SHA, task=`check`, 반복 10 | job 20개 성공, 비교 스크립트의 `integrationTest` 중앙값 차이·95% 구간·단측 p, 두 설정의 테스트 목록 동일, 실패 0 | 없음 |

INT-001의 기동 횟수는 결과 XML의 `system-out`에서 센다. 컨테이너는 첫 클래스의 컨텍스트가 static 필드를 읽을 때 뜨므로
로그는 첫 클래스 결과에 남는다.

INT-003의 main 결과는 같은 Mac에서 main을 한 번 실행해 얻는다. 이 실행 시간은 로컬 전후 비교에도 쓴다.

INT-006에서 artifact로 뽑을 내부 지표: PostGIS 기동 횟수와 `started in` 합계(기대 106번 → 1번), Flyway
`Successfully applied` 횟수와 시간, 컨텍스트 캐시 `missCount`(이번 단계에서는 106 그대로가 정상), GC 로그의 heap 최대,
Full GC 직후 값, 멈춤 합계.

## 7. Cross-cutting scenarios

### Database and transactions

- 스키마는 첫 컨텍스트의 Flyway가 한 번 적용하고, 뒤 컨텍스트의 Flyway는 이력을 검증만 하고 적용하지 않는다.
  `flyway_schema_history`는 지우지 않는다.
- V19·V27의 `BEFORE UPDATE OR DELETE` 트리거가 걸린 `report_content_snapshot`·`report_case_event`는 DELETE 대신
  TRUNCATE로 지운다. V1의 트리거는 `approved_question.question_text`·`question_proposal.proposed_text`의 UPDATE만 막는다.
  수정할 때 이 제약을 지킨다.
- V1·V29가 넣는 기준 데이터(`direction_scheme`·`direction_segment`, `region_code`)를 지우는 수정은 하지 않는다. 지우면 뒤
  클래스가 깨진다.
- `pg_stat_statements` 확장을 만드는 클래스는 `performance` 태그라 `integrationTest`에서 돌지 않는다.

### Concurrency and idempotency

- 동시성 테스트는 지금처럼 클래스 안에서만 스레드를 띄운다. 클래스끼리 동시에 돌지 않는다(JVM 1개, 병렬 실행 설정 없음).
- 컨텍스트를 닫을 때 남은 비동기 작업이 다음 클래스의 데이터를 건드릴 수 있는지는 실패가 생기면 그때 원인으로 확인한다.

### External APIs

- LocalStack S3만 공유한다. 버킷 생성은 이미 있으면 건너뛰고, 객체 key는 UUID라 클래스끼리 겹치지 않는다.
- FCM 등 외부 공급자는 test profile의 fake를 그대로 쓴다.

### Failure recovery and reconciliation

- 컨테이너는 JVM이 끝날 때 Ryuk가 지운다. 로컬 실행을 중간에 끊으면 Ryuk가 정리한다. 로컬 `~/.testcontainers.properties`에
  Ryuk를 끄는 설정은 없다.
- 이 변경을 revert하면 클래스마다 컨테이너를 띄우는 원래 동작으로 돌아간다. 데이터·스키마 정리는 필요 없다.

## 8. Test data and isolation

- Fixtures: 각 클래스의 기존 fixture를 그대로 쓴다.
- Database isolation: JVM당 PostGIS 컨테이너 하나. 클래스 사이 데이터 격리는 각 클래스의 정리 코드에 맡긴다.
- Clock/randomness: 기존 고정 `Clock`을 그대로 쓴다. INT-004의 seed 값은 보고서에 기록한다.
- External API doubles: LocalStack 하나, FCM fake.
- Cleanup: 깨진 클래스만 최소로 고친다(결정 D1).

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 실행 에이전트(이 세션) | 로컬 main 기준 결과(커밋하지 않음) | INT-003 기준 | main에서 `./gradlew integrationTest` |
| 2 | 실행 에이전트 | `PostgisContainerIntegrationTestSupport.java`, `LocalStackContainerIntegrationTestSupport.java` | INT-001, INT-002 | `./gradlew integrationTest` |
| 3 | 실행 에이전트 | `PostgisContainerIntegrationTestSupport.java`, 마이그레이션 테스트 5개(`CountrySeed`, `SchemaRevision`, `NotificationPreference`, `QuestionProposalDeleteMute`, `FlywayMigration`), `QelloLocalProfileIntegrationTest.java` | INT-001, INT-005 | 클래스마다 새 DB로 바꾼 뒤 전체 재실행 |
| 4 | 실행 에이전트 | 저장소 밖 init script | INT-004 | seed 2개 전체 실행, 실패 시 3으로 돌아감 |
| 5 | 실행 에이전트 | `docs/reports/tests/` 보고서 | INT-003 | `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |
| 6 | 실행 에이전트(사용자 승인 후) | 없음 | INT-006 | push, CI Benchmark dispatch, 비교 스크립트, artifact 지표 추출 |

메인 클론에서 Gradle을 돌리기 전에 같은 클론의 다른 Gradle 실행이 없는지 확인한다.

## 10. Completion criteria

- [ ] INT-001~005 통과
- [ ] 수정한 테스트 클래스의 `@DisplayName` 유지, 헤더에 원본 시나리오 줄 추가
- [ ] 테스트를 지우거나 `@Disabled`로 건너뛰지 않음
- [ ] `./harness check`, `./harness pr-ready --project-tests` 통과
- [ ] INT-006 비교 출력과 내부 지표를 보고서와 PR에 기록
- [ ] 잠재 문제 분석과 깨진 테스트 목록을 담은 테스트 보고서 생성

## 11. Human approval

- Reviewer: 사용자(tkv00)
- Decision: 승인. 아래 D1~D3을 권장안대로 승인했다.
- Approved at: `2026-10-10T04:35:25+09:00`

승인받을 결정:

- D1 깨진 테스트 수정 원칙: 깨진 클래스 안에서만 최소로 고친다. 전체 행 수 단언은 그 클래스가 만든 ID로 범위를 좁히고,
  FK 때문에 막힌 정리는 그 클래스의 정리 목록에 자식 테이블을 더한다. base 클래스에 공통 정리를 넣지 않는다(다음 단계).
- D2 `FlywayMigrationIntegrationTest`의 표시 이름: 바꾸지 않는다. 바꾸면 테스트 이름 목록이 main과 달라진다. 빈 DB 적용은
  첫 클래스 컨텍스트의 기동이 증명하고, 이 클래스의 단언(버전마다 성공 이력 1행)은 그대로 통과해야 한다. 보고서에 기록한다.
- D3 무작위 순서 실행은 저장소 밖 init script로 하고 `build.gradle`에 넣지 않는다.

## 12. 방식 변경(2026-10-10)

컨테이너만 한 번 띄우고 DB를 공유한 첫 실행(INT-005)에서 852건 중 438건, 111개 클래스 중 51개가 실패했다. 약 45개
클래스는 정리 코드의 `DELETE FROM 부모`가 앞 클래스가 남긴 자식 행에 FK로 막혔다. 나머지는 남은 행·스키마 때문에 단언이
틀어졌다. 상세는 테스트 보고서에 둔다.

사용자 결정: 컨테이너 하나 안에서 클래스마다 새 DB를 만든다. D1(깨진 클래스마다 정리 보강)과 D2(Flyway 표시 이름 유지)는
이 결정으로 필요 없어져 대체한다. D3(무작위 순서는 저장소 밖 init script)는 유지한다.

| 방안 | 장점 | 단점 |
| --- | --- | --- |
| 클래스마다 새 DB(선택) | 클래스마다 빈 DB에서 Flyway가 도는 지금 격리를 그대로 유지한다. 테스트 정리 코드를 바꾸지 않는다. 줄어드는 몫이 컨테이너 기동뿐이라 다음 단계(컨텍스트·Flyway)와 측정이 섞이지 않는다 | base 클래스에 JUnit 확장과 접속 정보 등록이 늘고 `@ServiceConnection`을 쓰지 않는다. 클래스마다 `CREATE DATABASE` 비용 |
| 깨진 클래스마다 정리 보강(D1) | base 클래스는 단순하다 | 약 50개 파일 수정. 각 클래스가 자기 밖 자식 테이블까지 FK 순서로 알아야 하고, 테이블이 늘 때마다 여러 클래스가 다시 깨진다. 정리 방식이 다음 단계 결정과 겹친다 |
| base에서 클래스 시작 전 TRUNCATE | 수정 파일이 적다. Flyway가 한 번만 돈다 | 다음 단계에서 정하기로 한 정리 시점을 지금 정한다. 기준 데이터 테이블(`region_code` 등)을 테스트가 바꾼 경우와 마이그레이션 테스트가 남긴 스키마를 따로 처리해야 한다. 측정에 Flyway 몫이 섞인다 |
