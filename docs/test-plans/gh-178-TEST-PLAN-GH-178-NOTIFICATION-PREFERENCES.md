# Test Plan: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES

> Created at: `2026-08-21T16:31:55+09:00`
> GitHub Issue: `#178`
> Status: Approved
> Approved by: 사용자
> Approved at: `2026-08-21T17:30:00+09:00`

## 1. Objective

인증 사용자가 앱 푸시 전체 설정, 알림 6종별 설정과 사용자 공통 방해 금지 시간을
일관된 snapshot으로 조회·변경하고, 설정을 꺼도 알림함 원장은 남으며 신규 push
delivery만 억제되는 계약을 검증한다. 미배포 quiet 값은 이관하지 않지만 기존
`notification_preference.enabled`는 migration 전후 동일해야 한다.

## 2. Scope

### Included

- `notification_user_setting` 생성과 기존 quiet 컬럼·CHECK 제거
- 사용자 공통 `pushEnabled`, quiet start/end/IANA Zone ID 저장
- 설정 행 부재 시 전체 ON·6종 ON·quiet OFF 기본값
- 본인 전용 GET·PUT API와 정확히 6종인 완성 snapshot
- quiet pair/zone, 자정 통과, 같은 시작·종료 검증
- global/type effective push gate와 알림함 원장 선저장
- V25 이후 기존 preference 저장 SQL이 제거된 quiet 컬럼을 참조하지 않는지 검증
- transaction rollback, 동시 PUT 직렬화, OpenAPI와 데이터 모델 계약

### Excluded

- 기존 quiet 값 이관 — 아직 배포되지 않았으므로 신규 계약에서 제거한다.
- quiet 시간의 실제 발송 억제·묶음·일 상한(`#180`).
- Push provider·토큰·발송 직전 preference 재검사(`#179`)와 scheduler(`#182`).
- 이미 생성된 delivery 취소, notification 삭제·REVOKED, 클라이언트 UI와 OS 권한.
- 외부 API 실제 호출, 인프라 apply, 배포와 프로덕션 변경.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #178 | GET·PUT 설정 API, 사용자당 quiet 한 쌍, 한쪽만 지정 시 400, `enabled` 보존, 본인 전용 접근 |
| `TASK.md` | 전체+종류별 설정, sparse defaults, 정확히 6종, IANA Zone ID, 원장과 delivery 분리 |
| `NOTIFICATION_INBOX_DESIGN.md` §1·§10·§12-2 | push가 꺼져도 원장은 남고 quiet는 사용자 단위로 분리 |
| `NotificationType` | 응답·요청에 6종을 중복 없이 한 번씩 포함 |
| `AGENTS.md` §3·§10 | JUnit 5, DisplayName·헤더, 단위/통합 분리, 완료 명령과 미검증 위험 보고 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| migration이 종류별 `enabled`를 잃거나 기본값으로 덮음 | 사용자 선택 유실 | 중간 | P0 | V24 fixture에 서로 다른 6종 값을 넣고 V25 후 동일 비교 |
| global OFF가 종류별 선택을 false로 덮음 | 다시 켰을 때 개인화 유실 | 중간 | P0 | OFF→ON round-trip 전후 6종 값 동일 |
| preference를 notification 저장 전에 검사 | 알림함 원장 누락 | 중간 | P0 | global/type OFF에서 notification 1, delivery 0 PostgreSQL 증거 |
| PUT 일부만 커밋 | 전역·종류별 설정 불일치 | 중간 | P0 | 중간 실패 주입 후 전체 snapshot 불변 |
| 동시 PUT이 종류별 혼합 snapshot을 남김 | 재현 어려운 설정 오염 | 낮음 | P0 | 두 완성 snapshot 경합 후 결과가 둘 중 하나와 정확히 일치 |
| 행 부재 기본값이 종류마다 달라짐 | 신규 사용자 푸시 누락 | 중간 | P0 | 무설정 사용자 GET 6종 ON·global ON·quiet null |
| quiet 시간대·자정 의미가 모호함 | 실제 억제 시각 오류 | 중간 | P0 | Zone ID 필수, overnight 허용, same-time 거부 도메인 증거 |
| 인증 입력에 타 사용자 ID가 노출됨 | 수평 권한 상승 | 낮음 | P0 | API signature·MockMvc에서 subject만 service에 전달 |
| 이미 생성된 delivery까지 #178이 임의 취소 | 후속 #179 책임 침범 | 낮음 | P1 | 기존 delivery 불변 및 제외 범위 검토 |
| enum 추가 시 API 문서와 실제 6종이 어긋남 | 클라이언트 설정 누락 | 중간 | P1 | web contract와 OpenAPI enum/operation 검증 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-001 | start 22:00, end 07:00, `Asia/Seoul` | quiet value를 생성 | 자정 통과 값이 그대로 생성된다 | P0 | Domain executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-002 | quiet 세 값이 모두 없음 | OFF value를 생성 | quiet가 없는 상태로 생성된다 | P1 | Domain executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-003 | start만 있음 | quiet value를 생성 | `NOT-VAL-008` 예외다 | P0 | Domain executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-004 | end만 있음 | quiet value를 생성 | `NOT-VAL-008` 예외다 | P0 | Domain executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-005 | start/end만 있고 Zone ID가 없음 | quiet value를 생성 | `NOT-VAL-008` 예외다 | P0 | Domain executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-006 | start와 end가 같음 | quiet value를 생성 | 24시간으로 추정하지 않고 `NOT-VAL-008`이다 | P0 | Domain executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-007 | 유효한 6종이 각 한 번 있음 | update command를 생성 | 완성 snapshot을 허용한다 | P0 | Service executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-008 | 한 종류 누락 또는 중복 | update command를 생성 | `NOT-VAL-008`이며 repository를 호출하지 않는다 | P0 | Service executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-009 | 설정 행이 전혀 없음 | GET service를 호출 | global ON, 6종 ON, quiet null이다 | P0 | Service executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-010 | 종류별 혼합값과 global ON | global OFF snapshot을 저장 | 종류별 혼합값은 그대로 전달된다 | P0 | Service executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-011 | global OFF와 보존된 종류별 혼합값 | global ON snapshot을 저장 | 이전 종류별 값이 복원된다 | P0 | Service executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-012 | 존재하지 않는 계정 | GET 또는 PUT | `NOT-APP-001`이다 | P1 | Service executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-013 | USER가 아니거나 비활성 계정 | GET 또는 PUT | `NOT-APP-002`다 | P0 | Service executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-014 | 인증 subject가 있음 | GET controller 호출 | subject ID만 service에 전달한다 | P0 | Web executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-015 | 유효한 전체 request | PUT controller 호출 | canonical 응답과 `ALWAYS_RECORD`를 반환한다 | P0 | Web executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-016 | quiet 일부 누락·중복 type·잘못된 Zone ID | PUT 요청 | 400 `NOT-VAL-008`이고 내부 식별자를 노출하지 않는다 | P0 | Web executor |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-017 | global OFF 또는 type OFF | 공통·수신·신고 fan-out gate 판정 | notification 저장 후 device/delivery 조회를 생략한다 | P0 | Fan-out verifier |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-UNIT-018 | global ON·type ON | 공통·수신·신고 fan-out gate 판정 | active device별 delivery를 기존 순서로 생성한다 | P0 | Fan-out verifier |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-001 | Flyway V24→V25 | 6종 `enabled`를 혼합해 V24 schema에 저장하고 quiet에는 임의값 저장 | V25 migrate | 6종 enabled 동일, old quiet 컬럼·CHECK 제거, 신규 테이블 생성; quiet 값은 이관하지 않음 | 전용 schema drop |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-002 | PostgreSQL catalog | 최신 migration 적용 | table·column·FK·CHECK 조회 | 세 quiet 필드는 0개 또는 3개이고 신규 PK/FK가 정확하다 | transaction rollback |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-003 | Flyway | V25까지 적용된 schema | migrate 재실행 | 추가 migration 0건이고 schema 불변 | 전용 schema drop |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-004 | repository·service·GET API | ACTIVE USER, 설정 행 없음 | 본인 GET | global ON, 6종 ON, quiet null, `ALWAYS_RECORD` | 계정·설정 삭제 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-005 | PUT·repository·GET | ACTIVE USER | global OFF, 6종 혼합, overnight quiet 저장 후 GET | 입력과 canonical snapshot이 동일하고 종류별 값이 보존된다 | 계정·설정 삭제 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-006 | PUT·repository | quiet가 저장된 사용자 | quiet null인 완성 snapshot 저장 | quiet 세 컬럼이 모두 null이고 종류별 값은 유지된다 | 계정·설정 삭제 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-007 | service transaction·PostgreSQL CHECK | 기존 완성 snapshot | 마지막 type 저장에서 실패를 주입 | global·quiet·6종이 요청 전 snapshot과 동일하다 | 실패 fixture 제거·rollback |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-008 | generic fan-out·PostgreSQL | global OFF, type ON, active device | outbox 소비 | notification 1, delivery 0, event 완료 | 관련 aggregate 역순 삭제 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-009 | recipient/report fan-out·PostgreSQL | global ON, 해당 type OFF, active device | 각 outbox 소비 | 각 notification 1, delivery 0, 기존 dedup/retry 계약 불변 | 관련 aggregate 역순 삭제 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-010 | Security·GET·PUT | ACTIVE USER A·B와 비활성 USER | A 인증으로 호출 | A만 조회·변경되고 비활성 사용자는 403; request/path에 target user ID 없음 | 계정·설정 삭제 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-011 | 두 transaction·row lock | 같은 사용자에 서로 다른 완성 snapshot A·B | PUT을 barrier로 경합 | 최종값은 A 또는 B 전체와 일치하고 혼합되지 않는다 | executor 종료·계정 삭제 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-012 | springdoc·OpenAPI | application context | 스펙 재생성 | GET·PUT operation, 6종 enum, request/response와 400/401/403/404가 문서화된다 | 생성 diff 검토 |
| TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-013 | V25 schema·전용 preference repository | V25 적용 후 설정 행이 없는 사용자 | `replaceTypePreferences` 호출 | 제거된 quiet 컬럼을 참조하지 않고 종류별 `enabled`가 저장·재조회된다 | 설정·계정 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- V25는 PostgreSQL transactional migration으로 수행하고 `enabled` 보존을 값 단위로 비교한다.
- PUT은 사용자 설정 row 또는 사용자 식별자를 먼저 lock한 뒤 global→6종 고정 순서로
  저장하고 canonical snapshot을 같은 transaction에서 다시 읽는다.
- quiet 값은 미배포 데이터이므로 이관하지 않는다. 이 결정은 데이터 손실 예외가 아니라
  승인된 계약 변경이며 종류별 `enabled`에는 적용하지 않는다.

### Concurrency and idempotency

- 동일 PUT 재시도는 같은 snapshot을 반환하고 새 중복 row를 만들지 않는다.
- 같은 사용자의 동시 PUT은 row lock으로 직렬화해 완성 snapshot끼리만 경쟁한다.
- fan-out 재처리의 기존 `(recipient_id, dedup_key)`·delivery dedup 계약은 변경하지 않는다.

### External APIs

- 외부 API 호출은 없다. IANA Zone ID는 JDK `ZoneId.of`로 로컬 검증한다.
- FCM/APNs와 OS 알림 권한은 미검증 범위이며 `#179`가 소유한다.

### Failure recovery and reconciliation

- migration 실패는 Flyway/PostgreSQL transaction 전체를 롤백하고 V24 schema를 보존해야 한다.
- PUT 실패는 기존 snapshot을 보존하며 클라이언트는 성공 응답을 받기 전 로컬 저장 완료로
  표시하지 않는다.
- 이미 생성된 PENDING/FAILED delivery는 이 이슈에서 변경하지 않고 `#179` 발송 직전
  재검사 계약으로 넘긴다.

## 8. Test data and isolation

- Fixtures: 식별자가 노출되지 않는 테스트 계정, 6종 혼합 boolean, overnight quiet,
  global ON/OFF snapshot, active push device.
- Database isolation: migration은 전용 schema, repository/fan-out은 Testcontainers
  PostgreSQL transaction과 명시적 역순 cleanup을 사용한다.
- Clock/randomness: `Clock.fixed` 또는 고정 `Instant`; 동시성은 `CountDownLatch` barrier와
  제한시간을 사용한다.
- External API doubles: 없음. Push provider를 mock하거나 호출하지 않는다.
- Cleanup: `notification_delivery` → `notification` → outbox/aggregate → preference/user
  setting → account 순서. executor는 `finally`에서 종료한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Domain/service executor | 신규 `NotificationQuietHoursTest`, `NotificationPreferenceServiceTest` | UNIT-001~013 | `./gradlew test --tests "*NotificationQuietHoursTest" --tests "*NotificationPreferenceServiceTest"` |
| 2 | Web executor | 신규 `NotificationPreferenceApiMockMvcTest`, 기존 `NotificationWebContractTest` | UNIT-014~016, INT-012 | 해당 MockMvc test + `OpenApiSpecificationIntegrationTest` |
| 3 | Migration/persistence executor | 신규 `NotificationPreferenceMigrationIntegrationTest`, `NotificationPreferencePersistenceIntegrationTest`, 기존 `NotificationFanOutPersistenceIntegrationTest` | INT-001~007, INT-011, INT-013 | `./gradlew integrationTest --tests "com.dnd.qello.NotificationPreference*" --tests "com.dnd.qello.NotificationFanOutPersistenceIntegrationTest"` |
| 4 | Fan-out verifier | 기존 `NotificationFanOutWorkerTest`, `RecipientNotificationFanOutWorkerTest`, 신고 fan-out test·integration | UNIT-017~018, INT-008~009 | notification unit + fan-out integration pattern |
| 5 | Security verifier | 신규 `NotificationPreferenceApiIntegrationTest` | INT-010 | `./gradlew integrationTest --tests "com.dnd.qello.NotificationPreferenceApiIntegrationTest"` |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 계획된 단위·MockMvc 18개와 PostgreSQL 통합 13개를 구현하거나 제외 승인을 기록
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: 사용자
- Decision: 승인
- Approved at: `2026-08-21T17:30:00+09:00`
