# Test Report: TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE

> Created at: `2026-08-18T00:20:00+09:00`
> GitHub Issue: `#112`
> Branch: `feat/gh-112-appeal-and-manual-restore`
> Commit: 이 보고서를 포함한 커밋 직전 기준 `origin/main` 2bfec4c 위의 작업 브랜치

## 1. Executive summary

- Result: `PASS`
- Tested scope: 이의제기 접수(소유권·대상 유형·중복·접수 기간·fallback), 검토자
  결정(`UPHOLD_HIDDEN`/`OVERTURN_HIDDEN`, 공개 금지 사유 재검증, 복원 콜백 발행
  조건), 접수 기간 연장·단축 금지, 조회 경로 분리, V18 마이그레이션과
  `outbox_event` 계약 확장, 운영자 endpoint 인가. 접수·결정 경합은 실제
  PostgreSQL에서 두 스레드로 재현했다.
- Unverified scope: `MODERATION_APPEAL_RESOLVED` 이벤트의 **소비**(답변
  `moderationStatus`와 공개 상태 복원)는 이슈 범위 밖이라 검증하지 않았다.
  발행된 이벤트가 `PENDING`으로 남는 것까지만 확인했다. `NICKNAME` 대상은
  거절 동작만 확인했고 그 이상은 다루지 않았다.
- Release recommendation: 병합 가능. 다만 이 변경만으로는 사용자에게 보이는
  복원이 일어나지 않는다 — 복원 콜백 소비자가 붙기 전까지 `OVERTURN_HIDDEN`은
  이벤트만 남긴다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 17.0.8 LTS |
| Spring Boot | 3.5.16 |
| Database | Testcontainers로 기동한 PostGIS 16 계열 테스트 컨테이너 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 537 (실패 0, 건너뜀 0) | 약 17초 | `build/test-results/test` |
| `./gradlew integrationTest` | PASS | 415 (실패 0, 건너뜀 0) | 약 5분 39초 | `build/test-results/integrationTest` |
| `./harness check` | PASS | 민감정보 873파일·JUnit 정책 141파일·컨벤션·workflow·label·Husky | — | 명령 출력 |
| `git diff --check` | PASS | — | — | 공백 오류 없음 |

이번 이슈가 추가한 테스트는 단위 20건(`AppealCaseTest` 15,
`AppealCaseServiceTest` 5), 통합 15건(`AppealCaseIntegrationTest`)이다. 기존
테스트 4개 파일은 확장된 계약에 맞춰 갱신했다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `AppealCaseTest.rejectsAcceptanceWindowShorterThanSixMonths` | 183일 거절, 184·365일 허용 |
| UNIT-002 | PASS | `AppealCaseTest.acceptsFilingWithinWindow` | |
| UNIT-003 | PASS | `AppealCaseTest.rejectsFilingAfterWindowElapsed` | |
| UNIT-004 | PASS | `AppealCaseTest.acceptsFilingWhenWindowStartIsUnknown` | fallback |
| UNIT-005 | PASS | `AppealCaseTest.acceptsFilingWhenWindowStartIsInTheFuture` | fallback |
| UNIT-006 | PASS | `AppealCaseTest.fixesExpiryAtFilingTime` | |
| UNIT-007 | PASS | `AppealCaseTest.fixesExpiryFromFilingTimeOnFallback` | |
| UNIT-008 | PASS | `AppealCaseTest.refusesToFileRejectedAcceptance` | 계획의 "거절된 접수는 case가 되지 않는다" |
| UNIT-009 | PASS | `AppealCaseTest.refusesToDecideResolvedCase` | |
| UNIT-010 | PASS | `AppealCaseTest.resolvesWithUpholdDecision` | |
| UNIT-011 | PASS | `AppealCaseTest.requiresRestoreCallbackOnlyWhenNotBlocked` | |
| UNIT-012 | PASS | `AppealCaseTest.refusesRestoreBlockedReasonOnUphold` | |
| UNIT-013 | PASS | `AppealCaseTest.extendsExpiryOnly` | 이른 시각·동일 시각 모두 거절 |
| UNIT-014 | PASS | `AppealCaseTest.validatesRequiredValues` | |
| UNIT-015 | PASS | `AppealCaseTest.refusesInconsistentRestoredState` | 3가지 부정 조합 |
| UNIT-016 | PASS | `AppealCaseServiceTest.rejectsUnsupportedTargetType` | |
| UNIT-017 | PASS | `AppealCaseServiceTest.rejectsFilingByNonOwner` | 계획 추가분 |
| UNIT-018 | PASS | `AppealCaseServiceTest.doesNotEmitRestoreCallbackWhenPublicationIsBlocked` | 계획 추가분 |
| UNIT-019 | PASS | `AppealCaseServiceTest.emitsRestoreCallbackWhenPublicationIsClear` | 계획 추가분 |
| UNIT-020 | PASS | `AppealCaseServiceTest.upholdSkipsPublicationBlockCheckAndCallback` | 계획 추가분 |
| INT-001 | PASS | `AppealCaseIntegrationTest.appliesAppealCaseSchemaAndOutboxContract` | 기존 12개 + 신규 event type 전부 삽입 검증 |
| INT-002 | PASS | `AppealCaseIntegrationTest.filingDoesNotChangePublicationState` | `INV-APL-003` |
| INT-003 | PASS | `AppealCaseIntegrationTest.rejectsDuplicateFiling` | `INV-APL-002` |
| INT-004 | PASS | `AppealCaseIntegrationTest.concurrentFilingKeepsUniqueness` | 2스레드 경합 |
| INT-005 | PASS | `AppealCaseIntegrationTest.rejectsFilingByNonAuthor` | |
| INT-006 | PASS | `AppealCaseIntegrationTest.rejectsFilingAfterWindowElapsed` | |
| INT-007 | PASS | `AppealCaseIntegrationTest.acceptsFilingWhenWindowStartIsUnverifiable` | |
| INT-008 | PASS | `AppealCaseIntegrationTest.overturnEmitsRestoreCallback` | |
| INT-009 | PASS | `AppealCaseIntegrationTest.overturnWithBlockedAccountSuppressesRestoreCallback` | 계정 `BLOCKED` |
| INT-010 | PASS | `AppealCaseIntegrationTest.upholdEmitsNoCallback` | |
| INT-011 | PASS | `AppealCaseIntegrationTest.concurrentDecisionResolvesOnce` | 행 잠금, 콜백 1건 |
| INT-012 | PASS | `AppealCaseIntegrationTest.neverShortensAcceptanceWindow` | 서비스·DB 양쪽 거절 |
| INT-013 | PASS | `AppealCaseIntegrationTest.expiredAppealDoesNotBlockNewSubmission` | `INV-APL-011` |
| INT-014 | PASS | `FilteringPersistenceIntegrationTest.enforcesAppealCaseUniquenessAndIdempotentLookup` | 기존 계약 회귀 |
| INT-015 | PASS | `AppealCaseIntegrationTest.reviewerEndpointsRequireOperatorSession` | 401 후 세션으로 200 |
| INT-016 | PASS | `AppealCaseIntegrationTest.findsOnlyOwnAppeals` | 계획 추가분 |

## 5. Failures and diagnostics

최종 실행에서 실패한 테스트는 없다. 구현 도중 발생했다가 해결한 실패는 셋이다.

1. **정적 import 충돌.** 통합 테스트의 헬퍼 메서드 `post(long)`이
   `MockMvcRequestBuilders.post`를 가려 컴파일이 깨졌다. 인스턴스 메서드가 정적
   import보다 우선하기 때문이다. 헬퍼를 `directionPost(long, String)`으로
   바꿔 해결했다.
2. **`uq_answer_one_per_recipient` 위반.** INT-013이 같은 recipient slot에 두
   번째 답변을 만들려 했다. 답변은 slot당 하나라는 기존 제약을 몰랐던 것이며,
   "만료 뒤 새 콘텐츠 제출"을 새 slot에서 확인하도록 고쳤다. 제품 코드의
   결함이 아니라 테스트 fixture의 문제였다.
3. **값 집합 계약 테스트 2건.** `FlywayMigrationContractTest`(마이그레이션 파일
   목록)와 `SafetyNotificationBoundaryTest`(`OutboxEventType` 값 집합)가 신규
   V18과 `MODERATION_APPEAL_RESOLVED`를 몰라 실패했다. 두 테스트는 "값이 늘어난
   것을 사람이 인지했는가"를 묻는 장치이므로, 값을 추가하는 방향으로 갱신했다.

## 6. Potential issues

### Application code

- `AppealCaseService.file`은 소유권 → decision 조회 → 판정 종류 → 중복 → 접수
  기간 순으로 검사한다. 순서를 바꾸면 남의 콘텐츠에 대해 "그 decision이
  존재하는가"를 응답 코드로 알려주게 된다. 순서 자체를 고정하는 테스트는
  UNIT-016·UNIT-017의 `verifyNoInteractions`뿐이므로, 이후 리팩터링 시 이
  단언이 사라지면 정보 노출이 조용히 되살아날 수 있다.
- `restore_blocked_reason_code`가 문자열이라 오타나 30자 초과가 런타임에야
  걸린다. 도메인이 길이·공백을 검증하지만, 사유 코드의 **집합**은 검증하지
  않는다. 사유 목록이 확정되면 열거형으로 좁히는 편이 낫다.

### Infrastructure and resource limits

- 검토자 큐(`findOpenQueue`)는 `limit` 상한이 없다. 호출자가 큰 값을 넘기면 OPEN
  case 전체를 한 번에 읽는다. 현재는 운영자 전용 경로라 위험이 작지만,
  `manual_review_case` 큐와 달리 상한이 없다는 점은 기록해 둔다.

### Database and migrations

- V18은 `appeal_case`에 기존 행 보정 없이 NOT NULL 컬럼을 추가한다. 저장소
  전체에서 `AppealCase`를 저장하는 프로덕션 경로가 없다는 전제(V16이
  `manual_review_case`에 쓴 것과 같은 논리)에 의존한다. 이 전제가 깨진 환경에
  적용하면 마이그레이션이 실패한다.
- `outbox_event`의 두 CHECK를 drop 후 재생성하므로, 재생성 목록에서 값이 빠지면
  해당 기능의 이벤트 발행이 전면 중단된다. INT-001이 기존 12개 event type을
  모두 다시 삽입해 이 사고를 막는다.

### Concurrency and idempotency

- 접수 경합은 `uq_appeal_case_target_decision`이, 결정 경합은
  `findByIdForUpdate` 행 잠금이 직렬화한다. 둘 다 2스레드로 재현했다.
- `AppealCaseService.file`은 사전 조회로 중복을 거르고 유일성 위반도 잡아
  같은 오류로 변환한다. 조회만 있으면 경합에서 `DataIntegrityViolationException`이
  그대로 새어 나가 500이 된다.

### Transactions and event ordering

- 결정 트랜잭션이 case 갱신과 outbox 삽입을 함께 커밋한다. 복원 콜백만 나가고
  case가 열린 채 남는 상태는 생기지 않는다.
- `PublicationBlockChecker`가 예외를 던지면 트랜잭션 전체가 롤백된다. "확인하지
  못했다"를 "차단이 없다"로 해석하지 않는 fail-closed 설계이며, 어댑터도 판단
  불가 시 빈 값 대신 차단 사유를 돌려준다.

### External APIs

- 외부 API 호출이 없다. 두 포트는 같은 데이터베이스를 읽는 인프로세스 어댑터다.

### Failure recovery and reconciliation

- 발행된 `MODERATION_APPEAL_RESOLVED`는 소비자가 없어 `PENDING`으로 남는다.
  기존 `RecipientNotificationFanOutWorker`처럼 소비자가 붙기 전까지는 이 행이
  계속 쌓인다는 뜻이므로, 소비자 배선 전에 적재량을 확인하는 편이 좋다.

## 7. Regression and residual risk

- `AppealCase.file`/`restore` 시그니처가 바뀌어 기존 호출부 2곳
  (`FilteringValueObjectsTest`, `FilteringPersistenceIntegrationTest`)을
  갱신했다. 두 테스트 모두 통과하며 기존 유일성 계약은 그대로다.
- `OutboxEventType`·`OutboxAggregateType`에 값을 추가했다. 두 enum을 `switch`로
  전수 분기하는 코드는 없어 컴파일 영향이 없었고, 값 집합을 단언하는 계약
  테스트만 갱신했다.
- 남은 위험: 이의제기 남용 제한(rate limit)이 없다. 작성자는 자신의 BLOCK
  판정마다 appeal 하나를 만들 수 있고 그 이상은 막히지만, 판정 수만큼 case가
  생기는 것 자체는 제한되지 않는다. 이슈 본문이 "악용 제한"을 미결정으로
  제외했으므로 이번 범위에서 다루지 않았다.
- 남은 위험: 접수 기간을 184일로 근사했다. 실제 달력 기준 6개월보다 최대 3일
  길어질 수 있다. 하한을 지키는 방향이라 작성자에게 불리하지 않지만, 정확한
  달력 계산이 필요해지면 `AppealWindow`를 `Period` 기반으로 바꿔야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-112-TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE.md`
- CI run: 로컬 실행 결과만 존재한다. GitHub Actions 실행은 PR 생성 후 확인한다.
- Related ADR: 없음. 설계 판단은 `TASK.md`의 Design decisions 절에 기록했다.
- PR: 본 브랜치의 Pull Request

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (복원 콜백 소비, `NICKNAME` 대상 상세 동작)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 아직 생성하지 않았다. 악용 제한과
      복원 콜백 소비자 배선은 별도 이슈가 필요하다.
- [x] 실행 결과와 PR 설명이 일치함
