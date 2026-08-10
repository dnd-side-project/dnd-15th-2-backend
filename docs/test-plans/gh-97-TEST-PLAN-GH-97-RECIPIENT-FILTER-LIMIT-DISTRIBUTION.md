# Test Plan: TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION

> Created at: `2026-08-10T22:48:33+09:00`
> GitHub Issue: `#97`
> Status: Approved — implementation executed; targeted verification PASS

## 1. Objective

방향 글 발송이 후보 전체를 수신자로 확정하지 않고, 현재 차단·계정 상태·수신
이력·설정된 발송별 인원 상한을 함께 적용하는지 검증한다. 답변 열람도 같은
차단 관계를 재확인해 차단된 상대가 매칭 결과나 답변 내용으로 다시 연결되지
않아야 한다.

실패하면 차단된 사용자에게 질문이 노출되거나, BLOCKED/DELETED 계정에 배달되고,
가까운 사용자에게만 수신이 집중되며, 한 발송의 수신자 수가 정책 상한을 넘을 수
있다. 후보 선정 SQL의 결과와 `post_recipient`, `recipient_receive_state`의
트랜잭션 결과가 함께 맞아야 한다.

## 2. Scope

### Included

- `ActiveUserPresenceSql.FIND_CANDIDATES_SQL`의 양방향 활성 차단 필터
- `user_account.status = 'ACTIVE'` 계정 필터
- `recipient_receive_state.recent_received_count` 우선 정렬과 결정론적 tie-break
- 발송별 수신자 인원 상한을 설정값으로 읽고 적용하는 경로
- 답변 열람 자격 SQL의 양방향 활성 차단 필터
- 실제 PostgreSQL/PostGIS 후보 조회와 `DirectionPostService.send()` 결과
- 기존 인덱스·쿼리 계획·transaction rollback·동시 발송 회귀 검증

### Excluded

- 단계적 추가 선정 대기 시간(D18)과 부족한 후보를 보충하는 후속 선정 워커
- 지도 마커 집계, 푸시/Outbox/API/controller/auth 변경
- Flyway migration 및 운영 데이터 백필. 인덱스 추가가 필요하면 별도 Issue로 분리
- 수신자 1명당 `qello.direction.receive-capacity` 슬롯 상한의 의미·기본값 변경
- 인프라 apply, 배포, 운영 DB 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #97 | 양방향 차단·비활성 계정 제외, 발송별 후보 상한, 최근 수신 적은 사용자 우선, 상한 설정값, 답변 열람 차단, 통합 테스트 |
| `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md` 핵심 불변식 11·17·21 | 활성 차단 관계는 매칭·조회에서 제외하고, 활성 미처리 슬롯 상한과 최근 수신이 적은 후보부터 제한된 인원을 선정한다. |
| `src/main/resources/db/migration/V1__create_direction_communication_schema.sql` / V2 | `user_block`의 PK·reverse index, `recipient_receive_state`의 selection index와 안전 상한 50을 변경하지 않고 활용한다. |
| `FeedScopeSql` / `PostAnswerQuerySql` | 답변 열람은 질문자 또는 수신 자격자이며, explicit `at` 기반 상태·만료 규칙은 유지한다. 차단 조건만 양방향으로 확장한다. |

### Decision-needed before implementation

| ID | Decision | Why it changes the test oracle |
| --- | --- | --- |
| DEC-001 | **RESOLVED** — `qello.direction.max-recipients-per-post`, MVP 기본값 10, 1 이상 설정 가능 | 기존 `receive-capacity`는 사용자별 활성 미처리 슬롯 상한이라 재사용하지 않는다. |
| DEC-002 | **RESOLVED** — 후보를 공정성 순으로 순회하며 슬롯 예약 성공자를 최대 10명까지 확정한다 | 개인 슬롯이 가득 찬 상위 후보 때문에 발송 상한을 낭비하지 않고 후순위 후보를 확인한다. |
| DEC-003 | **RESOLVED** — `recent_received_count ASC, last_received_at ASC NULLS FIRST, distance_m ASC, user_id ASC` | 랜덤 정렬 없이 공정성을 우선하고 거리와 ID로 재현성을 보장한다. |
| DEC-004 | **RESOLVED** — viewer와 `direction_post.sender_id` 사이 어느 방향이든 활성 차단이면 `canView=false` | 후보 선정과 답변 열람의 차단 전파 범위를 일치시킨다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 한 방향의 `user_block`만 검사해 차단당한 쪽이 다시 후보로 들어간다. | 차단 전파 실패·안전 정책 위반 | High | High | 양방향 각각의 PostGIS 통합 fixture |
| `BLOCKED`/`DELETED` 계정이 presence만 유효해 후보로 선정된다. | 비활성 계정에 질문 배달 | High | Medium | account status별 후보 결과 |
| 발송별 상한이 기존 사용자별 슬롯 상한과 혼동된다. | 후보 전체 배달 또는 잘못된 설정 변경 | High | High | 설정 단위 독립성 단위·통합 테스트 |
| 상한 적용 위치가 예약 실패 후보를 보충하지 못하거나 초과 확정한다. | 수신자 수 부족 또는 상한 위반 | High | Medium | DEC-002 승인 oracle과 send end-to-end |
| `recent_received_count`만 정렬하고 동률 순서가 비결정적이다. | 특정 사용자 집중·flaky 결과·재현 불가 | Medium | Medium | 동일 count/거리 fixture와 반복 조회 |
| `CAN_VIEW_ANSWERS_SQL`만 한 방향 또는 답변 작성자 조건만 확인한다. | 차단된 상대의 답변 내용 노출 | High | Medium | 양방향 `canView` 및 `answers` 회귀 |
| 정렬·필터 조인으로 PostGIS spatial index 또는 기존 block reverse index를 잃는다. | 후보 조회 지연·DB 부하 | Medium | Medium | Testcontainers `EXPLAIN (FORMAT JSON)`과 인덱스 존재 확인 |
| 후보 선정과 슬롯 예약/recipient 저장이 분리되어 부분 반영된다. | 카운터와 실제 수신자 행 drift | High | Low | 실패 주입 rollback 및 count reconciliation |
| 동시 발송에서 같은 후보가 상한을 초과하거나 post별 unique가 중복된다. | 과배달·중복 수신 | High | Medium | 서로 다른 sender/key 동시 send와 DB 재조회 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-UNIT-001 | 발송별 상한 설정값과 기존 사용자별 `receive-capacity` 설정값 | 설정 객체를 생성·바인딩한다 | 두 값이 서로 다른 정책으로 검증되고, 0 이하 값은 거절되며 기본값은 10이다 | P0 | Direction config executor |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-UNIT-002 | 후보 선정 SQL과 답변 열람 SQL source | SQL 경계 계약을 검사한다 | 후보 SQL이 `user_account.status`, 양방향 활성 `user_block`, `recipient_receive_state`와 limit/order 계약을 포함하고, 답변 SQL이 같은 양방향 차단 정책을 사용한다. 기존 explicit `:at`·recipient eligibility는 유지한다 | P0 | Boundary-test executor |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-UNIT-003 | `recent_received_count`가 같은 후보와 null/동일 시각 `last_received_at` | 승인된 tie-break를 적용한다 | 정렬 결과가 매번 동일하고 최종 `user_id`까지 결정론적이다. 랜덤/현재 시각 기반 정렬은 사용하지 않는다 | P0 | Direction SQL executor |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-UNIT-004 | 비활성 계정 status 값 `BLOCKED`, `DELETED`와 `ACTIVE` | 후보 eligibility 계약을 검사한다 | `ACTIVE`만 후보 허용 대상이고 status 문자열을 임의의 presence flag로 대체하지 않는다 | P1 | Direction SQL executor |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-UNIT-005 | 상한보다 많은 후보와 상한 이하 후보 | 후보 제한 contract를 검증한다 | 승인된 DEC-002 기준으로 예약 성공자 수가 설정값을 넘지 않으며, 상한 0/음수 입력은 설정 계층에서 거절된다 | P0 | Direction config executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-001 | Flyway, `JdbcActiveUserPresenceRepository`, `user_account`, `user_block`, PostGIS | 동일 region·sector 안에 ACTIVE 후보, BLOCKED/DELETED 후보, 양방향 활성 차단 후보, 해제된 차단 후보와 sender presence를 seed한다 | `findCandidates()` 또는 승인된 후보 조회 경로를 고정 `at`으로 호출한다 | 자기 자신·만료·수신 불가·비활성 계정·양방향 활성 차단 후보는 제외되고, 해제된 차단과 정상 ACTIVE 후보만 결과에 남는다 | 관련 user_block → presence → account → region 역순 삭제 |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-002 | `DirectionPostService.send()`, candidate SQL, `recipient_receive_state`, `post_recipient` | 정상 후보를 설정 상한보다 많이 만들고 각 후보에 다른 `recent_received_count`를 seed한다. 상한 설정은 10으로 주입한다 | 서로 다른 idempotency key로 발송한다 | 공정성 순서로 후보를 순회해 예약 성공자를 최대 10명까지 확정한다. 어떠한 경우에도 `post_recipient` 수가 10명을 넘지 않고, `recipient_receive_state.active_unhandled_count`는 실제 신규 recipient 행과 일치한다 | post_recipient → audience → post → receive state → presence → user 정리 |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-003 | candidate SQL, `recipient_receive_state` selection index | 거리와 sector가 같은 후보들을 `recent_received_count` 0/1/2와 동률 lastReceivedAt으로 만든다 | 같은 입력을 여러 번 조회하고 send 결과를 비교한다 | 최근 수신 횟수가 작은 후보가 먼저 선택되고, 동률은 DEC-003의 순서로 재현된다. 가까운 거리만으로 결과가 결정되지 않는다 | INT-002와 동일 |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-004 | Spring Boot config binding, `DirectionPostService` | 동일 코드에서 발송별 상한 설정을 A와 B로 바꾼 두 격리 context를 준비한다 | 후보 수가 A보다 큰 발송, B보다 큰 발송을 각각 실행한다 | 설정 변경만으로 확정 recipient 수가 바뀌고, 기존 사용자별 `receive-capacity` 값을 바꾸지 않아도 발송별 상한 검증이 독립적으로 동작한다 | context/container lifecycle |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-005 | `PostAnswerQueryService`, `JdbcPostAnswerQueryRepository`, `PostAnswerQuerySql`, `user_block` | sender와 recipient가 답변 열람 자격을 가진 post를 만들고 sender↔viewer 차단을 한 방향씩 별도로 seed한다 | 각 방향의 active block 상태에서 `canView()`와 `answers()`를 호출하고, block release 후 재호출한다 | 어느 방향의 활성 차단도 `canView=false` 및 빈 답변 결과를 만들며, `released_at`이 채워진 관계는 기존 자격 규칙으로 복귀한다 | answer/post_recipient/post/user_block/account 정리 |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-006 | candidate SQL + send transaction + DB constraints | 상한 후보, 차단 후보, 슬롯 상한 도달 후보를 함께 만들고 DEC-002 승인 상태를 고정한다 | send를 수행한 뒤 post/audience/recipient/state를 모두 재조회한다 | 선정되지 않은 후보에 recipient row가 없고, 예약되지 않은 후보의 상태가 갱신되지 않으며, 성공한 예약 수·recipient 수·상한 결과가 일치한다 | INT-002와 동일 |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-007 | PostgreSQL planner, existing indexes | 충분한 presence/account/block/receive-state 행을 만들고 후보 SQL을 `EXPLAIN (FORMAT JSON)`으로 실행한다 | 필터·정렬·limit 전후 계획과 인덱스 목록을 확인한다 | 기존 spatial index와 `user_block_reverse_idx`/selection index 활용 가능성이 유지되고, schema migration 없이 쿼리가 실행된다. 실제 planner 선택은 환경에 따라 기록하고 강제하지 않는다 | container 폐기 |
| TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION-INT-008 | `DirectionPostService.send()`, transaction manager, `post_recipient` unique | 서로 다른 sender가 같은 상위 후보를 공유하도록 구성하고, 동시 실행용 pool 여유를 확인한다 | `CountDownLatch`로 두 send를 동시에 실행한다 | 발송별 수신자 상한을 넘지 않고, 동일 post-recipient 중복과 count drift가 없으며, 실패 시 post/audience/recipient/state가 부분 커밋되지 않는다 | 모든 방향 테이블을 FK 역순 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- H2가 아닌 기존 PostgreSQL/PostGIS Testcontainers를 사용한다. `user_account` 조인,
  partial block index, selection index, geography predicate를 실제 DB에서 실행한다.
- `direction_post`·`post_audience`·`post_recipient`·`recipient_receive_state`가
  같은 send transaction에 속하는지 확인하고, limit/filter SQL 자체의 결과와
  reserve 후 최종 recipient 결과를 분리해 관찰한다.
- schema/Flyway 파일은 수정하지 않는다. 인덱스가 없거나 planner가 구조적으로
  비효율적이면 구현에 포함하지 않고 별도 Issue/승인으로 반환한다.

### Concurrency and idempotency

- `#94`에서 확정한 `reserve()`의 신규 행 생성 원자성과 기존 row update를 회귀시킨다.
  이 계획은 `SAVE`/`RELEASE` SQL을 변경하는 테스트를 소유하지 않는다.
- 동일 sender/idempotency key 재시도는 기존 결과를 반환하고 recipient 수와
  `recent_received_count`를 증가시키지 않아야 한다.
- 서로 다른 발송의 공통 후보 경쟁에서는 발송별 limit과 사용자별 active slot을
  각각 검사한다. 후보 순회는 예약 성공자 10명에서 멈추며, `reserve()` 실패 후보는
  후순위 후보의 기회를 막지 않는다.

### External APIs

- 외부 API와 push 연동은 없다. PostGIS는 Testcontainers 내부의 실제 DB 경계이며
  mock 외부 서비스는 사용하지 않는다.

### Failure recovery and reconciliation

- 후보 필터 또는 recipient insert가 실패하면 post/audience/recipient/state가
  부분 커밋되지 않고 새 transaction에서 안전하게 재시도되어야 한다.
- 실패 후 `count(post_recipient)`와 `active_unhandled_count`, 선정된 사용자별
  `recent_received_count`를 비교해 drift가 없는지 확인한다.
- `EXPLAIN` 출력·테스트 보고서에는 실제 계정 식별자·URL·secret을 기록하지 않는다.

## 8. Test data and isolation

- Fixtures: 고유 test region, sender, ACTIVE/BLOCKED/DELETED 계정, 양방향 active/released
  block pair, presence, ACTIVE question, 8-segment scheme, receive-state count 0/1/2와
  slot-full 후보, 발송별 상한보다 많은 후보를 사용한다.
- Database isolation: 기존 `PostgisContainerIntegrationTestSupport`와 `@ActiveProfiles("test")`;
  각 테스트 고유 region·idempotency key를 사용하고 `@BeforeEach`에서 FK 역순 삭제한다.
- Clock/randomness: `Instant.parse(...)` 고정값과 명시적 `at`; 랜덤 정렬·현재 DB 시각을
  결과 oracle로 사용하지 않는다.
- External API doubles: 없음.
- Cleanup: `user_block`·`post_recipient`·`post_audience`·`direction_post`·
  `recipient_receive_state`·`active_user_presence`·question·account·region 순서를
  준수한다. 다른 테스트 seed는 삭제하지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Config/boundary test executor | `src/test/java/com/dnd/qello/direction/config/**` 및 새 recipient-selection boundary test 파일 | UNIT-001, UNIT-002, UNIT-005 | `./gradlew test --tests "*RecipientSelection*Test"`와 관련 boundary test |
| 2 | Candidate integration executor | 신규 `src/integrationTest/java/com/dnd/qello/DirectionRecipientSelectionIntegrationTest.java` 및 해당 fixture 내부 코드 | INT-001~004, INT-006~008 | `./gradlew integrationTest --tests "com.dnd.qello.DirectionRecipientSelectionIntegrationTest"` |
| 3 | Answer visibility executor | `src/integrationTest/java/com/dnd/qello/PostAnswerQueryIntegrationTest.java` 내 #97 시나리오만 추가, 기존 다른 시나리오 보존 | INT-005 | `./gradlew integrationTest --tests "com.dnd.qello.PostAnswerQueryIntegrationTest"` |
| 4 | Independent verification executor | production 변경 없음; `docs/reports/tests/gh-97-TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION.md` | 모든 P0, rollback·planner·잠재 문제 | `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check` |

실행 에이전트는 승인된 계획의 소유 파일 밖을 수정하지 않는다. production SQL의
최종 파일명이나 신규 설정 클래스가 달라지면 계획 승인 전에 소유 범위를 갱신한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 관련 targeted 통합 테스트 통과
- [x] 잠재 문제 분석
- [x] 테스트 보고서 생성
- [x] DEC-001~DEC-004가 승인된 구현 기준으로 기록됨
- [x] 양방향 차단·ACTIVE 계정·상한·분산 정렬 P0 통합 시나리오 통과
- [ ] 발송별 상한과 사용자별 `receive-capacity`가 독립적으로 검증됨
- [ ] 동시 send·멱등 재시도·rollback 후 count reconciliation 증거 확보
- [ ] 기존 schema/index를 변경하지 않음
- [x] `templates/test-report.md` 기반 보고서에 실행하지 못한 검증과 잔여 위험 기록

## 11. Human approval

- Reviewer: User
- Decision: Approved
- Approved at: 2026-08-10
