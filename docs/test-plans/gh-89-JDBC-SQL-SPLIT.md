# Test Plan: TEST-PLAN-GH-89-JDBC-SQL-SPLIT

> Created at: `2026-08-08T17:34:50+09:00`
> GitHub Issue: `#89`
> Status: Approved

## 1. Objective

이 작업은 새 비즈니스 로직을 추가하지 않는 순수 리팩터링이다 — 4개 `Jdbc*Repository`의
정적 SELECT/CTE SQL 텍스트 블록을 `sql/` 서브패키지의 상수 클래스로 옮기고, 동적 조합
로직(WHERE 필터, cursor, `StringBuilder` 조립)은 리포지토리에 남긴다. 새 자격 규칙이나
집계 로직을 검증할 대상이 없으므로, 이 계획의 목적은 "이동 자체가 기존 동작을 깨지
않았다"는 것을 값싸고 정확하게 증명하는 데 있다.

리팩터링 특유의 실패 모드는 사업 로직 버그가 아니라 **기계적 이동 사고**다.

1. 텍스트 블록을 다른 클래스·다른 들여쓰기 위치로 옮기며 컴파일된 SQL 문자열
   내용이 원본과 미묘하게 달라진다.
2. `JdbcInboxQueryRepository`(82~83행 기존 주석), `JdbcSentPostQueryRepository`,
   `JdbcPostAnswerQueryRepository`는 `SELECT_CARD`/`CAN_VIEW_ANSWERS_SQL` 뒤에
   문자열을 이어붙인다. 텍스트 블록 끝의 공백이 이동 중 사라지면 `AND` 뒤에
   토큰이 그대로 붙어(`ANDpr.status...`) SQL 구문 오류가 난다.
3. `sql/` 서브패키지로 옮긴 클래스·상수의 접근 제한자가 `public`이 아니면
   패키지 경계를 넘는 리포지토리에서 컴파일이 깨진다.
4. 상수 옆 WHY 주석(예: `distance_m`/`distance_band` 상호배제, `ST_Azimuth`
   인자 순서)이 이동 중 상수와 분리되거나 유실된다.

## 2. Scope

### Included

- 4개 대상(`JdbcInboxQueryRepository`, `JdbcSentPostQueryRepository`,
  `JdbcPostAnswerQueryRepository`, `JdbcActiveUserPresenceRepository`)의 SQL 상수를
  옮긴 뒤에도 이동 전과 동일한 SQL이 동일한 순서로 조합·실행되는지 검증
- 신규 `sql/` 서브패키지 클래스가 컴파일되고 원래 패키지에서 참조 가능한지 확인
- 기존 통합 테스트 스위트(Testcontainers 기반 실제 PostgreSQL/PostGIS)가 **수정 없이**
  그대로 통과하는지 확인 — 이것이 이 계획의 핵심 증거다

### Excluded

- 새 자격 규칙, 집계 로직, API 계약 검증 — 이 이슈는 그런 로직을 바꾸지 않는다
- `JdbcNotificationRepository`, `JdbcSafetyRepository` — 이슈 범위 밖(TASK.md 제외 항목)
- 실행 계획·성능 비교 — SQL 텍스트 내용이 동일하므로 실행 계획도 동일하다고 본다
- 신규 API/DTO/컨트롤러 테스트 — 이 이슈는 controller/endpoint를 추가하지 않는다
- MyBatis·jOOQ 등 신규 SQL 관리 방식 검증 — 채택하지 않기로 결정됨(TASK.md 제외 항목)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #89 | 4개 저장소의 정적 SELECT/CTE SQL을 `sql/` 서브패키지 상수 클래스로 이동한다. SQL 실행 결과와 파라미터 바인딩 동작은 바꾸지 않는다. |
| TASK.md — Scope/이동 원칙 | 상수 이름 불변, 동적 조합 로직은 리포지토리에 유지, WHY 주석은 상수와 함께 이동 |
| TASK.md — Completion criteria | 기존 단위/통합 테스트가 수정 없이 그대로 통과해야 한다 |
| ADR-0002 (`docs/adr/0002-jpa-jdbc-boundary.md`) | raw JDBC를 선택한 이유 — "SQL 의도와 lock 대상이 코드에서 가려지지 않게" 한다. 이동 후에도 이 원칙(주석이 SQL 옆에 남아 있어야 함)이 지켜져야 한다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 텍스트 블록을 옮기며 상대 들여쓰기가 바뀌어 컴파일된 SQL 문자열 내용이 원본과 달라짐 | Low~Medium | Medium | P1 | `git diff`에서 SQL 상수 리터럴이 순수 이동(추가·삭제 없음)인지 리뷰 + 통합 테스트 통과 |
| `SELECT_CARD`/`CAN_VIEW_ANSWERS_SQL` 뒤 문자열 concat 경계에서 공백이 유실돼 토큰이 붙음 (`ANDpr.status...`) | High — SQL 구문 오류로 요청 자체가 실패 | Medium | P0 | InboxQueryIntegrationTest·SentPostQueryIntegrationTest·PostAnswerQueryIntegrationTest의 WHERE 절 경로(카테고리 필터·cursor·`canViewAnswers`)가 여전히 성공한다 |
| 새 `sql/` 서브패키지 클래스·상수가 `public`이 아니어서 컴파일 실패 | High이지만 즉시 발견됨 | Low | P2 | `./gradlew compileJava` |
| WHY 주석이 상수와 분리되거나 유실됨 | Medium — 지금 동작엔 영향 없지만 다음 사람이 근거를 잃음 | Medium | P2 | 코드 리뷰(diff) — 자동화 테스트로는 검증 불가, 리뷰 체크리스트로 남김 |
| 동적 조합 로직(WHERE switch, cursor, `StringBuilder`)까지 실수로 Sql 클래스로 옮겨 TASK.md 이동 원칙을 벗어남 | Low — 동작은 유지되지만 설계 원칙 위반 | Low | P2 | 코드 리뷰 |

## 5. Unit scenarios

새 비즈니스 로직이 없으므로 신규 단위 테스트 시나리오는 없다. 유일한 단위 수준
검증은 컴파일 성공 여부다 — 이는 위험 항목 3(접근 제한자 실수)의 직접 증거다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-UNIT-001 | 4개 `*Sql` 클래스가 각 feature의 `repository/jdbc/sql/` 서브패키지에 있고, 4개 리포지토리가 이를 import한다 | `./gradlew compileJava`를 실행하면 | 컴파일 오류 없이 성공한다(접근 제한자·import 경로가 올바름을 의미) | P0 | Feed / Direction executor |

기존 단위 테스트(예: `AnswerJdbcBoundaryTest`의 feature 경계 검사)는 이 4개 저장소를
대상으로 하지 않으므로 영향받지 않는다 — 그대로 재실행만 한다.

## 6. Integration scenarios

이 4개 통합 테스트 클래스는 이미 대상 SQL 상수가 만드는 정확한 쿼리 경로(근거리
하한 CASE, 상관 서브쿼리 집계, WHERE 동적 조합, PostGIS CTE)를 실제 컨테이너
DB로 검증하고 있다. **신규 시나리오를 추가하지 않고, 다음 기존 시나리오 전체를
무수정 재실행하는 것 자체가 이 계획의 통합 테스트 증거다.**

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-001 | `JdbcInboxQueryRepository` + 신규 `InboxQuerySql` | 기존 Testcontainers PostgreSQL 픽스처(변경 없음) | `src/integrationTest/.../InboxQueryIntegrationTest.java`의 기존 11개 시나리오를 그대로 재실행 (근거리 하한 CASE, unread 집계, 카테고리 필터+WHERE 동적 조합, 차단 필터 등) | 11개 전부 이동 전과 동일하게 통과 | Testcontainers 자동 정리(기존과 동일) |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-002 | `JdbcSentPostQueryRepository` + 신규 `SentPostQuerySql` | 기존 Testcontainers PostgreSQL 픽스처(변경 없음) | `src/integrationTest/.../SentPostQueryIntegrationTest.java`의 기존 6개 시나리오를 그대로 재실행 (필터·커서 페이징·집계·차단 필터) | 6개 전부 이동 전과 동일하게 통과 | Testcontainers 자동 정리(기존과 동일) |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-003 | `JdbcPostAnswerQueryRepository` + 신규 `PostAnswerQuerySql` | 기존 Testcontainers PostgreSQL 픽스처(변경 없음) | `src/integrationTest/.../PostAnswerQueryIntegrationTest.java`의 기존 6개 시나리오를 그대로 재실행 (열람 자격 판정, 시점 의존 만료, 차단 필터) | 6개 전부 이동 전과 동일하게 통과 | Testcontainers 자동 정리(기존과 동일) |
| TEST-PLAN-GH-89-JDBC-SQL-SPLIT-INT-004 | `JdbcActiveUserPresenceRepository` + 신규 `ActiveUserPresenceSql` | 기존 Testcontainers PostgreSQL+PostGIS 픽스처(변경 없음) | `src/integrationTest/.../DirectionPostgisPersistenceIntegrationTest.java`의 PostGIS 후보 쿼리 관련 시나리오를 그대로 재실행 | 이동 전과 동일하게 통과 | Testcontainers 자동 정리(기존과 동일) |

## 7. Cross-cutting scenarios

### Database and transactions

- 해당 없음 — 스키마·트랜잭션 경계를 바꾸지 않는다. 기존 Flyway 마이그레이션(V1~V8)을
  그대로 쓴다.

### Concurrency and idempotency

- 해당 없음 — 락 대상이나 커밋 순서를 바꾸지 않는다. `SELECT ... FOR UPDATE`,
  조건부 `UPDATE` 같은 동시성 계약이 있는 코드는 이번 이동 대상에 없다.

### External APIs

- 해당 없음 — 4개 대상 저장소는 외부 API를 호출하지 않는다.

### Failure recovery and reconciliation

- 해당 없음 — Outbox·재시도 로직은 이번 이동 대상에 없다.

## 8. Test data and isolation

- Fixtures: 기존 4개 통합 테스트 클래스가 이미 쓰는 Testcontainers 기반
  PostgreSQL/PostGIS 픽스처를 그대로 사용한다. 신규 픽스처를 만들지 않는다.
- Database isolation: 기존 각 테스트 클래스/메서드의 격리 전략을 그대로 따른다(변경 없음).
- Clock/randomness: 해당 없음 — 시간 의존 로직을 추가하지 않는다.
- External API doubles: 해당 없음.
- Cleanup: Testcontainers 컨테이너 라이프사이클(기존 설정) 그대로.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 (병렬) | Feed executor | `feed/repository/jdbc/JdbcInboxQueryRepository.java`, `feed/repository/jdbc/JdbcSentPostQueryRepository.java`, `feed/repository/jdbc/JdbcPostAnswerQueryRepository.java`, `feed/repository/jdbc/sql/InboxQuerySql.java`(신규), `feed/repository/jdbc/sql/SentPostQuerySql.java`(신규), `feed/repository/jdbc/sql/PostAnswerQuerySql.java`(신규) | UNIT-001(feed 부분), INT-001, INT-002, INT-003 | `./gradlew compileJava`, `./gradlew integrationTest --tests "*InboxQueryIntegrationTest" --tests "*SentPostQueryIntegrationTest" --tests "*PostAnswerQueryIntegrationTest"` |
| 1 (병렬) | Direction executor | `direction/repository/jdbc/JdbcActiveUserPresenceRepository.java`, `direction/repository/jdbc/sql/ActiveUserPresenceSql.java`(신규) | UNIT-001(direction 부분), INT-004 | `./gradlew compileJava`, `./gradlew integrationTest --tests "*DirectionPostgisPersistenceIntegrationTest"` |
| 2 | Test orchestrator(본 계획 승인자) | 코드 소유 없음 — 최종 게이트만 실행 | 전체 재확인 | `./gradlew test`, `./gradlew integrationTest`, `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |

Feed executor와 Direction executor는 서로 다른 파일을 소유하므로 병렬 진행 가능하다.

## 10. Completion criteria

- [x] 새 Sql 클래스 4개가 이동 원칙(정적 SELECT/CTE만 포함, 동적 조합 로직 제외,
      상수명 불변, WHY 주석 보존)을 지킨다
- [x] `git diff`에서 SQL 상수 리터럴 내용이 이동 전과 동일함을 리뷰로 확인한다
      (순수 위치 이동 — 원본 파일에서 `sed`로 해당 줄 범위를 그대로 추출해
      새 클래스에 옮겼고, 상대 들여쓰기가 달라진 `ActiveUserPresenceSql`만
      메서드 내부(2탭 본문/3탭 SQL) → 클래스 필드(1탭 필드/2탭 SQL)로 정확히
      한 탭씩 일괄 이동해 재계산 없이 동일 문자열을 보존했다)
- [x] `./gradlew compileJava`가 통과한다 (UNIT-001)
- [x] 4개 통합 테스트 클래스(INT-001~004)가 무수정으로 전부 통과한다
- [x] `./gradlew test`, `./gradlew integrationTest` 전체가 통과한다
- [x] `./harness check`, `./harness pr-ready --project-tests`가 통과한다
- [x] 잠재 문제 분석 — §4 위험 항목을 구현 후 재확인했다. 위험 항목 2(문자열
      concat 경계 공백 유실)는 4개 통합 테스트 스위트가 무수정 통과해 배제됐다.
      위험 항목 1(들여쓰기로 인한 내용 변질)·3(접근 제한자)은 위 diff 검증과
      compileJava 통과로 배제됐다. 위험 항목에 없던 **새 위험**이 하나
      발견됐다 — `FeedPersistenceBoundaryTest`가 다른 feature 이름을 명시하지
      않고 `.repository.jdbc.` 부분 문자열만으로 경계를 검사해서, feed 자신의
      신설 서브패키지(`feed.repository.jdbc.sql`)까지 "다른 feature 침투"로
      오탐했다. `AnswerJdbcBoundaryTest`가 이미 쓰던 "다른 feature 이름 명시"
      패턴으로 그 테스트를 고쳐 해결했다(검증 의도는 유지, 오탐만 제거).
- [x] 테스트 보고서 생성 — `docs/reports/tests/gh-89-jdbc-sql-split.md` 참고

## 11. Addendum — 2차 확장 (긴 단순 CRUD SQL, 12줄 기준)

구현 중 추가 피드백을 받아, 서브쿼리 유무와 무관하게 "한 메서드의 SQL 리터럴 합이
12줄을 넘으면 상수로 뺀다"는 기준으로 범위를 넓혔다(TASK.md §Scope 참고). 대상은
`JdbcDirectionPostRepository.save`, `JdbcDirectionSchemeRepository.save`·
`saveSegment`, `JdbcPostAudienceRepository.save`, `JdbcPostRecipientRepository.save`,
`JdbcActiveUserPresenceRepository.save` 5곳이다.

이 5곳은 위험 성격이 1차와 다르다 — 서브쿼리·CTE가 없는 단순 `INSERT`/`UPDATE`라
"의미가 훼손될 정도로 복잡한 SQL"은 아니다. 대신 4곳이 `id == null ? INSERT :
UPDATE` 삼항 분기를 쓰는데, 텍스트 블록 두 개가 `""" : """`처럼 한 줄에 붙어 있어
분리 추출 시 실수로 한쪽 블록의 내용이 다른 쪽에 섞일 위험이 1차보다 크다.

### 회귀 커버리지 확인(신규 시나리오 없이 기존 테스트로 검증)

각 상수가 기존 테스트에서 실제로 실행되는지 확인했다. **id가 있는 UPDATE 분기
두 곳은 현재 애플리케이션 코드에 호출부 자체가 없어 이번 리팩터링 이전부터
테스트되지 않던 죽은 경로다** — 이 갭은 새로 만든 게 아니라 이동하면서 발견한
기존 상태이므로, 여기서 새로 테스트를 추가하지 않았다(범위 밖 판단).

| 상수 | 실행 여부 | 근거 |
| --- | --- | --- |
| `DirectionPostSql.INSERT` | 실행됨 | `DirectionPostgisPersistenceIntegrationTest`, `InboxSentPostWriteIntegrationTest`가 `DirectionPostService`의 send 플로우를 통해 호출 |
| `DirectionPostSql.UPDATE` | **미실행** | `DirectionPost.save()`를 기존 id로 호출하는 애플리케이션 코드가 없다(리팩터링 이전부터) |
| `DirectionSchemeSql.SCHEME_INSERT` / `SEGMENT_INSERT` | 실행됨 | `DirectionPostgisPersistenceIntegrationTest`가 테스트 픽스처로 `schemeRepository.save`/`saveSegment` 호출(항상 신규 id) |
| `DirectionSchemeSql.SCHEME_UPDATE` / `SEGMENT_UPDATE` | **미실행** | scheme/segment를 기존 id로 갱신하는 애플리케이션 코드가 없다(리팩터링 이전부터) |
| `PostAudienceSql.UPSERT` | 실행됨 | 위 send 플로우가 `audienceRepository.save` 호출(`ON CONFLICT`라 분기 없이 단일 상수) |
| `PostRecipientSql.INSERT` | 실행됨 | 위 send 플로우가 신규 수신자 스냅샷 저장 시 호출 |
| `PostRecipientSql.UPDATE` | 실행됨 | `InboxSentPostWriteIntegrationTest`가 `PostRecipientService`의 open/skip/revertSkip 플로우를 통해 기존 id로 호출 |
| `ActiveUserPresenceSql.UPSERT` | 실행됨 | `DirectionPostgisPersistenceIntegrationTest`가 테스트 픽스처로 위치 저장 시 호출(`ON CONFLICT`) |

### 완료 조건(2차)

- [x] 5곳의 SQL이 `sql/` 서브패키지로 옮겨지고 삼항 분기는 리포지토리에 남는다
- [x] `""" : """` 한 줄 분리 지점에서 두 블록의 내용이 섞이지 않았다 — `sed`로
      독립된 줄 범위만 추출해 검증 없이 섞일 수 없는 방식으로 작업했다
- [x] `./gradlew compileJava`, `test`, `integrationTest` 전체 통과
- [x] `./harness check`, `./harness pr-ready --project-tests` 통과
- [x] UPDATE 계열 미실행 경로 2곳을 숨기지 않고 위 표에 명시했다

## 12. Human approval

- Reviewer: Byuntil
- Decision: Approved
- Approved at: `2026-08-08T17:39:36+09:00`
- 2차 확장 승인: Byuntil, 2026-08-08(세션 내 — 12줄 기준과 대상 5곳을 사용자가
  직접 확정)
