# Test Plan: TEST-PLAN-GH-80-INBOX-DIRECTION-CHIPS

> Created at: `2026-08-08T21:14:15+09:00`
> GitHub Issue: `#80`
> Status: Approved

## 1. Objective

`내게 온 질문` 목록의 방향 칩을 서버가 카테고리 스코프 전체에서 집계한다. 검증할
사용자 가치는 "칩에 적힌 숫자가 그 칩을 눌렀을 때 실제로 보이는 목록과 같다"이며,
구간은 저장값이 아니라 조회 시점에 ACTIVE `direction_scheme`에서 파생된다.

실패 시 위험은 세 가지다.

1. **원형 각도 판정이 틀리면** 북 구간이 350°와 10° 중 한쪽을 놓쳐 칩 숫자와 목록이
   어긋난다. 사용자는 "4건"이라 적힌 칩을 눌러 3건을 보게 된다.
2. **목록 쿼리가 스킴에 의존하면** 방향 필터를 쓰지도 않은 사용자의 수신함에서
   항목이 조용히 사라진다. 원인을 화면에서 추적할 수 없는 종류의 결함이다.
3. **판정 로직이 Java와 SQL 두 곳에 생기므로**(`DirectionSegment.contains` /
   `InboxQuerySql`) 한쪽만 수정되면 조용히 갈라진다.

## 2. Scope

### Included

- `DirectionChip`, `InboxListing` view record의 불변식
- `inbound_bearing_deg` → `direction_segment` 파생의 원형 경계 판정
- 카테고리별(`UNANSWERED` / `ANSWERED`) 칩 집계 범위 한정
- 방향 필터가 걸린 목록과 칩 count의 일치
- 만료·차단·`SKIPPED` 항목이 목록과 칩에서 동일하게 빠지는지
- 필터 없는 목록이 스킴 데이터에 의존하지 않는지
- 복수 ACTIVE 스킴에서의 중복 집계 방어
- 알 수 없는 `segmentKey`의 처리
- `findInbox` 시그니처 교체에 따른 기존 수신함 조회 회귀

### Excluded

- controller, DTO, endpoint, OpenAPI 산출물 — `#80` 범위 밖이다.
- `내가 쓴 질문` 탭의 방향 필터, 지도 마커 방향 집계.
- `ANSWERED` 목록 페이징.
- 슬롯(`active_unhandled_count`) 계산 — 이번 변경이 건드리지 않으며 기존
  `InboxSentPostWriteIntegrationTest`가 회귀를 이미 덮는다.
- 새 마이그레이션 검증 — DB 변경이 없다. V1 시드(`OCTANT`)와 V8 컬럼을 그대로 쓴다.
- 0.5° 단위 전수 스윕 — §7에 근거를 적었다. 경계각 집중 검증으로 대체한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue `#80` | 구간 키를 저장하지 않고 조회 시점에 파생 / 북 구간이 350°와 10°를 모두 포함 / 0건 방향은 칩에 없음 / 카테고리를 바꾸면 칩 범위도 한정 / 칩으로 필터한 목록 건수가 그 칩의 count와 일치 / controller 추가 금지 |
| `TASK.md` (`#80`) | 경계각 단일 귀속, 0~359° 대조, 만료·차단·`SKIPPED`의 목록/칩 동일 제외, 미지 `segmentKey`에서 칩 유지 |
| `V8__widen_answer_visibility_to_recipients.sql:118,158` | 구간 라벨은 조회 시점 ACTIVE `direction_scheme`에서 파생 |
| `V1__create_direction_communication_schema.sql:1113` | `uq_direction_scheme_active`는 `(code) WHERE status='ACTIVE'` — code별 유일 |
| `V1__create_direction_communication_schema.sql:1259~1282` | `OCTANT` v1 시드: 8구간 × 45°, `start_offset_deg = 337.5`, `N` 중심 0° |
| `direction/domain/DirectionSegment.java:45` | 포함 판정은 반열림 `[start, start+width)` |
| `#89` 이동 원칙 | 정적 SQL 뼈대는 `InboxQuerySql`, `switch` 같은 동적 조합은 리포지토리 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 원형 경계 오류로 북 구간이 350°/10° 중 하나를 놓침 (`MOD` 음수 피연산자 미보정) | High | Medium | P0 | INT-001 |
| 경계각이 두 칩에 중복 계상되거나 어느 칩에도 안 잡힘 (반열림 미준수) | High | Medium | P0 | INT-002 |
| SQL 파생과 `DirectionSegment.contains`가 갈라짐 (로직 이중화) | High | Medium | P0 | INT-003 |
| 칩 count와 필터 목록 건수 불일치 (두 쿼리의 scope 조건 드리프트) | High | Medium | P0 | INT-006 |
| 만료·차단·`SKIPPED`가 목록에선 빠지는데 칩에는 남음 | High | Medium | P0 | INT-007 |
| 카테고리 경계 누락 — `ANSWERED` 행이 `UNANSWERED` 칩에 섞임 | Medium | Medium | P0 | INT-005 |
| 목록 쿼리가 스킴에 의존해 필터 없는 조회에서 항목이 사라짐 | High | Low | P0 | INT-009 |
| `NUMERIC`을 부동소수로 캐스팅해 22.5° 같은 경계각이 인접 칩으로 밀림 | Medium | Low | P1 | INT-002 |
| 다른 code의 스킴이 동시에 ACTIVE일 때 중복 집계 | Medium | Low | P1 | INT-010 |
| 미지 `segmentKey`에서 예외가 나 화면이 잠김 | Medium | Low | P1 | INT-008 |
| `findInbox` 시그니처 교체로 기존 수신함 조회 회귀 | Medium | Medium | P0 | INT-011 |
| 0건 방향이 count 0으로 칩에 남아 빈 칩이 표시됨 | Low | Medium | P1 | INT-004 |
| view record가 잘못된 값(공백 키·음수 count)을 그대로 통과시킴 | Low | Low | P2 | UNIT-001, UNIT-002 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-80-INBOX-DIRECTION-CHIPS-UNIT-001 | `DirectionChip` 생성 인자 | `segmentKey` 또는 `displayName`에 `null`·공백 문자열 전달 | `IllegalArgumentException` — 도메인 에러코드를 쓰지 않는다. 이 값은 사용자 입력이 아니라 조회 계층이 채우므로 위반은 리포지토리 버그다 | P2 | Executor 3 |
| TEST-PLAN-GH-80-INBOX-DIRECTION-CHIPS-UNIT-002 | 위와 동일 | `count`에 음수, `sortOrder`에 음수 전달 | 각각 `IllegalArgumentException`. `count == 0`은 허용된다 — 0건 방향을 걸러내는 책임은 SQL에 있고 record는 그 판단을 하지 않는다 | P2 | Executor 3 |
| TEST-PLAN-GH-80-INBOX-DIRECTION-CHIPS-UNIT-003 | 가변 `List`로 만든 `InboxListing` | 생성 후 원본 리스트를 수정하고, 반환된 `cards()`·`chips()`에 `add` 시도 | 레코드 내부 값이 바뀌지 않고 반환 리스트는 수정 불가 — `InboxCard.mediaIds`의 `List.copyOf` 관례와 동일 | P2 | Executor 3 |
| TEST-PLAN-GH-80-INBOX-DIRECTION-CHIPS-UNIT-004 | `FeedPersistenceBoundaryTest` | 신규 view 2종이 추가된 상태로 기존 경계 검사 재실행 | `feed/view` 전체가 여전히 `jakarta.persistence`·`org.springframework` 무의존. 별도 assertion 추가 없이 디렉터리 순회로 자동 포함됨을 확인 | P1 | Executor 3 |

## 6. Integration scenarios

모두 `PostgisContainerIntegrationTestSupport`(공유 PostGIS 컨테이너)와 V1 시드의
`OCTANT` 스킴을 사용한다. `N` 구간은 `[337.5, 22.5)`다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| …-INT-001 | `InboxQueryService.list`, `JdbcInboxQueryRepository.countByDirection` | 같은 수신자에게 `inbound_bearing_deg`가 350°, 10°, 0°인 미답변 수신 항목 3건 | `UNANSWERED` 칩 집계 | `N` 칩 하나에 count 3. 0°를 가로지르는 구간이 갈라지지 않는다 | `@BeforeEach` delete |
| …-INT-002 | 위와 동일 | 8개 구간 경계각 각각에 대해 `경계각`과 `경계각 - 0.001` 두 행씩 삽입(22.5, 67.5, …, 337.5) | 칩 집계 | 각 행이 정확히 한 칩에만 잡히고 칩 count 합 == 삽입 행 수. `22.5`는 `NE`, `22.499`는 `N` — 반열림 규칙이 시작각 포함·끝각 배제임을 고정한다 | 동일 |
| …-INT-003 | `JdbcInboxQueryRepository`, `DirectionSchemeRepository.findSegments`, `DirectionSegment.contains` | INT-002의 경계각 집합에 대표각(0·45·90·180·270)과 원형 케이스(350·10)를 더한 30개 내외의 방위각 | 같은 방위각 집합에 대해 (a) SQL 파생 구간과 (b) Java `DirectionSegment.contains`로 각각 구간 키를 구해 대조 | 모든 방위각에서 두 결과가 일치. 판정 로직이 두 곳에 존재하는 위험을 이 테스트가 고정한다 | 동일 |
| …-INT-004 | `countByDirection` | 수신 항목이 `N`과 `E` 방향에만 존재 | 칩 집계 | 반환 칩이 정확히 2개. 나머지 6방향은 count 0으로도 나오지 않는다 | 동일 |
| …-INT-005 | `countByDirection`(양쪽 카테고리) | 미답변 항목은 `N` 방향 2건, 답변한 항목은 `S` 방향 1건 | `UNANSWERED`와 `ANSWERED`로 각각 집계 | `UNANSWERED`는 `N`만, `ANSWERED`는 `S`만. 두 칩 집합이 서로 섞이지 않는다 | 동일 |
| …-INT-006 | `findInbox`(필터), `countByDirection` | 3개 이상 방향에 걸친 수신 항목 다수(각 방향 건수가 서로 다르게) | 반환된 **모든** 칩을 순회하며 그 `segmentKey`로 `findInbox` 재호출 | 각 칩의 count가 해당 목록의 크기와 같다. 필터 없는 목록의 크기는 칩 count 합과 같다 | 동일 |
| …-INT-007 | `findInbox`, `countByDirection` | 같은 방향(`N`)에 정상 항목 1건, `SKIPPED` 1건, 만료된 질문글 1건, 발신자를 차단한 항목 1건 | `UNANSWERED` 목록과 칩을 함께 조회 | 목록은 1건, `N` 칩 count도 1. 제외 규칙이 두 쿼리에서 동일하게 적용된다 | 동일 |
| …-INT-008 | `findInbox`(필터), `countByDirection` | 수신 항목 여러 건 | `directionSegmentKey`에 스킴에 없는 값(`"XX"`) 전달 | 예외 없이 목록은 빈 리스트, 칩은 카테고리 전체 집계 그대로. 클라이언트가 옛 키로도 다른 방향으로 갈아탈 수 있다 | 동일 |
| …-INT-009 | `findInbox`(필터 없음), `countByDirection` | 정상 수신 항목 2건. 시드 `OCTANT` 스킴의 `status`를 `INACTIVE`로 변경 | 필터 없이 목록과 칩을 조회 | **목록은 2건 그대로**, 칩만 빈 리스트. 필터 없는 목록이 스킴 데이터에 의존하지 않음을 확인한다. 테스트 종료 시 스킴 상태 복원 | 스킴 `status` 복원 + delete |
| …-INT-010 | `countByDirection` | 시드 `OCTANT` 외에 다른 code(`TEST-OCTANT`)의 ACTIVE 스킴과 8개 세그먼트를 추가 삽입 | 칩 집계 | count가 두 배가 되지 않는다. 설정 `qello.direction.scheme-code`가 지정한 스킴만 참여한다 | 추가 스킴·세그먼트 delete |
| …-INT-011 | 기존 `InboxQueryIntegrationTest` 전 시나리오 | 시그니처 교체(`findInbox`에 `directionSegmentKey` 추가, `list`가 `InboxListing` 반환) 반영 | 기존 스위트 재실행 | 방향 필터를 `null`로 넘긴 호출에서 기존 assertion이 전부 그대로 통과. `list(...)`의 반환 타입 변경에 따른 `.cards()` 경유 외에 기대값 변화 없음 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- 목록과 칩은 같은 `@Transactional(readOnly = true)` 안에서 같은 `at`으로 조회한다.
  다만 격리 수준이 READ COMMITTED이므로 **두 쿼리는 서로 다른 스냅샷을 본다.** 조회
  도중 누군가 답변을 공개하거나 만료가 지나면 칩 count와 목록 건수가 순간적으로 1
  어긋날 수 있다. 테스트는 고정 `Instant`와 단일 스레드라 결정적이며, 이 한계는
  결함이 아니라 **알려진 동작**으로 보고서 §6에 기록한다. 막으려면 이 메서드만
  `REPEATABLE READ`로 올리거나 `GROUPING SETS`로 한 쿼리에 합쳐야 하는데, 이득 대비
  비용이 커 이번 범위에서 제외했다.
- 두 쿼리 모두 `post_recipient_inbox_idx (recipient_id, status, matched_at DESC)`를
  주도 인덱스로 쓴다. 세그먼트 매칭 표현식은 인덱스를 타지 않지만 이미 한 사용자로
  좁혀진 행에만 평가되므로(행 수 × 8) 계획 검증은 별도 시나리오로 두지 않는다.
- 0.5° 단위 전수 스윕을 하지 않는 이유: 방위각 하나마다 별도 `direction_post`가
  필요해(`uq_post_recipient_post_user`) 720개 글을 만들어야 하는데, 드리프트가 실제로
  드러나는 곳은 경계각이다. INT-002·INT-003의 경계 집중 검증이 같은 위험을 훨씬 싼
  비용으로 덮는다.

### Concurrency and idempotency

- 조회 전용이라 멱등하다. 같은 입력으로 반복 호출하면 같은 결과가 나온다.
- 동시 쓰기와의 경합은 위 스냅샷 항목이 유일한 노출 지점이며, 재현 가능한 동시성
  시나리오를 만들 만한 쓰기 경로가 이 이슈에 없다.

### External APIs

- 해당 없음. 외부 호출이 없다.

### Failure recovery and reconciliation

- 스킴 데이터 결손(ACTIVE 스킴 없음, 세그먼트가 360°를 못 덮음)에서도 **목록은
  온전해야 한다.** INT-009가 이 성질을 고정한다. 실패는 칩이 비는 형태로만 나타난다.
- 설정한 `scheme-code`에 해당하는 ACTIVE 스킴이 없으면 칩은 빈 리스트다. 예외를
  던지지 않는다 — 칩 하나 때문에 수신함 전체가 막히면 안 된다.

## 8. Test data and isolation

- Fixtures: `PostgisContainerIntegrationTestSupport`를 그대로 사용한다. 신규 통합
  테스트는 고유 `region_code`(`TEST-DIRCHIP`)를 써서 기존 `TEST-INBOXQ`와 겹치지
  않게 한다.
- 기존 `InboxQueryIntegrationTest.recipient(...)` 헬퍼는 `inbound_bearing_deg`를 225로
  하드코딩한다. 방향별 값이 필요한 신규 파일은 **자체 헬퍼를 갖고**, 기존 파일의
  헬퍼는 INT-011의 회귀 범위를 좁히기 위해 시그니처를 바꾸지 않는다.
- Database isolation: 기본(public) schema. `@BeforeEach`에서 자식→부모 순서로 delete
  (기존 관행 유지). INT-009·INT-010은 시드 `direction_scheme`을 건드리므로 **해당
  테스트 안에서 원상 복구**한다 — 시드 훼손이 다른 클래스로 새면 안 된다.
- Clock/randomness: 고정 `Instant` 상수만 사용한다. `Instant.now()`를 테스트에서 직접
  호출하지 않는다.
- External API doubles: 해당 없음.
- Cleanup: 전부 JDBC raw INSERT/DELETE.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor 1 (Direction — ACTIVE 스킴 선택 설정) | `src/main/java/com/dnd/qello/direction/config/DirectionSchemeProperties.java`(신규), `src/main/resources/application.properties`(`qello.direction.scheme-code` 추가) | 없음(Executor 2의 전제) | `./gradlew compileJava` |
| 2 | Executor 2 (Feed — 칩 집계 본체) | `.../feed/view/DirectionChip.java`(신규), `.../feed/view/InboxListing.java`(신규), `.../feed/repository/InboxQueryRepository.java`, `.../feed/repository/jdbc/sql/InboxQuerySql.java`, `.../feed/repository/jdbc/JdbcInboxQueryRepository.java`, `.../feed/service/InboxQueryService.java`, `src/integrationTest/java/com/dnd/qello/InboxDirectionChipIntegrationTest.java`(신규) | INT-001 ~ INT-010 | `./gradlew integrationTest --tests "com.dnd.qello.InboxDirectionChipIntegrationTest"` |
| 3 | Executor 3 (회귀와 view 단위) | `src/test/java/com/dnd/qello/feed/view/DirectionChipTest.java`(신규), `src/integrationTest/java/com/dnd/qello/InboxQueryIntegrationTest.java` | UNIT-001 ~ UNIT-004, INT-011 | `./gradlew test --tests "com.dnd.qello.feed.*"`, `./gradlew integrationTest --tests "com.dnd.qello.InboxQueryIntegrationTest"` |

Executor 1의 설정이 있어야 Executor 2가 조인 조건을 완성할 수 있다. Executor 3은
Executor 2의 시그니처 교체가 끝난 뒤에만 착수한다 — 세 실행 단계는 순차이며 병렬
구간이 없다. 소유 파일은 서로 겹치지 않는다.

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과 (160 tests, 0 failed)
- [x] 통합 테스트 통과 (153 tests, 0 failed)
- [x] 잠재 문제 분석 (`docs/reports/tests/gh-80-INBOX-DIRECTION-CHIPS.md` §6)
- [x] 테스트 보고서 생성
- [x] INT-009·INT-010이 건드린 시드 `direction_scheme`이 테스트 종료 후 원상
      복구됨 (`finally` 블록으로 확인)

## 11. Human approval

- Reviewer: Byuntil
- Decision: Approved
- Approved at: `2026-08-08T21:24:08+09:00`
