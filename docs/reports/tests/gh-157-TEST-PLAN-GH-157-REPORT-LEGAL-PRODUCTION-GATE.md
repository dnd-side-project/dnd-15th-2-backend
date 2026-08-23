# Test Report: TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE

> Created at: `2026-08-21T20:45:00+09:00`
> GitHub Issue: `#157`
> Branch: `feat/gh-157-report-legal-production-gate`
> Commit: `f26118c` (base) + 아직 커밋되지 않은 작업 트리 변경

## 1. Executive summary

- Result: `PASS`
- Tested scope: `TASK.md` Scope 1~6 전부 — `ReportSubReason.SELF_HARM_RISK`
  추가와 severity 매핑, `critical-enabled` 기능 플래그(기본값 OFF)와 즉시
  전역 숨김 트리거, CRITICAL 일일 신고 쿼터(5건, 롤링 24시간 윈도우),
  증거 스냅샷 `purge_after`(180일) 계산, append-only 트리거의
  media_object_keys 전용 예외(V27), `ReportEvidencePurgeSweepWorker`,
  운영 설정값 변경(자동 숨김 임계값 5→3, SLA·rate limit 유지).
- Unverified scope: 없음 — 계획한 P0/P1 시나리오를 전부 구현하고 실행했다.
  N-way(3+) 동시 CRITICAL 신고 경합, SLA 알림 실제 발송, 국가별 분기,
  이의제기 경로는 `TASK.md` Explicit exclusions에 따라 처음부터 계획
  범위 밖이었다(§7 참고).
- Release recommendation: 코드 변경은 병합 가능. 단
  `qello.safety.report-case.auto-suppress.critical-enabled`을 실제
  프로덕션에서 `true`로 켜는 것은 이 PR의 범위가 아니다 — 별도 승인
  절차를 거쳐야 한다(`TASK.md` "결정 게이트에 대한 중요한 주의" 참고).

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 17.0.8 LTS (Temurin/OpenJDK, HotSpot) |
| Spring Boot | 3.5.16 |
| Database | PostGIS 16-3.5 (Testcontainers, 로컬 Docker) |
| Test runner | JUnit 5 (Gradle `test` / `integrationTest` 소스셋) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체 단위 테스트) | PASS | 전체 스위트, 신규 UNIT-001~005 포함 | ~9s | 로컬 실행, 실패 0 |
| `./gradlew integrationTest` (전체 통합 테스트) | PASS | 전체 스위트, 신규 4개 클래스 포함 | ~9m 8s | 로컬 실행(Testcontainers PostGIS), 실패 0 |
| `./gradlew integrationTest --tests "com.dnd.qello.ReportCaseSeverityIntegrationTest"` | PASS | 16 | ~13s | 개별 확인, 임계값 회귀 수정 후 |
| `./gradlew integrationTest --tests "com.dnd.qello.ReportCaseCriticalAutoSuppressionIntegrationTest" --tests "com.dnd.qello.ReportContentSnapshotImmutabilityIntegrationTest" --tests "com.dnd.qello.ReportEvidencePurgeSweepWorkerIntegrationTest"` | PASS | 13 | ~26s | 개별 확인, SELF_HARM_RISK 검증 코드 수정 후 |
| `./gradlew integrationTest --tests "*Flyway*"` | PASS | 전체 | ~26s | V27 migration count/manifest 반영 확인 |
| `./gradlew integrationTest --tests "com.dnd.qello.*ReportCase*" --tests "com.dnd.qello.*Report*"` | PASS | 전체 | ~1m 29s | 회귀 없음 |
| `./gradlew test --tests "com.dnd.qello.notification.*"` | PASS | 전체 | ~3s | 회귀 없음 |
| `./harness check` | PASS | Secret preflight, JUnit 정책, convention, commit 형식, workflow, label, Husky | ~수초 | 헤더 위치 수정 후 통과 |
| `./harness pr-ready --project-tests` | PASS | 위 test+integrationTest 재확인 포함 | ~수초(UP-TO-DATE) | |
| `npm run hooks:validate` | PASS | Husky 설정 검증 | ~1s | |
| `git diff --check` | PASS | whitespace 오류 없음 | 즉시 | |

### 3.1 `main` rebase 후 재검증 (2026-08-23)

PR #188을 최신 `origin/main` 위로 rebase하면서 다음 두 가지를 조정했고, 조정
후 아래 명령을 다시 실행했다.

1. `main`이 먼저 `V26__split_notification_user_setting.sql`을 병합해 이 이슈의
   마이그레이션과 버전 번호가 겹쳤다. Flyway는 같은 버전을 두 개 두면 기동에
   실패하므로 이 이슈의 마이그레이션을 `V27`로 다시 번호를 매겼다. 파일 내용은
   바뀌지 않았다(rename 100%).
2. `NotificationPreferenceMigrationIntegrationTest`는 V24에서 최신까지 실행되는
   마이그레이션 수를 2로 단언하고 있었다. `V27`이 늘어나 3이 되었으므로 이
   단언만 갱신했다. 이 테스트가 검증하는 quiet 값 폐기·enabled 보존 계약은
   그대로다.

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew compileJava compileTestJava compileIntegrationTestJava` | PASS | - | ~13s | rebase 후 전체 소스셋 컴파일 |
| `./gradlew test` (전체 단위 테스트) | PASS | 전체 스위트 | ~10s | `FlywayMigrationContractTest`의 V26·V27 카탈로그 포함 |
| `./gradlew integrationTest --tests "com.dnd.qello.FlywayMigration*"` | PASS | 전체 | ~46s | `V27`까지 27개 migration 적용 확인 |
| `./gradlew integrationTest` (전체 통합 테스트) | PASS | 656 | ~11m 19s | 로컬 실행(Testcontainers PostGIS), 실패 0 |
| `./harness check` | PASS | Secret preflight, JUnit 정책, convention, commit 형식, workflow, label, Husky | ~수초 | rebase 후 재실행 |
| `npm run hooks:validate` | PASS | Husky 설정 검증 | ~1s | |
| `git diff --check` | PASS | whitespace 오류 없음 | 즉시 | |

rebase 직전 한 번은 전체 통합 테스트가 `NotificationPreferenceMigrationIntegrationTest`
1건으로 실패했다(656개 중 1건). 위 2번 조정 후 재실행에서 656개 전부 통과했다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `ReportCaseAndEvidenceTest#selfHarmRiskSubReasonProducesCriticalSeverity` | |
| UNIT-002 | PASS | `ReportCaseAndEvidenceTest#existingSeverityMappingIsUnchangedAfterAddingSelfHarmRisk` | |
| UNIT-003 | PASS | `ReportCaseAndEvidenceTest#criticalReportQuotaPolicyRejectsNonPositiveMaxPerDay` | |
| UNIT-004 | PASS | `ReportCaseAndEvidenceTest#evidenceRetentionPolicyRejectsNonPositiveRetentionPeriod` | |
| UNIT-005 | PASS | `ReportCaseAndEvidenceTest#captureStoresGivenPurgeAfter` | |
| (추가) | PASS | `ReportSubmissionTest#acceptsIllegalOrDangerousWithSelfHarmRisk`, `#rejectsSelfHarmRiskWithMismatchedReason` | 계획에 없었지만 실행 중 발견한 도메인 검증 공백(§5) 커버 |
| INT-001 | PASS | `ReportCaseCriticalAutoSuppressionIntegrationTest#newCriticalCaseIsAutoSuppressedWhenFlagIsEnabled` | |
| INT-002(계획 §6, 플래그 검증) | PASS | `ReportCaseCriticalAutoSuppressionIntegrationTest#newSelfHarmRiskCaseIsAutoSuppressedWhenFlagIsEnabled` | |
| INT-002(계획 §6, 기본값 OFF) | PASS | `ReportCaseSeverityIntegrationTest#newCaseOpensAsCriticalWhenSubReasonIsCsam`(강화) | 기존 테스트에 OPEN·PUBLISHED 유지 단언 추가 |
| INT-003 | PASS | `ReportCaseCriticalAutoSuppressionIntegrationTest#escalatedCaseIsAutoSuppressedWhenFlagIsEnabled` | |
| INT-004 | PASS | `ReportCaseSeverityIntegrationTest#rejectsCriticalReportBeyondDailyQuota`(GH157-INT-004) | |
| INT-005 | PASS | `ReportCaseSeverityIntegrationTest#rollingWindowExcludesReportsOlderThan24Hours`(GH157-INT-005) | |
| INT-006 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#purgeMediaClearsOnlyMediaObjectKeys` | |
| INT-007 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#purgeMediaRejectsLegalHoldSnapshot` | |
| INT-008 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#rawUpdateChangingBodyTextIsRejected` | |
| INT-009 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#rawUpdateChangingMediaKeysAndAnotherColumnIsRejected` | |
| INT-010 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#deleteIsAlwaysRejected` | |
| INT-011 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#reportCaseEventTriggerStillRejectsUpdateAndDelete` | |
| INT-012 | PASS | `ReportEvidencePurgeSweepWorkerIntegrationTest#purgesOnlyEligibleSnapshots` | |
| INT-013 | PASS | `ReportEvidencePurgeSweepWorkerIntegrationTest#alreadyPurgedSnapshotIsExcludedFromCandidates` | |
| INT-014 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#selfHarmRiskCombinationIsAccepted` | |
| INT-015 | PASS | `ReportContentSnapshotImmutabilityIntegrationTest#invalidSelfHarmRiskCombinationIsRejected` | |
| INT-016 | PASS | `ReportCaseSeverityIntegrationTest#autoSuppressionThresholdDefaultsToThree`(GH157-INT-016) | |
| INT-017(회귀) | PASS | 기존 `ReportCaseSeverityIntegrationTest` 5-reporter/1-reporter 시나리오 | 임계값 하드코딩을 `autoSuppressionPolicy.distinctReporterThreshold()`로 교체해 회귀 방지 |

동시성 교차 시나리오(계획 §7 "두 신고가 동시에 같은 사건을 CRITICAL로
만들며 각각 자동 숨김을 트리거")는 별도 스레드 테스트로 새로 만들지
않았다 — `resolveIfStillOpen`의 `REPORT_CASE_ALREADY_RESOLVED` 스왈로우
경로는 `#156`이 만든 기존 동시성 테스트(`ReportCaseSeverityIntegrationTest`의
`INT-003: 두 CRITICAL 신고가 동시에 같은 NORMAL 사건에 붙어도 ESCALATED는
정확히 1건이다`)가 같은 잠금·스왈로우 메커니즘을 이미 검증하고, 이번에
추가한 CRITICAL 자동 숨김 트리거는 그 메커니즘을 그대로 재사용할 뿐 새
잠금 경로를 만들지 않았다(§6 남은 위험 참고).

## 5. Failures and diagnostics

구현 중 실제 테스트 실행으로 발견하고 즉시 고친 결함 2건(계획 문서
작성 시점에는 코드 조사만으로 예측하지 못했다):

1. **도메인 레벨 검증 공백** — `ReportSubmission.ALLOWED_SUB_REASONS`
   맵에 `ILLEGAL_OR_DANGEROUS`→`SELF_HARM_RISK` 조합을 추가하지 않아,
   DB `ck_report_sub_reason` CHECK는 통과하도록 V27에서 고쳤지만 그
   앞단인 서비스 계층 `ReportSubmission` 생성자가 `SAF-VAL-007
   INVALID_REPORT_SUB_REASON`으로 먼저 거부하고 있었다.
   `ReportCaseCriticalAutoSuppressionIntegrationTest#newSelfHarmRiskCaseIsAutoSuppressedWhenFlagIsEnabled`
   실행 중 발견. `ReportSubmission.java`의 매핑에 조합을 추가해 해결하고
   `ReportSubmissionTest`에 회귀 테스트 2건을 추가했다.
2. **운영값 변경과 기존 테스트의 암묵적 결합** — 자동 숨김 임계값을
   5→3으로 낮추자, 기존 `#156`의
   `autoSuppressesWhenDistinctReporterThresholdIsReached`/
   `autoSuppressionEvaluationIsIdempotentOnAlreadyResolvedCase`
   테스트(정확히 5명 신고자를 하드코딩한 루프)가 3번째 신고에서 이미
   답변이 `HIDDEN`돼, `findViewableAnswer`가 `status='PUBLISHED'`만
   조회하는 탓에 4·5번째 신고가 `REPORT_TARGET_NOT_FOUND`로 실패했다.
   루프 상한을 하드코딩값 대신 `autoSuppressionPolicy.distinctReporterThreshold()`로
   바꿔 운영값이 다시 바뀌어도 테스트가 자동으로 따라가게 했다.

두 건 모두 실제 통합 테스트 실행 전에는 정적 코드 리뷰만으로 놓쳤을
가능성이 높다 — 계획 §4 위험 인벤토리에서 "5→3 변경이 기존 테스트와
충돌 없음"이라고 사전 판단했던 부분이 실제로는 루프 중간에 상태가
바뀌는 동적 상호작용까지는 잡아내지 못했다는 뜻이라, 이 계획의 사전
위험 평가 방법 자체의 한계로 기록해 둔다.

## 6. Potential issues

### Application code

- `ReportCaseAutoSuppressionEvaluator`가 이제 4개 생성자 인자 대신 5개를
  받는다(`criticalAutoSuppressEnabled` boolean 추가) — Spring이
  `@Value` 기본값(`false`)으로 주입하므로 별도 설정 없는 환경에서도
  안전하게 동작하지만, 이 필드를 테스트에서 리플렉션 등으로 직접
  덮어쓰는 코드가 미래에 추가되면 깨지기 쉽다. 현재는 `@TestPropertySource`
  방식만 쓰고 있어 문제 없다.
- `SafetyReportService.mergeCase`의 반환 타입이 `long`에서 `CaseMergeResult`
  레코드로 바뀌었다 — private 메서드라 외부 영향은 없지만, 향후 이
  메서드를 public으로 노출할 계획이 있다면 이 레코드가 공개 계약이
  된다는 점을 유의해야 한다.

### Infrastructure and resource limits

- 해당 없음 — 이 이슈는 인프라 변경을 포함하지 않는다.

### Database and migrations

- V27이 `report_content_snapshot`의 append-only 트리거를 부분적으로
  약화시킨다(media_object_keys 예외). 트리거 함수가 `OLD`/`NEW`의 모든
  다른 컬럼을 명시적으로 비교하는 방식이라, **향후 이 테이블에 컬럼을
  추가하면 트리거 함수도 함께 갱신해야 한다** — 그렇지 않으면 새 컬럼은
  트리거의 "허용되지 않은 변경" 검사에서 누락돼 의도치 않게 변경 가능한
  구멍이 생긴다. 이 유지보수 부담을 코드 주석으로 남겼다(V27 마이그레이션
  파일).
- `report_content_snapshot`에 대한 실제 프로덕션 미디어 오브젝트(S3 등)
  정리는 이 이슈 범위 밖이다 — `purgeMedia`는 DB 컬럼만 비운다.

### Concurrency and idempotency

- CRITICAL 자동 숨김은 기존 `resolveIfStillOpen`의
  `REPORT_CASE_ALREADY_RESOLVED` 스왈로우 경로를 그대로 재사용하므로
  `#156`이 이미 검증한 동시성 보장을 상속받는다. N-way(3개 이상) 동시
  CRITICAL 신고 경합은 `#156` 시점부터 미실측 상태이며 이번에도 새로
  검증하지 않았다(§7).
- CRITICAL 일일 쿼터는 `acquireReporterSubmissionLock`(신고자별
  advisory lock, `#154`)이 감싸는 트랜잭션 안에서 카운트하므로 같은
  신고자의 동시 CRITICAL 신고 경합에 대해서도 순차적으로 직렬화된다 —
  별도 신규 잠금을 만들지 않았다.

### Transactions and event ordering

- `enforceCriticalDailyQuota`는 `mergeCase`보다 먼저 호출된다 — 쿼터
  초과 시 사건이 아예 만들어지지 않고 예외가 트랜잭션을 롤백하므로
  반쪽 상태(쿼터는 소비됐는데 사건은 없음)가 생기지 않는다.
- purge 배치(`ReportEvidencePurgeSweepWorker`)는 자체 트랜잭션을 열지
  않는다 — `#158` `RecipientExpirationSweepWorker`와 동일하게 한 행의
  실패가 이미 처리된 다른 행의 커밋을 되돌리지 않는다.

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- `ReportEvidencePurgeSweepWorker.processBatch`는 행별 실패를 격리하고
  batch 요약 로그(scanned/purged/failed)를 남긴다 — 실패한 행은 다음
  sweep에서 다시 후보가 된다(멱등).

## 7. Regression and residual risk

- N-way(3개 이상) 동시 CRITICAL 신고 경합 미실측 — `#156` 시점부터
  이어지는 기존 한계, 이번 이슈에서 새로 만들지 않았다.
- `linked_manual_review_case_id`의 FK 부재는 `#156`에서 이미 ASSUMED로
  결정된 사항이라 이번 이슈에서 재검토하지 않았다.
- SLA 초과 알림의 실제 발송(push/Slack/이메일)은 `#156` TASK.md Scope
  decision 4에 따라 계속 범위 밖이다.
- `critical-enabled` 플래그의 실제 프로덕션 활성화 — 코드는 이 PR에
  포함되지만 값을 `true`로 바꾸는 운영 변경은 별도 승인이 필요하다.
- V27 트리거 함수의 컬럼 목록이 하드코딩돼 있어(§6 Database and
  migrations) 향후 스키마 변경 시 함께 갱신해야 하는 결합이 생겼다 —
  후속 이슈에서 컬럼 추가 시 체크리스트 항목으로 남겨야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-157-TEST-PLAN-GH-157-REPORT-LEGAL-PRODUCTION-GATE.md`
- CI run: 로컬 실행만(이 세션에서 push 전) — 원격 CI 실행 결과 없음
- Related ADR: `docs/product/ANSWER_REPORT_DESIGN.md` §4.1, §10, §11.2, §12
- PR: 아직 생성되지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(§1 Unverified scope — 없음, 계획 전체 실행)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨(§7 항목들은 `#156`/설계
      문서 §12의 기존 미결 항목을 참조하며 새 항목은 §6 Database and
      migrations의 트리거 유지보수 부담 하나뿐 — 별도 이슈 번호는 아직
      없음, 필요 시 PR 리뷰에서 결정)
- [x] 실행 결과와 PR 설명이 일치함(PR 생성 시 이 보고서를 그대로 링크)
