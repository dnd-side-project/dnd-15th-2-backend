# Test Plan: TEST-PLAN-GH-179-PUSH-DELIVERY

> Created at: `2026-08-24T18:43:26+09:00`
> GitHub Issue: `#179`
> Status: Approved — P0/P1 범위 승인

## 1. Objective

인증된 사용자가 FCM registration token을 원문 노출 없이 등록·해지하고, 이미 적재된
`notification_delivery`가 최신 차단·preference·기기·대상 상태를 다시 확인한 뒤 FCM HTTP v1로
전달되는지 검증한다.

가장 큰 실패 위험은 token 유출, 다른 사용자 기기의 소유권 탈취·오해지, worker crash 후
`PROCESSING` 고착, stale worker의 결과 덮어쓰기, 차단 이후 발송, provider 오류 오분류와
잠금화면 payload의 개인정보 노출이다. 테스트는 정상 성공보다 이 실패 경계와 transaction
원자성을 우선한다.

## 2. Scope

### Included

- `PushToken` 입력 제한과 문자열 redaction
- AES-256-GCM ciphertext envelope, HMAC-SHA-256 fingerprint, key 분리와 rotation 읽기
- 같은 사용자의 재등록, 다른 사용자의 원자적 token 소유권 이전과 멱등 해지
- 등록·해지 API 인증, validation, `204 No Content` 계약
- FCM HTTP v1 요청 생성, OAuth credential port, 응답·오류 분류
- delivery claim, lease 만료 회수, attempt generation fencing과 batch 경합
- retryable/permanent/invalid-token 결과의 delivery·device 상태 전이
- 발송 직전 preference, 차단, 소유자, device, notification과 대상 유효성 재검사
- `type`, `count`, `hasRemainingTime` payload allowlist와 금지 정보 비노출
- 발송 실패·억제 뒤 `notification`과 콘텐츠 열람 자격 보존

### Excluded

- 실제 FCM project와 실제 Android/iOS 기기로의 end-to-end 발송
- 실제 credential, encryption key, fingerprint key 또는 `.env` 값
- 모바일 앱의 foreground·background·종료·잠금 상태 표시 동작
- APNs 직접 adapter와 multi-provider routing
- bundle·일일 상한·quiet hours — #180
- scheduler/polling 활성화 — #182
- Terraform, AWS resource, 배포와 production 변경
- DB schema migration. query plan이 현재 index로 불충분하면 결과만 보고하고 범위를 재승인한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #179 | 등록 `POST /notifications/devices`, 해지 `POST /notifications/devices/revoke`, FCM HTTP v1, token 비노출, retry/DEAD, invalid device, 발송 직전 정책 재검사 |
| `TASK.md` | DEC-179-001~008, schema 무변경, 기존 delivery 컬럼 lease/generation 재사용, payload 세 필드 제한 |
| `push_device` V1 schema | ACTIVE fingerprint 전역 unique, platform IOS/ANDROID, ACTIVE/INVALID/REVOKED와 `revoked_at` 불변식 |
| `notification_delivery` V1 schema | PENDING/PROCESSING/SENT/FAILED/CANCELLED/DEAD, non-negative attempt, SENT와 `sent_at` 불변식 |
| `NotificationRepository` 현재 계약 | single-ID claim과 무조건 update는 generation을 보호하지 않으므로 due batch claim과 fenced terminal API가 필요 |
| 기존 fan-out 통합 테스트 | `notification`과 delivery 분리, ACTIVE device만 delivery 생성, 실패가 notification/eligibility를 변경하지 않음 |
| FCM HTTP v1 adapter 계약 | provider 세부 오류를 ACCEPTED/INVALID_TOKEN/RETRYABLE_FAILURE/PERMANENT_FAILURE로 제한해 application에 반환 |
| AGENTS.md §3 | JUnit 5, 모든 메서드 `@DisplayName`, 정확한 ISO 8601 class header와 source scenario, 단위·통합 분리 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| token이 log·예외·응답·fixture에 평문으로 남음 | 제3자가 임의 push 가능 | 중간 | P0 | redaction unit, API/adapter log capture, repository 원문 부재 assertion |
| encryption nonce 재사용 또는 fingerprint를 단순 hash로 구현 | ciphertext 상관관계·offline 대입 위험 | 중간 | P0 | 같은 원문의 서로 다른 ciphertext, 같은 HMAC, key별 결과 assertion |
| 같은 ACTIVE token 동시 등록으로 소유자가 둘이 되거나 500 발생 | 계정 전환 실패·개인정보 노출 | 높음 | P0 | 실제 PostgreSQL 동시성 통합 테스트와 unique 결과 검사 |
| 다른 사용자가 남의 token을 해지 | 알림 전달 방해 | 중간 | P0 | user ID + fingerprint 조건의 0행 update와 동일 204 응답 |
| worker crash 후 PROCESSING 영구 고착 | push 유실 | 높음 | P0 | lease 전·후 claim 결과와 회수 assertion |
| stale worker가 새 worker 결과를 덮어씀 | SENT가 FAILED/DEAD로 역행 | 중간 | P0 | generation 불일치 terminal update 0행 assertion |
| 차단·preference 변경 뒤 queued push 발송 | 명시적 사용자 선택 위반 | 높음 | P0 | queue 생성 후 정책 변경, provider 미호출·CANCELLED assertion |
| provider 오류 오분류 | 무한 retry 또는 복구 가능한 delivery 유실 | 높음 | P0 | HTTP status/error code matrix와 Retry-After 검증 |
| invalid token 처리 중 일부만 commit | INVALID 기기에 대기 delivery 잔존 | 중간 | P0 | device INVALID + 미발송 CANCELLED 원자성·rollback 검증 |
| provider network 호출을 DB transaction 안에서 수행 | lock 장기 점유·batch 정체 | 중간 | P1 | blocking fake provider 중 별도 connection의 DB update 가능 assertion |
| payload에 본문·닉네임·위치·내부 ID 포함 | 잠금화면 개인정보 노출 | 중간 | P0 | serialized provider request key/value allowlist 검사 |
| 현재 partial index가 stale PROCESSING query를 지원하지 못함 | backlog 증가 시 full scan | 중간 | P1 | PostgreSQL `EXPLAIN` evidence; 부적합 시 migration 재승인 요청 |
| FCM 수락 후 응답 유실로 중복 발송 | 사용자에게 중복 push | 낮음~중간 | P1 | at-least-once failure test와 남은 위험 보고 |
| key 교체 후 과거 ciphertext 복호화 실패 | 모든 기존 기기 발송 중단 | 중간 | P0 | current write + previous-key read + unknown key ID rejection |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-001 | null·blank·허용 크기 초과 token과 정상 token | `PushToken` 생성·`toString`·예외 경로 실행 | 잘못된 값은 원문 없는 validation 오류, 정상 객체의 문자열은 `REDACTED` | P0 | Token Security Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-002 | 같은 원문과 동일 encryption/fingerprint key | 두 번 protect | ciphertext nonce는 달라지고 fingerprint는 같으며 어느 출력에도 원문 없음 | P0 | Token Security Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-003 | 서로 다른 token, encryption key, fingerprint key | protect와 fingerprint 계산 | 다른 token fingerprint는 다르고 key를 바꾸면 결과도 달라짐 | P0 | Token Security Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-004 | 변조된 ciphertext/tag, 미지원 version, 알 수 없는 key ID | decrypt | 원문 없는 제한된 오류로 거절 | P0 | Token Security Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-005 | current key와 previous read key가 있는 key ring | 새 token 쓰기와 과거 envelope 읽기 | 쓰기는 current key ID, 읽기는 두 key 모두 가능하며 폐기 key는 거절 | P0 | Token Security Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-006 | 같은 사용자 ACTIVE/REVOKED/INVALID 행 | register command | ACTIVE는 갱신, 비활성은 ACTIVE 복구, 새 ciphertext·lastSeen·revokedAt 규칙 유지 | P0 | Device Service Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-007 | 다른 사용자의 같은 ACTIVE fingerprint | register command | 원자적 이전 repository command를 호출하고 이전 기기 미발송 취소를 요청 | P0 | Device Service Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-008 | 본인 token, 남의 token, 없는 token, 이미 REVOKED token | revoke command | 네 경우 모두 외부 응답은 동일하며 본인 ACTIVE만 전이 | P0 | Device Service Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-009 | 각 `NotificationType`과 event별 남은 시간 조건 | payload 생성 | key는 정확히 세 개, count는 1, 값은 FCM data 문자열이며 내부 ID·본문 없음 | P0 | Dispatch Policy Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-010 | preference OFF, block, inactive device, owner mismatch, revoked notification, invalid target, 정상 context | eligibility 판정 | 정상만 SEND, 정책 억제는 CANCEL, 무결성 위반은 별도 terminal 분류 | P0 | Dispatch Policy Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-011 | PENDING/FAILED/PROCESSING와 generation·lease 시각 | claim·sent·failed·cancel transition | attempt는 claim 때 증가하고 stale generation은 terminal command가 되지 않음 | P0 | Delivery State Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-012 | retryable, rate limit+Retry-After, permanent, max-attempt, invalid token | retry policy 판정 | 정확한 nextAttemptAt 또는 DEAD/INVALID terminal decision | P0 | Delivery State Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-013 | FCM 2xx, UNREGISTERED, payload-valid INVALID_ARGUMENT, 429, 5xx, timeout, 인증 실패 | adapter 결과 mapping | 네 provider result 중 정확한 값으로 변환하고 response body·token은 exception에 없음 | P0 | FCM Adapter Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-014 | 안전 payload와 fake OAuth access token provider | FCM request serialization | endpoint path·authorization header·data fields가 맞고 token은 request 대상 필드 외 log에 없음 | P0 | FCM Adapter Executor |
| TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-015 | provider 활성/비활성 profile과 key·credential 누락/오형식 | configuration 생성 | 실제 provider 활성 환경은 fail-fast, test fake 환경은 실제 credential 없이 기동 | P1 | Configuration Executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-001 | MockMvc, service, protector, PostgreSQL | 인증 사용자와 fake key material, token sentinel | 등록 POST | 204, ACTIVE 한 행, DB·응답·captured log에 sentinel 원문 없음 | 사용자·device 삭제, log appender 분리 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-002 | MockMvc, repository transaction | 본인 ACTIVE device와 PENDING/FAILED/SENT delivery | revoke POST 두 번 | 둘 다 204, device REVOKED, PENDING/FAILED만 CANCELLED, SENT 보존 | 관련 notification/delivery/device 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-003 | MockMvc, repository | A 소유 token, B 인증 | B가 revoke POST | 204지만 A device와 delivery는 불변 | 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-004 | registration service, PostgreSQL | 같은 사용자, 동일 token, 동시 시작 barrier | 두 transaction 동시 등록 | ACTIVE 한 행, unique 오류 외부 노출 없음, latest lastSeen·유효 ciphertext | executor 종료, 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-005 | registration service, PostgreSQL | A ACTIVE token과 B 인증, 동시 재등록 barrier | A 재등록과 B 소유권 이전 경합 | ACTIVE 소유자는 한 명, 이전 소유 미발송 CANCELLED, partial commit 없음 | executor 종료, 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-006 | delivery repository, PostgreSQL | due PENDING/FAILED와 미래 due·terminal 행 | worker 두 개가 batch claim | 각 due ID는 한 worker만 획득, 미래/terminal 제외, attempt 1회 증가 | delivery 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-007 | delivery repository, PostgreSQL | PROCESSING lease와 generation G | 만료 전·후 다른 worker claim, 구 worker terminal update | 만료 전 회수 불가, 후에는 G+1 회수, G update는 0행 | delivery 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-008 | dispatch worker, fake provider, PostgreSQL | 정상 context와 PENDING delivery | batch 처리 | provider 1회 호출, SENT·sentAt·providerMessageId 기록 | fake reset, 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-009 | dispatch worker, retry policy | provider가 retryable 후 accepted | due 시각 전·후 batch 처리 | FAILED/backoff, 이른 poll 제외, due 뒤 SENT; notification 불변 | 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-010 | dispatch worker, fake provider | max attempt와 permanent failure | batch 처리 | provider 분류에 따라 DEAD, 추가 claim 없음, notification 불변 | 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-011 | dispatch worker, device repository | provider INVALID_TOKEN, 같은 device의 복수 미발송 delivery | 첫 delivery 처리 | device INVALID, 현재 건 terminal, 나머지 PENDING/FAILED CANCELLED가 한 transaction으로 반영 | 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-012 | fan-out, preference repository, dispatch | delivery 생성 뒤 global/type OFF | dispatch | provider 미호출, CANCELLED, notification 보존 | preference·관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-013 | fan-out, block repository, dispatch | actor가 있는 delivery 생성 뒤 양방향 중 한쪽 block | dispatch | 두 block 방향 모두 provider 미호출·CANCELLED | block·관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-014 | dispatch context queries | queued 뒤 device revoke, notification revoke, target 만료/숨김 각각 | dispatch | 각 건 provider 미호출·CANCELLED, 원장과 열람 자격은 기존 정책대로 보존 | 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-015 | payload factory, FCM fake HTTP server | 본문·닉네임·위치·거리·내부 ID sentinel이 있는 domain data | dispatch request capture | data에는 세 key만 있고 모든 sentinel과 notificationId/title/body 없음 | fake server 종료, 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-016 | dispatch transaction, blocking fake provider, PostgreSQL | provider call을 latch로 중지 | 다른 connection에서 무관한 delivery/device 갱신 | provider 대기 중 장기 transaction lock 없이 갱신 완료 | latch 해제, executor 종료, 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-017 | batch worker, fake provider | success·retry·invalid·policy-cancel delivery 혼합 | 한 batch 처리 | 한 건 실패가 다음 건을 막지 않고 결과·상태가 각각 일치 | fake reset, 관련 행 삭제 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-018 | MockMvc, security, validation | 미인증, null body, blank/oversize token, 잘못된 platform | 두 API 호출 | 인증·validation 표준 오류, 응답·log에 token 원문 없음 | log appender 분리 |
| TEST-PLAN-GH-179-PUSH-DELIVERY-INT-019 | PostgreSQL query planner | 운영 비율을 모사한 PENDING/FAILED/PROCESSING fixture | due+stale claim SQL `EXPLAIN` | 사용 index와 예상 row scan을 기록; full scan이면 schema 무변경 가정을 BLOCKED로 보고 | fixture 삭제 |

## 7. Cross-cutting scenarios

### Database and transactions

- 등록은 fingerprint row lock과 ACTIVE partial unique index를 함께 사용한다. 소유권 이전의
  REVOKED·CANCELLED·새 ACTIVE 변경은 한 transaction에서 전부 성공하거나 전부 rollback한다.
- 해지는 `user_id + token_fingerprint + ACTIVE` 조건으로 한 행만 갱신하고 대상 기기의
  PENDING/FAILED 취소를 같은 transaction에 둔다.
- claim은 `FOR UPDATE SKIP LOCKED` 또는 동등한 원자적 UPDATE/RETURNING으로 batch 경합을 보호한다.
- provider network call은 claim transaction commit 뒤, terminal transaction 시작 전에 수행한다.
- terminal update는 `id + PROCESSING + attempt_count(generation)` 조건으로 stale worker를 차단한다.

### Concurrency and idempotency

- 동일 사용자 동일 token 등록, 다른 사용자 소유권 이전, revoke와 register 경합을 실제 PostgreSQL
  transaction으로 재현한다.
- 두 worker의 동일 batch claim에서 중복 획득이 없는지 검증한다.
- revoke와 terminal update가 경합해도 REVOKED 기기로 새 발송이 시작되지 않는지 검증한다.
- POST revoke는 반복 호출과 존재하지 않는 token 모두 동일 204이며 존재 여부를 노출하지 않는다.

### External APIs

- 실제 FCM과 실제 OAuth credential을 사용하지 않는다.
- local fake HTTP server와 credential port double로 요청 path, header, JSON, timeout, status/error body를
  검증한다.
- FCM `INVALID_ARGUMENT`은 payload 자체가 유효하다고 검증된 fixture에서만 invalid token으로 분류한다.
- 429의 `Retry-After`, 5xx와 timeout은 retryable이며 인증·권한·유효한 요청의 영구 거절은
  permanent로 분리한다.
- request/response 전문을 assertion failure나 application exception에 복사하지 않는다.

### Failure recovery and reconciliation

- claim commit 직후 crash는 lease 만료 뒤 재처리한다.
- provider 수락 직후 DB commit 전 crash는 중복 가능성이 있는 at-least-once 구간으로 보고서에 남긴다.
- ciphertext 인증 실패와 알 수 없는 key ID는 provider를 호출하지 않고 운영 조사 가능한 terminal
  결과로 끝낸다. token 원문은 관측 정보에 포함하지 않는다.
- batch 중 한 delivery의 provider·DB terminal 기록 실패가 나머지 delivery 처리를 막지 않게 한다.

## 8. Test data and isolation

- Fixtures: 각 scenario ID 접두사를 dedup/fingerprint label에 사용한다. token sentinel은 실제 provider
  형식을 모방하지 않는 고정 가짜 문자열만 사용하며 production 값은 사용하지 않는다.
- Database isolation: Testcontainers PostgreSQL과 기존 integration profile을 사용한다. FK 역순으로
  delivery → notification → push_device → 관련 domain fixture를 정리한다.
- Clock/randomness: 고정 `Clock`과 테스트용 `SecureRandom`/nonce source를 주입한다. production
  `SecureRandom`을 mock하지 않고 crypto unit에서만 결정적 source를 사용한다.
- External API doubles: fake FCM HTTP server와 in-memory `PushProvider`. 실제 network·credential 금지.
- Log capture: Logback `ListAppender`를 test별 attach/detach하고 token·본문·위치 sentinel 부재만 검사한다.
- Concurrency: `CountDownLatch`, 별도 transaction/executor, 명시적 timeout을 사용하고 `finally`에서
  latch와 executor를 정리한다. sleep 기반 순서 의존 테스트는 사용하지 않는다.
- Cleanup: trigger나 function을 임시 생성할 경우 scenario별 고유 이름을 쓰고 `finally`에서 제거한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Token Security Executor | `src/test/java/com/dnd/qello/notification/push/security/PushTokenTest.java`, `PushTokenProtectorTest.java` | UNIT-001~005 | `./gradlew test --tests '*PushToken*Test'` |
| 2 | Delivery State Executor | `src/test/java/com/dnd/qello/notification/push/PushDeliveryStateTest.java`, `PushDeliveryRetryPolicyTest.java` | UNIT-011~012 | `./gradlew test --tests '*PushDelivery*Test'` |
| 3 | Device Service Executor | `src/test/java/com/dnd/qello/notification/service/PushDeviceServiceTest.java` | UNIT-006~008 | `./gradlew test --tests '*PushDeviceServiceTest'` |
| 4 | Dispatch Policy Executor | `src/test/java/com/dnd/qello/notification/push/PushPayloadFactoryTest.java`, `PushDispatchEligibilityTest.java` | UNIT-009~010 | `./gradlew test --tests '*PushPayloadFactoryTest' --tests '*PushDispatchEligibilityTest'` |
| 5 | FCM Adapter Executor | `src/test/java/com/dnd/qello/notification/push/fcm/FcmHttpV1PushProviderTest.java`, `src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java` | UNIT-013~015 | `./gradlew test --tests '*FcmHttpV1PushProviderTest' --tests '*PushConfigurationTest'` |
| 6 | Device Persistence Executor | `src/integrationTest/java/com/dnd/qello/PushDeviceRegistrationIntegrationTest.java` | INT-001~005, INT-018 | `./gradlew integrationTest --tests '*PushDeviceRegistrationIntegrationTest'` |
| 7 | Delivery Lease Executor | `src/integrationTest/java/com/dnd/qello/PushDeliveryLeaseIntegrationTest.java` | INT-006~007, INT-019 | `./gradlew integrationTest --tests '*PushDeliveryLeaseIntegrationTest'` |
| 8 | Dispatch Integration Executor | `src/integrationTest/java/com/dnd/qello/PushDeliveryDispatchIntegrationTest.java` | INT-008~017 | `./gradlew integrationTest --tests '*PushDeliveryDispatchIntegrationTest'` |
| 9 | Test Report Executor | `docs/reports/tests/gh-179-TEST-PLAN-GH-179-PUSH-DELIVERY.md` | 전체 | `./harness test-run --id TEST-PLAN-GH-179-PUSH-DELIVERY`, 필수 저장소 검증 |

각 executor는 표의 파일만 소유한다. production 파일은 승인된 구현 계획의 별도 실행자가 소유하며,
test executor는 production code를 수정하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] P1 미실행 항목은 이유·영향·남은 위험·후속 검증 방법 기록
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 모든 테스트 class header에 정확한 ISO 8601 생성 시각과 source scenario 기록
- [ ] 단위와 통합 테스트 source set 분리
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 동시성 테스트가 sleep 대신 latch/transaction으로 결정적으로 동작
- [ ] 실제 credential·token·URL·계정 식별자 비노출
- [ ] 애플리케이션·DB·동시성·transaction·외부 API·장애 복구 잠재 문제 분석
- [ ] `templates/test-report.md` 기반 테스트 보고서 생성
- [ ] `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`,
      `git diff --check` 결과 기록

## 11. Human approval

- Reviewer: `@Byuntil`
- Decision: `APPROVED`
- Approved at: `2026-08-24`
- Approval note: 현재 P0/P1 시나리오와 범위를 그대로 승인한다. D-2 보강안, FCM HTTP v1,
  Android/iOS 모두 FCM, token 비절단 정책, `POST /devices/revoke`, FID 전환 후속 분리를
  함께 승인한다. 실제 credential·모바일 OS 동작·Terraform apply는 계획 범위 밖이다.
