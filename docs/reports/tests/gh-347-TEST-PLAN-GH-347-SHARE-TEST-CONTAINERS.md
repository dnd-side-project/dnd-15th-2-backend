# Test Report: TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS

> Created at: `2026-10-10T05:19:46+09:00`
> GitHub Issue: `#347`
> Branch: `test/gh-347-share-test-containers`
> Commit: `d2bb4bb8`(최종 로컬 실행은 커밋 전, 이 커밋과 같은 작업 트리에서 했다)

## 1. Executive summary

- Result: `PASS`. 로컬 시나리오 INT-001~005와 CI Benchmark(INT-006)가 통과했다.
- Tested scope: PostGIS·LocalStack 컨테이너를 JVM에서 한 번만 띄우고, 테스트 클래스가 시작될 때마다 같은 이름의 DB를
  `template_postgis`로 다시 만드는 변경. 통합 테스트 전체를 기본 순서와 무작위 클래스 순서(seed 2개)로 실행했다.
- Unverified scope: 클래스마다 DB를 다시 만드는 시간(로그에 남지 않아 따로 재지 않았다).
- Release recommendation: 병합 가능. CI Benchmark(run 38023671076)에서 `integrationTest` 중앙값이 706.5초에서 315.5초로
  줄었고(단측 p<0.0001), 두 설정의 테스트 수·목록이 같고 실패 0이다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain Java 21(Temurin) |
| Spring Boot | 3.5.16 |
| Database | 로컬 Docker의 Testcontainers `postgis/postgis:16-3.5-alpine`(linux/amd64) 1개, 클래스마다 `template_postgis`로 다시 만든 DB |
| External double | Testcontainers `localstack/localstack:3.8`(S3) 1개 |
| Test runner | JUnit 5, Gradle 8.14.3, `integrationTest` fork 1개 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Unit(`./harness test-run`의 `test`) | PASS | 1,381건, 실패 0 | 50초 | `build/test-results/test` |
| Integration, main 코드(기준) | PASS | 852건, 실패 0 | 737초 | 로컬 결과 XML(저장소 밖) |
| Integration, DB 공유 첫 시도 | FAIL | 852건 중 438건 실패 | 186초 | 5절 |
| Integration, 번호 붙은 DB 구현 첫 실행 | FAIL | 852건 중 1건 실패 | 246초 | 5.2절 |
| Integration, 번호 붙은 DB 구현 기본 순서·seed 1·seed 2 | PASS | 852건, 실패 0 | 241·230·226초 | 로컬 결과 XML |
| Integration(`./harness test-run`, 번호 붙은 DB 구현) | PASS | 852건, 실패 0 | 4분 2초 | 로컬 결과 XML |
| Integration, 최종(같은 이름 DB 재생성) 기본 순서 | PASS | 852건, 실패 0 | 284초 | 로컬 결과 XML |
| Integration, 최종 무작위 순서 seed 1 | PASS | 852건, 실패 0 | 288초 | 로컬 결과 XML |
| Integration, 최종 무작위 순서 seed 2 | PASS | 852건, 실패 0 | 275초 | 로컬 결과 XML |

| CI Benchmark run 38023671076(`check`, 설정당 10회) | PASS | 단위 1,381건·통합 852건, 두 설정 같음, 실패 0 | 4.1절 | 비교 스크립트 출력 |

시간은 Gradle 명령의 벽시계 시간이고 같은 Mac에서 1회씩 쟀다. 최종 구현이 번호 붙은 DB 구현보다 40~60초 느렸다. 두 구현은
클래스마다 같은 DB 작업(삭제 1번, 생성 1번)을 한다. 그래서 실행 시점의 Mac 부하 차이로 보지만 확인하지 않았다. 판정은
CI Benchmark로 한다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-001 | PASS | 통합 테스트 전체 | 결과 XML에서 `postgis/postgis:16-3.5-alpine started in`이 1번(main 110번, 합계 414초) |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-002 | PASS | `ExifStrip`, `FeedMediaViewUrl`, `MediaAssetStorage`, `ProfileImage` | LocalStack 기동 1번(main 4번) |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-003 | PASS | main 결과와 비교 | `classname#name` 목록 852개 동일, 건너뜀 0 동일. Flyway 적용 로그 123번 동일 |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-004 | PASS | 무작위 클래스 순서 seed 1, 2 | 두 순서는 서로 다르고 알파벳순과도 다르다. 두 구현 모두 실패 0 |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-005 | 기록 완료 | DB 공유 첫 시도 | 51개 클래스 실패. 방식을 바꿨다(5절, 테스트 계획 12절) |
| TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS-INT-006 | PASS | CI Benchmark run 38023671076 | 4.1절 |

### 4.1 CI Benchmark (INT-006)

base = main `994bc0a1`, head = `60c5e6a4`, task `check`, 설정당 10회, job 20개 모두 성공. 비교 스크립트
(`scripts/experiments/ci-benchmark-compare.py`) 출력:

| task | base 중앙값 [최소~최대] | head 중앙값 [최소~최대] | 차이 head-base [95% 구간] | 단측 p |
| --- | --- | --- | --- | --- |
| `:integrationTest` | 706.5초 [586.4~739.4] | 315.5초 [230.8~343.8] | -380.3초 [-421.6, -337.3] | 0.0000 |
| `:test` | 75.9초 [55.3~80.2] | 75.6초 [53.9~80.4] | -0.4초 [-13.0, +4.0] | 0.4343 |
| 전체 | 879.2초 [720.6~930.9] | 498.4초 [370.4~532.1] | -378.0초 [-434.8, -316.3] | 0.0000 |

- 테스트 일관성: `:integrationTest` 852건, `:test` 1,381건이 두 설정 모든 job에서 같고 실패·오류·건너뜀 0이다.
- artifact 내부 지표(설정당 10개 job):

| 지표 | base | head |
| --- | --- | --- |
| PostGIS 기동 횟수 / `started in` 합계 중앙값 | 110번 / 366.3초 | 1번 / 3.2초 |
| LocalStack 기동 횟수 | 4번 | 1번 |
| Flyway `Successfully applied` 횟수 / 시간 합계 중앙값 | 123번 / 66.4초 | 123번 / 63.1초 |
| 컨텍스트 캐시 `missCount` | 110 | 110 |
| heap 최고(상한 512MB) | 489~512MB | 511~512MB |
| Full GC 직후 heap | 74~96MB | 73~88MB |
| GC 멈춤 합계(비교 스크립트 중앙값) | 6.2초 | 6.9초 |

줄어든 시간(-380초)은 PostGIS 기동 합계(366초)에 컨테이너 정지 시간이 더해진 정도다. Flyway 적용 횟수와 `missCount`가
그대로라서 컨텍스트·Flyway 몫은 다음 단계에 남아 있다.

## 5. Failures and diagnostics

### 5.1 DB 공유 첫 시도(INT-005)

컨테이너만 static 블록에서 한 번 띄우고 `@ServiceConnection`으로 같은 DB를 모든 클래스가 쓰게 했다. 852건 중 438건,
111개 클래스 중 51개가 실패했다.

| 원인 | 클래스 수 | 예 |
| --- | --- | --- |
| 정리 코드의 `DELETE FROM 부모`가 앞 클래스가 남긴 자식 행에 FK로 막힘. 정리가 `@BeforeEach`라 클래스 전체 실패 | 42 | `DELETE FROM post_recipient` ← `answer`(10개 클래스), `DELETE FROM notification` ← `push_dispatch_group_member`(8개), `DELETE FROM answer` ← `report_case`(6개), `DELETE FROM user_account` ← `media_asset`·`approved_question`(8개) |
| 마이그레이션 테스트가 남긴 별도 스키마 때문에 스키마 조건 없는 카탈로그 조회가 2행 | 2 | `FlywayMigration`(5/12), `ProfileImage`(1/8) |
| 다른 클래스가 남긴 기준 데이터·고정 닉네임 | 2 | `CountrySeedMigration`(COUNTRY가 아닌 `region_code` 7행), `ReportEvidencePurgeSweepWorker`(닉네임 유니크 위반) |
| 남은 행을 목록 조회나 worker claim이 함께 읽음 | 5 | `InboxApi`, `InboxListIsolation`, `PushDispatchGrouping`, `PushDispatchSuppression`, `ReceiveSlotRelease` |

각 클래스의 정리 코드는 빈 DB에서 시작한다는 전제로 자기 테이블만 지운다. 이 전제를 지키는 쪽으로 방식을 바꿨고, 51개
클래스의 정리 코드는 고치지 않았다. 대안별 트레이드오프와 선택 이유는 테스트 계획 12절에 있다.

### 5.2 번호 붙은 DB 구현

처음에는 클래스마다 `qello_test_class_<n>`을 만들고 `@ServiceConnection` 대신 `@DynamicPropertySource`로 접속 정보를 넘겼다.

| 클래스 | 증상 | 원인 | 조치 |
| --- | --- | --- | --- |
| `QelloLocalProfileIntegrationTest` | `current_database()` 기대값 `qello_test`, 실제 `qello_test_class_99` | local 프로필 설정 대신 테스트 컨테이너에 연결되는지 보려고 컨테이너 기본 DB 이름과 비교했다 | 이 구현에서는 기대값을 클래스 DB 이름으로 바꿨다. 최종 구현에서는 DB 이름이 같아 고치지 않는다 |

이 구현은 마이그레이션 테스트 5개의 Flyway 접속 URL도 바꿔야 했다. 고친 `SchemaRevisionMigrationIntegrationTest`는
`spotless ratchetFrom 'origin/main'` 때문에 파일 전체(379줄)가 다시 정렬됐다. 형식만 바꾼 커밋을 만들려고 하자 pre-commit의
`checkstyleStagedJava`가 이 파일의 기존 테스트 메서드 4개(67·57·57·58줄)를 50줄 제한(QELLO-JAVA-SIZE-001) 위반으로 막았다.
메서드를 쪼개는 일은 이번 범위 밖이고 suppression과 훅 우회는 저장소 규칙상 하지 않는다. 그래서 그 파일을 고치지 않아도 되는
구현으로 바꿨다(테스트 계획 12.1절).

### 5.3 최종 변경 범위

| 파일 | 변경 |
| --- | --- |
| `PostgisContainerIntegrationTestSupport` | `@Testcontainers`·`@Container` 제거, static 블록에서 start, 클래스가 시작될 때 관리용 `postgres` DB에서 `qello_test`를 `DROP DATABASE ... WITH (FORCE)` 후 `CREATE DATABASE ... TEMPLATE template_postgis`로 다시 만드는 `BeforeAllCallback` 추가. `@ServiceConnection`·`@DirtiesContext(AFTER_CLASS)` 유지 |
| `LocalStackContainerIntegrationTestSupport` | `@Container` 제거, static 블록에서 start |

`LocalStackContainerIntegrationTestSupport`는 원래 형식에 맞지 않아 `spotlessApply`로 정렬했다(들여쓰기 13줄). 형식만 바뀐
부분은 별도 커밋으로 나눈다. 이 클래스의 헤더는 Javadoc(`/**`)이라 포매터가 한 문단으로 합치므로
`FlywayMigrationIntegrationTest`처럼 블록 주석(`/*`)으로 바꿔 한 줄씩 유지했다.

```java
// before
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgisContainerIntegrationTestSupport {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = postgresContainer();
}

// after(요약)
@ExtendWith(PostgisContainerIntegrationTestSupport.ClassDatabaseExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgisContainerIntegrationTestSupport {

	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = postgresContainer();

	static {
		postgres.start();
	}

	static final class ClassDatabaseExtension implements BeforeAllCallback {
		// 관리용 postgres DB에 접속해
		// DROP DATABASE IF EXISTS qello_test WITH (FORCE)
		// CREATE DATABASE qello_test TEMPLATE template_postgis
	}
}
```

## 6. Potential issues

### Application code

- 운영 코드 변경은 없다.

### Infrastructure and resource limits

- 컨테이너가 JVM 끝까지 살아 있으므로 로컬 실행을 강제로 끊으면 Ryuk가 정리할 때까지 컨테이너가 남는다. 로컬
  Testcontainers 설정에 Ryuk를 끄는 값은 없다.
- 컨테이너가 중간에 죽으면 뒤 클래스가 모두 실패한다. 예전에는 그 클래스만 실패했다.

### Database and migrations

- 클래스마다 빈 DB에서 Flyway가 V1부터 적용되는 것은 main과 같다(적용 로그 123번 동일).
- `template_postgis`와 컨테이너 기본 DB는 이미지 초기화 스크립트가 같은 확장(postgis, postgis_topology, fuzzystrmatch,
  postgis_tiger_geocoder)을 설치한 DB다. `FlywayMigrationIntegrationTest`의 catalog 단언이 통과했다.
- 테스트가 `template_postgis`에 연결하면 `CREATE DATABASE ... TEMPLATE`이 실패한다. 지금 그런 테스트는 없다.

### Concurrency and idempotency

- 모든 클래스가 같은 이름의 DB를 쓴다. `integrationTest`는 fork 1개에서 클래스를 순서대로 실행하므로 안전하다. JUnit 클래스
  병렬 실행을 켜면 앞 클래스가 쓰는 DB를 다음 클래스가 지우므로 이 확장을 다시 설계해야 한다.

### Transactions and event ordering

- 바뀐 것이 없다. 각 클래스의 트랜잭션과 정리 코드는 그대로다.

### External APIs

- LocalStack 버킷과 객체가 클래스 사이에 남는다. 객체 key가 업로드마다 UUID를 포함해 겹치지 않는다. 버킷 전체 목록을
  세는 테스트가 생기면 깨질 수 있다.

### Failure recovery and reconciliation

- 이 변경을 revert하면 클래스마다 컨테이너를 띄우던 원래 동작으로 돌아간다. 데이터 정리는 필요 없다.

## 7. Regression and residual risk

- 로컬과 CI 모두 클래스를 알파벳순으로 실행했고(CI 기준 측정 결과 파일 2개로 확인), 무작위 순서 2회도 통과해 순서 의존
  위험은 낮다고 본다.
- 줄어든 시간이 PostGIS 기동 합계보다 크다(로컬 약 450~500초 대 414초, CI 380초 대 366초). 컨테이너 정지·삭제 시간이 기동
  로그에 안 잡히는 것으로 추정하며 확인하지 않았다.
- heap은 두 설정 모두 상한 512MB에 닿는다. 이번 변경으로 달라지지 않았다.
- 다음 단계에서 `@DirtiesContext`를 없애 컨텍스트를 공유하면 DB도 클래스 사이에 공유된다. 그때는 5.1의 문제를 다시 다뤄야
  한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-347-TEST-PLAN-GH-347-SHARE-TEST-CONTAINERS.md`
- CI run: CI Benchmark run 38023671076
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
