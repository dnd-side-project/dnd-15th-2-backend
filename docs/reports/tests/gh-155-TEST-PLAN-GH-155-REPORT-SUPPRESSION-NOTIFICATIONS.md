# Test Report: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS

> Created at: `2026-08-19T15:00:00+09:00`
> GitHub Issue: `#155`
> Branch: `feat/gh-155-report-suppression-notifications`
> Commit: `5309cc3a1f4ed2b59b69c2b32254d3e61b7dd1c7` (base) + working tree changes below

## 1. Executive summary

- Result: `PASS`
- Tested scope: 집계 제외 2계층(신고자 한정 숨김·전역 숨김)과 사건 종결 결과 알림
  (내부 전용 `SafetyCaseResolutionService.resolveCase` → 신고자별 outbox 이벤트 →
  `ReportResolutionFanOutWorker` fan-out). 단위 9개(UNIT-001~008, UNIT-009는
  §1 참고로 대체) + 통합 18개(INT-001~018), 총 27개 시나리오 중 26개를 직접
  구현·실행했고 나머지 1개(UNIT-009)는 기존 회귀 커버리지로 충당했다(§1 아래
  설명).
- Unverified scope: 사건 병합 재시도 소진(`SAF-INFRA-002`) 경로는 이번에도
  재현하지 못했다(#154에서 이미 미검증으로 기록된 것과 같은 한계 — 사건 종결
  경로 자체에는 그런 재시도 루프가 없어 해당 없음). 운영자 판정 REST API(#156)는
  이 이슈 범위 밖이라 검증하지 않았다.
- Release recommendation: 병합 가능. §6에 기록한 두 가지 설계 관찰(네이밍
  충돌, hide/restore의 유일한 진입점)은 기능 결함이 아니라 리뷰어가 알아야 할
  설계 특성이다.

### UNIT-009 처리 방식

테스트 계획의 UNIT-009("내부 종결 서비스 메서드가 내부적으로
`ReportCase.resolve(...)`를 호출")는 별도 mock 기반 단위 테스트를 새로 만들지
않았다. `ReportCase.resolve()` 도메인 가드는 `ReportCaseAndEvidenceTest`(#153)가
이미 단위 수준에서 검증하고, 이 이슈가 실제로 그 메서드를 호출하는지는
`ReportResolutionIntegrationTest`의 INT-011~017이 실제 서비스 호출로 이미
증명한다. mock으로 같은 사실을 다시 확인하는 테스트는 실행 비용만 늘리고
새로운 위험을 잡지 못한다고 판단해 생략했다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.11 |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 |
| Database | PostgreSQL + PostGIS(testcontainers, `postgis/postgis:16-3.5-alpine`), 로컬 Docker |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 672 / 672 | ~1분 | `build/test-results/test/*.xml` |
| `./gradlew integrationTest` | PASS | 521 / 521 | 26분 24초 | `build/test-results/integrationTest/*.xml` |
| `./harness check` | PASS | — | — | 콘솔 출력 |
| `./harness pr-ready --project-tests` | PASS | — | — | 콘솔 출력(내부적으로 `test`·`integrationTest`·`check` 재확인, 모두 UP-TO-DATE) |
| `git diff --check` | PASS | — | — | 공백 오류 없음 |

첫 통합 테스트 전체 실행에서 `FlywayMigrationIntegrationTest`가 1건
실패했다 — 이 이슈가 추가한 `V21` 마이그레이션 때문에 "V1부터 V20까지"를
전제한 하드코딩된 개수·이름 검증(`hasSize(20)`, `flyway_schema_history`
버전 체크, `EXPECTED_INDEXES` 목록)이 깨졌다. 이 저장소의 기존 테스트
자산이라 실행 에이전트가 함께 갱신했다(§6 "애플리케이션 코드 경계" 참고).
갱신 후 재실행에서 521개 전부 통과했다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `AnswerHideRestoreTest#hidePublishedAnswerTransitionsToHidden` | |
| UNIT-002 | PASS | `AnswerHideRestoreTest#hideRejectsNonPublishedAnswer` | |
| UNIT-003 | PASS | `AnswerHideRestoreTest#restoreHiddenAnswerTransitionsToPublished` | `publishedAt` 보존 정책으로 구현(§6) |
| UNIT-004 | PASS | `AnswerHideRestoreTest#restoreRejectsNonHiddenAnswer` | |
| UNIT-005 | PASS | `AnswerHideRestoreTest#hideRequiresTimestamp` | |
| UNIT-006 | PASS | `NotificationRevokeTest#revokesUnreadNotification`, `#revokesReadNotificationAndClearsReadAt` | `revoke()`는 인자 없음으로 구현(§6) |
| UNIT-007 | PASS | `NotificationRevokeTest#revokeIsIdempotent` | |
| UNIT-008 | PASS | `NotificationRevokeTest#revokedNotificationCannotBeMarkedRead` | |
| UNIT-009 | 대체 커버리지 | `ReportCaseAndEvidenceTest`(#153) + INT-011~017 | 위 "UNIT-009 처리 방식" 참고 |
| INT-001 | PASS | `ReportSuppressionIntegrationTest#reporterDoesNotSeeOwnReportedAnswerInList` | |
| INT-002 | PASS | `ReportSuppressionIntegrationTest#reporterInboxAnswerCountExcludesOwnReportedAnswer` | |
| INT-003 | PASS | `ReportSuppressionIntegrationTest#reporterInboxUnreadAnswerCountExcludesOwnReportedAnswer` | |
| INT-004 | PASS | `ReportSuppressionIntegrationTest#senderSentPostCountsExcludeOwnReportedAnswer` | |
| INT-005 | PASS | `ReportSuppressionIntegrationTest#suppressionSurvivesCaseResolution` | |
| INT-006 | PASS | `AnswerGlobalHideIntegrationTest#globalHideRemovesAnswerFromListAndCount` | `resolveCase(ACTIONED)` 경유로 재설계(§6) |
| INT-007 | PASS | `AnswerGlobalHideIntegrationTest#globalHideRemovesUnreadAnswerCount` | 위와 동일 |
| INT-008 | PASS | `AnswerGlobalHideIntegrationTest#globalHideRevokesExistingNotification` | 위와 동일 |
| INT-009 | PASS | `AnswerGlobalHideIntegrationTest#restoreBringsAnswerBackToListAndCount` | |
| INT-010 | PASS | `AnswerGlobalHideIntegrationTest#restoringContentlessAnswerAfterMediaCleanupFails` | deferred constraint, 커밋 시점에 실패 확인 |
| INT-011 | PASS | `ReportResolutionIntegrationTest#resolvingMergedCaseEmitsOneOutboxEventPerReport` | |
| INT-012 | PASS | `ReportResolutionIntegrationTest#outboxPayloadOmitsTargetAndAuthorIdentifiers` | |
| INT-013 | PASS | `ReportResolutionIntegrationTest#fanOutCreatesNotificationPerReporterWithDeliveries` | |
| INT-014 | PASS | `ReportResolutionIntegrationTest#fanOutIsIdempotentAcrossReruns` | |
| INT-015 | PASS | `ReportResolutionIntegrationTest#inAppNotificationIsCreatedEvenWhenPushPreferenceDisabled` | |
| INT-016 | PASS | `ReportResolutionIntegrationTest#outboxSaveFailureRollsBackCaseAndReportState` | `@MockitoSpyBean OutboxEventRepository` |
| INT-017 | PASS | `ReportResolutionIntegrationTest#concurrentResolveAttemptsResolveExactlyOnce` | 2-way 동시성 |
| INT-018 | PASS | `ReportSuppressionIntegrationTest#suppressionIndexExists` | |

## 5. Failures and diagnostics

최초 전체 실행에서 재현된 실패 1건:

- 실패한 명령: `./gradlew integrationTest`
- 오류 요약: `FlywayMigrationIntegrationTest > 빈 PostGIS 데이터베이스의
  startup에서 V1부터 V20까지 migration을 적용한다` — `AssertionError`
  (`flyway.info().applied()`가 20이 아니라 21).
- 재현 조건: `V21__add_report_reporter_answer_suppression_index.sql`
  마이그레이션이 존재하는 모든 실행에서 결정적으로 재현된다.
- 조치: 해당 테스트와 `FlywayMigrationContractTest`(파일 이름 정확 목록
  검증)를 V21을 반영하도록 갱신했다. `catalogMatchesApprovedManifest`는
  `retainAll` 기반 비교라 새 인덱스가 없어도 원래 통과했지만, 기존 관례(매
  마이그레이션의 새 인덱스를 `EXPECTED_INDEXES`에 추가)를 따라 함께 갱신했다.
- 남은 위험: 없음 — 수정 후 전체 재실행에서 521/521 통과.

## 6. Potential issues

### Application code

- **`Answer.restore(Instant at)`와 기존 `Answer.restore(Long id, ...)` 정적
  팩터리의 이름 충돌.** 이슈 본문과 승인된 테스트 계획이 명시적으로
  `restore(at)`라는 이름을 지정했고, 컴파일과 오버로드 해석에는 문제가
  없다(파라미터 타입이 완전히 다름) — 하지만 이 저장소는 `Report.restore(...)`,
  `ReportCase.restore(...)`처럼 "DB row 재구성" 의미로 `restore`를 이미 널리
  쓰고 있어서, `Answer`에서만 같은 이름이 "가시성 복원"이라는 다른 의미로
  쓰이는 것은 향후 리더를 헷갈리게 할 수 있다. 승인된 명세를 임의로 바꾸지
  않고 그대로 구현했지만, 리뷰어가 다른 이름(예: `unhide`)을 원하면 이후
  변경은 순수 rename이라 위험이 낮다.
- **`Answer.hide`/`revoke` 부수효과는 `SafetyCaseResolutionService.resolveCase`
  경유로만 배선돼 있다.** `Answer.hide(at)`를 리포지토리로 직접 저장해도
  알림 REVOKE는 일어나지 않는다 — 이는 최초 통합 테스트 작성 중 실제로
  발견한 문제였다(INT-008이 처음에는 `Answer.hide`를 직접 호출해 실패했다).
  이슈 본문의 "운영자 조치와 판정에 의한 숨김만 배선한다"는 제약과 일치하는
  의도된 설계이지만, 향후 다른 경로(예: 자동 임계값 숨김, R04)가 추가될 때
  이 REVOKE 부수효과를 다시 명시적으로 배선해야 한다는 점을 놓치기 쉽다.
- **`Notification.revoke()`는 인자를 받지 않는다.** 테스트 계획은
  `revoke(at)`를 예시로 들었지만, `notification` 테이블에는 "취소 시각"을
  저장할 별도 컬럼이 없다(도메인이 `readAt`을 재사용할 수도 있었지만 그러면
  "언제 읽었는지"라는 의미가 사라진다). 저장할 곳이 없는 시각 인자를 받는
  것은 죽은 매개변수라고 판단해 뺐다 — 향후 감사 추적이 필요해지면 새
  마이그레이션으로 `revoked_at` 컬럼을 추가하는 편이 인자만 조용히 버리는
  것보다 낫다.

### Infrastructure and resource limits

- 새 인덱스(`idx_report_reporter_answer_suppression`)는 `report` 테이블에
  대한 다섯 곳의 조회 경로 모두에 `NOT EXISTS` 서브쿼리를 추가한다. 데이터
  볼륨이 커지면 이 서브쿼리의 실행 계획을 EXPLAIN으로 별도 확인할 필요가
  있다 — 이번 테스트는 인덱스 "존재"만 확인했고(INT-018) 실행 계획까지는
  검증하지 않았다(테스트 계획 §4 Risk inventory에 P2로 이미 명시된 한계).

### Database and migrations

- `assert_answer_has_content` deferred constraint trigger는
  `DEFERRABLE INITIALLY DEFERRED`라 커밋 시점에 평가된다. `JpaAnswerRepository.save`가
  `saveAndFlush`를 쓰므로, 이 통합 테스트들은 `@Transactional` 롤백 없이(수동
  `DELETE`/`TRUNCATE` cleanup 방식) 각 `save` 호출이 곧바로 auto-commit되게
  작성했다 — 그래야 INT-010의 실패가 실제로 트리거를 통과해 재현된다. 만약
  이 스타일이 아니라 테스트 트랜잭션 롤백 컨벤션으로 바뀐다면 INT-010은
  거짓 양성(항상 통과)이 될 수 있다는 점을 남겨둔다.
- `V21` 마이그레이션은 기존 `uq_open_report_answer`(열린 상태에만 걸리는
  부분 유일 인덱스)와 별개로, 상태 무관 비유일 인덱스를 추가한다. 두
  인덱스가 겹치는 컬럼 조합(`reporter_id, answer_id`)을 갖지만 술어가 달라
  Postgres가 하나로 병합하지 않는다 — 저장 공간 소폭 증가는 있으나 기능
  결함은 아니다.

### Concurrency and idempotency

- INT-017이 `resolveCase`의 `findByIdForUpdate` 행 잠금을 실제 2-way 동시
  트랜잭션으로 검증했다 — 정확히 한 번만 성공하고 나머지는
  `REPORT_CASE_ALREADY_RESOLVED`로 실패하며, outbox event는 신고 수만큼만
  생긴다(중복 없음).
- INT-014가 fan-out worker 재실행 멱등성을 검증했다 — `saveIfAbsent`/
  `saveDeliveryIfAbsent`와 `notification`의 `UNIQUE(recipient_id, dedup_key)`,
  `notification_delivery`의 `UNIQUE(notification_id, push_device_id)`에
  의존한다.
- `SafetyRepository.acquireReporterSubmissionLock`(#154, PG advisory lock)은
  신고 **접수** 경로만 직렬화한다 — 사건 **종결** 경로(`resolveCase`)의
  동시성은 advisory lock이 아니라 `report_case` 행 잠금(`FOR UPDATE`)으로
  별도로 보장한다. 두 메커니즘이 서로 다른 자원을 잠그므로 접수와 종결이
  동시에 일어나는 조합(예: 종결 도중 같은 사건에 새 신고가 병합 시도)은
  이번 테스트 범위에 포함하지 않았다 — TASK.md 완료 조건에도 명시되지 않은
  조합이라 이슈 범위 밖으로 판단했다.

### Transactions and event ordering

- INT-016이 outbox 저장 실패 시 사건 상태·신고 상태·outbox 삽입이 모두
  같은 트랜잭션에서 롤백됨을 검증했다 — 부분 커밋(사건만 RESOLVED로 남고
  outbox 이벤트가 없는 상태)은 재현되지 않는다.
- 결과 알림 outbox 이벤트는 **신고 1건당 1개**이지 사건 1개당 1개가
  아니다(INV-RPT-008과 일치). fan-out worker가 처리 순서를 보장하지
  않으므로, 신고자 2명의 알림이 도착하는 순서는 정해져 있지 않다 — 제품
  요구사항에도 순서 보장이 없어 문제는 아니다.

### External APIs

- 해당 없음 — 이 이슈는 외부 API 연동이 없다. push provider로의 실제 전송은
  `notification_delivery` 행 생성까지만 다루고, provider 호출 자체는 기존
  delivery worker의 책임이라 이 테스트 범위 밖이다.

### Failure recovery and reconciliation

- `ReportResolutionFanOutWorker`는 `RecipientNotificationFanOutWorker`와
  같은 lease-fencing·재시도(`OutboxRetryPolicy`)·stale 처리 골격을 그대로
  재사용한다 — 그 골격 자체의 재시도 횟수·backoff 산식은 기존
  `RecipientNotificationFanOutWorkerTest`/`...IntegrationTest`가 이미
  검증했으므로 이 계획에서는 재검증하지 않았다(테스트 계획 §2 Excluded와
  일치).
- INT-016이 "사건 종결 트랜잭션 중 실패"의 유일한 재현 시나리오다 —
  fan-out worker 자체의 실패(예: `notification` 저장 실패)로 인한 outbox
  이벤트 재시도 경로는 `RecipientNotificationFanOutWorkerTest`의 동등한
  경로 검증을 그대로 신뢰했고 별도로 재현하지 않았다.

## 7. Regression and residual risk

- `Notification` 도메인 레코드에 `reportId` 필드를 추가하면서 기존
  생성자 호출부 10곳(운영 코드 2곳, 테스트 8곳)을 함께 수정했다. 전체
  단위(672)·통합(521) 테스트가 모두 통과해 회귀는 없다고 확인했다.
- `FlywayMigrationContractTest`·`FlywayMigrationIntegrationTest`의 마이그레이션
  개수·이름 하드코딩을 V21에 맞게 갱신했다 — 다음 마이그레이션(V22)을 추가할
  실행 에이전트도 이 두 파일을 함께 갱신해야 한다는 점을 남겨둔다(이 이슈
  고유의 위험이 아니라 이 저장소의 기존 관례).
- 남은 위험: 인덱스 실행 계획 미검증(§6 Infrastructure), 접수·종결 동시
  조합 미검증(§6 Concurrency) — 둘 다 이번 이슈 완료 조건 밖이라 후속 이슈
  없이 기록만 남긴다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-155-TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS.md`
- CI run: 로컬 실행(`./gradlew test integrationTest`), CI 링크 없음
- Related ADR: 없음
- PR: 아직 생성 전

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(UNIT-009는 대체 커버리지로 명시, §1·§4)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 — 인덱스 실행 계획·접수/종결
      동시 조합은 이슈 완료 조건 밖이라 후속 이슈를 새로 열지 않고 이
      보고서에만 기록했다(§7)
- [x] 실행 결과와 PR 설명이 일치함(PR 생성 시 이 보고서를 링크한다)
