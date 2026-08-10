# Test Report: TEST-PLAN-GH-93-RELEASE-RECEIVE-SLOTS

> Created at: `2026-08-10T15:23:20+09:00`
> GitHub Issue: `#93`
> Branch: `fix/gh-93-release-receive-slot-transitions`
> Commit: `19e2a85` (원 구현 완료 시점 최종 커밋은 `b5827e7`)
> PR #98 code review follow-up commits: `e6183a5`, `ac3bdf6`, `a107d49`, `951fb1a` (§10 참고, 이 문서 자체를 커밋하는 마지막 커밋은 이 문서 특성상 자기 자신의 SHA를 미리 기록할 수 없다)

## 1. Executive summary

- Result: `PASS`
- Tested scope: `PostRecipient.expire()`/`block()` 도메인 불변식, `ReceiveSlotReleaseService`의
  만료·넘김확정·차단 세 전이(단건 및 재실행), 두 sweep(만료 후보/넘김확정 후보) 조회,
  `SafetyService.block()`과의 트랜잭션 결합, `AnswerNotificationService.publish()`와 만료
  전이의 동시 경쟁, `DirectionPostService.send()`를 통한 end-to-end 재수신 가능성
- Unverified scope: 실제 sweep 구동 진입점(스케줄러 또는 `SKIP_CONFIRMATION_DUE` outbox
  소비자)은 이슈 범위 밖이라 미구현·미검증. `recipient_receive_state` 카운터가 이미
  어긋난 상태에서의 복구 경로도 범위 밖.
- Release recommendation: 로컬 검증 전부 통과. 승인된 PR 검토 후 병합 가능.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 25 (Temurin LTS) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 172 / 0 failures | ~3s | Gradle test XML (`build/test-results/test/`) |
| `./gradlew integrationTest` | PASS | 173 / 0 failures | ~1m 30s | Gradle integrationTest XML (`build/test-results/integrationTest/`) |
| `./gradlew integrationTest --tests ReceiveSlotReleaseIntegrationTest` (5회 반복) | PASS | 매회 15 / 0 failures | 매회 ~7s | 동시성 시나리오(INT-006, INT-015) flakiness 확인 목적, 5회 연속 안정 |
| `./harness check` | PASS | secret preflight, JUnit policy, convention, workflow, label, husky 전부 통과 | ~수 초 | 이 보고서 |
| `./harness test-run --id TEST-PLAN-GH-93-RELEASE-RECEIVE-SLOTS` | PASS | 위 unit/integration 재확인(up-to-date) | 1s | 이 보고서 생성 |
| `./harness pr-ready --project-tests` | PASS | `test`/`integrationTest`/`check` 전부 up-to-date, BUILD SUCCESSFUL | ~1s | 2회차 검증(아래 §10 참고) |
| `npm run hooks:validate` | PASS | Husky validation passed | ~1s | 2회차 검증 |
| `git diff --check` | PASS | 공백 오류 없음(종료 코드 0) | 즉시 | 2회차 검증 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `DirectionDomainTest#expireReleasesCapacityFromEachNonTerminalSource` | AVAILABLE/DISCOVERED/OPENED 세 소스 모두 검증 |
| UNIT-002 | PASS | `DirectionDomainTest#expireRejectsTerminalAndSkipPendingSources` | ANSWERED/SKIPPED/SKIP_PENDING/EXPIRED/BLOCKED 5개 소스 전부 거절 확인. 계획의 원래 4개 terminal 상태에 SKIP_PENDING을 설계 확정으로 추가(§4 근거는 계획 문서 갱신 참고) |
| UNIT-003 | PASS | `DirectionDomainTest#expireRejectsReExpiringAnAlreadyExpiredRecipient` | 이미 EXPIRED인 행 재호출 시 예외로 확정(멱등 반환 대신 예외 — `confirmSkip`/`block`과 계약 통일) |
| UNIT-004 | PASS | `DirectionDomainTest#expireRejectsTimeBeforeDiscoveredOrOpened` | discoveredAt·openedAt 각각에 대한 시간 역전 거절 |
| UNIT-005 | PASS | `DirectionDomainTest#confirmSkipDoesNotEnforceGracePeriodItself` | 도메인 `confirmSkip()`이 유예 시간을 모른다는 사실 재확인 |
| UNIT-006 | PASS | `DirectionDomainTest#blockReleasesCapacityFromEachEligibleSource` | AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING 네 소스 모두 검증 |
| UNIT-007 | PASS | `DirectionDomainTest#blockRejectsAnsweredSource` | ANSWERED 거절 |
| UNIT-008 | PASS | `DirectionDomainTest#blockRejectsAlreadyTerminalSources` | SKIPPED/EXPIRED/BLOCKED 거절 |
| INT-001 | PASS | `ReceiveSlotReleaseIntegrationTest#expireReleasesSlotForAvailableRecipient` | |
| INT-002 | PASS | `ReceiveSlotReleaseIntegrationTest#expireSweepOnlyTouchesExpiredCandidates` | 3명 중 안 만료된 1명만 미전이 확인 |
| INT-003 | PASS | `ReceiveSlotReleaseIntegrationTest#confirmSkipReleasesSlotAfterGracePeriodElapses` | |
| INT-004 | PASS | `ReceiveSlotReleaseIntegrationTest#skipPendingIsNotConfirmedBeforeGracePeriodElapses` | 서비스 계층이 유예 시간을 직접 재확인함을 검증(계획 수정 사항, §5 참고) |
| INT-005 | PASS | `ReceiveSlotReleaseIntegrationTest#revertedSkipIsNoLongerConfirmable` | |
| INT-006 | PASS | `ReceiveSlotReleaseIntegrationTest#expiringTheSameRecipientConcurrentlyReleasesTheSlotOnlyOnce` | 대상 재정의(계획 §6 근거) — 같은 만료 전이의 동시 중복 실행으로 검증 |
| INT-007 | PASS | `ReceiveSlotReleaseIntegrationTest#blockingReleasesAllPendingRecipientItemsFromTheBlockedSender` | |
| INT-008 | PASS | `ReceiveSlotReleaseIntegrationTest#blockingDoesNotRetransitionAnAlreadyAnsweredItem` | |
| INT-009 | PASS | `ReceiveSlotReleaseIntegrationTest#blockingDoesNotTouchTheBlockedPersonsOwnRecipientItems` | |
| INT-010 | PASS | `ReceiveSlotReleaseIntegrationTest#blockingDoesNotTouchUnrelatedSendersRecipientItems` | |
| INT-011 | PASS | `ReceiveSlotReleaseIntegrationTest#reRunningExpireOnAnAlreadyExpiredRowDoesNotReleaseTwice` | |
| INT-012 | PASS | `ReceiveSlotReleaseIntegrationTest#userBecomesEligibleAgainAfterExpiringAllUnansweredSlots` | 이슈 재현 절차 재현. 새 스킴을 만들지 않고 V1 시드 `OCTANT`를 재사용(계획 헤더 근거) |
| INT-013 | PASS | `ReceiveSlotReleaseIntegrationTest#expireSweepExcludesAnsweredItems` | |
| INT-014 | PASS | `ReceiveSlotReleaseIntegrationTest#allThreeTransitionsLeaveAlreadyTerminalRowsUntouched` | SKIPPED/EXPIRED/BLOCKED 3행 × 세 전이(expire/confirmSkip/block) 9개 조합 전부 no-op 확인 |
| INT-015 | PASS | `ReceiveSlotReleaseIntegrationTest#expireAndPublishRaceExclusivelyOnTheSameRecipient` | `AnswerNotificationService.publish()`의 순서 재배치(§5) 없이는 실패했을 시나리오 |
| INT-016 | PASS | `ReceiveSlotReleaseIntegrationTest#blockingClearsSkipRequestedAtForSkipPendingItems` | PR #98 CodeRabbit 리뷰(§10)로 추가. `SKIP_PENDING → BLOCKED` 전이에서 `skip_requested_at`이 DB에 실제로 NULL로 반영되는지 확인 |

## 5. Failures and diagnostics

구현 과정에서 두 가지 결함을 발견하고 같은 이슈 범위 안에서 수정했다. 둘 다 테스트를
먼저 작성해 재현한 뒤 최소 변경으로 고쳤다.

**1) 신규 타임스탬프 필드가 `matched_at`/`submitted_at` 기준선보다 앞서는 값을 가질 수
없다는 기존 CHECK 제약을 처음에 놓쳤다.** `insertRecipient`/`post` fixture 헬퍼가
`matched_at`/`submitted_at`을 테스트 상수 `NOW`로 고정한 채 `NOW.minusSeconds(60)` 같은
과거 시각을 다른 필드(예: `expires_at`, `skip_requested_at`)에 넣어
`ck_direction_post_expiry`/`ck_post_recipient_timestamps` 위반으로 9개 테스트가 즉시
실패했다. 원인은 애플리케이션 코드가 아니라 테스트 fixture였다 — 모든 timestamp 필드를
공통 기준선(`BASELINE = NOW.minusSeconds(3600)`)보다 뒤에 두도록 fixture를 수정해
해결했다.

**2) `AnswerNotificationService.publish()`가 새로 도입된 만료 전이와 경쟁할 때 데이터
불일치를 만들 수 있었다.** 기존 코드는 Answer를 먼저 `PUBLISHED`로 저장한 뒤
`post_recipient`를 `ANSWERED`로 전이했고, 후자가 실패해도(다른 전이가 이미 그 행을
선점한 경우) 예외를 던지지 않고 조용히 넘어갔다. `#93`이 만료·차단 전이를 도입하기
전에는 `post_recipient`가 답변 경로 외의 다른 전이와 경쟁할 일이 없어 이 순서가
안전했지만, 지금은 "PUBLISHED 답변인데 그 질문글 항목은 EXPIRED/BLOCKED"인 상태가
가능해졌다. INT-015가 이 경쟁을 재현했고(`releaseSlot`이 `recipient.answered(at)`을
호출할 때 EXPIRED/BLOCKED 소스에서 `DirectionException`이 그대로 전파), 슬롯 확보를
Answer 저장보다 먼저 시도하고 실패 시 `AnswerException(INVALID_ANSWER_STATUS)`를 던져
게시 자체를 거절하도록 순서를 바꿔 해결했다. 기존 `AnswerSafetyNotificationPersistenceIntegrationTest`(12건)와
`InboxSentPostWriteIntegrationTest`(28건)를 재실행해 이 변경이 기존 정상 경로(경쟁이
없는 일반적인 답변 게시)에 회귀를 만들지 않음을 확인했다.

민감정보가 포함된 로그 원문은 기록하지 않는다.

## 6. Potential issues

### Application code

- 도메인 계층의 `expire()`는 terminal 상태에서 재호출 시 예외를 던지는 계약을
  택했다(`confirmSkip()`/`block()`과 통일). 호출자(`ReceiveSlotReleaseService`)가
  재전이 전에 반드시 현재 상태를 먼저 확인해야 하며, 이 계약을 모르고 새 호출자를
  추가하면 예외가 그대로 전파될 수 있다. 서비스 계층의 `Optional`-반환 계약을 우회하지
  않도록 후속 작업에서 주의가 필요하다.
- `SafetyService.block()`이 이제 `direction` 패키지의 `ReceiveSlotReleaseService`에
  의존한다. 이전에는 `safety`가 다른 feature의 service를 참조하지 않았다 — 이번이
  최초의 feature 간 service-to-service 의존이다. `direction`이 이미 `answer`의
  repository를 참조하는 선례(`AnswerNotificationService`)는 있었지만, service 계층
  의존은 새롭다. 아키텍처 경계 테스트(`DirectionPersistenceBoundaryTest`,
  `SafetyNotificationBoundaryTest`)는 여전히 통과하지만, 이 의존 방향이 앞으로도
  유지 가능한 패턴인지는 인프라/설계 리뷰에서 별도로 판단이 필요하다.

### Infrastructure and resource limits

- 해당 없음. 이번 변경은 순수 애플리케이션·DB 계층이다.

### Database and migrations

- 신규 마이그레이션은 없다. `PostRecipient.expire()`/`block()`이 쓰는 컬럼
  (`expired_at`, `blocked_at`, `capacity_released_at`, `skip_requested_at`)은 V1·V2에서
  이미 존재했다.
- `qello.direction.skip-confirmation-grace-seconds`(신규 설정, 기본값 5)가
  `application.properties`에 추가됐다. `application-test.properties`는 이 값을
  오버라이드하지 않고 기본값을 그대로 쓴다 — 테스트가 기본값(5초)에 암묵적으로
  의존한다는 점을 후속 변경 시 유의해야 한다.

### Concurrency and idempotency

- INT-006(같은 만료 전이의 동시 중복 실행)과 INT-015(만료 vs 답변 게시)가 핵심
  동시성 경로를 덮는다. 둘 다 5회 반복 실행에서 안정적으로 통과했으나, `ExecutorService`
  기반 테스트는 근본적으로 스케줄링에 민감하다 — CI 환경의 부하가 크게 다르면
  드물게 flaky해질 가능성은 완전히 배제할 수 없다.
- `ReceiveSlotReleaseService`의 세 전이 메서드는 각각 자체 트랜잭션이며 서로 다른
  status를 조건으로 조건부 UPDATE한다. 만료 sweep과 넘김확정 sweep은 대상 상태
  집합이 겹치지 않도록 설계했다(`expire()`는 `SKIP_PENDING`을 받지 않음) — 이 설계가
  깨지면(예: 누군가 `expire()`의 소스 상태에 `SKIP_PENDING`을 추가하면) 계획이 배제한
  실제 경쟁 시나리오가 다시 열린다는 점을 주석과 계획 문서에 남겼다.
- 만료·넘김확정 sweep을 실제로 반복 구동하는 진입점(스케줄러 등)이 없어, "여러 인스턴스가
  동시에 같은 배치를 스캔하는" 실제 운영 상황의 재현은 INT-006의 인위적 동시 호출로만
  대체 검증했다.

### Transactions and event ordering

- `SafetyService.block()`은 `user_block` insert와 대상 `post_recipient` 전이·슬롯 해제를
  같은 `@Transactional` 경계에서 수행한다(Spring 기본 전파로 같은 트랜잭션에 참여).
- `AnswerNotificationService.publish()`의 순서 변경(§5)으로 outbox 이벤트
  (`ANSWER_PUBLISHED`) 기록은 여전히 Answer 저장 이후, 같은 트랜잭션 안에서 일어난다 —
  이 순서 자체는 바뀌지 않았다.
- `DIRECTION_POST_EXPIRED`/`SKIP_CONFIRMATION_DUE` outbox 이벤트는 이번 변경에서
  생성하지 않는다 — 이슈 범위가 슬롯 해제 자체이고 알림 발송은 F07 영역이라 명시적으로
  제외했다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 각 전이 메서드가 단건·자체 트랜잭션이므로, 여러 행을 순회하는 sweep 도중 한 행이
  실패해도(예: DB 순간 장애) 이미 처리된 다른 행의 커밋은 되돌아가지 않는다. 이는
  fixture나 테스트가 아니라 설계 자체의 성질이며, 별도의 실패 주입 테스트는 작성하지
  않았다(재현하려면 특정 행에서만 실패하는 DB 장애를 인위적으로 만들어야 하는데, 이
  경로는 세 메서드가 공유하는 `PostRecipientRepository`/`RecipientReceiveStateRepository`의
  일반적인 신뢰성 문제이지 `#93`이 새로 만든 위험이 아니다).
- `recipient_receive_state` 카운터가 이미 실제 미처리 건수와 어긋난 상태에서 시작하는
  경우의 복구는 검증하지 않았다(계획 §2 Excluded).

## 7. Regression and residual risk

- `AnswerNotificationService.publish()`의 순서 변경이 기존 두 통합 테스트 스위트
  (`AnswerSafetyNotificationPersistenceIntegrationTest`, `InboxSentPostWriteIntegrationTest`,
  합계 40건)에서 회귀 없음을 확인했다.
- 잔여 위험: 만료·넘김확정 sweep을 실제로 반복 구동하는 스케줄러/워커가 아직 없다.
  `ReceiveSlotReleaseService.findExpirable`/`findConfirmableSkips`/`findBlockable`은
  호출 가능한 상태로 준비돼 있지만, 이번 이슈만으로는 프로덕션에서 아무것도 자동으로
  만료·확정되지 않는다 — `SafetyService.block()` 경로만 사용자 행동(차단)에 의해 실제로
  트리거된다. 후속 이슈로 진입점을 연결해야 이번 수정의 효과가 실제로 나타난다.
- `#94`(recipient_receive_state 초기화 경쟁)는 이번 변경과 독립적으로 남아 있다 —
  INT-012는 기존 행을 전제로 설계해 그 결함을 우회했을 뿐 고치지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-93-TEST-PLAN-GH-93-RELEASE-RECEIVE-SLOTS.md`
- CI run: 로컬 실행만 수행(PR 생성 전). CI 재실행 결과는 PR에서 확인.
- Related ADR: 없음(이번 변경은 기존 ADR 범위 안의 결함 수정)
- PR: 아직 생성되지 않음(이 보고서 이후 커밋·PR 순서로 진행)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (§1 Unverified scope, §6 각 섹션)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (`#94`, `#97` 참조. 스케줄러 진입점은 별도
      후속 이슈 필요 — 아직 미생성, PR 설명에 명시 예정)
- [x] 실행 결과와 PR 설명이 일치함 (PR 생성 시 이 보고서를 링크)

## 10. PR #98 code review follow-up (2026-08-10)

CodeRabbit이 PR #98에 남긴 코멘트 중 실제 결함·회귀 위험으로 판단한 항목만 수정했다.
반영 시점의 브랜치는 `origin/main`(`19e2a85`)의 최신 상태에서 분기된 상태를 유지했다
(`git merge-base --is-ancestor origin/main HEAD` 확인).

| CodeRabbit 코멘트 | 판단 | 조치 |
| --- | --- | --- |
| `PostRecipient.block()`에 `answered`/`expire`와 동일한 시간 역전 검증(`requireAtNotBeforeDiscoveryOrOpen`)이 없음 | 정당 — 세 전이의 시간 계약을 통일해 이후 호출자 추가 시 회귀를 방지 | `block()`에 검증 호출 추가, 관련 Javadoc 갱신 |
| `DirectionDomainTest`가 `SKIP_PENDING → BLOCKED`에서 `skipRequestedAt`/`skippedAt`이 비워지는지 단언하지 않음 | 정당 — 생성자 불변식 회귀를 검출하지 못함 | `blockReleasesCapacityFromEachEligibleSource`에 단언 2건 추가 |
| `JdbcPostRecipientRepository`가 `SKIP_PENDING → BLOCKED`에서 `skip_requested_at`을 NULL로 비우는지 확인 필요 | SQL(`TRANSITION_TO_BLOCKED`)은 이미 비우고 있었으나, 이를 DB 계층에서 검증하는 통합 테스트가 없었음 | `ReceiveSlotReleaseIntegrationTest#blockingClearsSkipRequestedAtForSkipPendingItems`(INT-016) 추가 |
| `ReceiveSlotReleaseService`의 클래스 주석("각 전이는 자체 트랜잭션")이 `blockAllPendingFor`의 자기 호출로 인한 단일 트랜잭션 결합과 불일치 | 정당 — 동작은 의도대로였으나 문서가 오해를 유발 | 클래스 Javadoc에 `blockAllPendingFor` 예외 명시 |
| `docs/test-plans/...md` 위험 목록 표에서 한 행에 선행 `|`가 빠짐 | 정당 — 표 렌더링 깨짐(MD055) | 선행 `|` 추가 |
| 이 보고서의 `Commit` 필드(`19e2a85`)가 실제 구현 커밋을 가리키지 않고, `./harness pr-ready --project-tests`/`npm run hooks:validate`/`git diff --check` 실행 결과가 없음 | 정당 | 위 §3에 세 명령의 재실행 결과를 추가. 헤더에 원 구현 최종 커밋(`b5827e7`)과 이번 리뷰 대응 커밋 목록을 함께 기록했다 |
| `TASK.md`에 브랜치가 최신 `origin/main`에서 분기됐다는 증거가 없음 | 이미 충족 — `git merge-base origin/main HEAD`가 `origin/main` 자체와 일치함을 확인(별도 코드 변경 불필요) | 조치 없음(위 근거를 이 보고서에 기록) |
| `PostRecipientRepository.findExpirableAsOf`/`findConfirmableSkips`에 배치 상한(limit)이 없음 | 시기상조 — sweep을 실제로 반복 구동하는 진입점이 이번 이슈 범위 밖(§7 잔여 위험)이라 무제한 조회가 아직 프로덕션에서 트리거되지 않음. 진입점을 추가하는 후속 이슈에서 함께 설계 | 조치 없음(후속 이슈 메모로 남김) |

재실행 결과: `./gradlew test`(172/0 실패), `./gradlew integrationTest`(174/0 실패,
INT-016 포함), `./harness check`, `./harness pr-ready --project-tests`,
`npm run hooks:validate`, `git diff --check` 전부 PASS.
