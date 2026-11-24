# Test Report: TEST-PLAN-GH-381-LOCATION-CONSENT

> Created at: `2026-11-23T23:02:54+09:00`
> GitHub Issue: `#381`
> Branch: `feat/gh-381-location-consent-history`
> Commit: `c1d00f82`
> 2026-11-24 rebase 및 V35 재검증 결과는 §10에 기록한다. 위 commit과 §3은 최초 구현 검증 이력이다.

## 1. Executive summary

- Result: `PASS` — 단위·통합, harness test-run/check/pr-ready, hooks 및 공백 검사가 통과했다.
- status: `PASS`
- Tested scope: 동의·철회 append-only 원장, 사용자 전체 최신 id 판정, 본인 인증 API, 위치 세 API의 B1/동의 우선 검사, 실제 PostGIS 잠금·rollback·탈퇴 경합, YAML 설정, OpenAPI 및 기존 회귀.
- Unverified scope: 모바일 앱, 실제 배포, 실제 외부 제공자, 별도 performanceTest 태스크. 필수 검증의 최종 상태는 §3에 기록한다.
- Release recommendation: 구현 검증과 운영 출시 승인은 별개다. D2 보존 정책과 D6 모바일 동의 화면 출시·서버 병합 순서가 확정되기 전 운영 출시는 보류한다.
- issue_number: `381`
- task_id: `TASK.md / TEST-PLAN-GH-381-LOCATION-CONSENT`
- design_id: 해당 없음. 승인 계획 §3의 D1~D8을 적용했다.
- assumptions: D5 원안보다 승인 기록을 우선했다. 약관 버전은 리뷰한 YAML 리터럴이며 dev/local은 `dev-v1`, test는 `test-v1`이다.
- required_human_decisions: D2 보존 목적·기간·기산점·파기/익명화·접근권한, D6 모바일 출시 및 병합 시점, 운영 게시 약관 버전.
- executed_checks / passed_checks: §3의 모든 필수 명령. failed_checks: 최종 결과 없음(수정한 과거 실패는 §5). blocked_checks: 구현 인수 범위에는 없음.
- risks: §6. 운영 출시 조건은 구현 검증 PASS로 승인된 것으로 간주하지 않는다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.13, Gradle 실행 시 JAVA_HOME을 Java 21로 지정 |
| Spring Boot | 3.5.16 |
| Database | 로컬 Docker Testcontainers PostgreSQL 16.14 / PostGIS 16-3.5-alpine |
| Test runner | JUnit 5 / Gradle |
| Docker | 29.6.2 |

`gh`와 `harness`는 SSL_CERT_FILE·SSL_CERT_DIR을 해제한 환경으로 실행했다.
공유 build 산출물 충돌을 피하기 위한 focused 실행만 별도 임시 buildDir을 사용했다.

## 3. Execution results (2026-11-23 최초 구현)

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew compileJava --console=plain` | PASS, exit 0 | 해당 없음 | 6초 | production 컴파일 |
| `./gradlew spotlessApply --console=plain` | PASS, exit 0 | 해당 없음 | 최종 931ms | 생성 시각·Source scenario 헤더 유지 확인 |
| `./gradlew test --console=plain` | PASS, exit 0 | 1,602 / 실패 0 / 오류 0 / skip 0 | 35초 | `build/test-results/test/TEST-*.xml`, 211 suites |
| `./gradlew integrationTest --console=plain` | PASS, exit 0 | 930 / 실패 0 / 오류 0 / skip 0 | 최종 2분 44초 | `build/test-results/integrationTest/TEST-*.xml`, 118 suites |
| `./gradlew integrationTest --tests "*OpenApiSpecificationIntegrationTest" --console=plain` | PASS, exit 0 | 14 | 17초 | 실제 springdoc 생성 및 계약 검증; 전체 통합에도 포함 |
| `./harness test-run --id TEST-PLAN-GH-381-LOCATION-CONSENT` | PASS, exit 0 | 단위 1,602 + 통합 930 | 단위 35초 / 통합 up-to-date 672ms | 실행 후 이 보고서 scaffold 생성 |
| `./harness check` | PASS, exit 0 | JUnit 정책 362개 파일 검사 | 해당 없음 | preflight·JUnit·convention·workflow·label·Husky·baseline 검사 |
| `./harness pr-ready --project-tests` | PASS, exit 0 | `check` 포함 | Gradle 6초 | production Checkstyle·Java source/architecture convention·spotless·test·integrationTest 통과 |
| `npm run hooks:validate` | PASS, exit 0 | 해당 없음 | 해당 없음 | Husky validation passed |
| `git diff --check` | PASS, exit 0 | 해당 없음 | 해당 없음 | 전체 변경 공백 검사 |

새 동의 단위 클래스 실행 수는 domain 4, service 18, properties 18, error 2, MVC 4로 총 46개다.
기존 두 서비스 테스트에도 게이트·동의·정상 경로 검증을 추가했다.
새 통합 클래스는 Persistence 5, API 15, Concurrency 17, Configuration 9로 총 46개다.
계획의 단위 10개·통합 15개는 시나리오 그룹 수이며 실행 테스트 수와 다르다.
pr-ready에서 로컬 stacked base가 다른 worktree에 checkout되어 fast-forward fetch가 거부됐지만 원본은 그대로 보존됐다.
원격 base의 ancestor 검사는 통과했고 최종 `Local PR readiness checks passed`와 exit 0을 확인했다.
기존 저장소 설정상 checkstyleTest/checkstyleIntegrationTest 태스크는 비활성이다. 이 설정은 변경하지 않았으며 JUnit skip 수는 두 suite 모두 0이다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `LocationConsentTest.permitsOnlyConsentToCurrentVersion`; `LocationConsentServiceTest.resolvesLatestStateAndEnforcesConsent`, `rejectsLatestWithdrawalDespiteOlderCurrentConsent` | 무기록·철회·이전/현재 버전·동률/역행 시각 |
| UNIT-002 | PASS | `LocationConsentServiceTest.rejectsVersionWithoutAppending` | 버전 불일치·빈 값·공백·길이 위반 시 append 없음 |
| UNIT-003 | PASS | `LocationConsentServiceTest.repeatedConsentAppendsWithServerClockAndPreservesPreviousEvent` | 재동의 추가, 이전 객체 불변, 고정 Clock |
| UNIT-004 | PASS | `LocationConsentServiceTest.withdrawalAppendsSelectedVersionAndDeletesOwnPresence` | 무기록·동의·기철회 버전과 본인 위치 삭제 |
| UNIT-005 | PASS | `DirectionPresenceServiceTest.rejectsBeforeAccountLookupWhenLocationCollectionGateIsDisabled`; `DirectionPostApplicationServiceTest.previewRejectsBeforeAnyLookupWhenLocationCollectionGateIsDisabled`, `submitRejectsBeforeReplayWhenLocationCollectionGateIsDisabled` | B1 우선 거부 |
| UNIT-006 | PASS | `DirectionPresenceServiceTest.rejectsMissingConsentBeforePresenceAccess`; `DirectionPostApplicationServiceTest.previewRejectsMissingConsentBeforeAnyDataAccess`, `submitRejectsMissingConsentBeforeReplayOrSend` | 미동의 후속 접근·replay·send 없음 |
| UNIT-007 | PASS | `DirectionPresenceServiceTest.updatesPresenceWithServerOwnedRegionAndExpiration`; `DirectionPostApplicationServiceTest.previewUsesServerAuthoritativeActorAndScheme`, `submitUsesServerPolicyAndClock`, `replaysExistingRequestBeforeCurrentPolicyValidation` | 정상 경로와 기존 멱등성 유지 |
| UNIT-008 | PASS | `LocationConsentPropertiesTest.acceptsValidConfiguration`, `rejectsInvalidConfigurationWithoutExposingValue` | B1 on/off와 1~100자 경계 |
| UNIT-009 | PASS | `LocationConsentErrorCodeTest.exposesApprovedConsentErrorMappings`, `directionErrorCodesAreUnique` | 403 DIR-APP-009 / 409 DIR-APP-010, wire code 유일성 |
| UNIT-010 | PASS | `LocationConsentControllerMockMvcTest.consentUsesAuthenticatedSubjectAndServerTime`, `withdrawalIgnoresExternalTarget`, `currentReadsOnlyAuthenticatedSubjectWithoutAppend`, `rejectsUnauthenticatedRequestsAcrossAllRoutes` | 본인 subject·공통 200·401 |
| INT-001 | PASS | `LocationConsentPersistenceIntegrationTest.migratesFreshAndV34WithoutChangingHistory` | 빈 DB/V34→V35, checksum·FK·NOT NULL·CHECK·인덱스·RESTRICT |
| INT-002 | PASS | `LocationConsentPersistenceIntegrationTest.appendsEverySuccessfulRequestAndKeepsEarlierRows`, `withdrawalChoosesLatestVersionOrCurrentWhenEmpty` | 요청별 행 추가, 이전/타인 행 불변, GET 무쓰기 |
| INT-003 | PASS | `LocationConsentPersistenceIntegrationTest.latestIdWinsOverVersionAndRecordedTime` | 전체 최신 id 우선, 과거 동의 부활 방지 |
| INT-004 | PASS | `LocationConsentApiIntegrationTest.rejectsPresenceWithoutCurrentConsent` | 세 미동의 상태 × 기존 presence 유무, 전체 snapshot 불변 |
| INT-005 | PASS | `LocationConsentApiIntegrationTest.rejectsPreviewAndExistingReplayBeforeSideEffects` | B1 on/off·기존 replay·재시도·7개 테이블 snapshot |
| INT-006 | PASS | `LocationConsentApiIntegrationTest.currentConsentAllowsAllApisAndIdempotentReplay` | PUT·preview·submit·멱등 replay |
| INT-007 | PASS | `LocationConsentConcurrencyIntegrationTest.withdrawalCommitsHistoryAndDeletionTogether`, `withdrawalFailureRollsBackBothResources` | 실제 DB INSERT/DELETE trigger 실패와 독립 connection 확인 |
| INT-008 | PASS | `LocationConsentConcurrencyIntegrationTest.consentAndWithdrawalSerializeFromEitherOrder` | 최초/기존 이력, 양쪽 잠금 순서, 서로 다른 connection |
| INT-009 | PASS | `LocationConsentConcurrencyIntegrationTest.updateAndWithdrawalLeaveNoPresence` | 양쪽 순서·presence 유무, 철회 후 좌표 없음 |
| INT-010 | PASS | `LocationConsentConcurrencyIntegrationTest.accountWithdrawalAndUpdateCannotResurrectPresence`, `accountLifecyclePreservesLedgerWithoutRestoringPresence` | 탈퇴 경합·취소·완료, 원장 보존 |
| INT-011 | PASS | `LocationConsentApiIntegrationTest.authenticationSubjectAndValidationProtectHistory`, `rejectsIneligibleAccountsWithoutWritingHistory` | 인증·위조 대상·버전·본문·정보 비노출 |
| INT-012 | PASS | `LocationConsentConfigurationIntegrationTest.realProfileYamlBindsReviewedVersion`, `missingDefaultVersionDependsOnCollectionGate`, `invalidVersionNeverStartsEnabledCollection`, `bootedTestProfileStillRejectsExistingNonconsentingUser` | 실제 YAML binding·기동 실패/성공·미동의 거부 |
| INT-013 | PASS | `OpenApiSpecificationIntegrationTest.documentsLocationConsentContract` 및 기존 생성/결정성 테스트 | 세 API·인증·DTO·403/409·기존 위치 403/503, 생성물 갱신 |
| INT-014 | PASS | `LocationConsentConcurrencyIntegrationTest.retryAfterRollbackLeavesOneCommittedEvent`, `retryAfterLostResponseAppendsAgain`; API 거부 검증 | rollback 뒤 1행, commit 뒤 재요청 2행, 외부 호출·outbox 없음 |
| INT-015 | PASS | §7 기존 회귀 클래스 및 `QelloLocalProfileIntegrationTest.localProfileRequiresExplicitConsent` | 전체 단위/통합 회귀 통과, 성능 태그는 별도 미실행 |

위 ID 앞의 공통 접두사는 `TEST-PLAN-GH-381-LOCATION-CONSENT-`다.

## 5. Failures and diagnostics

최초 실패를 수정한 뒤 최종 전체 suite를 다시 실행했다. 실패를 skip하거나 assertion을 약화하지 않았다.

1. 단위 최초 실행의 V34 목록/최신 버전 기대값과 자체 MVC fixture를 보정했다. 마지막 전체 단위는 1,602개 모두 통과했다.
2. focused 통합 최초 실행에서 기존 submit의 202 응답에 맞게 fixture 기대값을 고쳤고, 설정 context bean 충돌과 Flyway schema 생성 이력 비교를 보정했다. 새 통합 46개 및 변경 회귀 46개 targeted 실행이 통과했다.
3. `harness check` 최초 exit 1: 새 통합 클래스 4개의 `Created at`에 소수초가 있었다. 정책의 초 단위 ISO 8601 형식으로 보정했고 검사 자체는 변경하지 않았다. 이후 exit 0.
4. 전체 통합 최초 exit 1: 930개 중 2개 실패.
   - `AccountPersistenceIntegrationTest.startsWithFlywaySchemaValidationOnly`: expected 54, actual 55. V34의 새 테이블에 맞춰 기대값만 55로 보정했다.
   - `AccountWithdrawalConcurrencyIntegrationTest.cancelAndCompletionRaceLeavesOneConsistentState`: round 1의 cancel-first 검증 실패. 새 adapter가 탈퇴 완료 `updateDeletion`에도 불필요한 조기 잠금/flush를 추가해 기존 cleanup 전 경합 순서를 바꿨다. 해당 두 줄만 제거해 기존 완료의 낙관적 충돌 계약을 보존했다. 위치 갱신·동의·철회 및 탈퇴 요청의 잠금/flush는 유지했다. 기존 경쟁 테스트는 수정하지 않았다.
   - 수정 후 전체 통합 재실행: exit 0, 930개 모두 통과. 새 INT-007~010도 함께 재검증했다.

원문 로그는 환경 식별자를 포함할 수 있어 보고서에 복사하지 않는다.

## 6. Potential issues

### Application code

- D6: dev에도 자동 동의는 없다. 서버 enforcement 배포 즉시 기존 무동의 앱의 세 위치 API는 403이 된다. 모바일 동의 화면/API 계약과 출시 확인 후 병합해야 한다.
- D5: 기본 프로필은 B1 off·미게시 버전으로 기동할 수 있다. 이 상태에서 동의 생성 또는 무이력 철회를 호출하면 구성 검증의 IllegalStateException이 일반 500 응답으로 처리될 수 있다. 운영 게시 버전을 먼저 설정해야 하며, 미게시 동의 API에 별도의 정상 오류 계약이 필요한지는 제품 결정 대상이다. 이 분기의 HTTP 테스트는 이번 승인 시나리오에 포함되지 않았다.
- 존재하지 않는 계정의 presence는 이력이 없으므로 새 동의 우선 검사에서 403이 된다. ApiSpec의 기존 404 선언은 도달할 수 없는 경로로 남아 있으며 실제 우선순위는 회귀 테스트로 확인했다.

### Infrastructure and resource limits

- 인프라 변경·배포는 수행하지 않았다. Testcontainers 환경에서만 검증했다.
- D7은 같은 계정의 상태 변경과 위치 요청을 직렬화한다. 긴 트랜잭션·풀 고갈·대기 시간은 실제 트래픽에서 관측해야 한다. 이번 테스트는 잠금 안전성을 검증하며 운영 부하/성능을 보장하지 않는다.

### Database and migrations

- D2: 계정 soft-delete 후 FK와 원장을 보존하는 구조만 승인됐다. 무기한 보존 승인이 아니다. 목적·기간·기산점·접근권한·만료 파기/익명화 정책은 운영 출시 전 확정해야 한다. 원장 변경/삭제는 별도 승인 범위다.
- V34는 기존 migration을 수정하지 않는다. 빈 schema와 V33 upgrade, 기존 checksum, FK RESTRICT와 제약 위반을 검증했다.

### Concurrency and idempotency

- D7 순서는 HTTP 도착 시각이나 recorded_at이 아니라 계정 행 잠금 획득 순서다. 최신 상태는 잠금 이후 새 조회로 판정한다.
- 사전 등록 latch와 독립 connection의 `FOR UPDATE NOWAIT`가 실제 잠금 보유를 확인한다. 동의/잠금 데이터 mock이나 sleep/polling으로 경합을 대체하지 않았다.
- 같은 요청의 재전송은 성공마다 원장 행을 추가한다. 상태만 멱등이며 이벤트 dedup을 제공하지 않는다.

### Transactions and event ordering

- 철회 append와 presence 삭제는 같은 DataSource/실제 서비스 transaction이다. 각각의 DB 실패 후 양쪽 rollback과 타인 행 불변을 독립 connection으로 검증했다.
- D8: 철회 commit 뒤 진입한 preview/submit은 replay도 거부한다. 이미 동의를 확인하고 진행 중인 발송까지 취소하는 보장은 없다. 더 강한 취소 보장은 별도 트랜잭션 설계가 필요하다.
- 탈퇴 완료 경로의 기존 낙관적 잠금은 유지했다. 탈퇴 요청은 상태 변경을 cleanup 전에 flush하여 이후 위치 쓰기가 ACTIVE 상태로 통과하지 못하게 한다.

### External APIs

- 약관 홈페이지를 호출하거나 OS 위치 권한을 대신 판단하지 않는다.
- 테스트는 기존 외부 제공자 double을 유지했다. 거부·철회가 새 push 호출/outbox를 만들지 않는 것을 검증했다. 실제 FCM 등 외부 제공자의 가용성은 미검증이다.
- 이미 큐에 있던 발송 작업을 철회 시 취소하는 기능은 이번 범위 밖이다.

### Failure recovery and reconciliation

- commit 전 실패 후 재요청은 성공 행 1건, commit 후 응답 유실 뒤 재요청은 2건으로 검증했다. 과거 원장을 수정해 중복을 지우지 않는다.
- 장애 시 B1을 닫고 승인된 수정 배포로 복구한다. 동의 우회·기존 dev 사용자 자동 동의·적용된 migration/원장 되돌리기는 복구 수단이 아니다.
- 버전 변경은 리뷰한 YAML 변경과 재배포로 수행한다. 혼합 버전 인스턴스 기간에는 409 또는 위치 차단이 발생할 수 있다.

## 7. Regression and residual risk

계획 §9 추가 승인을 적용했다. 아래 기존 테스트 변경은 V34 기대값·명시적 동의 fixture·FK 정리 순서 보정이다. 기본 사용자 생성에 실제 동의를 자동 생성하는 production 변경은 없다.

| 파일 (기준: `src/`) | 변경 이유 |
| --- | --- |
| `test/java/com/dnd/qello/FlywayMigrationContractTest.java` | V34 파일 목록·최신 버전 기대값 |
| `test/java/com/dnd/qello/direction/service/DirectionPresenceServiceTest.java` | 잠금 port fixture 및 B1/동의/정상 경로 |
| `test/java/com/dnd/qello/direction/service/DirectionPostApplicationServiceTest.java` | 명시적 동의 collaborator와 B1/동의/replay 검증 |
| `integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java` | 최신 migration 33→34 |
| `integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java` | V24 이후 적용 수 9→10 |
| `integrationTest/java/com/dnd/qello/AccountPersistenceIntegrationTest.java` | 전체 테이블 수 54→55 |
| `integrationTest/java/com/dnd/qello/ActiveUserPresenceApiIntegrationTest.java` | 정상/계정 자격 경로 동의 fixture, 이력 정리, 없는 계정 동의 우선 403 코드 |
| `integrationTest/java/com/dnd/qello/ActiveUserPresenceConfigurationIntegrationTest.java` | 정상 정책 검증 동의 fixture와 이력 정리; 필수 formatter 적용 |
| `integrationTest/java/com/dnd/qello/AccountWithdrawalIntegrationTest.java` | 기존 위치/계정 자격 검증 경로에만 사전 동의 fixture와 이력 정리 |
| `integrationTest/java/com/dnd/qello/QuestionRecommendationIntegrationTest.java` | 정상 발송 사용자 동의와 이력 정리 |
| `integrationTest/java/com/dnd/qello/DirectionMatchingVerticalFlowIntegrationTest.java` | 정상 위치 fixture 동의와 정리 |
| `integrationTest/java/com/dnd/qello/DirectionMatchingVerticalFlowRecoveryIntegrationTest.java` | 복구 시나리오 정상 위치 fixture 동의와 정리 |
| `integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceIntegrationTest.java` | 성능 fixture의 발신자 동의와 정리 |
| `integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java` | 성능 fixture의 발신자 동의와 정리 |
| `integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java` | 미동의 거부를 먼저 검증하고 명시적 dev-v1 동의 후 저장 |
| `integrationTest/java/com/dnd/qello/QelloLocalProfileIntegrationTest.java` | local 미동의 거부→명시적 동의→저장 검증 |
| `integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java` | INT-013의 실제 schema·인증·오류 계약 검증 및 생성 |

`AccountRepository.java` 및 account/auth의 기존 repository fake 5개는 수정하지 않았다.
원래 사용자 소유인 TASK.md와 승인 계획도 수정하지 않았다.
기존 `AccountWithdrawalConcurrencyIntegrationTest`는 소스/기대값 변경 없이 통과했다.
별도 `performanceTest`는 미실행이며 해당 fixture 변경은 컴파일까지만 확인했다.
모든 새 테스트의 DisplayName, 초 단위 생성 시각, Source scenario가 정책 검사를 통과했다.
독립 읽기 전용 검토는 CRITICAL/HIGH 결함 없이 APPROVE였으며, §6의 미게시 API·문서 잔여 위험을 별도 기록했다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-381-TEST-PLAN-GH-381-LOCATION-CONSENT.md`
- Test results: `build/test-results/test/`, `build/test-results/integrationTest/`
- HTML results: `build/reports/tests/test/`, `build/reports/tests/integrationTest/`
- Generated specification: `docs/api/openapi.json`
- Related ADR: ADR-0001 DB 소유, ADR-0002 JPA/JDBC 경계. 기존 응답 관례도 따랐다.
- CI run / PR: 생성하지 않았다. 로컬 검증이며 commit·push·PR·외부 코멘트는 수행하지 않았다.
- changed_files: 아래 54개 구현/테스트/산출물과 이 보고서. TASK.md 및 테스트 계획은 기존 사용자 변경으로 제외했다.

```text
docs/api/openapi.json
docs/error-codes.md
docs/product/data-model/direction_communication.dbml
docs/product/data-model/schema-manifest.md
src/main/java/com/dnd/qello/account/repository/AccountLockRepository.java
src/main/java/com/dnd/qello/account/repository/jpa/JpaAccountRepository.java
src/main/java/com/dnd/qello/direction/consent/LocationConsent.java
src/main/java/com/dnd/qello/direction/consent/LocationConsentAction.java
src/main/java/com/dnd/qello/direction/consent/LocationConsentService.java
src/main/java/com/dnd/qello/direction/consent/LocationConsentProperties.java
src/main/java/com/dnd/qello/direction/consent/LocationConsentRepository.java
src/main/java/com/dnd/qello/direction/consent/JpaLocationConsentRepository.java
src/main/java/com/dnd/qello/direction/consent/LocationConsentEntity.java
src/main/java/com/dnd/qello/direction/consent/SpringDataLocationConsentRepository.java
src/main/java/com/dnd/qello/direction/service/DirectionPresenceService.java
src/main/java/com/dnd/qello/direction/service/DirectionPostApplicationService.java
src/main/java/com/dnd/qello/direction/error/DirectionErrorCode.java
src/main/java/com/dnd/qello/direction/web/LocationConsentController.java
src/main/java/com/dnd/qello/direction/web/LocationConsentApiSpec.java
src/main/java/com/dnd/qello/direction/web/ActiveUserPresenceApiSpec.java
src/main/java/com/dnd/qello/direction/web/DirectionPostApiSpec.java
src/main/java/com/dnd/qello/direction/web/request/LocationConsentRequest.java
src/main/java/com/dnd/qello/direction/web/response/LocationConsentResponse.java
src/main/resources/db/migration/V35__add_location_consent_history.sql
src/main/resources/application.yml
src/main/resources/application-dev.yml
src/main/resources/application-local.yml
src/test/java/com/dnd/qello/FlywayMigrationContractTest.java
src/test/java/com/dnd/qello/direction/consent/LocationConsentTest.java
src/test/java/com/dnd/qello/direction/consent/LocationConsentServiceTest.java
src/test/java/com/dnd/qello/direction/consent/LocationConsentPropertiesTest.java
src/test/java/com/dnd/qello/direction/error/LocationConsentErrorCodeTest.java
src/test/java/com/dnd/qello/direction/web/LocationConsentControllerMockMvcTest.java
src/test/java/com/dnd/qello/direction/service/DirectionPresenceServiceTest.java
src/test/java/com/dnd/qello/direction/service/DirectionPostApplicationServiceTest.java
src/integrationTest/java/com/dnd/qello/LocationConsentPersistenceIntegrationTest.java
src/integrationTest/java/com/dnd/qello/LocationConsentApiIntegrationTest.java
src/integrationTest/java/com/dnd/qello/LocationConsentConcurrencyIntegrationTest.java
src/integrationTest/java/com/dnd/qello/LocationConsentConfigurationIntegrationTest.java
src/integrationTest/java/com/dnd/qello/AccountPersistenceIntegrationTest.java
src/integrationTest/java/com/dnd/qello/AccountWithdrawalIntegrationTest.java
src/integrationTest/java/com/dnd/qello/ActiveUserPresenceApiIntegrationTest.java
src/integrationTest/java/com/dnd/qello/ActiveUserPresenceConfigurationIntegrationTest.java
src/integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java
src/integrationTest/java/com/dnd/qello/QelloLocalProfileIntegrationTest.java
src/integrationTest/java/com/dnd/qello/DirectionMatchingPerformanceIntegrationTest.java
src/integrationTest/java/com/dnd/qello/DirectionMatchingE3PerformanceIntegrationTest.java
src/integrationTest/java/com/dnd/qello/DirectionMatchingVerticalFlowIntegrationTest.java
src/integrationTest/java/com/dnd/qello/DirectionMatchingVerticalFlowRecoveryIntegrationTest.java
src/integrationTest/java/com/dnd/qello/QuestionRecommendationIntegrationTest.java
src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java
src/integrationTest/java/com/dnd/qello/NotificationPreferenceMigrationIntegrationTest.java
src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java
src/integrationTest/resources/application-test.yml
```

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제의 후속 GitHub Issue 생성은 미실행: 외부 쓰기 금지. D2/D6은 #381의 출시 조건으로 추적한다.
- [ ] PR 설명 대조는 해당 없음: PR을 생성하지 않았다.

## 10. 2026-11-24 main rebase 및 V35 재검증

- 상태: PASS. 기준 `origin/main`은 `dae144f8`, rebase 직후 HEAD는 `f90097ae`다.
- V35 번호·참조 변경 커밋: `e033723d` (정상 pre-commit·commit-msg hook 통과).
- `./harness sync` 후 충돌을 해결하고 `git rebase --continue`로 완료했다. main이 다른 worktree에 checkout되어 로컬 main fetch 경고가 있었으나 원격 main 반영과 ancestor 확인은 통과했다.
- 충돌 해결: `TASK.md`는 #381 계약을 유지했다. `FlywayMigrationContractTest`는 main의 V34 operator 검증과 동의 migration 목록을 모두 보존했다. `AccountPersistenceIntegrationTest`와 `NotificationPreferenceMigrationIntegrationTest`는 main의 설명·검증을 보존한 후 두 테이블의 최종 개수로 갱신했다. production 코드 충돌은 없었다.
- `V35__add_location_consent_history.sql`은 원본 V34에서 git mv만 수행했다. 전후 Git blob은 `a6d0683700179cbb91e24733903bd5854d0a318f`로 동일하다. main의 V34 operator SQL과 production 로직은 변경하지 않았다.
- 최신 migration 35, 전체 테이블 56, V24 이후 적용 수 11을 검증한다. INT-001은 빈 DB 및 V34→V35 upgrade를 검증하며 기존 checksum·제약 검증은 유지했다.
- DBML·manifest의 동의 버전을 V35로 바꾸고 ERD에 V35 참조를 명시했다. main의 V34 설명은 보존했다. TASK의 base/최신 migration과 계획의 경로·승인 기록도 갱신했다.

| 명령 | 결과 | 실제 실행 수 |
| --- | --- | --- |
| `./gradlew test --console=plain` | exit 0, 46초 | 1,654 |
| `./gradlew integrationTest --console=plain` 최초 | exit 1, 1분 26초 | 133, 초기화 실패 118 |
| `./gradlew clean test integrationTest --console=plain` | exit 0, 3분 54초 | 단위 1,654 / 통합 950 |
| `./harness check` | exit 0 | JUnit 정책 369개 파일 |
| `./harness pr-ready --project-tests` | exit 0, Gradle 10초 | 저장소 전체 check 통과 |
| `npm run hooks:validate` | exit 0 | Husky 검증 |
| `git diff --check` | exit 0 | 전체 변경 |

최종 XML은 `build/test-results/test/`의 216 suites 및 `build/test-results/integrationTest/`의 120 suites다. 두 suite 모두 실패·오류·skip 0이다.
최초 통합 실패는 `build/resources`에 남은 이전 V34 동의 SQL과 main V34 operator SQL의 중복 때문이었다. 소스가 아닌 stale 빌드 리소스를 Gradle clean으로 재생성한 후 통과했다. 검사나 SQL을 우회·수정하지 않았다.
이번 재실행 결과가 §3의 최초 실행 수보다 최신이며, 별도 성능 태그·운영 배포는 여전히 미실행이다.
이미 V34 동의 migration을 적용한 기존 DB의 자동 변경은 수행하지 않았다. 해당 DB가 있다면 별도 승인된 이력 정합성 확인이 필요하다. push는 수행하지 않는다.
