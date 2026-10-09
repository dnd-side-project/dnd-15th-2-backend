# Test Plan: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL

> Created at: `2026-10-09T17:44:48+09:00`
> GitHub Issue: `#337`
> Status: Approved

## 1. Objective

앱 사용자가 30일 유예 탈퇴를 요청·철회할 수 있고, 유예가 끝나면 계정이 DELETED가 되어 기기 자격증명이 폐기되는지
검증한다. F08이 호출할 차단·해제가 토큰 재발급에 반영되는지도 확인한다.

실패하면 세 방향으로 문제가 생긴다. 탈퇴를 요청한 사람이 계속 질문을 보내거나 매칭되거나 알림을 받을 수 있다.
반대로 철회와 만료가 경쟁하면 ACTIVE인데 자격증명이 폐기된 계정처럼 복구할 수 없는 상태가 남는다. 또 탈퇴한
닉네임을 다른 사람이 가져가면 옛 답변이 그 사람 것처럼 보인다.

## 2. Scope

### Included

- `AccountStatus.WITHDRAWAL_PENDING`, `Account`의 탈퇴 요청·철회·완료 전이와 `withdrawal_requested_at` 불변식
- V33: `ck_user_account_status` 값 추가, `withdrawal_requested_at` 컬럼과 CHECK, 만료 조회용 부분 인덱스
- 탈퇴 요청 `POST /api/v1/users/me/withdrawal`, 철회 `DELETE /api/v1/users/me/withdrawal`
- 요청 트랜잭션: 계정 상태, `push_device` 전체 해지와 미발송 `notification_delivery` 취소, `active_user_presence` 삭제
- 유예 만료 sweep 워커와 scheduled adapter: DELETED 전이, 닉네임 NULL, 기기 자격증명 전체 REVOKED, 푸시 기기 재해지(A4)
- `DeviceCredential.revoke`, 사용자 단위 일괄 폐기, `DeviceTokenService.reissue`의 WITHDRAWAL_PENDING 허용과 응답 상태 필드
- 차단 진입점 `block(userId)`·`unblock(userId)`
- `DirectionMatchingWorker` 발신자 상태 gate, 답변 목록의 탈퇴 작성자 표시, `AnswerPublicationBlockChecker`의 새 상태 처리
- 유예 기간 설정 `qello.account.withdrawal.grace-period`(기본 `P30D`)
- 회귀: 재발급·계정 영속화·매칭 워커·푸시 기기·이의제기·스케줄링·마이그레이션 기존 테스트

### Excluded

- 앱 로그아웃, 분실 기기 폐기, 즉시 차단 캐시, 차단 운영자 API(#337 제외 항목)
- 유예 중 닉네임·프로필 이미지 변경 차단. 계정 API는 지금 상태를 보지 않는다(위험 R10)
- 유예·탈퇴 작성자 콘텐츠 신고. `ReportTargetSql`은 지금도 ACTIVE 작성자만 신고 대상으로 본다(위험 R11)
- 실제 FCM 호출. 푸시 토큰은 테스트용 `AesGcmPushTokenProtector`로 만든다.
- 배포 설정에 sweep 워커 키 추가(위험 R9). 보고서와 PR에 남긴다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #337 | 탈퇴 요청 후 질문 발송·답변 제출·위치 갱신이 403을 반환한다 |
| GitHub Issue #337 | 탈퇴 요청 후 매칭 후보와 알림 fan-out에서 빠지고, 진행 중 질문글은 수신자를 만들지 않는다 |
| GitHub Issue #337 | 유예 중 같은 기기로 재발급 후 철회하면 ACTIVE로 돌아오고 닉네임이 유지된다 |
| GitHub Issue #337 | 유예 만료 후 DELETED, 닉네임 NULL, 기기 자격증명 전부 REVOKED, 재발급 거절 |
| GitHub Issue #337 | 만료 후 다른 계정이 같은 닉네임을 쓸 수 있고, 옛 답변은 탈퇴 작성자로 표시된다 |
| GitHub Issue #337 | 차단 후 재발급 403, 해제 후 같은 기기로 재발급 성공 |
| GitHub Issue #337 | 탈퇴 요청·철회·만료 워커를 중복 실행해도 상태가 같다 |
| TASK.md 결정 | 유예 30일, 공개 답변은 남기고 "탈퇴한 사용자" 표시, 진행 중 질문글은 매칭 gate 후 만료로 닫힘 |
| 기존 동작 | `DeviceTokenService.reissue`는 자격증명 ACTIVE·installationId 일치·계정 ACTIVE를 확인한다. 실패는 401 `AUT-APP-006`, 403 `AUT-APP-003` |
| 기존 동작 | `Account.block()`은 DELETED만 거절하고, `unblock()`은 BLOCKED만 허용하며, 실패는 409 `ACC-DOM-004`다 |
| 기존 동작 | `AccountJpaEntity`는 `@Version` 낙관적 잠금을 쓰고 충돌은 `GlobalExceptionHandler`가 409로 바꾼다 |
| 기존 동작 | 질문 발송·답변 제출·위치 갱신·질문 추천·제안, 알림 fan-out, 푸시 발송, 매칭 후보 조회는 `ACTIVE`가 아니면 거절하거나 제외한다 |
| 기존 동작 | `AnswerPublicationBlockChecker`는 BLOCKED·DELETED만 막는다. 새 상태는 통과한다(fail-open) |
| 기존 동작 | 답변 목록 SQL은 작성자 상태와 무관하게 `ua.nickname`을 내보낸다 |
| 기존 동작 | 푸시 기기 해지는 user-platform·fingerprint advisory lock을 잡고 PENDING·FAILED delivery만 CANCELLED로 바꾼다 |
| V1, V7, V21 스키마 | `ck_user_account_deleted_at`, `ck_device_credential_revoked_at`, 닉네임 유일 인덱스는 `deleted_at IS NULL` 조건이다 |
| 컨벤션 | 바뀐 production Service는 class 단위 쓰기 `@Transactional`을 쓸 수 없다(TX-001). `ProductionConventionAuditTest`는 `DeviceTokenService`가 class write 목록에 있다고 단언한다 |

## 4. Risk inventory

| ID | Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- | --- |
| R1 | 철회와 만료가 경쟁해 ACTIVE 계정의 자격증명이 REVOKED로 남음 | 높음(복구 불가) | 낮음 | P0 | INT-013 |
| R2 | 요청 트랜잭션 일부만 반영돼 계정은 ACTIVE인데 푸시가 해지됨, 또는 그 반대 | 중간 | 낮음 | P1 | INT-015 |
| R3 | 유예 중 계정이 질문 발송·매칭·알림에 계속 참여 | 높음 | 중간 | P0 | INT-003, INT-004, INT-006, UNIT-015, UNIT-024 |
| R4 | 유예 중 계정의 이의제기 인용으로 답변이 다시 공개됨(fail-open) | 중간 | 낮음 | P0 | UNIT-016 |
| R5 | 만료 후 풀린 닉네임을 다른 사람이 가져가 옛 답변이 그 사람 것처럼 보임 | 높음 | 중간 | P0 | INT-009, INT-017 |
| R6 | 유예 중에 닉네임이 풀려 철회 시 충돌 | 중간 | 낮음 | P0 | INT-009 |
| R7 | 만료 워커가 같은 계정을 두 번 처리하거나 유예가 남은 계정을 처리 | 높음 | 낮음 | P0 | INT-010, INT-011 |
| R8 | `DeviceTokenService` 수정으로 컨벤션 검사 실패 | 중간 | 높음(확정) | P0 | UNIT-022, `javaConventionCheck` |
| R9 | 배포 설정에 sweep 키가 없으면 adapter가 생성되지 않아 유예가 끝나도 삭제되지 않음 | 높음 | 중간 | 사람 결정 | 이 계획에서는 검증하지 않는다. 보고서와 PR에 운영 설정 추가 필요로 남긴다 |
| R10 | 유예 중 닉네임·프로필 이미지 변경이 가능 | 낮음 | 낮음 | P2 | 범위 밖. 보고서에 후속 후보로 남긴다 |
| R11 | 유예·탈퇴 작성자 답변은 신고 대상 조회에서 빠짐(BLOCKED·DELETED와 같은 기존 동작) | 중간 | 낮음 | P2 | 범위 밖. 보고서에 남긴다 |
| R12 | 탈퇴 요청의 푸시 일괄 해지와 같은 사용자의 푸시 등록이 동시에 실행되어 교착 | 중간 | 낮음 | P1 | INT-018 |
| R13 | 탈퇴 요청 직후에도 이미 발급된 액세스 토큰(최대 30분)으로 읽기 API를 호출할 수 있음 | 낮음 | 높음 | P2 | 설계상 수용(AUTH_DESIGN §4.6). 쓰기 차단은 INT-003으로 확인한다 |

## 5. Unit scenarios

`...`은 `TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL`을 줄인 표기다. 테스트 코드의 `@DisplayName`과 헤더에는 전체 ID를 쓴다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| ...-UNIT-001 | ACTIVE 계정 | `requestWithdrawal(at)` | WITHDRAWAL_PENDING, `withdrawalRequestedAt = at`, 닉네임 유지, `deletedAt` null. 삭제 예정 시각은 `at + grace`다 | P0 | Unit executor |
| ...-UNIT-002 | WITHDRAWAL_PENDING 계정 / BLOCKED·DELETED 계정 | `requestWithdrawal(later)` | 유예 중이면 값이 바뀌지 않는다(A1). BLOCKED·DELETED는 `INVALID_STATUS_TRANSITION`(A3) | P0 | Unit executor |
| ...-UNIT-003 | WITHDRAWAL_PENDING 계정 | `cancelWithdrawal(now, grace)`: now가 예정 시각 전 / 같음 / 후(parameterized) | 전이면 ACTIVE, `withdrawalRequestedAt` null, 닉네임 유지. 같거나 후면 `INVALID_STATUS_TRANSITION`(A2) | P0 | Unit executor |
| ...-UNIT-004 | ACTIVE / BLOCKED / DELETED 계정 | `cancelWithdrawal` | ACTIVE는 값이 바뀌지 않는다(A2). BLOCKED·DELETED는 `INVALID_STATUS_TRANSITION` | P0 | Unit executor |
| ...-UNIT-005 | WITHDRAWAL_PENDING 계정 / ACTIVE·BLOCKED·DELETED 계정 | `completeWithdrawal(at)` | 유예 중이면 DELETED, `deletedAt = at`, 닉네임 null, `withdrawalRequestedAt` null. 나머지는 `INVALID_STATUS_TRANSITION` | P0 | Unit executor |
| ...-UNIT-006 | `restore` 입력 | WITHDRAWAL_PENDING + `withdrawalRequestedAt` null, ACTIVE + 값 있음 | 둘 다 상태 불변식 오류다 | P0 | Unit executor |
| ...-UNIT-007 | WITHDRAWAL_PENDING 계정 | `block()` | `INVALID_STATUS_TRANSITION`. 기존 `AccountTest`의 block·unblock·delete 시나리오는 수정 없이 통과한다 | P0 | Unit executor |
| ...-UNIT-008 | ACTIVE / REVOKED 자격증명 | `revoke(at)` | ACTIVE는 REVOKED와 `revokedAt = at`. REVOKED는 값이 바뀌지 않는다(처음 `revokedAt` 유지) | P0 | Unit executor |
| ...-UNIT-009 | `DeviceTokenServiceTest`의 fake 저장소, 계정 ACTIVE / WITHDRAWAL_PENDING / BLOCKED / DELETED | `reissue` | ACTIVE·WITHDRAWAL_PENDING은 토큰과 계정 상태를 돌려준다. BLOCKED·DELETED는 기존 테스트대로 `ACCOUNT_NOT_ACTIVE` | P0 | Unit executor |
| ...-UNIT-010 | 탈퇴 서비스, ACTIVE 계정, 정리 포트 mock(A10) | `request(userId)` | 계정 저장 1회, 푸시·위치 정리 포트 호출 각 1회, 삭제 예정 시각 반환 | P0 | Unit executor |
| ...-UNIT-011 | 같은 서비스, 유예 중 계정 | `request(userId)` 재호출 | 계정 저장과 정리 포트 호출이 없고 기존 예정 시각을 반환한다(A1) | P0 | Unit executor |
| ...-UNIT-012 | 같은 서비스, 정리 포트가 예외를 던짐 | `request(userId)` | 예외가 호출자에게 전파된다. rollback은 INT-015에서 확인한다 | P1 | Unit executor |
| ...-UNIT-013 | 같은 서비스, 유예 중 계정 | `cancel(userId)`: 예정 시각 전 / 후, ACTIVE 계정 | 전이면 ACTIVE 저장. 후면 409. ACTIVE면 저장 없음 | P0 | Unit executor |
| ...-UNIT-014 | 만료 처리 서비스(행 단위), 유예가 끝난 계정 / 유예가 남은 계정 / 이미 ACTIVE·DELETED 계정 | `complete(userId, at)` | 끝난 계정만 `completeWithdrawal` 저장, 자격증명·푸시 일괄 폐기 포트 호출. 나머지는 ineligible이고 쓰기 없음 | P0 | Unit executor |
| ...-UNIT-015 | 차단 서비스 | `block` / `unblock` | 도메인 전이 결과를 저장하고 자격증명 폐기 포트를 호출하지 않는다. 유예 중 계정 `block`은 409 | P0 | Unit executor |
| ...-UNIT-016 | `AnswerPublicationBlockChecker`, 작성자 ACTIVE / BLOCKED / DELETED / WITHDRAWAL_PENDING | `blockReason` | ACTIVE는 empty, BLOCKED·DELETED는 기존 사유, WITHDRAWAL_PENDING은 `ACCOUNT_WITHDRAWAL_PENDING`(A6, 30자 이하) | P0 | Unit executor |
| ...-UNIT-017 | sweep 워커, 후보 3건 중 1건 예외 / 1건 ineligible | `processBatch` | 나머지 행이 처리되고 `scanned = completed + ineligible + failed`. `at` null이면 Clock 시각, `limit <= 0`은 생성 시 거절 | P0 | Unit executor |
| ...-UNIT-018 | `DirectionMatchingWorker`, moderation PASSED, 발신자 BLOCKED / WITHDRAWAL_PENDING / DELETED(parameterized) | `processBatch` | 수신자 저장과 `activate`가 없고 claim이 완료된다(재시도 아님) | P0 | Unit executor |
| ...-UNIT-019 | 같은 워커, moderation PENDING, 발신자 WITHDRAWAL_PENDING | `processBatch` | 재시도가 아니라 완료다. 발신자 gate가 moderation gate보다 먼저다(A5) | P0 | Unit executor |
| ...-UNIT-020 | `PostAnswerApiMockMvcTest`, 탈퇴 작성자 카드 / 일반 카드 | `GET /api/v1/direction/posts/{postId}/answers` | 탈퇴 카드는 `authorNickname` null, `authorWithdrawn` true. 일반 카드는 기존 assertion 유지, `authorWithdrawn` false | P0 | Unit executor |
| ...-UNIT-021 | 탈퇴 controller standalone MockMvc | POST·DELETE 성공, 서비스 409, 인증 없음 | 200 응답 본문(`status`, `scheduledDeletionAt`), 409 `ACC-DOM-004`, 401 | P0 | Unit executor |
| ...-UNIT-022 | `ProductionConventionAuditTest` | `DeviceTokenService`를 method 단위 `@Transactional`로 바꾼 뒤 | 서비스 목록에는 있고 class write 목록에는 없다고 단언을 바꾼다(A7). ratchet 검사가 통과한다 | P0 | Unit executor |
| ...-UNIT-023 | 유예 기간 properties | null·0·음수 / `application.yml` 기본값 | 생성 시 거절 / 기본값 30일(`RateLimitDefaultsTest` 방식) | P1 | Unit executor |
| ...-UNIT-024 | `RecipientNotificationFanOutWorkerTest`·`NotificationFanOutWorkerTest`의 비활성 계정 parameterized 시나리오 | 발신자·수신자 WITHDRAWAL_PENDING 추가 | 알림과 delivery를 만들지 않는다 | P1 | Unit executor |
| ...-UNIT-025 | 스케줄링 설정 | 새 sweep 블록 binding, 비활성 시 bean 없음, adapter metric·fixed-delay·`BATCH_FAILED` | 기존 sweep 테스트와 같은 단언이 새 adapter에도 통과한다 | P1 | Unit executor |
| ...-UNIT-026 | `FlywayMigrationContractTest`, `AccountJpaMapperTest` | V33 추가, 새 필드 | 마이그레이션 목록 계약이 통과하고 `withdrawalRequestedAt`·닉네임 NULL이 왕복 변환된다 | P0 | Unit executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| ...-INT-001 | V33 스키마 | 마이그레이션 완료 DB | 유예 행을 시각과 함께 / 시각 없이 insert, ACTIVE 행에 시각 넣어 insert | 첫째만 성공하고 나머지는 CHECK 위반. Flyway 최신 버전 33, CHECK·인덱스 수와 `EXPECTED_INDEXES` 갱신, `NotificationPreferenceMigrationIntegrationTest` 적용 수 9 | 행 삭제 |
| ...-INT-002 | 등록·재발급·탈퇴 API(MockMvc), PostGIS | 기기 등록, 푸시 기기 2개(PENDING·FAILED·SENT delivery), presence 행 | `POST /api/v1/users/me/withdrawal` | 200과 예정 시각. 계정 WITHDRAWAL_PENDING, 푸시 기기 전부 REVOKED, PENDING·FAILED는 CANCELLED, SENT는 그대로, presence 0건, 기기 자격증명 ACTIVE, 닉네임 유지 | 클래스 공통 정리 |
| ...-INT-003 | 같은 구성 | INT-002 직후, 같은 액세스 토큰 | 질문글 제출, 답변 제출, 위치 갱신 | 각각 403(`DIR-APP-007`, `ANS-APP-003`, `DIR-APP-007`). 행이 생기지 않는다 | 클래스 공통 정리 |
| ...-INT-004 | presence 후보 조회 | ACTIVE·WITHDRAWAL_PENDING 후보의 presence | `findCandidates` | 유예 중 후보가 빠진다(`DirectionRecipientSelectionIntegrationTest`에 추가) | 기존 |
| ...-INT-005 | 재발급 API | 유예 중 계정의 기기 | `POST /api/v1/auth/token` | 200, 응답 `accountStatus = WITHDRAWAL_PENDING` | 클래스 공통 정리 |
| ...-INT-006 | 매칭 워커 | PASSED 질문글, 발신자를 WITHDRAWAL_PENDING / BLOCKED로 변경 | 매칭 워커 실행 | 수신자·슬롯 0, 매칭 이벤트 PROCESSED, 질문글 MATCHING 유지(`DirectionMatchingWorkerIntegrationTest`에 추가) | 기존 |
| ...-INT-007 | 재발급·철회 API | INT-002 상태 | 재발급 → `DELETE /api/v1/users/me/withdrawal` → 재발급 → 위치 갱신 | 철회 200, ACTIVE, 시각 NULL, 닉네임 같음. 두 번째 재발급 `accountStatus = ACTIVE`, 위치 갱신 200 | 클래스 공통 정리 |
| ...-INT-008 | 탈퇴 API | 유예 중 계정 | 시계를 하루 옮긴 뒤 POST 재요청 | 200, 예정 시각과 `withdrawal_requested_at`이 처음 값 그대로 | 클래스 공통 정리 |
| ...-INT-009 | 등록 API, sweep | 계정 A 유예 중 | 다른 기기가 A의 닉네임으로 등록 → sweep으로 A 완료 → 다시 등록 | 첫 등록 409 `ACC-APP-002`, 완료 후 등록 성공 | 클래스 공통 정리 |
| ...-INT-010 | sweep 워커 | 유예가 1초 지난 계정(자격증명 2개, 유예 중 다시 등록한 푸시 기기 1개), 1초 남은 계정 | `processBatch(at)` | 앞 계정만 DELETED, `deleted_at = at`, 닉네임 NULL, 시각 NULL, 자격증명 전부 REVOKED, 푸시 기기 REVOKED(A4). 뒤 계정은 그대로. 앞 계정 재발급은 401 `AUT-APP-006`(A9) | 클래스 공통 정리 |
| ...-INT-011 | sweep 워커 | INT-010 이후 / limit보다 많은 후보 | 같은 `at`으로 재실행 / 반복 실행 | 재실행은 scanned 0. 반복 실행은 결정적 순서로 모두 소진 | 클래스 공통 정리 |
| ...-INT-012 | 철회 API, sweep | 예정 시각이 지났지만 sweep 전인 계정 | DELETE 철회 → sweep | 철회 409, 계정은 유예 중 그대로. sweep 후 DELETED | 클래스 공통 정리 |
| ...-INT-013 | 철회 서비스, 만료 처리 서비스, 동시 실행 | 예정 시각 직전 철회(`at` 전), 직후 만료 처리(`at` 후)로 같은 계정 | `CountDownLatch`로 동시에 실행 | 정확히 하나만 성공한다. 최종 상태는 (ACTIVE + 자격증명 ACTIVE) 또는 (DELETED + 자격증명 전부 REVOKED) 중 하나이고 섞이지 않는다 | executor `shutdownNow` |
| ...-INT-014 | 탈퇴 서비스, 동시 실행 | ACTIVE 계정 | 같은 계정에 요청 두 개 동시 실행 | 최종 유예 중 1회, 시각 하나. 결과는 성공 + (성공 또는 낙관적 잠금 409) | executor `shutdownNow` |
| ...-INT-015 | 탈퇴 서비스, 실패 주입 | ACTIVE 계정, 푸시 기기와 PENDING delivery, presence | presence 정리 포트 spy에 `doThrow` 후 요청 | 예외 전파. 계정 ACTIVE, 푸시 기기 ACTIVE, delivery PENDING, presence 그대로(전부 rollback) | spy 복구 |
| ...-INT-016 | 차단 서비스, 재발급 API | 등록한 기기 | `block` → 재발급 → `unblock` → 재발급, 유예 중 계정 `block` | 403 `AUT-APP-003`과 자격증명 ACTIVE 유지, 해제 후 200. 유예 중 차단은 409이고 상태 그대로 | 클래스 공통 정리 |
| ...-INT-017 | 답변 목록 조회 | 질문글 하나에 ACTIVE·유예 중·DELETED(완료 후 같은 닉네임을 새 계정이 사용) 작성자 답변 | 답변 목록 조회 | ACTIVE만 닉네임이 보이고 나머지는 null + `authorWithdrawn` true. 새 계정 닉네임이 옛 답변에 보이지 않는다. 본문은 셋 다 남는다(`PostAnswerQueryIntegrationTest`에 추가) | 기존 |
| ...-INT-018 | 탈퇴 서비스, 푸시 등록, 동시 실행 | ACTIVE 계정, 푸시 기기 1개 | 탈퇴 요청과 같은 사용자 푸시 재등록을 동시에 실행 | 10초 안에 둘 다 끝나고 교착이 없다. 최종 푸시 기기는 REVOKED이거나(등록 먼저) ACTIVE다(요청 먼저). 후자는 INT-010이 만료 때 정리한다 | executor `shutdownNow` |
| ...-INT-019 | 회귀 | 기존 setup | `DeviceAuthIntegrationTest`, `AccountPersistenceIntegrationTest`, `AccountEligibilityGateIntegrationTest`, `DirectionMatchingWorkerIntegrationTest`, `PushDeviceRegistrationIntegrationTest`, `AppealCaseIntegrationTest`, `OpenApiSpecificationIntegrationTest`, `CoreWorkerSchedulingIntegrationTest`(새 adapter를 목록에 추가) | 무수정 또는 목록 추가만으로 통과 | 기존 |

## 7. Cross-cutting scenarios

### Database and transactions

- 탈퇴 요청은 계정 상태, 푸시 해지·delivery 취소, presence 삭제를 한 트랜잭션에서 한다. 실패하면 전부 rollback된다(INT-002, INT-015).
- 만료 처리는 계정 하나에 트랜잭션 하나다. 계정 상태와 자격증명·푸시 폐기가 같은 트랜잭션이라 계정 갱신이 실패하면
  폐기도 rollback된다(INT-013).
- V33은 `ck_user_account_status`를 같은 이름으로 다시 만들고 CHECK 하나와 부분 인덱스 하나를 더한다. 기존 행은 그대로다(INT-001).
- 닉네임 유일 인덱스는 `deleted_at IS NULL`이 조건이라 유예 중에는 닉네임이 묶여 있고 완료 후 풀린다(INT-009).

### Concurrency and idempotency

- 계정 갱신 경쟁은 기존 `@Version` 낙관적 잠금으로 한쪽만 성공한다(INT-013, INT-014, A8).
- 탈퇴 요청의 푸시 일괄 해지는 기존 등록 경로와 같은 advisory lock 키를 같은 순서로 잡는다(INT-018).
- 탈퇴 재요청, ACTIVE 철회, 같은 `at`의 sweep 재실행, 이미 REVOKED인 자격증명 폐기는 상태를 바꾸지 않는다
  (UNIT-002, UNIT-004, UNIT-008, INT-008, INT-011).

### External APIs

- FCM은 호출하지 않는다. 푸시 해지는 DB 행만 바꾼다. 토큰은 `PushDeviceRegistrationIntegrationTest`의 테스트용 protector 구성을 따른다.

### Failure recovery and reconciliation

- 한 계정의 만료 처리 실패가 같은 batch의 다른 계정을 막지 않는다(UNIT-017).
- 예정 시각이 지났는데 sweep이 아직 돌지 않은 계정은 철회할 수 없고, 다음 sweep이 완료한다(INT-012).
- 유예 중 다시 등록한 푸시 기기는 만료 처리에서 다시 해지한다(INT-010, A4).

## 8. Test data and isolation

- Fixtures: 기기 등록·재발급은 `DeviceAuthIntegrationTest`의 MockMvc helper 방식, 푸시 기기·delivery는
  `PushDeviceRegistrationIntegrationTest`의 `saveDevice`·`NotificationDelivery` 방식, 질문글·presence·매칭은
  `DirectionMatchingWorkerIntegrationTest`의 준비 방식을 따른다. 승격된 release는 `AnswerModerationReleaseTestFixture`를 쓴다.
- Database isolation: PostGIS Testcontainers. 새 통합 클래스는 `@BeforeEach`에서 자기 테이블을 FK 순서대로 지운다.
- Clock/randomness: 유예 경계는 `TestClockConfiguration`의 `MutableClock`이나 `BatchCommand.at`으로 고정한다. 경계는 예정 시각
  ±1초와 정확히 같은 시각을 쓴다.
- External API doubles: FCM provider는 호출되지 않아야 한다. 실패 주입은 `AopTestUtils.getUltimateTargetObject` + spy를 쓴다.
- Cleanup: 동시성 테스트 executor는 `finally`에서 `shutdownNow()`

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Implementation executor | `account/domain/{Account,AccountStatus}.java`, `account/repository/**`(상태·시각·닉네임 갱신, sweep 후보 조회), `account/config/AccountWithdrawalProperties.java`(신규), `account/service/{AccountWithdrawalService,AccountWithdrawalCompletionService,AccountStatusService}.java`(신규), `account/service/port/AccountWithdrawalCleanup.java`(신규, A10), `account/sweep/AccountWithdrawalSweepWorker.java`(신규), `account/web/AccountWithdrawal*.java`(신규), `auth/domain/DeviceCredential.java`, `auth/repository/**`, `auth/service/DeviceTokenService.java`, `auth/service/DeviceCredentialWithdrawalCleanup.java`(신규), `auth/web/{DeviceTokenResponse,DeviceAuthApiSpec}.java`, `notification/repository/**`·`notification/service/PushDeviceWithdrawalCleanup.java`(신규), `direction/repository/**`(presence 삭제)·`direction/service/PresenceWithdrawalCleanup.java`(신규), `direction/matching/DirectionMatchingWorker.java`, `feed/**`(답변 목록), `answer/service/AnswerPublicationBlockChecker.java`, `scheduling/**`, `db/migration/V33__add_user_account_withdrawal.sql`(신규), `application.yml`, `docs/api/openapi.json`, `docs/product/AUTH_DESIGN.md` | — | `./gradlew compileJava javaConventionCheck` |
| 2 | Unit executor | `src/test/java/com/dnd/qello/account/domain/AccountWithdrawalTest.java`(신규, UNIT-001~007), `auth/domain/DeviceCredentialTest.java`(UNIT-008), `auth/service/DeviceTokenServiceTest.java`(UNIT-009, fake 갱신), `account/service/AccountWithdrawalServiceTest.java`(신규, UNIT-010~014), `account/service/AccountStatusServiceTest.java`(신규, UNIT-015), `answer/service/AnswerPublicationBlockCheckerTest.java`(신규, UNIT-016), `account/sweep/AccountWithdrawalSweepWorkerTest.java`(신규, UNIT-017), `direction/matching/DirectionMatchingWorkerTest.java`(UNIT-018~019, 생성자), `feed/web/PostAnswerApiMockMvcTest.java`(UNIT-020), `account/web/AccountWithdrawalControllerMockMvcTest.java`(신규, UNIT-021), `architecture/ProductionConventionAuditTest.java`(UNIT-022), `account/config/AccountWithdrawalPropertiesTest.java`(신규)·`config/RateLimitDefaultsTest.java`(UNIT-023), `notification/fanout/{RecipientNotificationFanOutWorkerTest,NotificationFanOutWorkerTest}.java`(UNIT-024), `scheduling/**Test.java`(UNIT-025, 생성자 3곳 포함), `FlywayMigrationContractTest.java`·`account/repository/jpa/AccountJpaMapperTest.java`(UNIT-026) | UNIT-001~026 | `./gradlew test` |
| 3 | Integration executor | `src/integrationTest/java/com/dnd/qello/AccountWithdrawalIntegrationTest.java`(신규, INT-002·003·005·007·008·009·016), `AccountWithdrawalSweepIntegrationTest.java`(신규, INT-010~012·015), `AccountWithdrawalConcurrencyIntegrationTest.java`(신규, INT-013·014·018), `AccountPersistenceIntegrationTest.java`·`FlywayMigrationIntegrationTest.java`·`NotificationPreferenceMigrationIntegrationTest.java`(INT-001), `DirectionRecipientSelectionIntegrationTest.java`(INT-004), `DirectionMatchingWorkerIntegrationTest.java`(INT-006), `PostAnswerQueryIntegrationTest.java`(INT-017), `CoreWorkerSchedulingIntegrationTest.java`(INT-019 목록), `docs/reports/tests/gh-337-TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL.md` | INT-001~019, 전체 | `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |

- 기존 테스트 클래스에 시나리오를 추가하면 그 클래스 헤더의 `Source scenario`에 이 계획의 ID와 추가 시각을 덧붙인다.
- 기존 assertion은 바꾸지 않는다. 예외는 UNIT-022의 감사 테스트 단언과 INT-001의 마이그레이션 수치뿐이다.
- Gradle은 JDK 21(`JAVA_HOME`)로 실행한다. 통합 테스트는 Docker가 필요하다. `pr-ready`는 백그라운드로 실행한다.
- worktree에서 Java 커밋 시 pre-commit 훅이 `DeviceTokenService.java` fixture를 스테이징하면 `git restore --staged`로 되돌리고
  보고서에 남긴다.

### 승인 필요한 가정

| ID | 가정 | 이유 |
| --- | --- | --- |
| A1 | 유예 중 탈퇴 재요청은 200과 기존 예정 시각을 돌려주고 기간을 늘리지 않는다 | 재시도에 안전하고, 재요청으로 삭제가 미뤄지지 않는다 |
| A2 | ACTIVE 계정의 철회는 200 no-op. 예정 시각이 지난 유예 계정의 철회는 sweep 전이라도 409 | 삭제 시각이 sweep 지연에 좌우되지 않는다 |
| A3 | 탈퇴 요청은 ACTIVE에서만 받는다. BLOCKED는 409 | 차단 회피 수단이 되지 않는다. 차단 계정은 재발급도 안 되므로 앱에서 호출할 일이 없다 |
| A4 | 만료 처리에서 푸시 기기를 한 번 더 전부 해지한다(#337 범위에 한 줄 추가) | 푸시 등록 API는 계정 상태를 보지 않아 유예 중 다시 등록될 수 있다 |
| A5 | 발신자 gate는 moderation gate보다 먼저다 | 판정 전 질문글이 발신자 탈퇴 후 만료까지 재시도만 반복하지 않게 한다 |
| A6 | `AnswerPublicationBlockChecker`는 유예 중 작성자에게 `ACCOUNT_WITHDRAWAL_PENDING`을 돌려준다 | 지금은 새 상태가 통과한다(fail-open) |
| A7 | `DeviceTokenService`를 method 단위 `@Transactional`로 바꾸고 감사 테스트 단언을 바꾼다 | 바뀐 Service는 TX-001을 통과해야 한다. 감사 테스트는 이 클래스를 class write 예시로 고정하고 있다 |
| A8 | 동시 갱신은 기존 `@Version` 충돌(409)로 처리하고 새 잠금을 추가하지 않는다 | 계정 영속화가 이미 낙관적 잠금이다 |
| A9 | 만료된 계정의 재발급은 401 `AUT-APP-006`이다(403 아님) | 자격증명이 REVOKED라 계정 상태 확인 전에 거절된다 |
| A10 | account 모듈에 정리 포트 인터페이스를 두고 auth·notification·direction이 구현한다 | 그 세 모듈은 이미 account를 참조한다. account가 직접 부르면 순환 의존이 생긴다 |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과(INT-019 회귀 포함)
- [ ] 잠재 문제 분석(R9~R11, R13 포함)
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: 사용자(tkv00)
- Decision: 승인. 가정 A1~A10을 그대로 채택한다. A4에 따라 #337 범위에 만료 처리의 푸시 기기 재해지를 추가한다.
- Approved at: `2026-10-09T17:54:28+09:00`
