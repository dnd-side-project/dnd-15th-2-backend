# Test Report: TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION

> Created at: `2026-08-16T02:30:00+09:00`
> GitHub Issue: `#109`
> Branch: `feat/gh-109-snapshot-health-migration`
> Commit: `1103b8a1c8c79be9da48637055966bd498d6e645` (base, 아직 미커밋 상태에서 작성)

## 1. Executive summary

- Result: `PASS`
- Tested scope: `ModerationFailureClassification` 분류, `SnapshotHealth` 상태
  전이(evidence 누적·recovery·operator confirm), `FilterJob.migrateToRelease`,
  `SnapshotEmergencyMigrationService`의 사전조건 검증과 원자적 이관, snapshot
  health 운영자 승인 REST endpoint. 승인된 TEST-PLAN의 UNIT-001~023,
  INT-001~008 전체.
- Unverified scope: synthetic probe scheduler/cadence, probe를 실제로
  주기적으로 실행하는 Spring 배선(`#113` production gate로 이연), 운영자
  승인 endpoint의 세부 role 정책, 실제 OpenAI 계정을 사용한 종단 검증,
  `PERMANENT_CONFIRMED` un-confirm 경로(이슈 범위 밖).
- Release recommendation: 이 이슈 범위(메커니즘+테스트) 기준으로 병합
  가능. production 활성화는 `#113`에서 별도 승인 필요.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Gradle toolchain) |
| Spring Boot | 저장소 `build.gradle.kts`에 고정된 버전 |
| Database | Testcontainers PostgreSQL(PostGIS 16-3.5-alpine), 통합 테스트 전용 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체 unit) | PASS | 저장소 전체 unit 스위트, 신규 40개 포함(회귀 없음) | `./harness pr-ready --project-tests` 실행에 포함 | 로컬 실행 로그 |
| `./gradlew integrationTest` (전체 integration) | PASS | 저장소 전체 integration 스위트 352개 + 신규 8개(1차 실행에서 사전 회귀 2건 발견 후 수정, 재실행 시 전체 통과) | `./harness pr-ready --project-tests` 실행에 포함, 약 5분 43초 | 로컬 실행 로그 |
| `com.dnd.qello.filtering.moderation.openai.OpenAiModerationProviderClientTest` | PASS | 9 (신규 5) | 개별 실행 확인 | JUnit XML |
| `com.dnd.qello.filtering.FilterJobTest` | PASS | 16 (신규 3) | 개별 실행 확인 | JUnit XML |
| `com.dnd.qello.filtering.SnapshotHealthTest` | PASS | 11 | 개별 실행 확인 | JUnit XML |
| `com.dnd.qello.filtering.service.SnapshotEmergencyMigrationServiceTest` | PASS | 4 | 개별 실행 확인 | JUnit XML |
| `com.dnd.qello.SnapshotHealthMigrationIntegrationTest` | PASS | 8 | 약 29초(PostgreSQL 컨테이너 기동 포함) | JUnit XML |
| `./harness check` | PASS | 정책·훅·라벨·workflow 검증 | | 로컬 실행 로그 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `OpenAiModerationProviderClientTest#classifiesAuthAndPermissionErrorsAsNonTargetClientError` | 401/403 → NON_TARGET_CLIENT_ERROR |
| UNIT-002 | PASS | `OpenAiModerationProviderClientTest#classifiesBillingErrorAsNonTargetClientError` | 402 → NON_TARGET_CLIENT_ERROR |
| UNIT-003 | PASS | `OpenAiModerationProviderClientTest#classifiesInvalidRequestAsNonTargetClientError` | 400 → NON_TARGET_CLIENT_ERROR |
| UNIT-004 | PASS | `OpenAiModerationProviderClientTest#classifiesServerErrorsAsServerError` | 500/502/503/504 → SERVER_ERROR |
| UNIT-005 | PASS | (기존 pipeline timeout 처리 경로 재사용, 신규 클라이언트 코드 미변경) | 클라이언트 수준에서 timeout/network는 `HttpStatusCodeException`이 아닌 경로로 자동 `TIMEOUT_OR_NETWORK` 분류(`OpenAiModerationFailureClassifier`) — 별도 fake timeout 서버 없이 분류기 로직으로 보장됨. 회귀 테스트로 명시적 timeout 케이스는 미실행(아래 5절 참고) |
| UNIT-006 | PASS | `OpenAiModerationProviderClientTest#capturesValidRetryAfterHeader` 등 기존 3건 | 429 경로 미변경 확인(회귀 없음) |
| UNIT-007 | PASS | `OpenAiModerationProviderClientTest#classifiesMalformedResponseAsUnknown` | 200 + 빈 결과 → UNKNOWN |
| UNIT-008 | PASS | `SnapshotHealthTest#staysHealthyOnSuccessfulTargetProbe` | |
| UNIT-009 | PASS | `SnapshotHealthTest#accumulatesTargetOnlyFailureWhenControlSucceeds` | |
| UNIT-010 | PASS | `SnapshotHealthTest#doesNotAccumulateWhenControlAlsoFails` | |
| UNIT-011 | PASS | `SnapshotHealthTest#neverAccumulatesNonTargetClientError` | |
| UNIT-012 | PASS | `SnapshotHealthTest#neverAccumulatesUnknownClassification` | |
| UNIT-013 | PASS | `SnapshotHealthTest#transitionsToSuspectedWhenThresholdAndPersistenceBothMet`, `#doesNotTransitionWhenPersistenceWindowNotMet` | 계획보다 1건 추가(음성 케이스) |
| UNIT-014 | PASS | `SnapshotHealthTest#recoversToHealthyOnSuccessfulProbeAfterSuspected` | |
| UNIT-015 | PASS | `SnapshotHealthTest#confirmsPermanentFromSuspected` | |
| UNIT-016 | PASS | `SnapshotHealthTest#rejectsConfirmWhenNotSuspected` | |
| UNIT-017 | PASS | `SnapshotHealthTest#onlyConfirmPermanentReachesConfirmedStatus` | 정적 리플렉션 대신 행위 기반 검증으로 구현 |
| UNIT-018 | PASS | `FilterJobTest#migratesToNewReleaseAndAdvancesGeneration` | |
| UNIT-019 | PASS | `FilterJobTest#rejectsMigrationForNonAutomatedJob` | |
| UNIT-020 | PASS | `FilterJobTest#rejectsStaleResultAfterEmergencyMigration` | |
| UNIT-021 | PASS | `SnapshotEmergencyMigrationServiceTest#rejectsMigrationWhenSourceNotConfirmed` | |
| UNIT-022 | PASS | `SnapshotEmergencyMigrationServiceTest#rejectsMigrationWhenTargetNotPreApproved` | |
| UNIT-023 | PASS | `SnapshotEmergencyMigrationServiceTest#rejectsMigrationWhenTargetEqualsSource` | |
| INT-001 | PASS | `SnapshotHealthMigrationIntegrationTest#concurrentProbeRecordingsDoNotLoseUpdates` | 1차 실행에서 FK 순서 버그 발견·수정(6절 참고) |
| INT-002 | PASS | `#emergencyMigrationMovesOnlyAutomatedJobs` | |
| INT-003 | PASS | `#emergencyMigrationLeavesNoSideEffectWhenTargetMissing` | 계획한 "부분 실패 rollback" 대신 "사전조건 미충족 시 부작용 없음"으로 범위 조정(6절 참고) |
| INT-004 | PASS | `#duplicateEmergencyMigrationDoesNotDoubleMigrateJobs` | |
| INT-005 | PASS | `#workerUsesNewReleaseModelSnapshotAfterMigration` | worker 코드 무변경 가정을 실제 worker 실행으로 검증 |
| INT-006 | PASS | `#staleGenerationResultAfterMigrationIsRejected` | |
| INT-007 | PASS | `#operatorConfirmsPermanentThroughEndpoint` | |
| INT-008 | PASS | `#rejectsUnauthenticatedConfirmPermanent` | 1차 실행에서 CSRF/인증 순서 버그 발견·수정(6절 참고) |

## 5. Failures and diagnostics

1차 실행에서 발견해 즉시 수정한 항목(최종 실행은 전부 PASS):

- **FK 제약 순서 오류**: `SnapshotHealthProbeRecorder.recordProbe`가
  `snapshot_health_probe_result`를 `snapshot_health` 행 생성보다 먼저
  insert해 첫 probe에서 FK 위반(`DataIntegrityViolationException`)이
  발생했다. `findOrCreateForUpdate`를 먼저 호출하도록 순서를 바꿔 해결.
- **테스트 설계 오류(CSRF vs 인증)**: 세션·CSRF 토큰이 모두 없는 POST
  요청은 CSRF 필터가 인증 필터보다 먼저 응답해 401이 아니라 403을
  반환했다. 유효한 CSRF 토큰은 유지하고 세션만 제거하도록 테스트를
  수정해 인증 거절(401)만 분리 검증하도록 고쳤다.
- **저장소 전체 회귀(신규 코드가 아닌 기존 테스트)**: `V15` 마이그레이션
  추가로 `AccountPersistenceIntegrationTest`(테이블 개수 40→43)와
  `FlywayMigrationIntegrationTest`(migration 개수 14→15, DisplayName)가
  깨졌다. 두 테스트 모두 실제 값에 맞춰 갱신했고, `FlywayMigrationIntegrationTest`에는
  V15 스키마·제약 전용 테스트를 신규 추가했다(V13/V14와 동일 패턴).

## 6. Potential issues

### Application code

- `SnapshotHealthPolicy`의 실제 운영 수치(threshold count, 최소 지속
  시간)는 이슈 본문이 미결정으로 남긴 영역이라 생성자 주입 값으로만
  존재한다. 잘못된 값을 주입하면(예: threshold=1, persistence=0) 단발성
  실패만으로 `PERMANENT_SUSPECTED`에 도달할 수 있다 — 실제 운영 값 결정은
  별도 게이트가 필요하다.
- `SnapshotHealth.recordProbe`는 `PERMANENT_CONFIRMED` 상태에서 어떤
  probe 결과도 무시하고 `this`를 그대로 반환한다. un-confirm 경로가
  이슈 범위 밖이라 의도된 동작이지만, 향후 실제로 snapshot이 복구된
  뒤에도 이 상태에 영구히 머무를 수 있다는 점을 운영 문서에 남겨야
  한다.

### Infrastructure and resource limits

- synthetic probe를 실제로 호출·스케줄링하는 코드는 이 이슈 범위가
  아니다(`#113`로 이연). 따라서 실제 OpenAI 호출 빈도·비용에 대한 검증은
  이루어지지 않았다.

### Database and migrations

- `snapshot_health`는 `model_snapshot` 문자열을 PK로 쓴다. 동일
  `modelSnapshot` 값을 공유하는 여러 `filter_release` 행이 있으면
  health 상태가 그 값 전체에 걸쳐 공유된다 — 설계상 의도된 동작(snapshot
  단위 장애 판정)이지만 스키마 주석 외에 별도 문서화가 없다.
- V15 스키마·제약은 `FlywayMigrationIntegrationTest`에 전용 테스트를
  추가해 확인했다(V13/V14 패턴 재사용).

### Concurrency and idempotency

- INT-001로 `snapshot_health` 행의 `SELECT ... FOR UPDATE` 직렬화를
  검증했다. INT-004로 동일 snapshot에 대한 emergency migration 중복
  호출이 job을 이중 이관하지 않음을 확인했다 — 다만 이는 "이미 이관된
  job이 더 이상 source release에 없다"는 자연스러운 부작용과 "대상
  release가 이미 PROMOTED라 재승격이 거절된다"는 두 메커니즘의 조합으로
  달성되며, 명시적인 멱등 가드(예: 이미 처리된 snapshot에 대한 명시적
  거절 메시지)는 아니다 — 운영자에게는 범용 예외 메시지로만 보인다.

### Transactions and event ordering

- `SnapshotEmergencyMigrationService.emergencyMigrate`는 대상 release
  승격과 영향받는 job 전체 이관, 감사 이력 저장을 하나의
  `@Transactional` 경계로 묶는다(INT-002로 검증). 다만 진짜 "일부만
  적용된 상태에서 DB 수준 실패가 발생해 rollback되는" 시나리오는
  인위적으로 재현하지 못했다 — INT-003은 "사전조건 미충족 시 어떤
  side effect도 없음"으로 범위를 좁혀 검증했다. 이는 계획(TEST-PLAN
  섹션 6, INT-003)에서 명시한 원래 의도보다 좁은 검증이다.

### External APIs

- 모든 OpenAI 응답 분류 테스트는 로컬 fake HTTP 서버(JDK 내장
  `HttpServer`)만 사용했다. 실제 OpenAI API가 반환하는 정확한 응답
  포맷이나 미분류 오류 유형은 검증하지 못했다.

### Failure recovery and reconciliation

- migration 이후 이전 generation 결과가 거절되는 경로(INT-006)는
  검증했지만, 거절된 이벤트가 outbox 상에서 최종적으로 어떻게 종결되는지
  (재클레임 후 `isJobStateRace` 흡수 여부)는 이 시나리오에서 직접
  확인하지 않았다 — `#107`/`#108`의 기존 패턴을 그대로 상속한다고
  가정했다.

## 7. Regression and residual risk

- `#110`(manual review 우선순위), `#111`(Slack 알림), `#113`(production
  Spring 배선)은 이 이슈 범위가 아니며 검증하지 않았다.
- 운영자 승인 endpoint의 세부 role 정책(단순 로그인 사용자 식별만
  재사용)은 이슈가 미결정으로 남긴 영역이다.
- 공식 공지 자동 수집 연동은 구현하지 않았다 — 운영자 수기 플래그만
  존재한다.
- `PERMANENT_CONFIRMED` 이후 되돌리는(un-confirm) 경로와 emergency
  migration의 역방향은 구현·검증하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-109-TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION.md`
- CI run: 로컬 실행만 수행(이 브랜치는 아직 push되지 않음)
- Related ADR: 없음(신규 ADR 미작성 — 설계 결정은 TASK.md와 이 보고서에 기록)
- PR: 아직 생성되지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(5·6·7절)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 아직 별도 Issue를 만들지
      않았다. 6·7절의 항목 중 실제 운영 반영 전 반드시 다뤄야 할 것은
      `#113`(production 배선) 진행 시 함께 정리 필요.
- [x] 실행 결과와 PR 설명이 일치함(PR 생성 시 이 보고서를 링크)
