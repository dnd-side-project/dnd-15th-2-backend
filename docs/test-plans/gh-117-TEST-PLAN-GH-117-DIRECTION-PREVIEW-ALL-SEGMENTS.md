# Test Plan: TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS

> Created at: `2026-08-12T00:42:36+09:00`
> GitHub Issue: `#117`
> Status: Draft — human approval required before test implementation

## 1. Objective

모바일이 회전할 때 서버를 방향별로 반복 호출하지 않도록, 하나의 PostgreSQL/PostGIS
질의가 선택된 활성 방향 정책의 모든 `direction_segment`별 예상 후보 수를 계산하는지
검증한다. `ST_DWithin`으로 최대 거리 반경을 먼저 축소하고 `ST_Azimuth`로 계산한
방위를 시작각 포함·종료각 제외 규칙에 따라 정확히 하나의 segment에 배정해야 한다.

실패하면 방향 경계에서 후보가 중복되거나 누락되고, 날짜 변경선에서 방위가 뒤집히며,
거리 경계에서 수가 틀리거나, 빈 구간이 응답에서 사라질 수 있다. 결과 모델에는 후보의
사용자 ID나 정확 좌표가 포함되어서는 안 된다.

## 2. Scope

### Included

- 활성 direction scheme에 속한 모든 segment를 한 번에 집계하는 repository SQL
- `ST_DWithin` 기반 최대 거리 후보 축소와 최소 거리 후속 필터
- `ST_Azimuth` 기반 방위 계산과 경도 ±180° 날짜 변경선 통과
- 시작각 포함·종료각 제외, 0/360° wrap-around, 인접 segment 간 단일 배정
- 후보가 없는 segment를 `count = 0`으로 채우는 service 결과 구성
- 활성·현재·수신 허용 후보의 기존 eligibility 회귀
- preview 결과 모델의 사용자 ID·위도·경도·PostGIS point 비노출
- 고정 시각을 사용한 만료/현재성 경계 및 최소·최대 거리 경계
- 실제 PostgreSQL 16/PostGIS Testcontainers 통합 검증
- 기존 V1~V12 migration, 기존 단일-sector `findCandidates()`와의 회귀 확인

### Excluded

- REST Controller, HTTP status/JSON 직렬화와 API 문서 생성
- preview cache 및 모바일 회전 이벤트 최적화
- 매칭 worker, `post_recipient` 확정, Outbox/푸시와 외부 연동
- 거리 정책의 미확정 기본 숫자 결정
- 새로운 migration, schema/index 변경, H3·Redis·Kafka 도입
- 모든 scheme/version을 섞는 집계
- 운영 배포, 인프라 apply와 실제 외부 서비스 호출

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #117 — 사용자 제공 본문 | 한 질의 결과로 모든 활성 direction segment의 count를 반환하고, segment 경계의 중복·누락을 없애며, 응답에 사용자 식별자와 정확 좌표를 포함하지 않는다. 실제 PostgreSQL/PostGIS 통합 테스트가 통과해야 한다. API Controller와 preview cache는 제외한다. |
| `docs/설계/1. 방향-기반-수신자-매칭-백엔드-설계.md` §3, §4, §8, §9, §11.2 | 서버는 화면 폴리곤이 아닌 origin·거리·방위 숫자를 권위 조건으로 사용한다. 후보는 geography 대권거리와 `ST_Azimuth`로 계산하며, 모든 구간 집계는 segment와 방위 결과를 조인하고 빈 구간을 0으로 채운다. 정확 좌표는 API·로그·분석·Outbox 경계에 전달하지 않는다. |
| `AGENTS.md` §3, §11 | JUnit 5, 단위/통합 테스트 분리, 모든 test method의 `@DisplayName`, 테스트 클래스의 정확한 ISO 8601 생성 시각 및 source scenario, 실제 실행 범위와 환경 실패 구분을 지킨다. |
| 현재 `JdbcActiveUserPresenceRepository` / `ActiveUserPresenceSql` | 기존 `findCandidates()`는 `ST_DWithin`, `ST_Azimuth`, 현재성·수신 허용·계정·차단 조건과 단일 sector half-open 필터를 제공한다. #117은 이 목록 반환 API를 모든 segment count용 집계 경계로 확장하며, 기존 API의 동작은 회귀 기준이다. |
| 현재 `DirectionScheme` / `DirectionSegment` | segment는 `[start, end)` 의미의 `contains()`와 0/360 wrap-around를 사용한다. 구현 결과는 같은 방위가 0개 또는 2개 segment에 들어가지 않도록 이 도메인 규칙과 일치해야 한다. |
| 현재 `PostgisContainerIntegrationTestSupport` | H2가 아닌 PostgreSQL/PostGIS Testcontainers에서 Flyway와 실제 geography 연산을 실행한다. 외부 API double은 필요하지 않다. |

### 3.1 Assumptions requiring human confirmation

- **A1 — 정책 범위:** preview 입력은 하나의 활성 `direction_scheme` 또는 그 ID/version
  snapshot을 가리키며, 여러 scheme/version의 segment를 한 응답에 섞지 않는다.
- **A2 — 거리 경계:** 설계 문서의 `minDistanceM <= distance <= maxDistanceM`를 따른다.
  현재 단일 후보 SQL은 `ST_DWithin(max)` 후 `distance >= min`을 적용하므로, 새 집계도
  최소·최대 모두 포함으로 검증한다.
- **A3 — 현재성 기준:** 테스트 가능한 deterministic contract를 위해 service/repository
  경계에 `Instant at`을 전달한다. 구현이 DB `now()`를 직접 사용하려면 동일한 시각을
  고정할 수 있는 대체 검증 경계를 먼저 승인받는다.
- **A4 — 결과 정렬:** 결과는 `sort_order` 또는 동등한 정책 순서로 모든 segment를 반환하며,
  count가 0인 segment도 포함한다. 응답 필드명은 구현 전 결정하되 사용자 ID·정확 좌표는
  어떤 이름으로도 노출하지 않는다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| segment마다 `findCandidates()`를 반복 호출한다. | 모바일 회전 호출 감소 목적이 달성되지 않고 DB 왕복이 segment 수만큼 증가한다. | High | P0 | 집계 repository가 하나의 SQL statement만 실행한다는 boundary/integration 증거 |
| `ST_DWithin`을 누락하거나 최대 거리 필터 뒤에 적용한다. | 반경 밖 사용자가 집계되거나 spatial index 사용 경계가 깨진다. | Medium | P0 | SQL boundary assertion과 실제 PostGIS 결과/선택적 EXPLAIN 증거 |
| `ST_Azimuth`의 point 순서 또는 degree 변환이 틀린다. | 방향 count가 반대 방향에 들어가고 날짜 변경선에서 오판한다. | High | P0 | 실제 좌표 fixture의 대표 방위와 ±180° 통과 fixture |
| 시작각·종료각을 모두 포함하거나 wrap 조건을 OR로 잘못 작성한다. | 경계 후보가 두 segment에 중복되거나 어떤 segment에도 들어가지 않는다. | High | P0 | 각 시작각·종료각·0/360° fixture의 합계 및 segment별 count |
| 빈 segment를 inner join으로 제거한다. | 응답 배열의 segment 수가 정책과 달라 모바일이 빈 방향을 알 수 없다. | High | P0 | 후보가 없는 segment가 `0`으로 반환되는 service/integration 증거 |
| min/max 거리 비교가 한쪽 경계를 제외한다. | 정확한 경계의 후보 count가 제품 예상과 달라진다. | Medium | P0 | 대권거리 exact/직전/직후 fixture와 inclusive assertion |
| 날짜 변경선 근처 geography 입력을 평면 경도 차이로 처리한다. | 179.999°와 -179.999° 후보가 비정상적으로 멀거나 방향이 누락된다. | Medium | P0 | ±180° 통과 실제 PostGIS fixture |
| 결과 DTO가 `userId`, latitude, longitude 또는 point를 전달한다. | preview 권한 경계와 위치 개인정보 보호가 깨진다. | Medium | P0 | DTO shape/unit 및 JDBC result mapping에 대한 금지 필드 검사 |
| 만료·수신 불가·비활성·차단 후보가 preview에 포함된다. | preview count가 실제 매칭 eligibility와 달라진다. | Medium | P1 | 기존 후보 eligibility와 동일한 통합 fixture |
| 여러 scheme/version의 segment가 섞이거나 inactive scheme이 포함된다. | 정책 버전 의미가 깨지고 방향별 count가 중복된다. | Medium | P1 | 활성 scheme 범위와 inactive/other-version fixture |
| 단일 SQL 결과는 맞지만 service가 segment metadata와 count를 잘못 매핑한다. | count가 다른 방향 label/order로 표시된다. | Medium | P1 | row mapping, sort order, zero-fill unit/integration assertion |
| preview 구현을 위해 불필요한 migration/index 변경을 추가한다. | #117 범위 확대와 #115 migration 충돌이 발생한다. | Low | P1 | 변경 파일/DDL diff 검토와 schema regression check |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-001 | 활성 scheme의 ordered segment metadata와 일부 segment count row만 존재 | preview result assembler를 실행 | 모든 활성 segment가 sort order대로 유지되고 count가 없는 segment는 0이 된다. | P0 | Preview service executor |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-002 | 시작각 `s`, 종료각 `e`인 인접 segment와 경계 방위 `s`·`e` | repository row 또는 approved binning mapper를 변환 | `s`는 해당 segment에 포함되고 `e`는 다음 segment에 포함되며 같은 방위가 두 결과에 중복되지 않는다. | P0 | Preview domain executor |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-003 | 0°를 가로지르는 segment `[315°, 360°)`와 `[0°, 45°)` | 0°, 359.999°, 45°의 방위 판정을 실행 | 0°와 359.999°는 첫/둘 중 올바른 half-open 구간에 각각 한 번만 들어가고 45°는 다음 구간으로 간다. | P0 | Preview domain executor |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-004 | 결과 row가 일부 segment에만 있고 metadata에는 모든 segment가 있음 | service가 preview response model을 생성 | count row에 없는 segment도 0으로 생성되고, 결과 model에 userId·latitude·longitude·position 필드가 없다. | P0 | Preview service executor |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-005 | 동일한 segment count row와 동일한 metadata를 반복 입력 | assembler를 여러 번 실행 | 결과 순서와 값이 deterministic하며 segment key/label과 count가 다른 row로 밀리지 않는다. | P1 | Preview service executor |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-006 | min distance가 max distance보다 크거나 origin/거리 값이 유효하지 않은 요청 | preview 입력 검증을 실행 | repository를 호출하지 않고 기존 direction validation 계약에 맞는 예외를 반환한다. 제품 기본 거리 숫자는 테스트에서 결정하지 않는다. | P1 | Preview service executor |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-UNIT-007 | 집계 SQL source와 preview repository interface | SQL boundary contract를 검사 | `ST_DWithin`, `ST_Azimuth`, active segment join, half-open 경계, zero-fill을 구현 가능한 SQL 경계로 확인하고 segment별 반복 호출 흔적이 없다. | P0 | JDBC boundary executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-001 | Flyway, PostgreSQL/PostGIS, preview repository | 현재 migration으로 빈 Testcontainers DB를 시작하고 하나의 ACTIVE scheme에 8개 segment를 저장 | 고정 origin과 후보가 여러 방향에 걸친 상태에서 all-segment preview를 호출 | 하나의 repository 호출 결과에 8개 segment가 모두 있고, 후보가 있는 방향은 정확한 count, 없는 방향은 0이다. 실제 DB에서 `ST_DWithin`/`ST_Azimuth`가 실행된다. | 테스트 전용 region·presence·scheme/segment 삭제 및 container 종료 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-002 | JDBC preview SQL, 8개 segment, 경계 후보 | 각 segment 시작각과 종료각에 대응하는 후보를 seed하고 preview 호출 | 모든 경계 후보를 한 번에 집계 | 시작각 포함·종료각 제외가 지켜지고, 인접 segment count 합계가 후보 수와 일치한다. 어떤 후보도 두 segment에 집계되지 않는다. | marker 기준 presence 삭제 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-003 | JDBC preview SQL, wrap-around segment | 315°~360°와 0°~45° 양쪽에 후보를 seed하고 0/360°에 가까운 방위를 포함 | preview 호출 | 0/360° 경계를 통과하는 후보가 올바른 segment에 한 번씩만 들어가며, SQL의 start/end 비교가 누락을 만들지 않는다. | marker 기준 정리 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-004 | PostgreSQL/PostGIS geography, preview repository | origin longitude를 179.999° 부근에 두고 후보를 -179.999° 부근에 둔다. 동일 위도·방위가 확실한 대조군도 둔다 | preview 호출 | 날짜 변경선을 가로지르는 후보가 반경 안에 포함되고 `ST_Azimuth` 기준 올바른 방향 count에 들어간다. 평면 경도 차이로 후보를 탈락시키지 않는다. | marker 기준 정리 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-005 | JDBC preview SQL, 거리 경계 | 대권거리가 min-ε, min, min+ε 및 max-ε, max, max+ε인 후보를 준비 | preview 호출 | 승인된 inclusive 계약에 따라 min과 max 정확 경계는 포함되고, 범위 밖 후보는 제외된다. 실제 거리 계산은 geography meter 단위다. | marker 기준 정리 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-006 | Preview service, scheme repository, preview repository | 활성 scheme에는 후보가 없는 segment를 포함하고 inactive scheme/다른 version segment도 준비 | service preview 호출 | 선택된 하나의 ACTIVE scheme segment만 ordered response로 반환하고, 후보가 없는 segment는 0이며 다른 scheme/version은 섞이지 않는다. | scheme/segment/presence 역순 정리 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-007 | Preview service, JDBC result mapping, PostgreSQL rows | 활성·만료·수신 불가·비활성 계정·양방향 active block·sender 자기 자신 후보를 각 방향에 준비 | preview 호출 | 현재 기존 `findCandidates()` eligibility와 같은 기준으로 유효 후보만 count되고, preview result에는 user ID와 정확 좌표가 없다. | block → presence → account → region 순서 정리 |
| TEST-PLAN-GH-117-DIRECTION-PREVIEW-ALL-SEGMENTS-INT-008 | Preview service/repository, datasource or JDBC statement observation | 둘 이상의 segment에 후보가 있는 고정 fixture와 query observation seam을 준비 | 한 번의 service preview 호출 | segment 수만큼 repository를 반복하지 않고 하나의 SQL statement/DB round-trip으로 결과를 구성한다. SQL 실패 시 부분 결과를 반환하지 않고 예외로 종료한다. | 테스트 context/container 종료 |

## 7. Cross-cutting scenarios

### Database and transactions

- 실제 PostgreSQL 16/PostGIS Testcontainers를 사용한다. H2, Java에서 좌표를 직접 계산한
  대체 구현, 정적 SQL 문자열 검사만으로 완료 처리하지 않는다.
- `active_user_presence.position`은 `geography(Point, 4326)`로 저장된 실제 위치를 사용하고,
  origin은 `ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography` 순서를
  검증한다.
- 집계 SQL은 `ST_DWithin`으로 `maxDistance` 후보를 먼저 줄인 뒤 `ST_Azimuth` 결과와
  segment metadata를 조인한다. 결과가 없는 segment를 보존하기 위해 approved SQL의
  left-join/zero-fill equivalent를 검증한다.
- preview는 읽기 작업이므로 도메인 row를 변경하지 않아야 한다. 호출 전후
  `active_user_presence`, `direction_scheme`, `direction_segment` row count와 값이
  변하지 않는지 P1 회귀로 확인한다.
- #117에서 migration/index를 추가하지 않는다. 구현자가 schema 변경을 제안하면 #115
  migration과의 version/manifest 정합성을 별도 사람 결정으로 승격하고 계획을 다시 승인한다.

### Concurrency and idempotency

- preview 자체는 쓰기 멱등성 대상이 아니지만, 같은 fixture에 대해 동시에 두 번 읽어도
  동일한 ordered counts를 반환해야 한다(P1).
- 후보의 수신 슬롯 예약이나 `post_recipient` 생성은 #117에서 수행하지 않는다. preview
  count를 실제 수신자 확정값으로 오인하는 side effect가 발견되면 FAIL로 판정한다.
- `direction_scheme`/`direction_segment`가 읽는 중 변경되는 운영 동시성 정책은 현재
  Issue에 없다. 구현이 transaction snapshot 또는 version을 요구하면 별도 결정 항목으로
  기록하고 임의의 lock 정책을 테스트에 추가하지 않는다.

### External APIs

- 외부 API, push provider, controller, HTTP client는 범위 밖이며 실행하지 않는다.
- 결과 모델의 검증은 Java domain/service model과 JDBC mapping 경계에서 수행한다. JSON
  직렬화 계약을 새로 확정하지 않는다.

### Failure recovery and reconciliation

- 단일 query가 실패하면 일부 segment count만 반환하거나 0으로 위장하지 않고 명시적인
  repository/service 오류로 끝나야 한다.
- 빈 segment 0 채움은 DB 장애를 0 count로 변환하는 fallback이 아니다. SQL 성공 결과와
  metadata 기반 zero-fill을 구분해 assertion한다.
- 위치가 만료되거나 preview 중 eligibility에서 제외되면 다음 호출에서 재계산되는
  ephemeral count로 취급한다. preview cache/backfill/reconciliation은 수행하지 않는다.

## 8. Test data and isolation

- Fixtures: 테스트 전용 `region_code`, sender account, 하나의 ACTIVE scheme, 8개 segment,
  방향·거리·날짜 변경선·eligibility 경계용 `active_user_presence`를 사용한다.
- Database isolation: 기존 `PostgisContainerIntegrationTestSupport`의 PostgreSQL 16 /
  PostGIS 3.5 container와 테스트 전용 region marker를 사용하고, `@BeforeEach`에서
  block → presence → scheme/segment → account/region 순서에 맞게 정리한다.
- Clock/randomness: 모든 현재성 판정은 계획 승인 시 A3가 확정되면 고정 `Instant at`으로
  주입한다. `now()`를 직접 사용하면 DB 시각을 고정하거나 명시적인 대체 seam을 제공한다.
- External API doubles: 없음.
- Query observation: 단일 SQL 증거를 위해 구현 가능한 datasource proxy, JDBC statement
  counter, 또는 repository boundary spy 중 하나를 승인받아 사용한다. 임의의 production
  logging을 추가해 검증하지 않는다.
- Cleanup: 테스트 marker를 포함한 block/presence/segment/scheme/account/region을
  역순으로 삭제하고, 테스트가 실패해도 다음 클래스가 오염되지 않도록 class-scoped
  container를 재사용하지 않는 기존 규칙을 따른다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Preview domain/service executor | 계획 승인 후 생성될 preview result model/service 테스트 파일만 | UNIT-001~006 | `./gradlew test --tests <approved unit classes>`; 모든 메서드 `@DisplayName`, 테스트 클래스 timestamp/source scenario 확인 |
| 2 | JDBC boundary executor | `src/test/java/com/dnd/qello/direction/**`의 preview SQL boundary 테스트 파일만 | UNIT-007 | `./gradlew test --tests <approved boundary class>`; `ST_DWithin`, `ST_Azimuth`, half-open/zero-fill 및 반복 호출 금지 경계 확인 |
| 3 | PostgreSQL/PostGIS integration executor | `src/integrationTest/java/com/dnd/qello/DirectionPreviewIntegrationTest.java` 또는 승인된 동등 파일만 | INT-001~006 | `./gradlew integrationTest --tests com.dnd.qello.DirectionPreviewIntegrationTest`; 실제 Testcontainers 결과와 fixture cleanup 확인 |
| 4 | Service/privacy integration executor | 위 통합 테스트 파일 또는 사전에 분리한 별도 preview integration 파일만 | INT-007~008 | service 결과 mapping, eligibility, 단일 statement observation, SQL failure 시 부분 결과 없음 확인 |
| 5 | Independent verifier | 실행 에이전트가 수정하지 않은 검증 전용 범위 | 전체 | `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`; 실패는 코드/환경으로 분리 보고 |

소유 파일은 실행 전 승인 시 실제 구현 파일명으로 확정한다. 서로 다른 executor가 같은
 테스트 파일을 수정하지 않으며, 구현 에이전트가 만든 결과를 검증 에이전트가 자동 승인하지
 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 클래스 상단에 정확한 ISO 8601 timestamp와 원본 scenario ID
- [ ] 한 preview 호출이 모든 활성 segment count와 빈 segment 0을 반환
- [ ] 시작각 포함·종료각 제외, 0/360°와 날짜 변경선에서 중복·누락 없음
- [ ] min/max 거리 경계와 고정 시각 eligibility 검증
- [ ] 결과 모델과 mapping에 사용자 ID·정확 좌표 없음
- [ ] 실제 PostgreSQL/PostGIS 통합 테스트 통과
- [ ] 기존 단일-sector `findCandidates()`와 관련 PostGIS 회귀 통과
- [ ] 단일 SQL/DB round-trip 증거 확보
- [ ] 테스트 후 application, database, concurrency, transaction, external API,
  failure-recovery 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] `./harness check`
- [ ] `./harness pr-ready --project-tests`
- [ ] `npm run hooks:validate`
- [ ] `git diff --check`

### Failure judgment

- **FAIL:** 실제 구현 또는 테스트가 segment 중복·누락, 거리/방위 오판, 개인정보 필드,
  반복 query, partial response를 재현한다.
- **BLOCKED:** Issue/설계에서 A1~A4 또는 결과 필드/시각 경계가 승인되지 않았거나,
  PostgreSQL/PostGIS/Docker 환경 또는 독립 query observation seam을 확보하지 못한다.
- **PASS:** 승인된 P0 범위가 구현되고 실제 PostgreSQL/PostGIS 및 필수 harness 검증이
  통과하며, 실행하지 못한 검증이 없다.

## 11. Human approval

- Reviewer: 미지정
- Decision: Pending — A1~A4 및 실행 파일 소유권 승인 필요
- Approved at:
