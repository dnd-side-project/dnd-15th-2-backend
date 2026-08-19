# GitHub Issue #155 Task Contract

> Generated at: `2026-08-19T00:00:00+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `신고 시스템 — 집계 제외와 처리 결과 알림 (R02)`
- GitHub Issue: `#155`
- Branch: `feat/gh-155-report-suppression-notifications`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS`
  (`docs/test-plans/gh-155-TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS.md`)
- Test plan approval: `APPROVED` — 사용자가 2026-08-19 "테스트 계획서 진행"으로 승인했다.

## Objective

신고된 콘텐츠를 목록과 모든 카운트에서 동시에 빼고(집계 제외 2계층), 사건이
종결되면 신고자에게 결과를 비동기로 알린다. `NotificationType.REPORT_RESOLVED`
· `OutboxEventType.REPORT_RESOLVED` · `OutboxAggregateType.REPORT`는 enum과
DB CHECK에 모두 있으나(코드베이스 확인 완료) 생산자·소비자가 없다.

## Scope decision (사용자 승인 완료)

`ReportCase`를 실제로 `RESOLVED`로 전이시키는 "판정 트랜잭션"은 지금 저장소에
없다 — `ReportCase.resolve()` 도메인 메서드는 단위 테스트에서만 호출되고,
서비스·리포지토리·REST 어느 계층에도 호출부가 없다. 운영자 판정 API는 #156
(아직 `OPEN`) 몫으로 #155가 명시적으로 제외한 항목이다.

사용자에게 확인한 결과: **#155는 내부 전용 서비스 메서드만 추가한다.** REST
엔드포인트는 만들지 않는다. `SafetyReportService`(또는 신규
`SafetyCaseResolutionService`)에 `resolveCase(...)` 같은 내부 메서드를 두고,
그 안에서 사건 종결 + 전역 숨김 부수효과(알림 REVOKED) + outbox 이벤트 발행을
같은 트랜잭션에 배선한다. #156은 이후 이 메서드를 운영자 API에서 호출한다.
통합 테스트는 이 서비스 메서드를 직접 호출해 검증한다.

## Scope

### 1. 신고자 한정 숨김 (즉시 적용, 종결 결과와 무관하게 유지)

- `ContentSuppressionSql`(신규 클래스, `safety` 또는 `feed` SQL 패키지) —
  뷰어가 본인이 신고한 콘텐츠를 다시 보지 않게 하는 `NOT EXISTS (SELECT 1
  FROM report r WHERE r.reporter_id = <viewer> AND r.answer_id = a.id)` 조각.
  **코드베이스에 이런 클래스·상수가 아직 없음을 확인했다 — 신규 작성.**
- 다음 5곳에 적용한다(모두 확인 완료, 현재 `a.status = 'PUBLISHED'`만 본다):
  1. `PostAnswerQuerySql.SELECT_ANSWERS` (답변 목록, viewer = `:viewerId`)
  2. `InboxQuerySql.SELECT_CARD`의 `answer_count` 서브쿼리 (viewer =
     `:recipientId`)
  3. `InboxQuerySql.SELECT_CARD`의 `unread_answer_count` 서브쿼리 (viewer =
     `:recipientId`)
  4. `SentPostQuerySql.SELECT_CARD`의 `answer_count` 서브쿼리 (viewer =
     `dp.sender_id` 컬럼 참조 — bind 파라미터 아님)
  5. `SentPostQuerySql.SELECT_CARD`의 `unread_answer_count` 서브쿼리 (viewer =
     `dp.sender_id`)
  viewer 표현식이 위치마다 다르므로(bind 파라미터 vs 컬럼 참조)
  `ContentSuppressionSql`은 viewer SQL 조각을 인자로 받는 형태로 설계한다.
- `report (reporter_id, answer_id) WHERE answer_id IS NOT NULL` 부분 인덱스
  신규 추가. **기존 인덱스 없음을 마이그레이션에서 확인.**

### 2. 전역 숨김

- `Answer.hide(at)` / `Answer.restore(at)` 전이 신규 추가.
  `AnswerStatus.HIDDEN`은 enum과 `ck_answer_status` DB CHECK에 이미 있다(확인
  완료). 도메인에 전이 메서드가 없을 뿐이다.
- 위 5곳은 `a.status = 'PUBLISHED'` 조건을 이미 갖고 있으므로 상태를
  `HIDDEN`으로 바꾸는 것만으로 자동으로 빠진다 — **이 5곳 자체는 코드 변경이
  필요 없다.** 신고자 한정 숨김(§1)만 새 필터가 필요하다.
- 위험: `assert_answer_has_content` DB 함수(`V1` 마이그레이션, deferred
  constraint trigger)는 `status = 'PUBLISHED'`이고 `body_text`가 비어 있고
  `READY` 상태 미디어가 하나도 없으면 예외를 던진다. `HIDDEN` 동안 미디어가
  정리되어(`media_asset.status`가 더 이상 `READY`가 아니게 되어) 텍스트 없는
  답변을 `restore(at)`로 다시 `PUBLISHED`로 되돌리면 이 트리거가 막는다.
  **이 실패 경로는 고치지 않고 테스트로 문서화한다**(issue 완료 조건 그대로).
- `AnswerRepository`는 JPA 구현(`JpaAnswerRepository`)이고 `save(Answer)`가
  기존 id로 upsert하므로, `answerRepository.save(answer.hide(at))` 형태로
  기존 `save`를 그대로 재사용한다 — 신규 리포지토리 메서드는 필요 없다(확인
  완료).
- 전역 숨김 시 그 답변을 가리키던 기존 `notification` 행을
  `NotificationStatus.REVOKED`로 전이. `NotificationRepository`에 해당
  리포지토리 메서드가 없음을 확인 — 신규 추가(예: `revokeByAnswerId(long
  answerId, Instant at)`).

### 3. 결과 알림 (outbox fan-out)

- §"Scope decision"의 내부 전용 서비스 메서드 안에서, `ReportCase`가
  `RESOLVED`로 전이하는 같은 트랜잭션에 배선한다.
- `SafetyRepository`에 `findReportsByCaseId(long caseId)` 신규 추가 — 사건에
  묶인 신고 전체를 찾아 **신고 1건당 outbox 이벤트 1개**를 발행한다(사건
  1개가 아니라 신고자 수만큼). 현재 이런 조회 메서드가 없음을 확인했다.
- `dedup_key = "report-resolved:" + reportId`, payload에 대상·작성자
  식별자를 넣지 않는다(`INV-RPT-005`와 같은 원칙 — #154 참고).
- `ReportResolutionFanOutWorker`(신규) — `RecipientNotificationFanOutWorker`
  (`notification/fanout/RecipientNotificationFanOutWorker.java`, 확인 완료)
  와 같은 claim(`outboxEventRepository.claimDue`)·트랜잭션·
  lease-fencing(`hasLeaseIdentity`)·재시도(`OutboxRetryPolicy`)·stale 처리
  골격을 따른다.
- **선호 설정 예외**: `RecipientNotificationFanOutWorker`는 `isPreferenceEnabled`
  가 알림 생성 자체를 게이트하지만, `ReportResolutionFanOutWorker`는 다르게
  동작해야 한다 — 인앱 `notification` 행은 항상
  `notificationRepository.saveIfAbsent(...)`로 생성하고, `isPreferenceEnabled`
  는 `notification_delivery`(push) 행 생성 여부만 게이트한다. 이 편차를
  구현에서 반드시 지킨다.
- worker를 두 번 실행해도 알림이 중복되지 않아야 한다 — `notification`의
  `UNIQUE (recipient_id, dedup_key)`와 `notification_delivery`의
  `UNIQUE (notification_id, push_device_id)`를 그대로 활용
  (`saveIfAbsent`/`saveDeliveryIfAbsent` 패턴, 확인 완료).

## Explicit exclusions

- 자동 전역 숨김의 임계값 결정(R04). 이 이슈는 운영자 조치와 판정에 의한
  숨김만 배선한다 — 임계값 기반 자동 숨김 트리거는 만들지 않는다.
- 심각도 산출, 대기열 라우팅, 운영자 판정 REST API(#156). 내부 서비스
  메서드까지만 만들고 그 메서드를 호출하는 API·인증·권한 로직은 만들지
  않는다.
- Slack 보조 알림(#111 범위).
- API 변경 없음 — issue 본문의 "백엔드 영향" 절대로 REST 계층은 손대지
  않는다(기존 조회 결과만 달라진다).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `ContentSuppressionSql`, 5개 조회 지점 반영, `Answer.hide/restore`, 리포지토리 확장, `NotificationRepository` REVOKE 확장, 내부 사건 종결 서비스 메서드, `ReportResolutionFanOutWorker`, 단위·통합 테스트 | Feature executor | `INV-RPT-006`·`008` 검증, `RecipientNotificationFanOutWorker` 골격과의 일관성 리뷰, `assert_answer_has_content` 실패 경로 테스트 리뷰, #154 `ReportCase`/`Report.attachToCase` 계약과의 호환성 리뷰 |

## Existing user-owned changes

- `git status --short` 결과: `docs/reports/harness/` (2026-08-18 작성된
  하네스 엔지니어링 감사 보고서, untracked) — 이번 작업과 무관한 이전 세션
  산출물이라 그대로 보존했다(worktree 정리를 위해 잠시 stash했다가 새
  branch로 전환 직후 복원, 삭제하지 않음).
- `main`(`#154` 병합 이후, `origin/main` 최신 커밋 `5309cc3a`)에서 새로
  분기했다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.safety.*" --console=plain
./gradlew test --tests "com.dnd.qello.notification.*" --console=plain
./gradlew test --tests "com.dnd.qello.answer.*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*Report*" --console=plain
./gradlew integrationTest --tests "com.dnd.qello.*Suppress*" --console=plain
./harness test-run --id <TEST-PLAN-ID>
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 답변 목록 길이와 `answer_count`가 항상 일치한다 — 신고자 한정 숨김과
      전역 숨김 각각에서(`INV-RPT-006`) — `ReportSuppressionIntegrationTest`
      INT-001/002, `AnswerGlobalHideIntegrationTest` INT-006.
- [x] `unread_answer_count`도 같은 규칙으로 감소한다 —
      `ReportSuppressionIntegrationTest` INT-003/004,
      `AnswerGlobalHideIntegrationTest` INT-007. (`InboxQuerySql.SELECT_CHIP_AGGREGATE`
      수신함 방향 칩 집계는 방향별 질문글 수를 세지 개별 답변을 세지 않으므로
      신고·숨김된 답변 하나 때문에 그 질문글 전체가 칩 집계에서 빠질 이유가
      없다 — 이 집계는 이번 억제 규칙의 대상이 아님을 코드로 확인했다.)
- [x] 전역 숨김된 답변을 가리키던 기존 알림이 `REVOKED`다 —
      `AnswerGlobalHideIntegrationTest#globalHideRevokesExistingNotification`
      (INT-008).
- [x] 사건 종결 시 신고자 수만큼 outbox 이벤트가 생기고 fan-out 후 알림이
      신고자당 1건이다(`INV-RPT-008`) —
      `ReportResolutionIntegrationTest` INT-011, INT-013.
- [x] worker를 두 번 실행해도 알림이 중복되지 않는다 —
      `ReportResolutionIntegrationTest#fanOutIsIdempotentAcrossReruns`
      (INT-014).
- [x] 푸시 선호가 꺼져 있어도 인앱 알림 행은 생성된다 —
      `ReportResolutionIntegrationTest#inAppNotificationIsCreatedEvenWhenPushPreferenceDisabled`
      (INT-015).
- [x] 숨김 기간에 미디어가 정리된 답변의 복원이 `assert_answer_has_content`로
      실패하는 경로가 테스트로 문서화돼 있다 —
      `AnswerGlobalHideIntegrationTest#restoringContentlessAnswerAfterMediaCleanupFails`
      (INT-010).
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 상세는
      `docs/reports/tests/gh-155-TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS.md`
      §1·§6·§7 참고. 인덱스 실행 계획 미검증과 접수·종결 동시 조합 미검증은
      이 이슈 완료 조건 밖으로 판단해 후속 이슈 없이 기록만 남겼다.

## Implementation notes (2026-08-19)

- 사건 종결은 REST API 없이 내부 전용 메서드
  (`SafetyCaseResolutionService.resolveCase`)로만 노출했다 — 사용자 승인
  받은 Scope decision과 일치. `#156`이 이후 운영자 API에서 이 메서드를
  호출한다.
- `Notification` 도메인 레코드에 `reportId` 필드를 추가하면서(V19가 이미
  추가한 `notification.report_id` 컬럼을 처음으로 실제 사용) 기존 생성자
  호출부 10곳을 함께 갱신했다. 전체 단위(672)·통합(521) 테스트 모두 통과.
- `V21__add_report_reporter_answer_suppression_index.sql` 추가로
  `FlywayMigrationContractTest`·`FlywayMigrationIntegrationTest`의 마이그레이션
  개수·이름 하드코딩이 깨져 함께 갱신했다(이 저장소의 기존 관례 — 다음
  마이그레이션 작업자도 유의).
- 전역 숨김(`Answer.hide`)과 알림 REVOKE 부수효과는 `resolveCase`(판정
  ACTIONED)를 통해서만 배선했다 — 이슈 본문의 "운영자 조치와 판정에 의한
  숨김만 배선한다"는 제약과 일치. `Answer.hide`를 다른 경로에서 직접
  호출해도 알림은 REVOKE되지 않는다 — 통합 테스트 작성 중 이 커플링을
  실제로 발견하고 테스트를 재설계했다(보고서 §6 참고).
- 새 `SafetyErrorCode.PAYLOAD_SERIALIZATION_FAILED`(`SAF-INFRA-003`) 1개를
  추가했다 — outbox payload 직렬화 실패를 기존 코드로 표현할 수 없었다.
