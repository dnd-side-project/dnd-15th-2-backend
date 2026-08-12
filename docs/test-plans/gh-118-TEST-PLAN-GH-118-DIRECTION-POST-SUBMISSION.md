# Test Plan: TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION

> Created at: `2026-08-12T14:08:50+09:00`
> GitHub Issue: `#118`
> Status: Approved for implementation and test execution

## 1. Objective

질문글 제출이 수신자 확정과 분리되어도 사용자가 제출 결과를 잃지 않고,
재시도에서 중복 질문글·중복 매칭 작업이 생기지 않는지 검증한다. 제출 트랜잭션의
원자성이 깨지면 post만 저장되거나 Outbox가 유실되어 이후 매칭이 영구히 누락될
수 있으므로 PostgreSQL 경계에서 이를 확인한다.

## 2. Scope

### Included

- `direction_post`·`post_audience`·`RECIPIENT_MATCH_REQUESTED` Outbox의 원자적 제출
- #115의 `v1:SHA-256` request fingerprint 비교와 legacy lazy backfill 회귀
- 동일/상이 fingerprint의 멱등 키 재시도 및 동시성
- 제출 경로에서 recipient·slot 변경이 발생하지 않는지 검증
- Outbox payload의 정확 좌표 비노출과 `match_round = 1` 계약
- 의도적 persistence 실패 뒤 rollback과 재시도

### Excluded

- 매칭 워커의 후보 재계산·수신자 선정·슬롯 예약
- Outbox batch claim/lease 구현(#119)
- REST Controller·외부 push·인앱 알림 fan-out
- 새로운 migration 및 #115의 fingerprint/lease schema 계약 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #118 | 질문글·audience 저장, fingerprint 멱등성, `IDEMPOTENCY_KEY_REUSED`, `match_round = 0` 요구. 이미 병합된 #115 계약과 충돌하므로 구현 기준은 승인된 #115의 `match_round = 1`이다. |
| #115 test plan / V12 | `request_fingerprint`, `match_round`, matching partial unique, 좌표 없는 payload, 동일 요청 기존 결과 반환 계약을 유지한다. |
| 설계 문서 §6.2·§10.1 | 제출 transaction은 post·audience·MatchRequested만 기록하고 commit 후 worker가 수신자를 확정한다. |
| `AGENTS.md` §3·§11 | JUnit 5, 단위/통합 분리, `@DisplayName`, timestamp/source scenario, PASS/FAIL/BLOCKED 증거를 따른다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 제출 경로가 후보·수신자·슬롯을 함께 변경한다. | 비동기 경계가 깨지고 중복·경쟁 제어가 분산됨 | High | P0 | service unit interaction + DB row count |
| 동일 key 재시도가 새 post/Outbox를 만든다. | 중복 질문글·중복 매칭 | High | P0 | same fingerprint retry integration |
| 같은 key의 다른 의도가 기존 결과를 덮어쓴다. | 사용자 의도 손실 | High | P0 | conflict unit/integration + concurrent race |
| post/audience/Outbox가 부분 커밋된다. | 매칭 작업 유실 또는 고아 데이터 | High | P0 | PostgreSQL rollback/failure injection |
| matching round/dedup 계약이 변한다. | #115/#119 worker 중복 방어 회귀 | Medium | P0 | Outbox row and unique contract assertions |
| payload에 정확 좌표가 들어간다. | 위치정보 노출 | Medium | P0 | JSONB key/value exclusion |
| 기존 동기 발송 테스트가 비동기 경계를 오해한다. | 후속 #120 구현과 계약 불일치 | High | P1 | moved/rewritten regression scenarios |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-UNIT-001 | 제출 command와 의존 repository mock | `send()` 정상 경로 실행 | post·audience·matching Outbox만 저장하고 후보 조회·슬롯 예약·recipient 저장은 호출하지 않는다. | P0 | Submission executor |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-UNIT-002 | 동일 key·동일 fingerprint의 기존 post | 재시도 | 기존 결과를 반환하고 신규 save/Outbox를 만들지 않는다. | P0 | Submission executor |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-UNIT-003 | 동일 key·상이 fingerprint의 기존 post | 재사용 요청 | `IDEMPOTENCY_KEY_REUSED`를 반환하고 쓰기를 수행하지 않는다. | P0 | Submission executor |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-UNIT-004 | matching Outbox payload 생성 입력 | payload 저장 | `match_round = 1`, stable dedup key를 사용하고 좌표 필드를 포함하지 않는다. | P0 | Submission executor |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-UNIT-005 | 잘못된 질문·presence·scheme 또는 persistence 실패 | 제출 | 검증 실패/rollback 뒤 downstream write가 남지 않는다. | P1 | Submission executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-INT-001 | Flyway, DirectionPostService, JDBC repositories | PostgreSQL/PostGIS에 sender·ACTIVE question·scheme·presence 준비 | 정상 제출 | direction_post, post_audience, matching outbox 각 1행; post_recipient와 receive state는 변화 없음. | scenario marker FK 역순 삭제 |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-INT-002 | DirectionPostService + DB unique/fingerprint | 동일 key 제출 1건 | 동일 fingerprint 재시도 후 다른 fingerprint 재사용 | 동일 결과 ID와 row count 유지; 상이 요청은 `IDEMPOTENCY_KEY_REUSED`. | scenario marker 삭제 |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-INT-003 | 두 application transaction + PostgreSQL unique | 동일/상이 fingerprint command를 barrier로 준비 | 두 요청 동시 실행 | 동일 요청은 하나의 post·Outbox만 남고, 상이 요청은 한쪽만 성공·나머지 충돌. | executor 종료 후 marker 삭제 |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-INT-004 | Outbox JSONB + V12 schema | 정상 제출 payload 조회 | JSONB와 round/dedup을 검증 | `match_round=1`; 좌표·geography/WKB/GeoJSON이 없고 허용 coarse 필드만 존재. | outbox/post marker 삭제 |
| TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION-INT-005 | TransactionTemplate + repository failure seam | audience 또는 Outbox 저장 실패를 주입 | transaction 실행 후 재조회·재시도 | post/audience/outbox 부분 행이 없고, 유효 재시도는 한 logical result를 생성. | marker 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- Testcontainers PostgreSQL/PostGIS에서 실제 Flyway schema와 unique/FK/JSONB를 사용한다.
- 제출 transaction의 쓰기 순서는 post → audience → matching Outbox이며 하나의
  `TransactionTemplate` 경계 안에 있다.
- production migration은 수정하지 않는다. #115 V12를 그대로 사용한다.
- failure injection은 안전한 repository decorator/spy 또는 transaction callback 예외로
  구성하고 운영 코드에 테스트 전용 분기를 추가하지 않는다.

### Concurrency and idempotency

- 동일 key·동일 fingerprint 경쟁은 DB unique 최종 방어선과 기존 결과 복구를 함께
  검증한다.
- 동일 key·상이 fingerprint 경쟁은 한 요청만 최초 결과를 만들고 나머지는 conflict다.
- retry/reclaim으로 `match_round`를 변경하지 않으며 최초 event는 `1`이다.

### External APIs

- 범위에 REST Controller와 외부 provider가 없으므로 외부 API double은 사용하지 않는다.
- Outbox payload만 내부 contract로 검증한다.

### Failure recovery and reconciliation

- transaction rollback 뒤 orphan post/audience/outbox가 없는지 확인한다.
- legacy null fingerprint는 동일 의도일 때만 lazy backfill되고, 다른 의도는 null을
  보존하면서 충돌한다.
- 매칭 worker 처리와 recipient 확정은 #120 후속 범위로 명시한다.

## 8. Test data and isolation

- Fixtures: scenario별 고유 region/nickname/idempotency key, ACTIVE question, active
  scheme/segment, sender presence.
- Database isolation: Testcontainers PostgreSQL; race 시나리오는 고유 marker와 명시적
  transaction을 사용한다.
- Clock/randomness: UTC fixed `Instant`; random UUID를 assertion에 사용하지 않는다.
- External API doubles: 없음.
- Cleanup: outbox/child rows부터 FK 역순 삭제.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Submission implementation | `DirectionPostService.java`, `SendResult` call sites | UNIT-001~005 | compile + focused unit tests |
| 2 | Submission integration | `DirectionMatchingContractIntegrationTest.java` and dedicated rollback test if needed | INT-001~005 | PostgreSQL/PostGIS integration |
| 3 | Regression cleanup | affected existing direction integration tests | P1 regressions | no synchronous recipient expectation in #118 path |

## 10. Completion criteria

- [x] 모든 P0 시나리오를 계획에 기록
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 통합 테스트 통과
- [x] 잠재 문제 분석
- [x] `templates/test-report.md` 기반 테스트 보고서 생성

## 11. Human approval

- Reviewer: User
- Decision: Approved — #115 계약과의 충돌은 `match_round = 1`로 해소하고 구현 진행
- Approved at: `2026-08-12`
