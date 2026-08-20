# Test Report: TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ

> Created at: `2026-08-20T15:54:20+09:00`
> GitHub Issue: `#176`
> Branch: `feat/gh-176-notification-inbox-read`
> Commit: `19a6227`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 계획 §5·§6의 단위 21개·통합 32개 시나리오 전부 구현·실행. 알림함
  읽기 경로 5개(`GET` 3, `PUT` 2), 계정 자격 게이트 승격, `RecipientNotification
  FanOutWorker`의 preference 게이트 재배치.
- Unverified scope: 없음. 계획에 명시된 실행하지 못한 검증 없음.
- Release recommendation: 병합 가능. `RecipientNotificationFanOutWorkerConcurrency
  IntegrationTest` 2건의 계획 외 수정(§7 참조)만 리뷰에서 별도 확인 필요.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Temurin 25.0.3 (LTS) |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 |
| Database | PostgreSQL(PostGIS) — Testcontainers 로컬 컨테이너 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (unit) | PASS | 810 / 810 | ~6.1s | `build/test-results/test/*.xml`, 0 failures |
| `./gradlew integrationTest` | PASS | 575 / 575 | ~19.4s (JVM 측정. 컨테이너 기동 포함 전체 벽시계 시간은 별도) | `build/test-results/integrationTest/*.xml`, 0 failures |
| `./harness test-run --id TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ` | PASS | 위 두 스위트 포함 | 전체 배치 ~4분 38초 | 본 보고서 자동 스캐폴드 |
| `./harness check` | PASS | — | — | secret preflight 1087 파일, JUnit policy 203 파일, convention·workflow·label·husky 검사 전부 통과 |

## 4. Scenario results

계획 §5(단위)·§6(통합)의 모든 ID가 구현·실행되어 PASS했다. 대표 항목만 표로
남기고 전체는 구현 커밋(S1~S9, `feat/gh-176-notification-inbox-read`)의 테스트
파일을 근거로 삼는다.

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~003 | PASS | `NotificationCardTest` | `expiresAt`은 `DIRECTION_POST`+`AVAILABLE`에서만 허용 |
| UNIT-002 | PASS | `NotificationTargetDecisionTest` | `navigable`·`reason`·`fallback`을 `targetState`에서 파생시켜 상태 자체가 어긋날 수 없게 설계(계획 원안보다 강한 보장) |
| UNIT-004~013 | PASS | `NotificationInboxServiceTest` | 인가 5경로, limit/cursor 검증, 읽음 멱등, `REVOKED` 409, 소유권 |
| UNIT-014~015 | PASS | `account.service.AccountEligibilityGateTest`, `feed.service.AccountEligibilityGateTest` | 승격된 게이트의 supplier 위임과 `FED-APP-*` 번역 |
| UNIT-016~018 | PASS | `NotificationWebContractTest`, `NotificationApiMockMvcTest` | 경로 선언, 응답 record 민감 필드 부재, 오류 코드→HTTP 매핑 |
| UNIT-019~021 | PASS | `RecipientNotificationFanOutWorkerTest` | preference off에서 `notification` 저장·`delivery` 0건 |
| INT-001~012 | PASS | `NotificationInboxQueryIntegrationTest` | cursor 페이징, 동일 `created_at` 다건, 상태·차단 우선순위 충돌 2건 포함 |
| INT-013~018 | PASS | `NotificationInboxCommandIntegrationTest`, `NotificationInboxConcurrencyIntegrationTest` | `seen_at` `GREATEST`, 동시 advance |
| INT-019~021 | PASS | `NotificationInboxCommandIntegrationTest` | 읽음 멱등, `REVOKED` 409, 남의 알림 무부작용 |
| INT-022~026 | PASS | `NotificationInboxQueryIntegrationTest` | 재평가, fallback 분기, `EXPLAIN`에 `notification_recipient_feed_idx` 확인, 민감 필드 부재, 6종 타입 |
| INT-027~029 | PASS | `RecipientNotificationFanOutWorkerIntegrationTest` | preference on/off/차단의 `notification`·`delivery` 건수 |
| INT-030~031 | PASS | `AccountEligibilityGateIntegrationTest` | `FED-APP-*`·`NOT-APP-*` 회귀 |
| INT-032 | PASS | `OpenApiSpecificationIntegrationTest` | 경로 5개, operation 5개(`GET` 3·`PUT` 2) 확인 |

## 5. Failures and diagnostics

최종 실행에는 실패가 없다. 구현 중 발견하고 그 자리에서 고친 것들은 §7에 기록한다.

## 6. Potential issues

### Application code

- `unreadCount`는 절단 없이 정확한 수를 반환한다(설계 §12-1 확정). 사용자당
  미읽음이 수백~수천 건 쌓이는 사용 패턴이 나타나면 `COUNT` 비용이 커질 수
  있다 — 후속 이슈에서 상한 절단을 검토할 필요가 있다.

### Infrastructure and resource limits

- 특이사항 없음. `V24`는 신규 테이블 1개와 부분 인덱스 1개만 추가한다.

### Database and migrations

- `notification_recipient_feed_idx`가 목록 쿼리에서 실제로 선택되는지 INT-024로
  확인했다(§11-1 확정 사항). `limit` 상한 50을 넘는 대량 조회 패턴이 생기면
  재검토가 필요하다.

### Concurrency and idempotency

- `seen_at` 전진은 `INSERT ... ON CONFLICT ... GREATEST` 단일 문장이라 동시
  호출에서 경합이 생기지 않는다(INT-018로 확인).
- `markRead`의 멱등성은 서비스 계층에서 "이미 READ면 갱신하지 않음"으로 보장한다
  — 도메인의 `Notification.markRead` 자체는 멱등이 아니므로, 이 계층을 우회해
  직접 호출하는 코드가 생기면 멱등성이 깨진다는 점을 유의해야 한다.

### Transactions and event ordering

- `RecipientNotificationFanOutWorker`의 preference 재배치로 `notification` INSERT와
  `notification_delivery` INSERT 사이에 preference 재조회가 들어간다. 같은
  트랜잭션 안에서 일어나므로 원자성은 유지되지만, `NotificationInboxQueryQuery
  Repository`가 이 트랜잭션이 커밋되기 전에는 새 알림을 볼 수 없다는 일반적인
  커밋 가시성 제약은 그대로다(신규 위험 아님).

### External APIs

- 해당 없음(#179 범위 밖).

### Failure recovery and reconciliation

- `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest`와
  `OutboxLeaseIntegrationTest`의 재시도·lease·stale 처리 경로는 이번 변경으로
  건드리지 않았고, 재실행에서 통과를 확인했다(단, 아래 §7의 계획 외 변경 참고).

## 7. Regression and residual risk

- **계획 외 변경**: `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest`는
  테스트 계획에서 "수정 없이 재실행만" 하기로 정했으나, S8(preference 게이트
  재배치) 적용 후 재실행하자 시나리오 2개가 실패했다. 원인은 버그가 아니라
  설계 변경의 정당한 결과다 — preference 읽기 시점이 `isEligible`(알림
  INSERT 이전)에서 `persistPendingDeliveries` 직전(알림 INSERT 이후)으로
  옮겨지면서, 두 동시성 테스트가 주입하는 "커밋 시점"과 preference 재조회
  시점의 상대 순서가 바뀌었다.
  - `suppressesWhenEligibilityChangesCommitBeforeRead`: preference 비활성화가
    worker 실행 전체보다 먼저 커밋되므로, 이제는 알림함 행이 생기고
    (`notification` 1건) delivery만 억제된다(`notification_delivery` 0건).
    이전에는 두 값 모두 0이었다.
  - `keepsEligibilitySnapshotWhenChangesCommitAfterRead`: gate가 `notification`
    INSERT 직전에서 재개되도록 걸려 있는데, preference 재조회가 이제 그
    INSERT *이후*에 일어나므로 이 시점에 커밋된 비활성화가 반영되어 delivery가
    억제된다. block·account·post 세 축은 여전히 `isEligible`(INSERT 이전)
    시점에서 판정하므로 영향이 없다.
  - 두 테스트 모두 `preference` fixture만 별도로 분리해 새 기댓값
    (`notification=1, delivery=0`)으로 갱신했고, 변경 이유를 테스트 코드
    주석에 남겼다. `RecipientNotificationFanOutWorkerTest`(단위)와
    `RecipientNotificationFanOutWorkerIntegrationTest`의 관련 시나리오도 같은
    이유로 갱신했다 — `TASK.md`의 "Contract changes to existing tests"에 이미
    반영되어 있다.
- **잔여 위험**: preference 재조회 시점이 이동하면서, "알림 INSERT는 성공했지만
  같은 트랜잭션 안에서 이후 단계가 실패해 롤백되는" 경우 `notification` 행도
  함께 사라진다 — 부분 반영 위험은 여전히 없다(단일 트랜잭션 경계 유지 확인,
  INT-027~029).

## 8. Artifacts

- Test plan: `docs/test-plans/gh-176-TEST-PLAN-GH-176-NOTIFICATION-INBOX-READ.md`
- CI run: 로컬 실행(`./harness test-run`, `./harness check`). 원격 CI 미실행.
- Related ADR: 없음. 설계 근거는 `docs/product/NOTIFICATION_INBOX_DESIGN.md`
  §4~§15.
- PR: 미생성(이 보고서 작성 시점 기준).

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (없음 — 계획의 모든 시나리오 실행)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (§6 Application code 항목은 후속
      이슈 생성이 필요하며 아직 만들지 않았다)
- [x] 실행 결과와 PR 설명이 일치함 (PR 생성 시 본 보고서를 링크하고
      Contract changes 항목을 본문에 명시할 것)
