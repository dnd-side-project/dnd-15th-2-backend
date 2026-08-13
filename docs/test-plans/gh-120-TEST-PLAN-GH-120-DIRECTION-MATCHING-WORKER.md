# Test Plan: TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER

> Created at: `2026-08-13T17:14:04+09:00`
> GitHub Issue: `#120`
> Status: Approved for test implementation and execution — user approval received on 2026-08-13

## 1. Objective

`RECIPIENT_MATCH_REQUESTED`를 처리하는 worker가 질문글 제출 당시의 예상 후보가
아니라 실행 시점의 서버 권위 조건으로 수신자를 다시 계산하고, 실제 확정된
`PostRecipient`와 수신 슬롯·최근 수신량·후속 Outbox를 원자적으로 일치시키는지
검증한다.

가장 큰 실패 위험은 동시 worker나 재시도 때문에 슬롯만 증가하거나 수신자가
중복되는 것, moderation 전 또는 만료 후 질문글이 노출되는 것, stale lease가 새
worker의 결과를 덮어쓰는 것이다. 이 계획은 해당 위험을 실제 PostgreSQL/PostGIS의
row lock, unique constraint, transaction rollback과 lease fencing으로 검증한다.

## 2. Scope

### Included

- #119 event-type claim으로 `RECIPIENT_MATCH_REQUESTED`만 점유하는 batch worker 경계
- claim transaction과 event별 matching transaction 분리
- 질문글 `FOR UPDATE`, 상태·deadline 선택 A·moderation gate 재검증
- 최초 `PostAudience` 위치·방향·거리 스냅샷 기반 PostGIS 후보 재계산
- 실행 시점 ACTIVE 계정, current/receive-allowed presence, 양방향 차단, region 필터
- 누락 `recipient_receive_state` 멱등 초기화와
  `FOR UPDATE OF recipient_receive_state SKIP LOCKED`
- 승인된 공정성 순서와 `max-recipients-per-post`/`receive-capacity` 상한
- `ON CONFLICT DO NOTHING` 신규 recipient만 slot/recent count 증가
- recipient별 `RECIPIENTS_CONFIRMED` Outbox와 정확 좌표 비노출
- 질문글 `ACTIVE` 또는 `EXPIRED` 전이와 원본 matching Outbox fenced 완료
- retryable/permanent/stale lease 분류, rollback, 중복·동시 실행 복구

### Excluded

- #115/#119의 migration, generic Outbox claim/fencing/retry policy 재구현
- scheduler, polling loop, 운영 기본 batch/lease/retry 숫자와 production 자동 기동
- #137 moderation job/callback/미디어 검사와 실제 사용자 flow activation
- #123 인앱 `Notification` fan-out, `notification_delivery`, FCM/APNs 호출
- 단계적 추가 매칭 round, 보충 매칭과 수신자 목표 인원 보장
- 신규 Flyway migration/index, REST API/OpenAPI 변경
- 운영 합성 데이터 성능 검증(#127)

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #120 | MatchRequested 점유, 질문글·안전·만료·차단 재검증, PostGIS 재계산, 공정 선정, state lock, 신규 recipient만 count 증가, recipient별 후속 Outbox를 같은 transaction에서 수행한다. |
| User decision, 2026-08-13 | deadline 선택 A: `expires_at <= worker now`이면 매칭하지 않는다. |
| GitHub Issue #137 | `moderation_status = PASSED`만 실제 매칭 가능하다. #120 구현은 선행 가능하지만 production activation은 #137 이후다. 글·미디어+글·미디어의 moderation 연결 자체는 #137 범위다. |
| GitHub Issue #123 | #120은 recipient별 `RECIPIENTS_CONFIRMED` 작업까지만 만들고 실제 `Notification` fan-out은 수행하지 않는다. |
| #118 / `DirectionPostService.send()` | 제출 transaction은 post·audience·round 1 MatchRequested만 저장하고 recipient/slot은 변경하지 않는다. worker는 preview나 payload의 좌표를 사용하지 않는다. |
| #119 / `OutboxEventRepository` | non-empty event type claim, owner·generation·유효 lease fencing과 injected retry policy를 재사용한다. |
| 설계 §6.2·§10.2·§11.3·§14.4 | post lock → 현재 후보 재계산 → receive state lock → 신규 recipient → 성공자 count → 후속 Outbox → post ACTIVE → commit 순서와 `SKIP LOCKED`를 지킨다. |
| V1/V2/V12 schema | `(post_id, recipient_id)`와 Outbox dedup/round unique, receive count 안전 상한, lease 상태 제약을 변경하지 않고 활용한다. |
| Approved #97 ordering | `recent_received_count ASC, last_received_at ASC NULLS FIRST, distance_m ASC, user_id ASC`의 결정론적 순서를 유지한다. |
| `AGENTS.md` §3·§11 | JUnit 5, unit/integration 분리, `@DisplayName`, 정확한 timestamp/source scenario, 실행 증거와 잠재 문제 분석을 요구한다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| preview 또는 Outbox payload 후보를 재사용한다. | 발송 후 바뀐 위치·차단·계정 상태를 무시해 잘못 노출 | High | P0 | 제출 후 후보 조건을 변경하고 worker 결과가 현재 DB를 따르는 통합 테스트 |
| moderation 미통과 또는 deadline 도달 질문을 매칭한다. | 안전 미검사/만료 콘텐츠 노출 | High | P0 | PASSED 외 3개 상태와 `expires_at` 직전·경계 테스트 |
| recipient insert 실패자도 slot/recent count가 증가한다. | 용량 누수·영구 under-delivery | High | P0 | duplicate/replay와 failure injection 후 row/count reconciliation |
| 두 질문이 같은 마지막 슬롯을 함께 예약한다. | 사용자별 수신 상한 초과 | High | P0 | 독립 transaction 동시 matching과 최종 count/recipient 합 비교 |
| receive state가 없는 legacy 사용자가 무제한 또는 영구 제외된다. | 대상 누락 또는 상한 우회 | Medium | P0 | missing row의 멱등 초기화·동시 예약 테스트 |
| fairness 정렬이나 발송별 상한이 worker 경계에서 사라진다. | 특정 사용자 집중·과다 fan-out | Medium | P0 | count/last/distance 동률 fixture와 limit assertion |
| 후속 Outbox가 recipient보다 적거나 중복된다. | 인앱 알림 영구 누락 또는 중복 fan-out | High | P0 | 신규 recipient ID 집합과 confirmed Outbox aggregate/dedup 1:1 비교 |
| stale worker가 domain write 후 source event 완료에 실패한다. | recipient는 생겼지만 event가 재처리되어 상태 불명확 | High | P0 | lease reclaim 뒤 A의 fenced complete 0행이 A transaction 전체를 rollback함을 확인 |
| 한 event 오류가 batch 전체를 rollback한다. | 정상 event까지 반복되어 backlog 증가 | Medium | P0 | 같은 batch의 정상/실패 event를 분리 transaction으로 처리하는 테스트 |
| 후보 0명을 실패로 취급해 무한 재시도한다. | 질문글이 MATCHING에 고착 | Medium | P0 | zero candidate에서 ACTIVE/PROCESSED와 0 fan-out 확인 |
| 정확 위치·거리·방위가 후속 payload나 로그에 포함된다. | 개인정보·위치정보 노출 | High | P0 | JSONB key/value와 worker 결과 DTO 비노출 검사 |
| `SKIP LOCKED` 경쟁에서 목표 인원을 보장하려 무제한 스캔한다. | 긴 transaction·DB 부하 | Medium | P1 | 한 pass가 발송 상한 이내 lock/insert로 끝나며 under-fill을 허용하는 계약 검사 |
| 공간·정렬 쿼리가 현재 index로 비효율적이다. | worker lag 증가 | Unknown | P1 | #127 합성 데이터 EXPLAIN; 이번 Issue에서는 기능 통합 실행 시간만 기록 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-001 | `MATCHING`, `PASSED`, deadline 전 post | matching 완료 전이를 호출 | `ACTIVE`, `publishedAt=now`가 되고 원래 식별자·본문·fingerprint·expiry는 보존된다. | P0 | Test/backend executor A |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-002 | deadline 직전·정확히 경계·이후 post | 실행 가능/만료 판정 | 직전만 실행 가능하고 `expires_at <= now`는 `EXPIRED`이며 recipient 처리로 진행하지 않는다. | P0 | Test/backend executor A |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-003 | `PENDING`, `REVIEW_HELD`, `REJECTED`, `PASSED` moderation | handler gate 판정 | PASSED만 confirm으로 진행한다. PENDING/REVIEW_HELD는 retryable not-ready, REJECTED는 terminal no-op이다. | P0 | Test/backend executor A |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-004 | due matching·answer·confirmed event와 batch command | worker가 claim | repository에 정확히 `{RECIPIENT_MATCH_REQUESTED}`를 전달하고 claimed event마다 별도 handler를 호출한다. | P0 | Backend executor C |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-005 | 한 정상 event와 한 retryable 실패 event | 같은 batch를 처리 | 정상 event는 계속 처리되고 실패 event만 retry policy로 FAILED/DEAD 전이를 시도한다. | P0 | Backend executor C |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-006 | transient DB 오류, moderation not-ready, 손상된 event/참조, stale lease | failure classifier 실행 | 앞의 둘은 RETRYABLE, 계약 손상은 PERMANENT, stale lease는 과거 owner의 fail/complete 없이 회수에 맡긴다. | P0 | Backend executor C |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-007 | aggregate type/event type/match round/status가 잘못된 claimed event | handler 입력 검증 | SQL write 전에 거절하고 permanent failure로 분류하며 payload 문자열은 후보 원천으로 파싱하지 않는다. | P0 | Test/backend executor A |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-UNIT-008 | recipient row ID와 post/recipient ID | confirmed Outbox 생성 | aggregate는 POST_RECIPIENT, event는 RECIPIENTS_CONFIRMED, dedup은 안정적이며 payload에는 세 ID 외 좌표·거리·방위가 없다. | P0 | Test/backend executor A |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-001 | batch worker, Outbox repository, matching service, PostgreSQL/PostGIS | PASSED/MATCHING post, audience, selected sector 안의 정상 후보 3명, due round 1 event | batch 1회 처리 | source event는 PROCESSED, post는 ACTIVE이고 recipient/state/confirmed Outbox가 후보별 1:1로 생성된다. notification 행은 0이다. | notification child → outbox → recipient → audience → post 등 FK 역순 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-002 | matching JDBC spatial query | 제출 후 한 후보는 sector 밖 이동, 한 후보는 presence 만료, 한 후보는 비활성 계정, 양방향 차단 후보와 새 정상 후보를 만든다 | worker 실행 | preview/제출 시점 후보와 무관하게 실행 시점 정상 후보만 확정된다. | block 포함 FK 역순 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-003 | PostGIS sector/distance query | wrap-around sector, 시작각·종료각과 최소·최대 거리 직전/경계/직후 후보 | worker 실행 | 시작각 포함·종료각 제외, 최소/최대 승인 경계가 기존 preview 계약과 일치하고 선택 sector 외 후보는 제외된다. | spatial fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-004 | fairness/order/limit SQL | 상한보다 많은 후보에 recent count, null/old/new last time, distance·ID tie를 배치 | worker 실행 | 승인 순서 앞의 신규 recipient만 `max-recipients-per-post`까지 확정되고 나머지 state는 변하지 않는다. | recipient/state fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-005 | receive state init + matching SQL | receive state가 없는 후보와 기존 count 0 후보 | worker 실행 및 재실행 | missing row가 1 slot/1 recent count로 생성되고 기존 후보와 동일 상한을 적용받으며 재실행으로 증가하지 않는다. | state fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-006 | moderation gate, source Outbox | 동일 조건의 PENDING/PASSED/REVIEW_HELD/REJECTED post | 각 claimed event 처리 | PASSED만 recipient를 만든다. PENDING/REVIEW_HELD는 source를 PROCESSED로 소비하지 않고, REJECTED는 recipient 없이 terminal 처리된다. | status fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-007 | deadline/post state/source Outbox | `expires_at`이 now 직전, 정확히 now, 직후인 PASSED post | worker 실행 | 직전·경계는 EXPIRED/PROCESSED이고 recipient/state가 0, 직후만 ACTIVE/recipient 생성이다. | time fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-008 | matching transaction | 정상 PASSED post지만 eligible candidate 0명 | worker 실행 | post ACTIVE, source PROCESSED, recipient/state increment/confirmed Outbox는 0이며 무한 retry하지 않는다. | post fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-009 | unique recipient, slot count, confirmed Outbox dedup | 이미 같은 post-recipient가 있거나 성공 처리 결과를 재현하는 fixture | duplicate/replay 처리 | 중복 row는 생기지 않고 기존 사용자 count가 다시 증가하지 않으며 confirmed Outbox도 한 건뿐이다. | duplicate fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-010 | 두 worker, 같은 source event | due event 1개와 barrier, 독립 transaction | 동시에 batch claim·처리 | 한 worker만 event를 받고 하나의 logical recipient/state/outbox 결과만 커밋한다. | executor 종료, fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-011 | 두 post, 공통 recipient state | receive capacity가 1 남은 공통 최우선 후보와 독립 matching event 2개 | 동시에 처리 | 공통 후보의 active count는 상한을 넘지 않고 두 post에 확정된 해당 후보 수의 합은 1이다. 각 post transaction은 deadlock 없이 종료한다. | executor 종료, fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-012 | stale lease fencing + matching transaction | A가 claim 후 lease 만료, B가 generation을 올려 reclaim, A/B handler 순서를 제어 | A가 늦게 처리한 뒤 B 처리 | A의 source complete가 0행이 되어 A의 recipient/state/confirmed Outbox/ACTIVE가 전부 rollback되고 B만 한 번 커밋한다. | lease fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-013 | trigger failure injection, transaction rollback | confirmed Outbox insert 또는 post ACTIVE update를 실패시키는 테스트 trigger | claimed event 처리 | recipient/state/confirmed Outbox/post ACTIVE/source PROCESSED가 부분 반영되지 않는다. worker failure 전이만 별도 transaction에서 정책대로 남는다. | trigger를 finally에서 제거, fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-014 | batch transaction isolation | 같은 batch에 정상 post와 영구 손상 fixture | batch 처리 | 정상 event는 ACTIVE/PROCESSED, 손상 event는 DEAD이며 한 event 실패가 다른 결과를 rollback하지 않는다. | fixture 삭제 |
| TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER-INT-015 | privacy regression | 실제 origin/candidate 좌표와 생성된 confirmed Outbox | payload key/value와 반환 DTO 검사 | latitude, longitude, geography, distance, bearing 및 실제 좌표 문자열이 payload·worker 결과에 없다. | fixture 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- H2/mock SQL이 아니라 기존 PostgreSQL/PostGIS Testcontainers에서 geography 계산,
  `FOR UPDATE SKIP LOCKED`, data-modifying CTE 또는 동등한 JDBC transaction을 실행한다.
- source Outbox claim은 먼저 커밋하고, 각 event의 post lock·recipient insert·state count·
  confirmed Outbox·post state·source complete는 event별 한 transaction으로 묶는다.
- recipient unique conflict는 정상 idempotency 경로로 흡수한다. 실제 inserted row를
  반환받아 그 사용자만 count/outbox에 연결한다.
- migration은 추가하지 않는다. 기존 unique/check/index로 요구를 충족하지 못한다는
  실행 증거가 생기면 계획을 멈추고 별도 승인 범위로 반환한다.

### Concurrency and idempotency

- 같은 event claim 경합과 서로 다른 post의 공통 receive state 경합을 분리해 검증한다.
- 동시 테스트는 Hikari test pool 크기를 고려해 2 thread와 timeout을 사용하고
  executor를 `finally`에서 종료한다.
- lock 순서는 공정성 정렬과 별개로 SQL이 선택한 receive state row에 한정하며,
  `SKIP LOCKED` 때문에 목표 수보다 적어지는 것은 허용한다. 추가 round는 만들지 않는다.
- source Outbox owner/generation/expiry와 post row lock이 모두 유효해야 domain write가
  commit된다.

### External APIs

- 외부 API, moderation provider, Push provider 호출은 없다.
- #137은 post moderation 상태만 fixture로 제공하고, #123은 confirmed Outbox를 입력으로
  받는 후속 소비자로 취급한다. notification table에 직접 쓰지 않는다.
- payload는 식별자만 포함하고 정확 위치·거리·방위·본문을 포함하지 않는다.

### Failure recovery and reconciliation

- transient 오류는 #119 retry policy로 FAILED/backoff 또는 max-attempt DEAD가 되고,
  permanent 계약 손상은 즉시 DEAD가 된다.
- moderation not-ready는 recipient를 만들지 않고 source event를 성공 소비하지 않는다.
  운영 activation과 재시도 시점 연결은 #137 완료 전까지 비활성 상태다.
- stale worker의 complete/fail이 0행이면 현재 owner 결과를 읽어 덮어쓰지 않고 종료한다.
- 실패 후 사용자별 `count(post_recipient where capacity_released_at is null)`과
  `active_unhandled_count`, 신규 confirmed Outbox 수를 비교해 drift를 기록한다.

## 8. Test data and isolation

- Fixtures: scenario별 고유 region, ACTIVE/BLOCKED/DELETED account, 양방향 active/released
  block, current/expired presence, PASSED/PENDING/REVIEW_HELD/REJECTED post, audience,
  fixed matching Outbox와 receive state 0/상한 직전/상한/missing.
- Database isolation: `PostgisContainerIntegrationTestSupport`와 고유 dedup/idempotency
  key를 사용하고 notification delivery → notification → Outbox → recipient → audience →
  post → block/state/presence → account/region 순으로 정리한다.
- Clock/randomness: 모든 worker 판단·claim·lease·post expiry는 고정 또는 제어 가능한
  `Clock`을 사용한다. random ordering과 DB 현재 시각을 assertion oracle로 쓰지 않는다.
- External API doubles: 해당 없음. repository/transaction failure는 mock 또는 test-only
  PostgreSQL trigger로 주입한다.
- Cleanup: latch timeout을 두고 executor를 `finally`에서 종료하며 test trigger/function은
  반드시 제거한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Test/backend executor A | 신규 `src/test/java/com/dnd/qello/direction/domain/DirectionPostMatchingTest.java`, 신규 `src/test/java/com/dnd/qello/direction/matching/DirectionMatchingServiceTest.java`, `DirectionPost.java` | UNIT-001~003, UNIT-007~008 | 대상 unit test를 먼저 실패시키고 최소 상태/gate 구현 후 통과 |
| 2 | Test/backend executor B | 신규 `src/integrationTest/java/com/dnd/qello/DirectionMatchingWorkerIntegrationTest.java`, 신규 matching repository interface/JDBC/SQL 파일 | INT-001~009, INT-013, INT-015 | 실제 PostgreSQL/PostGIS 대상 integration test |
| 3 | Backend executor C | 신규 `src/test/java/com/dnd/qello/direction/matching/DirectionMatchingWorkerTest.java`, 신규 worker/orchestrator·failure classifier 파일 | UNIT-004~006, INT-014 지원 | mock 단위 검증과 event별 transaction 경계 리뷰 |
| 4 | Concurrency executor | 신규 `src/integrationTest/java/com/dnd/qello/DirectionMatchingWorkerConcurrencyIntegrationTest.java`만 소유 | INT-010~012 | 두 독립 transaction, barrier, timeout, final row reconciliation |
| 5 | Repository integration owner | `DirectionPostRepository.java`, `JdbcDirectionPostRepository.java`, `DirectionPostSql.java`, 필요한 `OutboxEvent` factory만 소유 | 전체 P0 지원 | post lock/conditional state와 confirmed Outbox persistence 대상 테스트 |
| 6 | Regression verifier | production 수정 없음, 기존 direction/outbox test 실행만 수행 | #118/#119 회귀 | `DirectionMatchingContractIntegrationTest`, `OutboxLeaseIntegrationTest` |
| 7 | Independent reviewer | production 수정 없음, `docs/reports/tests/gh-120-TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER.md` | 모든 P0와 잠재 문제 | 전체 diff, transaction/SQL/fencing 독립 검토 |

실행 에이전트는 서로의 owned file을 수정하지 않는다. 실제 구현에서 한 파일이 두
역할에 필요하면 구현 전에 ownership을 한 역할로 재배정하고 계획에 기록한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 신규 테스트 클래스 헤더에 정확한 ISO 8601 생성 시각과 source scenario 기록
- [ ] 대상 단위 테스트 통과
- [ ] 실제 PostgreSQL/PostGIS 통합·동시성·rollback 테스트 통과
- [ ] #118 submission/좌표 비노출/dedup/round 회귀 통과
- [ ] #119 event type claim/retry/lease fencing 회귀 통과
- [ ] 신규 recipient ID 집합과 slot/recent count/confirmed Outbox 집합의 1:1 일치 증거
- [ ] `./harness check`, `./harness pr-ready --project-tests`,
      `npm run hooks:validate`, `git diff --check` 통과
- [ ] 애플리케이션, DB, 동시성, transaction, 외부 API, 장애 복구 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] 실행하지 못한 검증과 #137 production activation 의존성 기록

## 11. Human approval

- Reviewer: User
- Decision: Approved for test implementation and execution
- Approved at: 2026-08-13
