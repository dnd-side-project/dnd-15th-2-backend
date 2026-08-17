# Test Report: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP

> Created at: `2026-08-17T21:06:15+09:00`
> GitHub Issue: `#126`
> Branch: `feat/gh-126-expiration-skip-sweep`
> Commit: `65c6828`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 만료 sweep 실행기(`RecipientExpirationSweepWorker`)와 넘김확정
  sweep 실행기(`SkipConfirmationSweepWorker`)의 batch 처리, 처리량 제한,
  행 단위 실패 격리, 재실행 안전성, 답변 제출·사용자 되돌리기·차단·서로 다른
  sweep 간 경합. `ReceiveSlotReleaseService`/`PostRecipientRepository`에 추가한
  limit 오버로드.
- Unverified scope: 승인된 계획의 §2 Excluded 항목(스케줄러 트리거,
  actuator/Micrometer 메트릭, 분산 lease/advisory lock, 물리 삭제,
  `DirectionPost` 상태 전이 변경) — 이번 이슈 범위 밖이며 미구현.
- Release recommendation: 승인된 P0/P1 시나리오가 전부 통과했고 저장소 전체
  회귀(unit 581건 + integration 440건, 합계 1021건, 실패 0건)도 통과했으므로
  병합 가능.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Temurin 21 (Gradle toolchain, `JavaLanguageVersion.of(21)`) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (PostgreSQL 16.14 + PostGIS) |
| Test runner | JUnit 5 (Gradle `test` / `integrationTest` 태스크) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests 'com.dnd.qello.direction.sweep.*'` | PASS | 13 | 0.022s | `build/test-results/test/TEST-com.dnd.qello.direction.sweep.*.xml` |
| `./gradlew integrationTest --tests 'com.dnd.qello.RecipientSweepIntegrationTest'` | PASS | 14 | 0.266s | `build/test-results/integrationTest/TEST-com.dnd.qello.RecipientSweepIntegrationTest.xml` |
| `./gradlew integrationTest --tests 'com.dnd.qello.RecipientSweepConcurrencyIntegrationTest'` | PASS | 4 | 0.152s (반복 6회 모두 통과, flake 없음) | `build/test-results/integrationTest/TEST-com.dnd.qello.RecipientSweepConcurrencyIntegrationTest.xml` |
| `./gradlew integrationTest --tests 'ReceiveSlotReleaseIntegrationTest,AnswerSubmissionApiIntegrationTest,InboxApiIntegrationTest,InboxCommandConcurrencyIntegrationTest,AnswerSubmissionConcurrencyIntegrationTest'` | PASS | 회귀(limit 오버로드 추가가 기존 무제한 조회 호출자에 영향 없음 확인) | 별도 실패 없음 | 위 5개 클래스 XML |
| `./gradlew test` (전체 unit) | PASS | 581 | — | `build/test-results/test/` |
| `./gradlew integrationTest` (전체 integration) | PASS | 440 | — | `build/test-results/integrationTest/` |
| 전체 합산(unit+integration) | PASS | 1021, 실패 0, 오류 0, 스킵 0 | — | `build/test-results/{test,integrationTest}/TEST-*.xml` 전수 집계 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `RecipientExpirationSweepWorkerTest.passesLimitAndTimeToCandidateLookup` | |
| UNIT-002 | PASS | `RecipientExpirationSweepWorkerTest.callsExpireOncePerCandidate` | |
| UNIT-003 | PASS | `RecipientExpirationSweepWorkerTest.countsEmptyExpireAsIneligible` | |
| UNIT-004 | PASS | `RecipientExpirationSweepWorkerTest.isolatesFailurePerRow` | |
| UNIT-005 | PASS | `RecipientExpirationSweepWorkerTest.noCandidatesMeansNoExpireCalls` | |
| UNIT-006 | PASS | `RecipientExpirationSweepWorkerTest.rejectsNonPositiveLimit` | limit 검증은 `BatchCommand` 생성 시점(compact constructor)에서 일어난다 — `DirectionMatchingWorker.BatchCommand`와 동일한 기존 관례 |
| UNIT-007 | PASS | `RecipientExpirationSweepWorkerTest.usesClockWhenAtIsNull` | |
| UNIT-008 | PASS | `SkipConfirmationSweepWorkerTest.passesRawTimeAndLimitWithoutComputingDeadline` | |
| UNIT-009 | PASS | `SkipConfirmationSweepWorkerTest.countsEmptyConfirmSkipAsIneligible` | |
| UNIT-010 | PASS | `SkipConfirmationSweepWorkerTest.isolatesFailurePerRow` | |
| UNIT-011 | PASS | `RecipientExpirationSweepWorkerTest.scannedEqualsSumOfOutcomes` | |
| UNIT-012 | PASS | `RecipientExpirationSweepWorkerTest.resultHasNoSensitiveFields` | |
| UNIT-013 | PASS | `RecipientExpirationSweepWorkerTest.rejectsNegativeCounters` | |
| INT-001 | PASS | `RecipientSweepIntegrationTest.expirationSweepReleasesSlotForExpiredAvailableRecipient` | |
| INT-002 | PASS | `RecipientSweepIntegrationTest.reRunningExpirationSweepDoesNotDoubleReleaseTheSlot` | |
| INT-003 | PASS | `RecipientSweepIntegrationTest.expirationSweepLeavesNonExpiredRecipientsUntouched` | |
| INT-004 | PASS | `RecipientSweepIntegrationTest.expirationSweepExcludesSkipPendingRecipients` | |
| INT-005 | PASS | `RecipientSweepIntegrationTest.expirationSweepExcludesRecipientsWithPendingAnswers` | |
| INT-006 | PASS | `RecipientSweepIntegrationTest.expirationSweepRejectsStaleCandidateAfterAnswerIsSubmitted` | |
| INT-007 | PASS | `RecipientSweepIntegrationTest.skipConfirmationSweepDoesNotConfirmBeforeGraceElapses` | |
| INT-008 | PASS | `RecipientSweepIntegrationTest.skipConfirmationSweepConfirmsExactlyAtGraceBoundary` | |
| INT-009 | PASS | `RecipientSweepIntegrationTest.skipConfirmationSweepSetsCapacityReleasedAtOnConfirmation` | |
| INT-010 | PASS | `RecipientSweepIntegrationTest.reRunningSkipConfirmationSweepDoesNotDoubleReleaseTheSlot` | |
| INT-011 | PASS | `RecipientSweepIntegrationTest.expirationSweepPagesThroughCandidatesInDeterministicOrder` | |
| INT-012 | PASS | `RecipientSweepIntegrationTest.expirationSweepIsolatesOneRowFailureFromTheRestOfTheBatch` | `receiveSlotReleaseService`는 `@Transactional` 프록시라 그대로 `spy()`하면 Mockito가 프록시 언랩에 실패해 `AopTestUtils.getUltimateTargetObject`로 실제 대상을 꺼내 spy했다 — 아래 §6 참고 |
| INT-013 | PASS | `RecipientSweepConcurrencyIntegrationTest.expirationSweepAndAnswerPublicationRaceExclusivelyOnTheSameRecipient` | |
| INT-014 | PASS | `RecipientSweepConcurrencyIntegrationTest.skipConfirmationSweepAndRevertRaceOnTheSameRecipientNeverDoubleReleases` | 아래 §6 참고 — 그레이스 경과 후에는 규칙상 승자가 결정적이다 |
| INT-015 | PASS | `RecipientSweepConcurrencyIntegrationTest.expirationSweepAndBlockRaceExclusivelyOnTheSameRecipient` | |
| INT-016 | PASS | `RecipientSweepIntegrationTest.expirationSweepReleasesMultipleExpiredItemsForTheSameRecipient` | |
| INT-017 | PASS | `RecipientSweepIntegrationTest.expirationSweepCommitsEvenWhenCounterIsAlreadyZero` | |
| INT-018 | PASS | `RecipientSweepConcurrencyIntegrationTest.expirationAndSkipConfirmationSweepsRunConcurrentlyWithoutLosingCounterUpdates` | |

## 5. Failures and diagnostics

실행 중 재현된 테스트 실패는 없다. 구현 과정에서 발견해 즉시 수정한 도구 사용
오류 1건만 기록한다.

- 증상: INT-012에서 `@Autowired ReceiveSlotReleaseService`를 그대로
  `Mockito.spy()`하면 `IllegalStateException: Failed to unwrap proxied object`가
  발생했다.
- 원인: 해당 빈이 `@Transactional` AOP 프록시(Spring CGLIB)로 감싸여 있어
  Mockito의 `SpringMockResolver`가 프록시 언랩을 시도하다 미완료 스터빙 예외로
  이어졌다.
- 조치: `org.springframework.test.util.AopTestUtils.getUltimateTargetObject(...)`로
  실제 대상 객체를 꺼낸 뒤 그 객체를 spy했다. 이 경로는 `@Transactional` 경계
  없이 개별 JDBC 문장이 즉시 커밋되지만, 이 시나리오는 행 단위 실패 격리만
  검증하면 되므로 결과에 영향이 없다.

## 6. Potential issues

### Application code

- `RecipientExpirationSweepWorker`/`SkipConfirmationSweepWorker`는 계획대로
  `@Scheduled` 등 어떤 실행 트리거도 갖지 않는다. 실제 운영에서 이 sweep이
  주기적으로 돌지 않으면 만료·넘김확정 항목의 슬롯이 영구히 점유된 상태로
  남는다 — 트리거 도입은 TASK.md에서 이미 후속 이슈로 명시적으로 분리했다.
- 두 worker 모두 실패한 행을 `log.warn`으로만 남기고 별도 알림·재시도 큐로
  보내지 않는다. 실패가 반복되는 특정 행이 있다면 다음 sweep 실행에서 다시
  후보로 잡혀 재시도되지만, 그 사이 관측은 로그 검색에 의존한다.

### Infrastructure and resource limits

- limit 파라미터에 상한 검증이 없다(`BatchCommand`는 `limit > 0`만 검사한다).
  운영에서 극단적으로 큰 limit을 넘기면 한 batch가 오래 걸리는 단일 쿼리와
  다건의 개별 트랜잭션을 유발할 수 있다 — 호출자(향후 스케줄러)가 합리적인
  값을 정하는 책임을 진다.

### Database and migrations

- `PostRecipientSql.FIND_EXPIRABLE`/`FIND_CONFIRMABLE_SKIPS`는 각각
  `(dp.expires_at, pr.id)`/`(skip_requested_at, id)`로 정렬한다(`/simplify` 리뷰
  이후 무제한·제한 조회가 하나의 SQL 템플릿을 공유하도록 합쳤다 — 무제한
  호출자는 `JdbcPostRecipientRepository`가 `limit`에 `Integer.MAX_VALUE`를 넘겨
  같은 쿼리를 그대로 재사용한다). 두 정렬 컬럼 다 전용 복합 인덱스가 없다 —
  현재 스키마의 기존 인덱스로 커버되는지, 데이터가 늘었을 때 정렬 비용이
  어떻게 변하는지는 이번 검증 범위 밖이다. 이 이슈는 Flyway migration을
  명시적으로 제외했으므로 인덱스 추가가 필요하면 별도 이슈로 분리해야 한다.

### Concurrency and idempotency

- INT-013/INT-015는 `ReceiveSlotReleaseService`가 이미 갖고 있던 행 잠금
  (`findByIdForUpdate`)과 조건부 UPDATE 조합에 의존해 정확히 하나만 성공함을
  확인했다 — worker 계층이 이 보장을 훼손하지 않음을 재확인한 것이며 새 잠금을
  추가하지 않았다.
- INT-014는 넘김확정 sweep과 `revertSkip` 경합을 다루지만, 두 메서드 모두
  같은 `at` 값을 기준으로 한 시간 비교 규칙(`skip_requested_at + grace`와
  `at`의 대소)으로 승자를 가르도록 설계돼 있어(`PostRecipientService.revertSkip`
  주석의 "confirm lane 소유" 문구 참고) 그레이스가 이미 지난 이 픽스처에서는
  락 순서와 무관하게 확정이 항상 이긴다. 테스트는 그럼에도 실제 동시
  스레드에서 교착이나 예외 누수가 없는지, 최종 상태·카운터가 모순되지 않는지를
  검증한다 — 완전히 비결정적인 락 경합을 재현하지는 않는다는 한계를 여기 기록한다.
- INT-018은 서로 다른 `post_recipient` 행을 각각 건드리는 두 sweep이 같은
  `recipient_receive_state` 카운터 행을 동시에 감소시킬 때 lost update가
  없는지 확인했다. `RELEASE` SQL의 `active_unhandled_count = active_unhandled_count - 1`
  UPDATE가 Postgres 행 잠금으로 직렬화되므로 두 감소가 모두 반영됐다.
- `confirmSkip()`이 `findById`(비잠금)를 쓰고 `expire()`는 `findByIdForUpdate`를
  쓰는 기존 비대칭은 이번 이슈에서 바꾸지 않았다. INT-014가 이 경로를
  실행하지만 위에서 설명한 이유로 완전한 락 경쟁 재현은 아니므로, 이 비대칭
  자체의 안전성은 여전히 조건부 UPDATE(`transitionToSkipped`/
  `transitionFromSkipPending`)에 의존한다는 점을 남겨둔다.

### Transactions and event ordering

- 두 worker는 계획대로 자체 트랜잭션을 열지 않는다. 각 행의 커밋 경계는
  `ReceiveSlotReleaseService`의 `@Transactional` 메서드이며, INT-012가 한 행의
  실패가 이미 커밋된 다른 행을 되돌리지 않음을 확인했다.

### External APIs

- 해당 없음. 이 이슈에는 외부 API 연동이 없다.

### Failure recovery and reconciliation

- 실패한 행은 상태가 바뀌지 않으므로 다음 sweep의 후보로 자연히 다시 잡힌다
  (INT-002/INT-010의 재실행 시나리오가 "상태가 안 바뀌면 후보에서 빠지지
  않는다"는 대칭 성질을 뒷받침한다). 별도의 dead-letter나 최대 재시도 횟수는
  두지 않았다 — 조건부 전이가 실패를 유발하는 근본 원인(예: 잘못된 상태)을
  스스로 해소하지 못하면 무한히 재시도되는데, 이 위험은 트리거가 없는 현재
  범위에서는 운영 이슈로 이연한다.

## 7. Regression and residual risk

- 회귀: `PostRecipientRepository`/`ReceiveSlotReleaseService`에 limit 오버로드를
  추가하면서 기존 무제한 조회 메서드(`findExpirable(Instant)`,
  `findConfirmableSkips(Instant)`)는 시그니처를 바꾸지 않았다.
  `ReceiveSlotReleaseIntegrationTest`, `AnswerSubmissionApiIntegrationTest`,
  `InboxApiIntegrationTest`를 재실행해 기존 호출자가 영향받지 않음을 확인했다.
- 잔여 위험: 위 §6에 정리한 실행 트리거 부재, 인덱스 미검증, `confirmSkip()`의
  비잠금 조회 비대칭, 실패 행 재시도 상한 없음 — 모두 TASK.md의 명시적 제외
  범위와 일치하며 이 이슈에서 새로 만든 위험이 아니라 기존 설계나 후속 이슈로
  이미 분리된 항목이다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-126-TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP.md`
- CI run: 로컬 실행(`./gradlew test`, `./gradlew integrationTest`) — 이 세션에서
  별도 CI 파이프라인은 트리거하지 않았다.
- Related ADR: 없음
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(§1 Unverified scope, §6 전반)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — §6에서 서술한 항목 중 실행
      트리거·인덱스 검증은 아직 별도 Issue 번호가 없다. PR 생성 시 사람이
      판단해 연결한다.
- [x] 실행 결과와 PR 설명이 일치함(PR 작성 시 이 보고서를 그대로 링크한다)
