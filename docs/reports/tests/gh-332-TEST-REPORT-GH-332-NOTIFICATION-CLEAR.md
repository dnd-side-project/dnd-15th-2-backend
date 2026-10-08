# Test Report: TEST-PLAN-GH-332-NOTIFICATION-CLEAR

> Report ID: `TEST-REPORT-GH-332-NOTIFICATION-CLEAR`
> Created at: `2026-10-08T03:13:11+09:00`
> Updated at: `2026-10-08T05:05:55+09:00` — sync 이후 상태, 독립 검증 지적 반영
> GitHub Issue: `#332`
> Branch: `feat/gh-332-notification-clear-retention`
> Commit: `3387fcf0` (sync 이후 base HEAD; 검증한 변경은 미커밋)
> Task ID: `GH-332-NOTIFICATION-CLEAR`
> Design ID: 해당 없음

처음 `./harness test-run --id TEST-REPORT-GH-332-NOTIFICATION-CLEAR`는 sync 전 단위 단계의 환경 실패(5절)로
종료돼 scaffold를 만들지 못했다. 이 보고서는 같은 `templates/test-report.md` 구조로 직접 작성했다.

## 1. Executive summary

- Result: `PASS` — 승인된 시나리오 UNIT-001~008, INT-001~009가 모두 통과했다. sync 이후 필수 로컬 검사에
  실패·차단 항목이 없다. 독립 검증 지적 3건(MEDIUM 1, LOW 1, NIT 1)을 반영했다.
- Tested scope: 전체 지우기 범위(본인·요청 시각 이전·UNREAD/READ), `read_at` 보존, 멱등성, 직후 목록·배지,
  DISMISSED 읽음 유지(도메인·서비스·DB), 보존 설정 기본값·거부, 30일 하한의 마이크로초 경계를 목록·`countUnread`·
  `existsUnseen` 모두에서 확인, cursor 순회, `/target` 회귀, 전체 지우기와 읽음 처리 동시 실행, HTTP 계약,
  생성 OpenAPI, 두 legacy 위반 해소.
- Full suites (sync 이후):
  - 실행 에이전트: `./gradlew test` 1,313개 실패·오류·skip 0. 집중 통합(`Notification*` 11개 클래스 +
    `OpenApiSpecificationIntegrationTest`) 81개 실패·오류·skip 0.
  - 실행 에이전트 `./harness pr-ready --project-tests`(지적 반영 후): PASS, 전체 통합 823개(822 + 새 INT-005 메서드)
    실패·오류·skip 0.
  - 부모 실행 증거(지적 반영 전): 전체 `./gradlew integrationTest` 822개 0/0/0, `./harness pr-ready --project-tests` PASS.
- Unverified scope: CI, 배포, 실제 운영 호출, 사람의 최종 리뷰.
- Release recommendation: 필수 로컬 검사가 통과했다. 사람 리뷰로 진행한다. 이 보고서는 자체 승인이 아니다.

## 2. Environment

런타임과 일반 환경 설명만 기록한다. 자격 증명과 운영 식별자는 포함하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain Java 21 (Temurin 21), launcher JVM Temurin 24 |
| Spring Boot | 3.5.16 |
| Gradle | Wrapper 8.14.3 |
| Database | PostgreSQL 16 + PostGIS 3.5 Testcontainers, synthetic fixtures |
| Test runner | JUnit 5, Gradle `test` / `integrationTest` source sets |
| Clock | 새 통합 테스트는 `@Primary` 고정 `Clock`(2026-08-20T06:00:00Z)으로 기준 시각과 보존 하한을 고정 |
| External APIs | 변경 없음. 기존 test 프로필의 `NoOpPushProvider` 사용 |

## 3. Execution results

### 최종 결과 (sync 이후, base `3387fcf0`)

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (실행 에이전트) | PASS | 1,313 / 0 / 0 / 0 | 35s | `NotificationDismissTest` 4/0, `NotificationRevokeTest` 4/0 |
| `integrationTest --tests 'com.dnd.qello.Notification*' --tests OpenApiSpecificationIntegrationTest` (실행 에이전트) | PASS | 81 / 0 / 0 / 0 | 1m 23s | `NotificationInboxDismissRetentionIntegrationTest` 8, OpenAPI 13 포함 |
| `./gradlew integrationTest` 전체 (부모 실행, 지적 반영 전) | PASS | 822 / 0 / 0 / 0 | — | 부모 보고 |
| `./harness pr-ready --project-tests` (부모 실행, 지적 반영 전) | PASS | — | — | 부모 보고 |
| `./harness check` (실행 에이전트, 지적 반영 후) | PASS | — | — | Harness checks passed |
| `git diff --check` + 미추적 신규 파일 공백 검사 (지적 반영 후) | PASS | — | — | 출력 없음 |
| `./harness pr-ready --project-tests` (실행 에이전트, 지적 반영 후) | PASS | 단위 1,313 / 0 / 0 / 0 (`test` UP-TO-DATE, 같은 입력의 직전 실행 결과), 통합 823 / 0 / 0 / 0 | 7m 42s | "Local PR readiness checks passed." `javaConventionCheck` 포함 |
| `npm run hooks:validate` (부모 실행) | PASS | — | — | 부모 보고 |

### 초기 기준선과 예상 RED (base `0be1e925`)

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| 기준선 `test --tests 'com.dnd.qello.notification.*'` | PASS | 243 / 0 | 9s | 로컬 JUnit XML |
| 기준선 `integrationTest` (`NotificationInbox*`, `NotificationFanOutExpansionIntegrationTest`) | PASS | 36 / 0 | 52s | 로컬 JUnit XML |
| RED 1: `compileTestJava` (테스트 선작성 직후) | EXPECTED RED | 컴파일 실패 4건 | 1s | `NotificationDismissal`, `NotificationDismissResponse`, `NotificationInboxProperties` 미존재 |
| RED 2: 구조만 추가 후 `test --tests 'com.dnd.qello.notification.*'` | EXPECTED RED | 261 / 3 failures | 4s | UNIT-001 ×2, UNIT-005 |
| RED 2: 구조만 추가 후 `integrationTest --tests 'NotificationInboxDismiss*'` | EXPECTED RED | 8 / 6 failures | 21s | INT-001·002·003·008 stub 예외, INT-005·006 하한 미적용 |
| GREEN: 집중 단위 / 집중 통합 | PASS | 261 / 0, 44 / 0 | 4s, 32s | 로컬 JUnit XML |

### Mutation 증거 (EXISTS_UNSEEN 보존 하한)

| Run | Mutant | Result | Evidence |
| --- | --- | --- | --- |
| 독립 검증자 | `EXISTS_UNSEEN`에서 `AND created_at > :retentionFloor` 제거 | 살아남음 — `NotificationInbox*` 전부 통과 | 검증자 보고(MEDIUM) |
| 실행 에이전트 (지적 반영 후) | 같은 한 줄 제거, `integrationTest --tests 'com.dnd.qello.NotificationInbox*'` | 제거됨 — 35개 중 1 실패: 새 INT-005 메서드, `NotificationInboxDismissRetentionIntegrationTest.java:181` `withoutSeenAt.hasUnseen()` "Expecting value to be false but was true" | 복원 후 `cmp` 동일, SHA-256 `db8035f6…c8ae` 전후 일치 |

같은 메서드의 첫 assertion(seen 기준선 없음)에서 실패해 뒤의 `seenAt` 있는 단언은 이 mutant로 따로 실행되지
않았다. 그 경우 기준선이 두 줄보다 이르므로 `created_at > :seenAt`이 참이 되어 `hasUnseen`이 true가 되고 같은
방식으로 실패한다(추론이며 별도 실행하지 않음).

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-001 | PASS | `NotificationDismissTest` 4개 메서드 | RED: DISMISSED가 READ로 바뀌던 2건 실패 확인 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-002 | PASS | `NotificationInboxServiceTest.dismissAllRejectsUnknownAccount`, `dismissAllRejectsIneligibleAccount` | repository 미호출 검증 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-003 | PASS | `NotificationInboxServiceTest.dismissAllUsesServerInstantAndReturnsCount` | |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-004 | PASS | `NotificationInboxServiceTest.listAndUnreadSignalShareRetentionFloor` | 기존 테스트의 list stub·verify에 하한 인자만 추가 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-005 | PASS | `NotificationInboxServiceTest.markReadSkipsUpdateForDismissedNotification` | RED: update 호출로 실패 확인 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-006 | PASS | `NotificationInboxPropertiesTest` 4개 메서드 | null→30일, P30D, PT0S·음수 거부 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-007 | PASS | `NotificationApiMockMvcTest.dismissAllReturnsCountAndInstant`, `dismissAllRequiresAuthentication`, `dismissAllRejectsIneligibleAccountWith403` | |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-UNIT-008 | PASS | `NotificationWebContractTest.declaresDismissAllAlongsideUnchangedList`, `dismissResponseContainsOnlyCountAndInstant` | 기존 경로 선언 테스트는 수정하지 않음 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-001 | PASS | `NotificationInboxDismissRetentionIntegrationTest.dismissAllTransitionsOnlyOwnUnreadAndReadCreatedUpToRequestInstant` | 요청 시각 정각 생성 UNREAD도 전이(`<=`) |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-002 | PASS | `...repeatedDismissAllIsIdempotent` | |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-003 | PASS | `...inboxIsEmptyRightAfterDismissAll` | 사전 조건(배지 2, hasUnseen true)도 확인 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-004 | PASS | `...markReadKeepsDismissedNotificationUnchanged` | RED 단계에서도 통과 — 계획 3절대로 기존 `status = 'UNREAD'` 가드의 회귀 검사 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-005 | PASS | `...retentionFloorIsExclusiveAtMicrosecondPrecision`, `...badgeIgnoresRowsOutsideRetentionWithAndWithoutSeenAt` | 하한 −1µs·정각·+1µs. 두 번째 메서드는 검증 지적으로 추가: 하한 밖 줄만 있을 때 seen 기준선 없음/더 이른 `seenAt` 모두 `hasUnseen` false, `unreadCount` 0. 3절 mutation 증거 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-006 | PASS | `...cursorPagingStopsAtRetentionFloor` | RED: 2쪽에 하한 이전 줄 섞임 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-007 | PASS | `...targetDecisionIgnoresDismissAndRetention` | RED 단계에서도 통과 — 회귀 검사 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-008 | PASS | `NotificationInboxDismissConcurrencyIntegrationTest.concurrentDismissAllAndMarkReadAlwaysEndDismissed` | 20회 반복, 반복마다 fixture reset, 매번 `dismissedCount` 1·최종 DISMISSED. 시작 latch 시간 초과를 실패로 처리 |
| TEST-PLAN-GH-332-NOTIFICATION-CLEAR-INT-009 | PASS | `OpenApiSpecificationIntegrationTest` (수정 없음, 13 tests) | 경로 변경은 `/api/v1/notifications`의 `delete` 추가뿐, 스키마는 `NotificationDismissResponse`·`ApiResponseNotificationDismissResponse` 추가만 |

기존 통합 테스트 3개(`NotificationInboxQueryIntegrationTest` 17, `NotificationInboxCommandIntegrationTest` 8,
`NotificationFanOutExpansionIntegrationTest` 10)는 하한 인자(기존 `NOW - 30일`)만 추가했고 모두 통과했다.

## 5. Failures and diagnostics

### 초기 예상 RED와 최종 결과의 구분

3절의 RED 1·RED 2는 테스트를 먼저 작성한 뒤 의도한 실패다. RED 2는 새 타입과 시그니처만 추가하고
`dismissAll` 구현을 `UnsupportedOperationException` stub으로 두고, 보존 필터·도메인 변경을 넣지 않은 상태에서
실행했다. 최종 결과에는 이 실패들이 남아 있지 않다.

### 해소된 환경 실패: sync 전 `origin/main` baseline 불일치

- 작업 중 `origin/main`이 #329(`1e66cbd3`)로 전진했다. 그 커밋이 `DirectionPostService`를 바꾸고 baseline 항목
  JAVA-CONV-0018 hash를 갱신하지 않아, base `0be1e925`에서 `./gradlew test`의 convention 테스트 4건
  (`JavaConventionBaselineTest` 2, `JavaStaticAnalysisRuleTest` 1, `ProductionConventionRatchetTest` 1)과
  `./harness test-run`, `pr-ready`가 통과하지 못했다. 네 건 모두 `DirectionPostService`(BASELINE-006, TX-001)와
  `AnswerModerationVerdictWorker`(TX-001)만 가리켰고, 변경 없는 HEAD의 임시 worktree에서도 같게 재현됐다.
- 사람 승인으로 브랜치를 `3387fcf0`(#331)에 sync했다. #331이 JAVA-CONV-0018을 삭제해 이 실패는 사라졌고, sync
  이후 `./gradlew test`는 1,313개 전부 통과했다.

### 독립 검증자의 `pr-ready` 첫 실행: Testcontainers 연결 시간 초과 (환경)

- 검증자의 첫 `./harness pr-ready --project-tests` 실행에서 6건이 `SocketTimeoutException`(Testcontainers 연결)으로
  실패했다. 대상은 `SchemaRevisionMigrationIntegrationTest`, `QuestionProposalDeleteMuteMigrationIntegrationTest`다.
- 재실행에서 통과했다. 이 변경이 건드리지 않는 마이그레이션 테스트이며 환경의 일시적 지연으로 판단한다.
- 남은 위험: 로컬 Docker 부하 시 같은 flaky 실패가 다시 날 수 있다. 원인 분석은 이 Issue 범위 밖이다.

## 6. Potential issues

### Application code

- 서비스에 클래스 단위 `@Transactional(readOnly = true)`를 추가했다. 변경 Service가 TX-001 ratchet 대상이 되기
  때문이다. 중복된 메서드 단위 `readOnly`는 제거했고 쓰기 메서드(`markSeen`, `dismissAll`, `markRead`)만
  `@Transactional`을 유지한다.
- `markRead`는 이제 도메인 `markRead`를 먼저 호출하고(REVOKED는 계속 `NOT-DOM-003`) UNREAD일 때만 `update`한다.
  READ 알림에 대한 동작은 기존과 같다.
- 보존 하한은 `list`에서 목록 기준 시각과 같은 `Instant`로 계산하고, `unreadSignal`은 별도로 한 번 읽는다.
  두 API 호출 사이의 시각 차이만큼 경계 알림의 노출이 다를 수 있으나 같은 호출 안에서는 일관된다.
- `list`·`unreadCount` API 설명에는 30일 숨김을 추가하지 않았다. 완료 조건 "생성 OpenAPI의 다른 경로가 바뀌지
  않는다"를 지키기 위해서다. 프론트 안내가 필요하면 후속 문서 변경으로 다룬다.

### Infrastructure and resource limits

- 새 리소스 없음. 보존 필터는 행을 지우지 않으므로 `notification` 테이블 크기는 계속 증가한다(물리 삭제·스위프는
  범위 밖).
- 5절의 Testcontainers 연결 시간 초과는 로컬 자원 상황에 따른 flaky 위험이다.

### Database and migrations

- 스키마·마이그레이션 변경 없음. INT-001이 `ck_notification_status`, `ck_notification_read_at`과 함께 통과했다.
- 전체 지우기 UPDATE의 술어 `recipient_id = ? AND status IN ('UNREAD','READ')`는 부분 인덱스
  `notification_recipient_feed_idx`의 조건과 같다. 실행 계획 고정은 범위 밖이다.
- 기존 INT-024(EXPLAIN에 부분 인덱스 사용)는 고정 SQL로 검사하므로, 하한을 추가한 목록 쿼리의 실행 계획은 검증하지
  않았다.

### Concurrency and idempotency

- INT-008: 20회 반복 모두 최종 DISMISSED, 두 호출 예외 없음. READ COMMITTED에서 늦게 도착한 UPDATE가 행 잠금
  해제 후 술어를 다시 평가하므로 어느 순서든 `dismissedCount`는 1이었다. 반복 횟수만으로 모든 interleaving을
  보장하지는 않는다.
- 요청 시각 이전 `created_at`을 가진 미커밋 fan-out 삽입은 전체 지우기 이후 커밋되면 UNREAD로 남는다. 계획 7절의
  허용 동작이며 이번 테스트로 재현하지 않았다.
- 전체 지우기는 재호출 시 0건이다(INT-002).

### Transactions and event ordering

- 전체 지우기는 UPDATE 한 번이라 부분 반영이 없다. 이벤트 발행이나 outbox 기록은 추가하지 않았다.

### External APIs

- 외부 API 변경 없음. push 발송·fan-out은 건드리지 않았다. 지운 알림의 미발송 push 전달은 취소하지 않는다
  (범위 밖).

### Failure recovery and reconciliation

- 복구는 변경 commit revert다. DISMISSED 행은 revert 후에도 목록에서 계속 제외되며 DB 복구 작업은 없다.
  보존 필터는 revert하면 즉시 해제된다.
- 설정 오류(0·음수)는 기동 단계에서 `IllegalArgumentException`으로 실패한다.

## 7. Regression and residual risk

### Legacy 위반 해소 (사람 결정, 2026-10-08)

- `JdbcNotificationRepository`(JAVA-CONV-0007): `com.dnd.qello.notification.domain.*`를 실제로 쓰는 12개 타입의
  명시 import로 바꿨다.
- `Notification`(JAVA-CONV-0014): compact constructor 검증을 `requirePositiveOwnerIds`, `requireMandatoryValues`,
  `requireValidTarget`(+ `isNonPositive`), `requireReadAtMatchesStatus`로 분리했다. 검사 순서, 오류 코드, field
  인자(`null`/`"readAt"`)와 메시지는 원문 그대로다. target ID 형식 검사가 개수 검사보다 먼저인 순서도 유지했다.
  `NotificationRevokeTest`, `NotificationDismissTest`, 전체 단위·통합 suite가 회귀를 확인했다. 생성자 예외 경로만
  따로 다루는 새 테스트는 추가하지 않았다(승인된 시나리오 밖).
- `config/java-conventions/baseline.json`: base `3387fcf0` 대비 JAVA-CONV-0007·0014 두 항목 삭제만 있다.
  JAVA-CONV-0018은 #331이 main에서 이미 삭제했다.
- 정리 직후 `checkstyleMain` 오류 0이었다. 생성된 suppression에 두 파일 항목이 없으므로 IMPORT-001·CPLX-001
  suppression 없이 통과한 것이다.

### 독립 검증 지적과 처리

| Severity | Finding | Resolution |
| --- | --- | --- |
| MEDIUM | INT-005가 `EXISTS_UNSEEN`의 보존 하한을 검증하지 못함 — 필터를 지운 mutant가 살아남음 | INT-005에 `badgeIgnoresRowsOutsideRetentionWithAndWithoutSeenAt` 추가. 같은 mutant가 이제 실패한다(3절). production 변경 없음 |
| LOW | `JdbcNotificationRepository`의 `update(OutboxEvent)` 주석이 formatter로 조각남 | 문구를 바꾸지 않고 세 줄로 다시 감쌌다. Eclipse formatter가 줄 주석을 80자에서 자르므로 이 문구를 두 줄에 담으면 다시 조각난다(직접 시도해 확인). 세 줄 형태는 `spotlessApply` 후에도 유지된다 |
| NIT | 동시성 테스트가 `start.await(...)` 반환값을 무시 | 시간 초과면 `IllegalStateException`을 던져 그 반복을 실패시킨다 |

### 커밋 hook의 staged Checkstyle 대응 (사람 결정, 2026-10-08)

- 원인: 두 번째 커밋의 pre-commit `checkstyleStagedJava`가 staged test source까지 검사해
  `NotificationFanOutExpansionIntegrationTest.cleanupFixtures`(119줄, `QELLO-JAVA-SIZE-001`)를 거부했다. HEAD에도 있던
  기존 위반이며 이 PR이 하한 인자 때문에 파일을 수정하면서 검사 대상이 됐다.
- 처리: `cleanupFixtures`를 `deleteNotificationFixtures`(28줄), `deleteOutboxFixtures`(28줄),
  `deleteAnswerAndQuestionFixtures`(44줄), `deletePostAndAccountFixtures`(17줄) 호출로 나눴다. FK 순서를 바꾸지
  말라는 주석 한 줄을 붙였다. 분리 전후의 `jdbc.update` 20개 문장은 내용과 순서가 같다(스크립트로 비교).
  다른 수정은 없다.
- 결과: 이 PR에서 바뀐 Java 23개(staged·미추적 신규 포함, production·test 모두)에 저장소 `checkstyle.xml`로
  `checkstyleStagedJava`를 실행해 위반 0건. `spotlessJavaCheck` PASS.
  `integrationTest --tests NotificationFanOutExpansionIntegrationTest` 10 / 0 / 0 / 0.

### Formatter diff

- Spotless ratchet 때문에 수정한 Java 파일 전체가 formatter profile로 재정렬됐다(#323과 같은 방식).
  `JdbcNotificationRepository`(+398/−376), `NotificationSql`(+287/−272), `NotificationFanOutExpansionIntegrationTest`
  (+218/−204)의 diff가 크다. 공백 무시 diff는 `JdbcNotificationRepository` +40/−18, `Notification` +31/−5
  (formatter 줄바꿈 포함)이다. text block 들여쓰기 변경은 SQL 내용을 바꾸지 않으며 통합 suite로 확인했다.
- 30일이 지난 알림이 기존 응답에서 빠지므로 오래된 알림을 기대하는 프론트 화면은 영향을 받는다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-332-TEST-PLAN-GH-332-NOTIFICATION-CLEAR.md`
- Generated OpenAPI: `docs/api/openapi.json` (`OpenApiSpecificationIntegrationTest`로 생성, 직접 편집 없음)
- CI run: 없음(로컬 실행만)
- Related ADR: 없음
- PR: 없음(커밋·PR 미생성)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (mutation 증거의 `seenAt` 단언 단독 실행은 미실행으로 표시)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (Testcontainers flaky는 범위 밖, 사람 판단)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 미생성)

지적 반영 후 실행 에이전트의 마지막 `./harness check`, `git diff --check`, `./harness pr-ready --project-tests`는
모두 통과했다(3절).
