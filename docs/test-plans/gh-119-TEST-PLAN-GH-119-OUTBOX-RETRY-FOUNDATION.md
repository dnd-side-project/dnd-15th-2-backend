# Test Plan: TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION

> Created at: `2026-08-13T01:34:02+09:00`
> GitHub Issue: `#119`
> Status: Approved — implementation and verification completed on 2026-08-13

## 1. Objective

여러 Outbox worker가 같은 테이블을 사용하더라도 자신이 이해하는 event type만
중복 없이 점유하고, 실패 성격과 누적 시도 횟수에 따라 재시도 또는 DEAD 전이를
일관되게 결정하는지 검증한다. #115의 lease 회수·generation fencing을 보존해 worker
중단과 늦은 stale 갱신이 상태 역전이나 작업 유실로 이어지지 않게 한다.

## 2. Scope

### Included

- 비어 있지 않은 `OutboxEventType` 집합에 한정된 batch claim
- production 사용처가 없는 기존 무필터 batch claim 계약 제거
- due `PENDING`/`FAILED`와 만료된 `PROCESSING`의 원자적 claim/reclaim
- `next_attempt_at`, `id` 순서, batch limit, `FOR UPDATE SKIP LOCKED`
- claim 성공 시 status·owner·expiry·generation·attempt 원자 갱신
- `RETRYABLE`/`PERMANENT` 실패 분류를 입력받는 순수 재시도 정책
- 주입된 `maxAttempts`와 attempt별 backoff 전략으로 `FAILED`/`DEAD` 및 다음 시각 결정
- owner·generation·유효 lease fencing을 적용한 완료·실패 전이
- event dedup/matching round와 정확 좌표 비노출 회귀

### Excluded

- V12와 기존 lease 컬럼·제약·인덱스 변경, 신규 Flyway migration
- 성능 근거가 없는 event type 복합 index 추가
- scheduler, polling loop, thread pool과 실제 worker identity 생성
- 방향 매칭, 수신자·슬롯 확정, 인앱 알림 fan-out, 외부 Push 호출
- 업무 예외를 retryable/permanent로 분류하는 #120/#123 handler 규칙
- 운영 lease duration, 최대 시도 횟수와 backoff 기본값 확정
- 배포와 프로덕션 변경

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #119 | 동시 worker 중 하나만 같은 작업을 점유하고, 만료 후 회수하며, retryable/permanent 및 최대 시도 초과를 구분하고, 정확 좌표를 저장하지 않는다. |
| Approved scope, 2026-08-13 | #115 완료분을 반복하지 않고 event type별 claim과 공용 retry/DEAD 결정만 #119에 둔다. |
| Issue #115 / V12 | `lease_owner`, `lease_expires_at`, monotonic `lease_generation`과 `PROCESSING` 불변식, due claim index를 유지한다. |
| `OutboxEventRepository` | claim·complete·fail은 JDBC 경계이며, terminal 전이는 fencing tuple을 명시적으로 전달한다. |
| `NotificationSql` | batch claim은 materialized CTE, `ORDER BY`, `LIMIT`, `FOR UPDATE SKIP LOCKED`와 `UPDATE ... RETURNING`을 유지한다. |
| `outbox_event.next_attempt_at` | NOT NULL 컬럼이다. DEAD에는 terminal 전이 시각을 저장하되 status 조건으로 재점유하지 않는다. |
| Issue #120 / #123 exclusions | 실제 매칭 handler와 인앱 알림 handler는 이번 Issue에서 구현하지 않는다. |
| User-approved error handling addendum, 2026-08-13 | `JdbcNotificationRepository`와 `OutboxEventRepository`의 repository 인자 검증은 `IllegalArgumentException` 대신 `NotificationException`과 `NotificationErrorCode`로 표현해 전역 도메인 예외 처리 경로를 사용한다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| worker가 처리할 수 없는 event type을 점유한다. | 작업 지연, 불필요한 실패·lease 만료 반복 | High | P0 | 혼합 event fixture에서 요청한 type만 반환·변경됨을 PostgreSQL로 확인 |
| 빈 event type 입력이 무필터 claim으로 해석된다. | 한 worker가 모든 종류의 작업을 점유 | Medium | P0 | 빈 집합을 SQL 실행 전에 거절하는 단위/통합 경계 |
| 두 worker가 같은 batch row를 함께 받는다. | 매칭·알림 중복 실행 | High | P0 | 실제 두 transaction의 반환 ID 교집합이 비어 있음 |
| event type 필터 추가로 due·lease 조건이나 정렬이 깨진다. | future/유효 lease 점유 또는 오래된 작업 기아 | Medium | P0 | 상태·시간 혼합 fixture와 limit 순서 assertion |
| 만료 경계에서 기존 worker와 새 worker가 모두 유효하다. | 상태 역전·중복 처리 | Medium | P0 | `lease_expires_at == now` reclaim 및 기존 generation 갱신 거절 |
| attempt 포함 기준이 불명확해 한 번 일찍/늦게 DEAD가 된다. | 과소 재시도 또는 장애 반복 | High | P0 | claim 후 현재 attempt가 `maxAttempts`와 같은 경계 테스트 |
| permanent 실패를 재시도하거나 retryable 실패를 즉시 폐기한다. | 복구 가능한 작업 유실 또는 무의미한 반복 | Medium | P0 | failure kind별 상태·nextAttemptAt 결정 단위 테스트 |
| backoff 계산이 음수·overflow 또는 현재 이전 시각을 만든다. | 즉시 재점유 루프 또는 예외 | Medium | P0 | 잘못된 정책 값과 시각 overflow 입력 거절 |
| retry 판단과 DB 전이 사이 stale lease가 발생한다. | 이전 worker가 새 소유자의 상태를 덮어씀 | Medium | P0 | owner/generation/expiry 불일치 시 0행과 현재 상태 보존 |
| 정확 좌표 비노출·dedup·matching round가 회귀한다. | 개인정보 노출 또는 중복 매칭 | Low | P0 | 기존 계약 통합 테스트 회귀 실행 |
| event type 필터가 현재 index와 맞지 않아 느려진다. | backlog 증가 | Unknown | P1 | #127 합성 데이터 `EXPLAIN ANALYZE`; 이번 Issue에서는 신규 index 보류 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-UNIT-001 | `maxAttempts=3`, attempt별 fixed test backoff, claim 후 attempt 1 또는 2인 event | `RETRYABLE` 실패를 판정 | `FAILED`와 정확한 `now + delayForAttempt(currentAttempt)`를 반환하고 attempt/generation을 변경하지 않는다. | P0 | Test executor A |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-UNIT-002 | `maxAttempts=3`, claim 후 attempt 3인 event | `RETRYABLE` 실패를 판정 | 최대 시도 횟수에 도달했으므로 `DEAD`이고 terminal 전이 시각을 반환한다. | P0 | Test executor A |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-UNIT-003 | 남은 횟수가 있는 PROCESSING event | `PERMANENT` 실패를 판정 | 남은 횟수와 무관하게 `DEAD`이고 terminal 전이 시각을 반환한다. | P0 | Test executor A |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-UNIT-004 | PENDING/FAILED/PROCESSED/DEAD 또는 null 입력 | 실패 전이 정책을 호출 | PROCESSING event만 허용하고 나머지를 명시적으로 거절한다. | P0 | Test executor A |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-UNIT-005 | 0 이하 maxAttempts, null/음수/0 backoff 결과, null failure kind/time 또는 시각 overflow | 정책 생성·판정 | 잘못된 구성과 계산 불가능한 다음 시각을 명시적으로 거절한다. | P0 | Test executor A |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-UNIT-006 | repository 인자 검증의 `NotificationException` 변환 | GlobalExceptionHandler의 도메인 예외 경로 | `NotificationErrorCode`가 보존되어 notification 오류 응답으로 변환된다. | P0 | Notification repository executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-001 | JDBC repository, PostgreSQL | 같은 due 시각의 matching·answer·notification event와 요청 type 집합 | matching type만 batch claim | matching event만 반환·PROCESSING이 되고 다른 event의 상태·attempt·lease는 그대로다. | FK 역순 fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-002 | JDBC repository, 두 transaction | 요청 type에 해당하는 due event 여러 건과 barrier | 서로 다른 owner가 동시에 batch claim | 두 반환 ID 집합의 교집합이 없고 합집합은 batch limit 범위의 due event와 일치한다. | executor 종료, fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-003 | JDBC repository, PostgreSQL | 요청 type별 PENDING/FAILED/future, 유효·경계 만료 PROCESSING, PROCESSED, DEAD | limit을 둔 batch claim | 선택된 ID가 due PENDING/FAILED와 `expiry <= at` 중 `next_attempt_at, id` 기준 앞선 limit 범위와 일치하고 future/유효 lease/terminal은 제외된다. 반환 list 자체의 순서는 계약으로 가정하지 않는다. | fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-004 | retry policy, JDBC fail/claim | retryable event와 fixed max/backoff | 정책 결정으로 FAILED 저장 후 backoff 전·후 claim | 전에는 제외되고 후에는 재점유되며 attempt와 generation이 각각 1 증가한다. | fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-005 | retry policy, JDBC fail/claim | max attempt 도달 event와 permanent failure event | 정책 결정으로 fail 저장 후 다시 claim | 두 event 모두 DEAD, lease 해제, `next_attempt_at=terminalAt`이며 후속 batch에서 제외된다. | fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-006 | JDBC complete/fail fencing | 만료 후 B가 회수한 event와 A의 과거 owner/generation | A가 complete/fail, B가 유효 lease로 complete | A의 두 갱신은 0행, B만 성공하며 terminal 상태가 역전되지 않는다. | fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-007 | 기존 direction submit/Outbox persistence | `RECIPIENT_MATCH_REQUESTED` 저장 fixture | 저장 payload key와 dedup/round 조회 | 정확 좌표 key/value가 없고 동일 post·round·event 중복은 계속 차단된다. | fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-008 | 기존 Outbox repository 호환 경계 | 기존 단건 claim과 알림 persistence fixture | 대상 회귀 테스트 실행과 무필터 batch 호출 검색 | 단건 lease API와 기존 알림 persistence가 회귀하지 않고 production 코드에는 무필터 batch claim 경로가 남지 않는다. | fixture 삭제 |
| TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION-INT-009 | JDBC repository 입력 경계 | 빈/null/원소 null event type 집합, 0 이하 limit, 잘못된 owner/lease 시각 | filtered batch claim 호출 | SQL 실행 전에 명시적으로 거절되고 기존 Outbox 행은 변경되지 않는다. | fixture 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- 실제 PostgreSQL/PostGIS Testcontainers에서 batch claim과 fencing을 검증한다.
- claim 대상 선택과 `PROCESSING` 갱신은 한 SQL statement/transaction에서 수행한다.
- 기존 V1~V12 migration을 수정하지 않는다. 신규 index가 필요하다는 성능 근거가
  생기면 #127 또는 별도 승인 범위로 넘긴다.
- 실패 transition 결과와 terminal 상태는 같은 row update로 기록하고 lease
  owner/expiry를 함께 해제한다.
- DEAD는 `next_attempt_at` NOT NULL 계약을 유지하기 위해 terminal 전이 시각을
  저장하지만, status가 terminal이므로 claim 대상에는 포함하지 않는다.

### Concurrency and idempotency

- `FOR UPDATE SKIP LOCKED`의 효과는 mock이 아니라 실제 독립 transaction과 barrier로
  검증한다.
- worker별 반환 ID 집합의 교집합이 비어 있는지 확인한다. 한 event에 대한 외부 side
  effect는 이번 범위에 없으므로 호출 횟수 mock으로 대체하지 않는다.
- event dedup key와 방향 matching round unique 계약은 변경하지 않는다.

### External APIs

- 외부 API와 Push provider 호출은 없다. 정확 좌표는 Outbox payload와 테스트 출력에
  포함하지 않는다.
- provider별 retry 분류, rate limit과 `Retry-After` 해석은 #123 이후 별도 계약이다.

### Failure recovery and reconciliation

- `lease_expires_at <= now`는 회수 가능, terminal update의
  `lease_expires_at > now`는 기존 worker의 유효 조건으로 사용해 경계 중첩을 막는다.
- 정책 판단 이후 DB 갱신이 0행이면 stale lease로 취급하고 강제 overwrite하지 않는다.
- DEAD event의 운영자 재처리·원인 보관·retention은 현재 schema에 필드가 없으므로 이번
  범위에서 임의로 추가하지 않고 남은 운영 위험으로 보고한다.

## 8. Test data and isolation

- Fixtures: synthetic positive IDs, `RECIPIENT_MATCH_REQUESTED`,
  `ANSWER_PUBLISHED`, `RECIPIENTS_CONFIRMED`와 정확 좌표를 포함하지 않는 JSON object
- Database isolation: 각 테스트 전에 FK 역순으로 notification delivery,
  notification, Outbox fixture를 삭제하고 scenario별 dedup key를 사용한다.
- Clock/randomness: 모든 시각과 backoff는 고정 `Instant`/`Duration`을 주입한다.
- External API doubles: 해당 없음
- Cleanup: executor를 `finally`에서 종료하고 latch timeout을 두며 DB fixture를 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Test executor A | 신규 `OutboxRetryPolicyTest.java` | UNIT-001~005 | 대상 `test` 실패를 먼저 확인하고 production 구현 후 통과 |
| 2 | Test executor B | `OutboxLeaseIntegrationTest.java` | INT-001~006, INT-008~009 | 대상 `integrationTest`; PostgreSQL transaction·반환 row 증거 |
| 3 | Direction regression executor | 기존 `DirectionMatchingContractIntegrationTest.java`는 수정하지 않고 실행만 수행 | INT-007 | 기존 payload/dedup/round 회귀 통과 |
| 4 | Notification repository executor | `JdbcNotificationRepository.java`, `OutboxEventRepository.java`, `OutboxLeaseIntegrationTest.java` | UNIT-006 | repository precondition을 `NotificationException`으로 변환하고 error code를 보존 |
| 5 | Backend executor | `notification` retry failure/decision/backoff/policy 신규 파일, `OutboxEventRepository.java`, `JdbcNotificationRepository.java`, `NotificationSql.java` | 승인된 P0 구현 | executor A/B 테스트를 통과시키는 최소 변경 |
| 6 | Independent reviewer | 소스 수정 없음 | 전체 시나리오와 diff | 범위·SQL·fencing·미검증 항목 독립 검토 |

실행 에이전트는 서로 소유하지 않은 파일을 수정하지 않으며, 기존 사용자 변경을
되돌리지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 정확한 ISO 8601 timestamp와 source scenario 검증
- [ ] 대상 단위 테스트 통과
- [ ] 실제 PostgreSQL/PostGIS 대상 통합·동시성 테스트 통과
- [ ] 기존 direction payload/dedup/round와 알림 persistence 회귀 통과
- [ ] `./harness check`, `./harness pr-ready --project-tests`,
      `npm run hooks:validate`, `git diff --check` 통과
- [ ] 애플리케이션, DB, 동시성, 트랜잭션, 외부 API, 장애 복구 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] 실행하지 못한 검증과 운영 retry 기본값 미확정 위험 기록

## 11. Human approval

- Reviewer:
- Decision: PENDING
- Approved at:
