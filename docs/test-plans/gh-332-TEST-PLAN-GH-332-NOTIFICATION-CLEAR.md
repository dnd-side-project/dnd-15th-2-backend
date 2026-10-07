# Test Plan: TEST-PLAN-GH-332-NOTIFICATION-CLEAR

> Created at: `2026-10-08T02:27:08+09:00`
> GitHub Issue: `#332`
> Status: Approved — 사람 승인 완료

## 1. Objective

사용자가 알림함을 한 번에 비우고, 생성 후 30일이 지난 알림이 목록과 배지에서 자동으로 빠지는지 검증한다.
실패하면 타인 알림이나 `REVOKED` 알림의 상태가 바뀌거나, 지운 알림이 다시 나타나거나, 배지 개수와 목록이 서로 다른 기준으로 계산된다.

## 2. Scope

### Included

- `DELETE /api/v1/notifications` 전체 지우기: 본인 소유, 요청 시각 이전 생성, `UNREAD`·`READ`만 `DISMISSED`로 전이한다.
- 전체 지우기 응답 `{ dismissedCount, dismissedAt }`, 멱등성, 계정 자격 게이트.
- `DISMISSED` 알림에 읽음 처리 요청 시 상태 유지(도메인과 서비스).
- `qello.notification.inbox.retention`(기본 `P30D`) 설정 검증.
- 목록, `countUnread`, `existsUnseen`의 `created_at > retentionFloor` 경계와 cursor 페이지 순회.
- 전체 지우기와 읽음 처리의 동시 실행.
- `/notifications/{id}/target`가 지운 알림과 보존 기간이 지난 알림을 기존처럼 판정하는지 회귀 확인.
- 생성 OpenAPI 갱신과 기존 알림 경로 계약 불변.

### Excluded

- 단건 지우기, 행 물리 삭제, 스위프 워커, 사용자별 보존 기간.
- 스키마·마이그레이션 변경, 푸시 발송 파이프라인과 fan-out 변경.
- 성능 벤치마크와 실행 계획(EXPLAIN) 고정 검증.
- 프론트 구현과 운영 데이터 사용.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #332 | 전체 지우기 범위·멱등성·직후 목록과 배지, `DISMISSED` 읽음 유지, 30일 경계, MockMvc·OpenAPI |
| 사용자 결정 (2026-10-08) | 보존 기간 서버 고정 30일·조회 시 숨김, 전체 지우기는 서버 요청 시각 이전 전부 |
| `V1__create_direction_communication_schema.sql` | `ck_notification_status`에 `DISMISSED` 포함, `ck_notification_read_at`은 `READ`·`DISMISSED`에만 `read_at` 허용 |
| `V24__add_notification_inbox_read_state.sql` | `notification_recipient_feed_idx`는 `(recipient_id, created_at DESC, id DESC) WHERE status IN ('UNREAD','READ')` |
| `NotificationInboxQuerySql` | 목록 `LIST_STATUS_FILTER`, `COUNT_UNREAD`, `EXISTS_UNSEEN`이 `UNREAD`·`READ`만 사용 |
| `JdbcNotificationRepository.update` | `WHERE id = :id AND status = 'UNREAD'` 조건으로 동시 전이를 막는다 |
| `NotificationInboxService` | 모든 경로가 `AccountEligibilityGate`를 먼저 호출하고 `Clock`으로 서버 시각을 정한다 |
| `src/test/AGENTS.md`, `src/integrationTest/AGENTS.md`, test-policy | JUnit 5, `@DisplayName`, 클래스 헤더 생성 시각·원본 시나리오, 보고서 템플릿 |

조사 결과: `DISMISSED` 알림에 읽음 요청이 오면 도메인 `Notification.markRead`는 `READ` 객체를 만든다. 하지만 `update`의 `status = 'UNREAD'` 조건 때문에 DB 행은 바뀌지 않는다. 따라서 현재도 관찰 가능한 결과는 `DISMISSED` 유지다. 이번 변경은 도메인 모델이 같은 규칙을 표현하게 맞추는 작업이며, INT-004는 이 동작의 회귀 방지 검사다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 전체 지우기 UPDATE에 `recipient_id` 또는 상태 조건이 빠짐 | 타인 알림 변경, `REVOKED` 상태 덮어쓰기 | 중간 | P0 | 타인·`REVOKED`·기존 `DISMISSED` 혼합 통합 검사 |
| `created_at <= at` 조건 누락 | 요청 처리 중 도착한 새 알림까지 사라짐 | 중간 | P0 | 요청 시각 이후 생성 알림 유지 검사 |
| `UNREAD`를 지울 때 `read_at`을 채우거나 `READ`의 `read_at`을 지움 | `ck_notification_read_at` 위반 또는 이력 손실 | 낮음 | P1 | 전이 후 `read_at` 값 검사 |
| 보존 하한을 목록에만 적용하고 배지 쿼리에 누락 | 배지 개수와 목록 불일치 | 높음 | P0 | 같은 fixture로 목록·`countUnread`·`existsUnseen` 동시 검사 |
| 경계 비교 연산자 오류(`>=` 대 `>`) | 30일 정각 알림 노출 여부가 계약과 다름 | 중간 | P1 | 하한 정각·직전·직후 fixture |
| 보존 필터가 cursor 조건과 결합되며 페이지 누락 | 다음 페이지 누락·중복 | 낮음 | P1 | 보존 경계를 걸친 cursor 순회 |
| 전체 지우기와 읽음 처리가 동시에 실행돼 지운 알림이 `READ`로 남음 | 지운 알림 재노출 | 중간 | P0 | 두 transaction 동시 실행 후 최종 상태 `DISMISSED` |
| 보존 설정 누락 시 0 또는 null로 기동 | 모든 알림이 숨겨지거나 NPE | 낮음 | P1 | properties 기본값·잘못된 값 거부 단위 검사 |
| 기존 경로 계약 변경 | 프론트 회귀 | 낮음 | P1 | WebContract·OpenAPI 생성 검사 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-001 | `DISMISSED` 알림(`read_at` null / 값 있음), `UNREAD` 알림, `REVOKED` 알림 | `Notification.markRead(at)` | `DISMISSED`는 같은 상태와 기존 `read_at`을 유지, `UNREAD`는 `READ`와 `at`, `REVOKED`는 `NOT-DOM-003` 유지 | P0 | notification-executor |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-002 | 계정 없음 / 자격 없음 | `dismissAll` | `NOT-APP-001` / `NOT-APP-002`, repository 미호출 | P0 | notification-executor |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-003 | 고정 `Clock`, repository가 전이 건수 3 반환 | `dismissAll` | repository에 `clock.instant()`를 기준 시각으로 전달, 결과의 건수 3과 기준 시각 일치 | P0 | notification-executor |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-004 | 고정 `Clock`, 보존 30일 | `list`, `unreadSignal` | 두 경로 모두 `clock.instant() - 30일`을 하한으로 전달 | P0 | notification-executor |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-005 | repository가 `DISMISSED` 알림 반환 | 서비스 `markRead` | `notificationRepository.update` 미호출, 조회한 카드 반환 | P1 | notification-executor |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-006 | retention 미지정 / `P30D` / `PT0S` / 음수 | properties 생성 | 미지정은 30일, 0과 음수는 `IllegalArgumentException` | P1 | notification-executor |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-007 | 서비스 mock이 건수·시각 반환 / 미인증 / 자격 없음 | `DELETE /api/v1/notifications` | 200과 `dismissedCount`·`dismissedAt` / 401 / 403 | P0 | notification-executor |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-008 | `NotificationApiSpec`, 응답 record | 계약 리플렉션 검사 | `DELETE /notifications` 추가와 기존 경로 불변, 응답 record가 계정 식별자를 노출하지 않음 | P1 | notification-executor |

기존 `NotificationInboxServiceTest`와 `NotificationWebContractTest`는 새 생성자 인자·경로 개수 기대만 갱신하고 다른 기대는 바꾸지 않는다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-001 | Service + JDBC + PostgreSQL | 본인 `UNREAD`·`READ`(at 이전), 본인 `REVOKED`·`DISMISSED`, 본인 `UNREAD`(at 이후), 타인 `UNREAD` | 고정 `Clock`으로 `dismissAll` | 본인 at 이전 `UNREAD`·`READ`만 `DISMISSED`, 건수 일치, `UNREAD`였던 행의 `read_at` null, `READ`였던 행의 `read_at` 유지, 나머지 행 불변 | 기존 클래스 reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-002 | Service + JDBC | INT-001 수행 직후 | 같은 시각으로 `dismissAll` 재호출 | `dismissedCount` 0, 모든 행 상태 불변 | 기존 클래스 reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-003 | Service + JDBC + seen state | `UNREAD` 2건, `READ` 1건, seen state 없음 | `dismissAll` 후 `list`, `unreadSignal` | 목록 비어 있음, `unreadCount` 0, `hasUnseen` false | 기존 클래스 reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-004 | Service + JDBC | `DISMISSED` 알림 1건 | `markRead` | 반환 카드와 DB 상태 모두 `DISMISSED`, `read_at` 불변, 예외 없음 | 기존 클래스 reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-005 | Service + JDBC + Clock | `UNREAD` 알림을 하한 정각, 하한 1마이크로초 뒤, 하한 1마이크로초 전에 생성 | `list`, `unreadSignal` | 하한 뒤 1건만 목록에 있고 `unreadCount` 1, `hasUnseen` true. 하한 정각과 그 이전은 제외 | 기존 클래스 reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-006 | Service + JDBC | 하한 이후 3건과 하한 이전 2건 | limit 2로 cursor 순회 | 하한 이후 3건만 중복·누락 없이 순서대로 나오고 마지막 `nextCursor` null | 기존 클래스 reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-007 | Service + target 판정 SQL | `DISMISSED` 알림 1건, 하한 이전 생성 알림 1건 | `target` | 두 알림 모두 기존 판정 결과 반환, 404 아님 | 기존 클래스 reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-008 | Service + JDBC + 두 transaction | `UNREAD` 알림 1건 | `dismissAll`과 `markRead`를 latch로 동시에 시작해 반복 실행 | 모든 반복에서 최종 상태 `DISMISSED`, 두 호출 모두 예외 없음 | 반복마다 fixture reset |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-009 | Springdoc OpenAPI | 기존 생성 테스트 | `OpenApiSpecificationIntegrationTest` | `DELETE /api/v1/notifications`와 응답 schema 추가, 다른 알림 경로 불변 | 테스트 컨테이너 종료 |

기존 통합 테스트(`NotificationInboxQueryIntegrationTest`, `NotificationInboxCommandIntegrationTest`, `NotificationFanOutExpansionIntegrationTest`)는 repository 메서드 시그니처에 하한 인자를 추가하는 기계적 수정만 한다. 하한은 기존 `NOW`에서 30일을 뺀 값으로 넘기고, 다른 기대는 바꾸지 않는다.

## 7. Cross-cutting scenarios

### Database and transactions

- 실제 PostgreSQL Testcontainers를 사용한다. H2로 대체하지 않는다.
- 전체 지우기는 UPDATE 한 번으로 실행해 부분 반영을 만들지 않는다. 쓰기 transaction은 기본 격리 수준을 유지한다.
- 스키마·마이그레이션 변경이 없으므로 기존 `ck_notification_status`, `ck_notification_read_at`이 INT-001에서 함께 검증된다.
- 하한 조건은 `notification_recipient_feed_idx`의 정렬 열과 같은 `created_at`을 쓴다. 실행 계획 고정은 범위 밖이다.

### Concurrency and idempotency

- INT-008: 전체 지우기와 읽음 처리 중 어느 쪽이 먼저 커밋돼도 최종 상태는 `DISMISSED`다. 읽음이 먼저면 `READ` 행을 전체 지우기가 전이하고, 전체 지우기가 먼저면 읽음 UPDATE가 `status = 'UNREAD'` 조건으로 0건이 된다.
- INT-002: 전체 지우기 재호출은 0건이며 상태를 바꾸지 않는다.
- 요청 시각 이전 `created_at`을 가진 미커밋 fan-out 삽입은 전체 지우기 이후 커밋되면 `UNREAD`로 남는다. 사용자에게는 새로 도착한 알림으로 보이며 이번 계약의 허용 동작으로 기록한다.

### External APIs

- 외부 공급자 연동 변경이 없다. 기존 `NoOpPushProvider` 테스트 구성을 그대로 쓴다.

### Failure recovery and reconciliation

- 새 복구 흐름은 없다. DB 오류는 기존 전역 예외 처리 회귀에 맡긴다.
- 복구는 기능 commit revert로 한다. `DISMISSED` 행은 revert 이후에도 목록에서 계속 제외되며 DB 복구 작업이 필요 없다. 보존 필터는 revert하면 즉시 해제된다.

## 8. Test data and isolation

- Fixtures: 기존 `Notification176IntegrationFixtures`의 알림 생성·상태 변경 helper와 synthetic 계정을 재사용한다.
- Database isolation: 각 클래스의 기존 reset 계약을 따른다. 동시성 클래스는 반복마다 fixture를 다시 만든다.
- Clock/randomness: 고정 `Instant`와 고정 `Clock`을 사용하고 보존 경계는 마이크로초 단위로 명시한다(PostgreSQL `timestamptz` 정밀도).
- External API doubles: 새 외부 API가 없다.
- Cleanup: 기존 Testcontainers lifecycle과 fixture reset을 따른다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | 부모 오케스트레이터 | `TASK.md`, 이 계획 | 요구사항·파일·시나리오 계약 | Issue·branch·TASK 일치, 문서 공백 검사 |
| 2 | notification-executor (승인 후 호출) | `TASK.md`의 production 파일, 아래 테스트 파일, 생성 OpenAPI, 테스트 보고서 | UNIT-001~008, INT-001~009 | 기준선 실행, 실패 테스트 확인, 구현, 단위·통합 회귀, OpenAPI 생성 |
| 3 | notification-verifier (구현 후 독립 호출) | 소스 수정 권한 없음 | 전체 시나리오·범위 | 실제 diff, JUnit 결과, 필수 검사, 범위 밖 파일 불변 |

신규 테스트 파일:
- `src/test/java/com/dnd/qello/notification/NotificationDismissTest.java` (UNIT-001)
- `src/test/java/com/dnd/qello/notification/config/NotificationInboxPropertiesTest.java` (UNIT-006)
- `src/integrationTest/java/com/dnd/qello/NotificationInboxDismissRetentionIntegrationTest.java` (INT-001~007)
- `src/integrationTest/java/com/dnd/qello/NotificationInboxDismissConcurrencyIntegrationTest.java` (INT-008)

수정 테스트 파일:
- `src/test/java/com/dnd/qello/notification/service/NotificationInboxServiceTest.java` (UNIT-002~005, 생성자 인자 갱신)
- `src/test/java/com/dnd/qello/notification/web/NotificationApiMockMvcTest.java` (UNIT-007)
- `src/test/java/com/dnd/qello/notification/web/NotificationWebContractTest.java` (UNIT-008)
- `src/integrationTest/java/com/dnd/qello/NotificationInboxQueryIntegrationTest.java` (시그니처만)
- `src/integrationTest/java/com/dnd/qello/NotificationInboxCommandIntegrationTest.java` (시그니처만)
- `src/integrationTest/java/com/dnd/qello/NotificationFanOutExpansionIntegrationTest.java` (시그니처만)

`OpenApiSpecificationIntegrationTest`는 수정하지 않고 실행만 한다(INT-009).
추가 파일 변경이 필요하면 부모에게 근거를 보고하고 범위를 먼저 확인한다.
실행 에이전트는 다른 사람의 변경을 되돌리지 않는다.
새 테스트 메서드의 `@DisplayName`과 클래스의 ISO 8601 생성 시각·원본 시나리오 헤더를 정책에 맞춘다.

### Commands after approval

Java 21, Docker/Testcontainers, 프로젝트 의존성이 필요하다.

```bash
./gradlew test
./gradlew integrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

검사 실패나 필수 환경 부족은 FAIL/BLOCKED로 보고하며 미실행을 PASS로 표현하지 않는다.
독립 검증자는 실패를 통과시키려고 소스를 수정하지 않는다.

## 10. Completion criteria

- [ ] 사람 테스트 계획 승인 후 테스트 작성·실행 및 production 구현
- [ ] 모든 P0 시나리오 구현과 P1 회귀
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 생성 OpenAPI에 `DELETE /api/v1/notifications` 반영, 다른 경로 불변
- [ ] 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] 구현자와 독립 검증자의 증거 분리, 실패·차단 항목 없음

## 11. Human approval

- Reviewer: 현재 세션의 사용자
- Decision: APPROVED
- Approved at: `2026-10-08T02:48:27+09:00` (승인 기록 시각)
- Note: 현재 세션에서 사용자가 “어 승인해”라고 명시적으로 승인했다. 승인 범위는 이 계획과 TASK.md의 허용 파일·시나리오이다.
