# Test Report: TEST-PLAN-GH-137-DIRECTION-POST-MODERATION

> Created at: `2026-10-07T23:01:00+09:00`
> GitHub Issue: `#137`
> Branch: `feat/gh-137-direction-post-moderation`
> Commit: `1a3b1254` (기준 커밋. 변경은 커밋 전 작업 트리 상태로 실행했다)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 질문글 moderation 상태 전이(도메인), 질문글 판정 적용기, 판정 worker의 대상 종류별 분배, 제출 시
  job 접수(본문 있음·미디어 단독·멱등 재요청·release 없음), V32 제약, 제출 → 실행 worker → 판정 worker → 매칭
  worker 전체 흐름(ALLOW·BLOCK), 중복·반대·늦은 판정과 deadline 신호, 만료 후 ALLOW, 판정 적용과 매칭의 동시 실행,
  재시도 소진 후 수동 검토 case, 기존 답변 판정 회귀
- Unverified scope: `@Tag("performance")` 통합 테스트 2개(release fixture 한 줄만 추가했고 컴파일만 확인했다.
  `performanceTest`는 `check`에 포함되지 않아 실행하지 않았다). 매칭 이벤트 재시도 운영값(R8)은 계획상 범위 밖이다.
- Release recommendation: 병합 가능. 단 R8 운영값 확인 전에는 수동 검토가 길어진 질문글이 매칭되지 않을 수 있다(7절).

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Temurin 21 (Gradle toolchain 21, `JAVA_HOME`을 JDK 21로 지정) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine`, 테스트 클래스마다 새 컨테이너 |
| Test runner | JUnit 5, Mockito, AssertJ |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test javaConventionCheck` | PASS | 1254 | 59s | 단위 전체와 Java 관례 검사 |
| `./harness test-run` 1차 (`test` + `integrationTest`) | FAIL | 800 중 11 실패 | 11m 35s | 기존 통합 테스트 5개 클래스의 격리 문제(5절) |
| `./harness test-run` 2차 | PASS | 단위 1254, 통합 800 | 11m 58s | 1차 실패 수정 후. 이 실행이 보고서 틀을 만들었다 |
| `./harness check` 1차 | FAIL | — | — | `@DisplayName(PLAN + "...")`를 JUnit 정책 검사기가 인정하지 않음(5절) |
| `./harness check` 2차 | PASS | — | — | secret·JUnit 정책·workflow·label·Husky 검사 |
| `git diff --check` | PASS | — | — | |
| `./harness pr-ready --project-tests` | PASS | 단위 1254, 통합 800 | 14m 9s | Gradle `check`(checkstyle, spotless, `javaConventionCheck`, `test`, `integrationTest`) |

새 통합 클래스 `DirectionPostModerationIntegrationTest`는 2차 실행에서 12건 모두 통과했다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `DirectionPostModerationTest.allowPassesPendingPost` | |
| UNIT-002 | PASS | `DirectionPostModerationTest.blockRejectsPendingPostWithoutChangingStatus` | D1 |
| UNIT-003 | PASS | `DirectionPostModerationTest.deadlineHoldsPendingPostForReview` | |
| UNIT-004 | PASS | `DirectionPostModerationTest.lateVerdictResolvesReviewHeldPost` | ALLOW·BLOCK 2건 |
| UNIT-005 | PASS | `DirectionPostModerationTest.decidedPostIgnoresAnyLaterSignal` | 2×3건 |
| UNIT-006 | PASS | `DirectionPostModerationTest.repeatedDeadlineKeepsReviewHeld` | |
| UNIT-007 | PASS | `DirectionPostModerationTest.lateAllowDoesNotReopenClosedPost` | 만료 시각 같음·지남, EXPIRED, DELETED |
| UNIT-008 | PASS | `DirectionPostModerationTest.submitDecidesInitialModerationByBody` | |
| UNIT-009 | PASS | `DirectionPostModerationVerdictApplierTest.savesLockedPostOnlyWhenTransitioned`, `doesNotSaveWhenNoTransition` | |
| UNIT-010 | PASS | `DirectionPostModerationVerdictApplierTest.missingPostEndsWithoutException` | |
| UNIT-011 | PASS | `AnswerModerationVerdictWorkerTest.dispatchesDirectionPostVerdictToItsApplier` | |
| UNIT-012 | PASS | `AnswerModerationVerdictWorkerTest.dispatchesDirectionPostDeadlineToItsApplier` | |
| UNIT-013 | PASS | `AnswerModerationVerdictWorkerTest`의 GH-125 기존 5개 메서드 | 실제 `AnswerModerationVerdictApplier` + mock `AnswerNotificationService`로 assertion 무수정 |
| UNIT-014 | PASS | `AnswerModerationVerdictWorkerTest.isolatesApplierFailurePerEvent` | |
| UNIT-015 | PASS | `AnswerModerationVerdictWorkerTest.rejectsDuplicateAppliersForSameTargetType` | |
| UNIT-016 | PASS | `DirectionPostSubmissionServiceTest.bodyPostSubmitsModerationJobOnce` | 미디어 0·1개 |
| UNIT-017 | PASS | `DirectionPostSubmissionServiceTest.mediaOnlyPostSkipsModerationJobAndPasses` | |
| UNIT-018 | PASS | `DirectionPostSubmissionServiceTest.idempotentReplayDoesNotSubmitModerationAgain` | |
| UNIT-019 | PASS | `DirectionPostSubmissionServiceTest.moderationIntakeRejectionPropagatesAndRollsBack` | |
| UNIT-020 | PASS | `FlywayMigrationContractTest.migrationsMatchAcceptedContent` | V32 목록 추가 |
| INT-001 | PASS | `DirectionPostModerationIntegrationTest.bodyPostCommitsJobWithPost` | |
| INT-002 | PASS | `...mediaOnlyPostPassesWithoutJobAndMatches` | 매칭까지 확인 |
| INT-003 | PASS | `...missingReleaseRollsBackWholeSubmission` | |
| INT-004 | PASS | `...targetTypeConstraintsFollowV32` | `pg_get_constraintdef`로 확인 |
| INT-005 | PASS | `...allowFlowsThroughWorkersIntoMatching` | 공급자 double 호출 1회 |
| INT-006 | PASS | `...blockLeavesPostUnmatched` | |
| INT-007 | PASS | `...duplicateAndOppositeVerdictsDoNotRevert` | 재전달은 같은 job의 이벤트를 다른 dedup 키로 넣어 흉내 냈다 |
| INT-008 | PASS | `...lateDeadlineDoesNotRevertPassed` | |
| INT-009 | PASS | `...deadlineHoldsThenManualAllowResumesMatching` | 실제 `AnswerModerationDeadlineWorker` 사용 |
| INT-010 | PASS | `...lateAllowDoesNotReopenExpiredPost` | |
| INT-011 | PASS | `...concurrentVerdictAndMatchingStayConsistent` | 한 실행에서는 두 순서 중 하나만 관측된다(6절) |
| INT-012 | PASS | `AnswerModerationPublicationIntegrationTest`, `AnswerModerationRetryIntegrationTest` | 무수정 통과 |
| INT-013 | PASS | `...exhaustedJobHandsOffToManualReview` | |

## 5. Failures and diagnostics

1차 전체 실행에서 기존 통합 테스트 11건이 실패했다. 모두 질문글 제출이 job·상태 이력·실행 요청 Outbox를 만들게 되면서
생긴 테스트 격리 문제였고, production 결함은 아니었다.

| 테스트 | 원인 | 조치 |
| --- | --- | --- |
| `DirectionMatchingVerticalFlowIntegrationTest` 4건, `QuestionRecommendationIntegrationTest` 1건 | setup의 `DELETE FROM filter_job`이 `filter_job_status_history` FK에 막힘 | 이력을 먼저 지운다 |
| `DirectionMatchingWorkerIntegrationTest` 4건 | `outbox_event`를 `aggregate_id`만으로 조회. 새 컨테이너에서 질문글 id와 job id가 같아 두 행이 나옴 | `aggregate_type = 'DIRECTION_POST'` 조건 추가 |
| `FlywayMigrationIntegrationTest` 1건 | 최신 migration 기대값 31 | 32 |
| `NotificationPreferenceMigrationIntegrationTest` 1건 | V24 이후 적용 수 기대값 7 | 8 |

`./harness check` 1차는 새 테스트의 `@DisplayName(PLAN + "...")`를 `scripts/validate-java-tests.py`가 문자열 리터럴로
인정하지 않아 실패했다. 전체 ID를 리터럴로 적도록 바꿨다.

1차 실행과 같은 시각에 별도 Gradle 실행을 겹쳐 돌려 `build/test-results` 쓰기가 충돌한 일이 있었다. 그 결과는 버리고
단독 실행 결과만 근거로 삼았다.

## 6. Potential issues

### Application code

- 질문글 판정 적용기는 질문글이 없으면 상태 변경 없이 끝난다. 삭제된 질문글의 판정 이벤트가 조용히 소비되므로 로그가
  필요하면 후속으로 추가한다.
- 이의제기 API 스키마가 `FilterTargetType`을 그대로 써서 OpenAPI enum에 `DIRECTION_POST`가 나타난다. 요청하면
  `AppealCaseService`가 `NICKNAME`과 같이 400(`FLT-VAL-005`)으로 거절하므로 동작은 안전하다.
- 보이지 않는 문자만 있는 본문(R9)은 `normalizeBody`의 strip을 통과해 job이 되고, 실행 경로에서 입력 오류로 재시도를
  소진한 뒤 REVIEW_HELD가 될 수 있다. 이번 범위에서 검증하지 않았다.

### Infrastructure and resource limits

- 질문글 하나가 실행 요청 Outbox 1건과 job·상태 이력 행을 추가로 만든다. 판정 worker는 답변·질문글 판정을 같은 batch
  한도로 함께 처리하므로 질문글이 많아지면 답변 판정 지연이 늘 수 있다.

### Database and migrations

- V32는 CHECK 제약을 drop/add한다. 기존 행은 모두 `ANSWER`·`NICKNAME`이라 재검증이 실패할 수 없다. 큰 테이블에서는
  ADD CONSTRAINT 검증 중 잠금이 걸린다.

### Concurrency and idempotency

- 판정 적용과 매칭 worker는 같은 질문글 행을 `FOR UPDATE`로 잠근다. INT-011은 한 실행에서 두 순서 중 하나만 관측하므로
  반복 실행으로 두 순서를 모두 확인하지는 않았다.
- 같은 판정 이벤트의 재전달은 다른 dedup 키로 넣어 흉내 냈다. lease 만료 후 실제 재claim 경로는 답변 쪽 기존 테스트
  (`AnswerModerationPublicationIntegrationTest` INT-013, INT-021)가 소유한다.

### Transactions and event ordering

- 질문글 저장과 job 접수는 한 트랜잭션이다(INT-003). 판정 적용과 claim 완료는 별도 트랜잭션이라 그 사이에 실패하면
  재처리되며, 적용기가 멱등이라 상태는 바뀌지 않는다.
- DEADLINE_ELAPSED가 판정보다 늦게 처리돼도 PASSED·REJECTED를 되돌리지 않는다(INT-008, UNIT-005).

### External APIs

- 공급자는 test double이다. 실제 OpenAI 호출은 이번 변경과 무관하며 실행하지 않았다.

### Failure recovery and reconciliation

- R8: 매칭 이벤트는 PENDING·REVIEW_HELD 동안 재시도한다. 재시도 상한(`maxAttempts`)과 backoff는 배포 때 주입되는데,
  그 합이 deadline(5분)과 수동 검토 시간보다 짧으면 매칭 이벤트가 DEAD가 된다. 그 뒤 PASSED가 돼도 매칭되지 않는다.
  `DirectionMatchingVerticalFlowRecoveryIntegrationTest` INT-006이 이 상태(MATCHING에 머무름, 자동 복구 없음)를 이미
  관측한다. 운영값 확인 또는 PASSED 전이 시 매칭 이벤트 재예약이 후속 과제다.
- 승격된 release가 없으면 질문글 제출이 503(`FLT-DOM-006`)으로 실패한다(fail-closed). 운영에서 release 승격 상태가
  질문글 기능의 전제 조건이 된다.

## 7. Regression and residual risk

- 답변 판정 반영: 단위 UNIT-013과 답변 통합 테스트가 assertion 수정 없이 통과했다.
- 기존 Direction 통합 테스트 13개에 release fixture를 추가했고, 그중 3개는 정리 순서·조회 조건·기대값을 고쳤다(5절).
  assertion이 검증하는 의미는 바꾸지 않았다.
- 남은 위험: R8(운영값), R9(보이지 않는 문자 본문), 성능 태그 테스트 미실행.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-137-TEST-PLAN-GH-137-DIRECTION-POST-MODERATION.md`
- CI run: PR 생성 후 기록
- Related ADR: 없음
- PR: 생성 전

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
