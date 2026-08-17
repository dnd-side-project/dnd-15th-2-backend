# Test Report: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API

> Created at: `2026-08-17T16:25:34+09:00`
> GitHub Issue: `#125`
> Branch: `feat/gh-125-direction-answer-api`
> Commit: `uncommitted`

## 1. Executive summary

- Result: `PASS` (최초 실행은 `PARTIAL` — 아래 결함 발견·수정 경위 참고)
- Tested scope: 답변 제출(`AnswerSubmissionService`/`AnswerSubmissionApplicationService`/Controller), 멱등
  재생·재사용 거절, 수신 자격(소유권·ACTIVE post·양방향 차단·만료·상태) 검증, moderation job
  intake 원자적 저장(`AnswerModerationJobIntakeService`, `deadlineWindow=PT5M`로 활성화됨),
  `AnswerModerationVerdictWorker`의 ALLOW/BLOCK/DEADLINE_ELAPSED 처리, `AnswerNotificationService`의
  publish/reject 멱등성과 슬롯 해제, PostgreSQL/PostGIS 기반 동시성(같은 키 재생, 다른 키 경쟁,
  publish-vs-block, submit-vs-block), Outbox lease 동시 claim, 강제 rollback, 만료 sweep의 검사 중
  답변 보존, privacy(응답·Outbox payload 비노출).
- Unverified scope: `AnswerFormat`별 TEXT/PHOTO/BOTH 조합(명시적 미구현, 아래 6절). `filter_job`
  실행(pipeline 실제 호출로 verdict를 만들어내는 `AnswerModerationExecutionWorker`)은 여전히 Spring
  bean이 아니므로 e2e 자동 판정 경로는 검증 범위 밖이다 — 이 계획은 verdict/deadline outbox row를
  직접 시딩해 `AnswerModerationVerdictWorker`만 검증한다(계획 7절 "External APIs"가 이미 이 접근을
  전제한다). FCM/APNs 실제 전달, 인프라 apply는 범위 밖(TASK.md 명시 제외).
- Release recommendation: 실행 중 `AnswerModerationVerdictWorker`가 이벤트별 예외를 격리하지 않는
  실제 결함을 발견했다(INT-021/INT-012에서 재현). 사용자 승인(2026-08-17, 현재 Claude Code 대화)에
  따라 `AnswerModerationDeadlineWorker.processOne`과 동일한 이벤트별 try/catch 패턴으로 즉시 수정했고,
  INT-012/INT-021 두 시나리오의 assertion을 수정된 동작(예외 없이 `FAILED` outcome 반환, 같은 batch
  안에서 뒤 이벤트 즉시 처리)에 맞게 다시 작성했다. 수정 후 전체 회귀 스위트(unit, integration)를
  재실행해 실패 0건을 확인했다 — 아래 3·4·6절은 수정 전/후 상태를 모두 기록한다. 최종 판정은 `PASS`다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain 21 (OpenJDK, `JavaLanguageVersion.of(21)`) |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL/PostGIS 16-3.5(Testcontainers, `postgis/postgis:16-3.5-alpine`), local Docker Desktop |
| Test runner | JUnit 5, AssertJ, Mockito |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests 'com.dnd.qello.answer.*'` | PASS | 43 | ~2s | `build/test-results/test/TEST-com.dnd.qello.answer.*.xml` |
| `./gradlew integrationTest --tests 'com.dnd.qello.AnswerSubmissionApiIntegrationTest' --tests 'com.dnd.qello.AnswerSubmissionConcurrencyIntegrationTest' --tests 'com.dnd.qello.AnswerModerationPublicationIntegrationTest'` | PASS | 21 | ~25s | `build/test-results/integrationTest/TEST-com.dnd.qello.Answer*.xml` |
| `./gradlew test`(전체) | PASS | 536 | ~10s | `build/test-results/test/*.xml` |
| `./gradlew integrationTest`(전체) | PASS | 405 | ~3m 13s | `build/test-results/integrationTest/*.xml` |
| `./harness test-run --id TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API` | PASS | 위 전체 unit+integration 재실행 | ~3m 20s | 본 문서 자동 생성(scaffold) |
| `./harness check` | PASS | - | - | secret/JUnit/convention/commit-msg/workflow/label/husky 검증 전체 통과 |
| `./harness pr-ready --project-tests` | BLOCKED | - | - | `origin/main`이 분기점(`56e5135`) 이후 10개 커밋 앞서 있어 `./harness sync`(rebase) 선행 필요 — 아래 4절 참고 |
| `npm run hooks:validate` | PASS | - | - | Husky validation passed |
| `git diff --check` | PASS | - | - | whitespace/conflict marker 없음 |

**배치 격리 결함 수정 후 재실행**: `AnswerModerationVerdictWorker.processClaimed`를 이벤트별
try/catch(`RuntimeException` → `FAILED` outcome)로 고친 뒤 `./gradlew test`(전체 536건)와
`./gradlew integrationTest`(전체 405건)를 다시 실행했다. 두 스위트 모두 실패·오류 0건이다(기존
스위트 포함, 회귀 없음). INT-012/INT-021은 수정된 동작에 맞게 assertion을 다시 작성한 뒤 재통과를
확인했다 — 4절 참고.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `AnswerSubmissionServiceTest`(null/recipientId/submittedAt), `AnswerSubmissionApplicationServiceTest`(idempotency key 형식) | 계획이 한 시나리오에 묶은 검증을 코드 위치별로 두 클래스에 나눠 구현 |
| UNIT-002 | PASS | `AnswerSubmissionServiceTest` | 공백/정상 Unicode/미디어 0~2개 |
| UNIT-003 | PASS | `AnswerSubmissionApplicationServiceTest` | ACTIVE/BLOCKED/DELETED/OPERATOR |
| UNIT-004 | PASS | `AnswerSubmissionServiceTest.acceptsOpenRecipientStatuses` | AVAILABLE/DISCOVERED/OPENED 파라미터화 |
| UNIT-005 | PASS | `AnswerSubmissionServiceTest.rejectsTerminalOrPendingStatuses` 외 1건 | SKIP_PENDING/SKIPPED/EXPIRED/BLOCKED/ANSWERED + 타인/부재 |
| UNIT-006 | PASS | `AnswerSubmissionServiceTest.derivesSnapshotFieldsFromLockedRecipientOnly` | region/bearing/distance/author 출처 검증 |
| UNIT-007 | PASS | `AnswerSubmissionServiceTest.replaysIdenticalRequestWithoutSideEffects` | attach/intake/save 미호출 확인 |
| UNIT-008 | PASS | `AnswerSubmissionServiceTest`(recipient/body/media 각 다름 3건) | |
| UNIT-009 | PASS | `AnswerSubmissionServiceTest.propagatesRaceOnDuplicateActiveAnswerAsDataIntegrityViolation` | 실제 코드 매핑은 INT-006에서 재확인 |
| UNIT-010 | PASS(기존 재사용) | `AnswerModerationJobIntakeServiceTest.emitsHistoryAndExecutionRequestedEvent`(GH-107에서 이미 작성) | 새 테스트 미작성, 기존 커버리지로 충분하다고 판단 |
| UNIT-011 | PASS | `AnswerModerationVerdictWorkerTest.appliesAllowVerdictByPublishingOnly` | |
| UNIT-012 | PASS | `AnswerModerationVerdictWorkerTest.appliesBlockVerdictByRejectingOnly` | |
| UNIT-013 | PASS | `AnswerModerationVerdictWorkerTest.treatsDeadlineElapsedAsFailClosedAndStillAppliesLateAllow` | |
| UNIT-014 | PASS(부분) | `AnswerModerationVerdictWorkerTest.delegatesTerminalIdempotencyToNotificationService` | targetVersion 불일치 케이스는 대상이 없음 — 아래 5절 참고 |
| UNIT-015 | PASS | `AnswerNotificationServiceTest`(4건) | 이중 publish, 슬롯 미확보 시 거절 포함 |
| UNIT-016 | PASS | `AnswerSubmissionApiMockMvcTest.submitReturnsAcceptedWithCommonWrapper` | |
| UNIT-017 | PASS | `AnswerSubmissionApiMockMvcTest`(6건: 인증/헤더/본문/미디어/path) | |
| UNIT-018 | PASS | `AnswerSubmissionApiMockMvcTest`(3건: 404/409×2) | |
| UNIT-019 | PASS | `AnswerSubmissionWebContractTest`(3건) | |
| INT-001 | PASS | `AnswerSubmissionApiIntegrationTest.submitsAnswerAndCreatesModerationJobAtomically` | |
| INT-002 | PASS | `AnswerSubmissionApiIntegrationTest.rollsBackWholeSubmissionWhenModerationIntakeFails` | 강제 실패 지점은 NO_ACTIVE_RELEASE(실제 정책 실패) 사용 — 아래 5절 |
| INT-003 | PASS | `AnswerSubmissionApiIntegrationTest.replaysIdenticalPayloadWithoutSideEffects` | |
| INT-004 | PASS | `AnswerSubmissionApiIntegrationTest.rejectsReplayWithDifferentPayload` | |
| INT-005 | PASS | `AnswerSubmissionConcurrencyIntegrationTest.concurrentIdenticalSubmissionsConvergeOnSingleAnswer` | 3회 반복 실행, flaky 없음 |
| INT-006 | PASS | `AnswerSubmissionConcurrencyIntegrationTest.concurrentDifferentSubmissionsToSameRecipientLeaveOnlyOneAnswer` | 실제 `GlobalExceptionHandler`로 원문 미노출까지 확인 |
| INT-007 | PASS | `AnswerSubmissionApiIntegrationTest.rejectsIneligibleRecipientsUniformlyWithoutSideEffects` | |
| INT-008 | PASS | `AnswerSubmissionApiIntegrationTest.enforcesExpiryBoundaryUsingServerClockOnly` | |
| INT-009 | PASS | `AnswerSubmissionApiIntegrationTest.preservesPendingAnswerEligibilityAcrossExpirySweep` | `FIND_EXPIRABLE` 수정의 회귀 증거 |
| INT-010 | PASS | `AnswerModerationPublicationIntegrationTest.allowVerdictPublishesAnswerAndReleasesSlotAtomically` | INT-011과 통합 구현 |
| INT-011 | PASS | 〃 | |
| INT-012 | PASS | `AnswerModerationPublicationIntegrationTest.rollsBackPublishTransactionWhenPublishedOutboxSaveFails` | 결함 수정 후 재작성: `processBatch()`는 예외 없이 반환하고 outcome이 `FAILED`다. rollback(SUBMITTED/AVAILABLE/count=1/outbox 0건)은 그대로 유지 — 5/6절 |
| INT-013 | PASS | `AnswerModerationPublicationIntegrationTest.concurrentWorkersClaimVerdictOnlyOnce` | 3회 반복 실행, flaky 없음 |
| INT-014 | PASS | `AnswerModerationPublicationIntegrationTest.replayingProcessedEventDoesNotDuplicateEffects` | |
| INT-015 | PASS | `AnswerSubmissionConcurrencyIntegrationTest.concurrentPublishAndBlockLeaveExactlyOneWinner` | 3회 반복 실행, flaky 없음 |
| INT-016 | PASS(재정의) | `AnswerSubmissionConcurrencyIntegrationTest.concurrentSubmitAndBlockKeepEligibilityConsistentWithLockedState` | 원 시나리오 문구("정확히 하나만 성립")가 실제 설계와 달라 재정의 — 아래 5절 |
| INT-017 | PASS | `AnswerModerationPublicationIntegrationTest.blockVerdictRejectsAnswerWithoutTouchingSlot` | |
| INT-018 | PASS | `AnswerModerationPublicationIntegrationTest.deadlineElapsedAloneDoesNotPublishButLateAllowStillDoes` | |
| INT-019 | PASS | `AnswerSubmissionApiIntegrationTest.attachesOnlyOwnedReadyMediaAndRejectsForeignOrUnsafeMedia` | 오류 코드는 계획의 추정과 달리 MEDIA_NOT_FOUND — 5절 참고 |
| INT-020 | PASS | `AnswerSubmissionApiIntegrationTest.publishedAnswerRemainsVisibleInAnsweredInboxCategory` | |
| INT-021 | PASS | `AnswerModerationPublicationIntegrationTest.batchIsolatesFailingEventFromLaterEventsInTheSameClaim` | 결함 수정 후 재작성: 원래 목표(즉시 격리)대로 첫 호출에서 바로 B가 PUBLISHED됨을 확인. 실패한 A는 완료 처리 자체가 rollback돼 lease가 그대로 남고, 만료 후 재시도로 A도 공개됨을 이어서 확인 — 5/6절 |
| INT-022 | PASS | `AnswerSubmissionApiIntegrationTest.publicResponseAndPublishedOutboxExcludeSensitiveFields` | |

## 5. Failures and diagnostics

이번 실행에서 최종 테스트 스위트는 전부 PASS다(코드를 고치지 않고, 계획 대비 시나리오 재해석이나
범위 조정으로 해결한 항목만 아래에 기록한다). 개발 중 발견해 수정한 테스트 자체 결함(모두 production
코드가 아니라 이 테스트 코드의 오류였다):

- `AnswerSubmissionApplicationServiceTest`: `AccountStatus.DELETED` 픽스처에 `deletedAt`을 채우지
  않아 `Account.restore()`의 도메인 불변식 위반으로 실패 — 테스트 픽스처 수정으로 해결.
- `AnswerSubmissionApiIntegrationTest.attachesOnlyOwnedReadyMediaAndRejectsForeignOrUnsafeMedia`:
  타인 소유 미디어 첨부 시 기대 오류 코드를 `MEDIA_OWNER_MISMATCH`로 잘못 가정했다. 실제 코드
  (`MediaAttachmentService.attach()`)는 `findByIdAndOwnerId`로 미디어와 소유권을 함께 조회하므로
  타인 미디어는 존재 자체를 노출하지 않는 `MEDIA_NOT_FOUND`로 거절한다 —
  `MEDIA_OWNER_MISMATCH`는 반대 방향(본인 미디어를 남의 콘텐츠에 붙이려는 시도) 전용이다.
  테스트 기대값을 실제 계약에 맞게 수정했다.
- `AnswerModerationPublicationIntegrationTest`(INT-012, INT-018): deadline elapsed 처리와
  outbox 저장 실패 시 Answer 상태를 `SAFETY_CHECKING`으로 잘못 가정했다. 실제로는
  `startSafetyCheck()`가 `publish()`/`reject()` 호출 안에서만 일어나고 그 트랜잭션이 rollback되면
  DB에는 전혀 반영되지 않으므로 원래 상태(`SUBMITTED`)로 남는다 — 기대값을 수정했다.
- INT-021: 초기 설계는 재시도 시 같은 시각(`NOW+30s`)으로 다시 claim을 시도했는데, 첫 시도에서
  이미 두 이벤트 모두 `worker-1`에게 60초 lease로 선점된 상태였다. 두 번째 시도는 lease가 실제로
  만료되는 시점(`NOW+120s`) 이후로 조정해 진짜 reclaim 경로를 태우도록 수정했다.

## 6. Potential issues

### Application code

- **[발견, 수정 완료] `AnswerModerationVerdictWorker.processClaimed`가 이벤트별 예외를 격리하지 않았다.**
  `AnswerModerationDeadlineWorker.processOne`과 `AnswerModerationExecutionWorker`(기존 #107/#108
  코드)는 한 이벤트의 처리 실패(제약 위반이 아닌 임의 `RuntimeException` 포함)를 catch해서 나머지
  batch 처리를 막지 않도록 명시적으로 격리한다(`FAILED`/`RETRY_*` outcome으로 계속 진행). 반면
  최초 구현의 `AnswerModerationVerdictWorker.processVerdictReady`/`processClaimed`는
  `StaleLeaseException`만 잡고 그 외 예외(예: outbox 완료 처리 자체가 실패하는 경우)는 그대로
  전파시켜 `processBatch()` 전체가 예외로 끝났다.
  - 재현: `AnswerModerationPublicationIntegrationTest.rollsBackPublishTransactionWhenPublishedOutboxSaveFails`(INT-012)와
    `.batchIsolatesFailingEventFromLaterEventsInTheSameClaim`(INT-021)이 실제 PostgreSQL로 재현했다.
  - 영향 범위: TASK.md 완료 기준의 "제출 transaction은 답변과 moderation 작업을 모두 commit하거나
    모두 rollback"은 결함 상태에서도 지켜졌다(트랜잭션 원자성 자체는 정상). 영향받은 것은 "한 이벤트
    실패가 나머지 batch 처리를 막지 않는다"는 계획 7절의 명시적 요구뿐이었다 — 데이터 유실은 없지만
    지연이 생기는 문제였다.
  - **수정(사용자 승인, 2026-08-17, 현재 Claude Code 대화)**:
    `src/main/java/com/dnd/qello/filtering/moderation/AnswerModerationVerdictWorker.java`의
    `processClaimed`를 `AnswerModerationDeadlineWorker.processOne`과 동일하게 이벤트별
    try/catch(`StaleLeaseException` → `STALE_LEASE`, 그 외 `RuntimeException` → 신설한
    `Outcome.FAILED`)로 감쌌다. `processVerdictReady`/`processDeadlineElapsed`/`finishSkipped`
    내부의 중복 `StaleLeaseException` catch는 제거해 이제 `processClaimed` 한 곳에서만 예외를
    처리한다.
  - INT-012/INT-021의 assertion을 수정된 동작(예외 없이 `FAILED` outcome 반환, 같은 batch 안에서
    뒤 이벤트가 즉시 처리됨)에 맞게 다시 작성했다. 수정 후 전체 unit(536건)·integration(405건)
    재실행 결과 실패 0건.

### Infrastructure and resource limits

- Testcontainers PostgreSQL/PostGIS 컨테이너 기동 비용으로 통합 테스트 전체가 약 3분 13초 걸린다.
  로컬 Docker Desktop 리소스에 의존하므로 CI 환경의 컨테이너 가용 자원에 따라 달라질 수 있다.

### Database and migrations

- 신규 Flyway migration은 만들지 않았다(TASK.md 승인 범위). `uq_answer_one_per_recipient`,
  `uq_answer_idempotency`, `ct_post_recipient_capacity_release` 기존 제약만으로 요구된 불변식을
  구현할 수 있음을 INT-005/006/010/011에서 실제 PostgreSQL로 확인했다.
- `PostRecipientSql.FIND_EXPIRABLE`에 검사 중(SUBMITTED/SAFETY_CHECKING) 답변이 있는 recipient를
  제외하는 조건을 추가했다(제출 프로덕션 코드 구현 단계에서 발견해 수정한 기존 버그) — INT-009가
  이 수정의 회귀 가드다.

### Concurrency and idempotency

- INT-005/006/013/015 동시성 테스트를 각 3회 이상 반복 실행해 재현성을 확인했다(flaky 없음).
- INT-016은 원래 계획 문구("정확히 하나만 성립")가 실제 구현과 맞지 않아 재정의했다 — `submit()`은
  `post_recipient.status`를 직접 쓰지 않으므로(그 전이는 이후 `publish()`의 몫) 동시 `block()`과
  진짜 상호 배타적 경쟁을 벌이지 않는다. 실제 불변식은 "제출 성패가 잠근 시점 행 상태와 항상
  일치하고, block()은 항상 최종적으로 성공한다"이며 이를 검증하도록 테스트를 다시 설계했다. 이
  차이 자체는 설계 결함이 아니다 — 제출 이후의 차단은 이미 `#93`의 `releaseSlot()` fail-closed
  검사가 이후 publish 단계에서 막도록 설계돼 있다(INT-015가 그 경로를 검증한다).

### Transactions and event ordering

- `AnswerSubmissionService.submitInTransaction`과 `AnswerModerationJobIntakeService.submit`이
  같은 물리 transaction에 참여함(REQUIRED 전파)을 INT-001/002로 확인했다 — intake 실패(승격된
  release 없음)가 answer 저장까지 통째로 rollback시킨다.
- `AnswerModerationVerdictWorker.processVerdictReady`도 `applyAllow`/`reject`와
  `completeClaimOrThrow`가 한 transaction에 묶여 있음을 INT-012로 확인했다 — 다만 이 결합이 위
  "Application code" 절의 batch 격리 결함과 상호작용한다는 점이 이번에 새로 드러났다.

### External APIs

- 답변 제출 요청 thread에서 moderation provider를 호출하지 않음을 INT-001에서 확인했다
  (`MODERATION_EXECUTION_REQUESTED` outbox row 생성까지만 같은 transaction, 실제 pipeline 호출은
  없음).
- `AnswerModerationExecutionWorker`(pipeline 실제 호출)는 여전히 Spring bean이 아니므로(기존 설계
  결정, 이 이슈 범위 밖) 이번 테스트는 verdict/deadline outbox row를 직접 시딩해 검증했다 — 계획
  7절이 이미 이 접근을 요구한다.

### Failure recovery and reconciliation

- 제출 transaction 강제 실패(INT-002) → 전체 rollback 확인.
- 공개 transaction 강제 실패(INT-012) → 전체 rollback 확인. 최초 실행에서는 그 실패가
  `processBatch()` 자체를 예외로 종료시켜 같은 batch의 다른 이벤트 처리를 지연시키는 결함이
  함께 드러났으나(위 "Application code" 참고), 수정 후에는 예외 없이 `FAILED` outcome만 반환한다.
- Outbox lease 동시 claim(INT-013)에서 한 worker만 처리하고 슬롯·Outbox가 정확히 1회만 변경됨을
  확인했다.
- 배치 격리 결함 수정 후 INT-021에서 실패한 이벤트 뒤의 다른 이벤트가 같은 호출 안에서 즉시
  처리됨을 확인했다. 실패한 이벤트 자신은 완료 처리가 rollback되어 lease를 그대로 들고 있으므로,
  lease 만료 후 재수집으로 데이터 유실이나 중복 공개 없이 정확히 한 번 공개됨을 이어서 확인했다.

## 7. Regression and residual risk

- 전체 회귀 스위트(배치 격리 결함 수정 후 unit 536건, integration 405건) 통과 — 기존 기능 회귀 없음.
- 위 6절의 `AnswerModerationVerdictWorker` batch 격리 결함은 발견 즉시 수정하고 회귀 테스트까지
  통과시켰다 — 더 이상 잔여 위험이 아니다.
- `AnswerModerationExecutionWorker`가 여전히 미배선 상태라, 실제 운영에서 moderation 판정이
  자동으로 만들어지지 않는다(이 이슈 범위 밖으로 이미 합의됨) — verdict를 만드는 별도 배선 작업이
  없으면 제출된 답변이 영원히 SAFETY_CHECKING에 머문다는 점을 후속 작업 우선순위에 반영해야 한다.
- `AnswerFormat`별 TEXT/PHOTO/BOTH 조합은 명시적으로 미구현이다(TASK.md 제외 항목).
- `origin/main`이 이 브랜치 분기점 이후 10개 커밋 앞서 있고 `TASK.md`·`docs/api/openapi.json`을
  모두 건드린 커밋이 포함돼 있어, PR 전 `./harness sync`(rebase)에서 수동 충돌 해결이 필요할 수
  있다(`./harness pr-ready --project-tests`가 이 때문에 BLOCKED — 3절 참고).

## 8. Artifacts

- Test plan: `docs/test-plans/gh-125-TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API.md`
- CI run: 로컬 실행(이 세션), CI 파이프라인 실행 기록 없음
- Related ADR: 없음
- PR: 아직 생성되지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(`AnswerFormat` 조합, 실제 moderation pipeline e2e)
- [x] 잠재 문제에 후속 조치가 연결됨 — `AnswerModerationVerdictWorker` batch 격리 결함은 별도
      이슈로 미루지 않고 이 PR 범위에서 직접 수정했다(사용자 승인, 2026-08-17)
- [x] 실행 결과와 PR 설명이 일치함(PR 본문 작성 시 이 보고서를 그대로 링크)
