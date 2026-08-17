# Test Report: TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION

> Created at: `2026-08-17T19:07:39+09:00`
> GitHub Issue: `#153`
> Branch: `feat/gh-153-report-case-foundation`
> Commit: `3acbc4a` (pre-commit — 이 보고서는 커밋 전 작업 트리 기준이다)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 테스트 계획의 UNIT-001~021, INT-001~013 전부(34개) 구현·실행.
  `ReportReason`/`ReportSubReason`/`ReportTargetType`/`ReportCaseStatus`/
  `ReportCaseSeverity`/`ReportCaseQueue`/`ReportCaseEventType` enum, `ReportCase`/
  `ReportCaseEvent`/`ReportContentSnapshot`/`ReportContentHasher` 신규 도메인,
  `Report`의 `caseId`/`subReasonCode`/`attachToCase`/`requestMoreInfo` 확장과
  `resolve()` 축소, `report_case`/`report_content_snapshot`/`report_case_event`
  신규 저장소(JDBC), V17 마이그레이션(테이블 3개·append-only 트리거·
  `uq_open_report_*` 술어 수정·`notification.report_id` 확장), 신규 오류 코드
  `SAF-DOM-004`·`SAF-DOM-005`·`SAF-VAL-008`.
- Unverified scope: 아래 항목은 이 이슈의 승인된 범위 밖이라 의도적으로
  실행하지 않았다.
  - 신고 INSERT + 스냅샷 INSERT의 실제 단일 트랜잭션 원자성(스키마가 허용함은
    증명했으나, 그렇게 호출하는 서비스는 #154가 만든다).
  - 사건 병합의 `ON CONFLICT ... DO NOTHING` 재조회 로직(#154).
  - `severity`/`queue`의 실제 산출 로직(#156) — 이 이슈는 항상
    `NORMAL`/`STANDARD`로 연다.
  - 신고 접수 REST API, 집계 제외, 결과 알림 fan-out(#154~#156).
  - 3개보다 많은 동시 신고자에 대한 사건 생성 경쟁(2-way만 검증).
- Release recommendation: 이 이슈 단독으로는 사용자에게 노출되는 API가 없으므로
  배포 위험은 낮다. 다만 §7의 트리거 설계 공백(퇴거/purge 배치가 막힘)은
  #157 착수 전에 반드시 반영해야 한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 17.0.8 LTS (Temurin/HotSpot) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (test-only, 로컬 컨테이너) |
| Test runner | JUnit 5 (Gradle `test`/`integrationTest` source set) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체) | PASS | 518, 실패 0 | 6.6s | `build/test-results/test/*.xml` |
| `./gradlew integrationTest` (전체) | PASS | 407, 실패 0 | 20.7s | `build/test-results/integrationTest/*.xml` |
| `./gradlew test --tests "com.dnd.qello.safety.*"` | PASS | 26, 실패 0 | — | `TEST-com.dnd.qello.safety.{ReportLifecycleTest,ReportCaseAndEvidenceTest,SafetyNotificationBoundaryTest}.xml` |
| `./gradlew integrationTest --tests "com.dnd.qello.ReportCaseFoundationIntegrationTest"` | PASS | 13, 실패 0 | — | `TEST-com.dnd.qello.ReportCaseFoundationIntegrationTest.xml` |
| `./harness test-run --id TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION` | PASS | 위 전체 재확인(UP-TO-DATE) | — | 위 결과와 동일 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `ReportLifecycleTest#reportReasonHasExactlyEightValues` | |
| UNIT-002 | PASS | `ReportLifecycleTest#reportSubReasonHasExactlyThreeValues` | |
| UNIT-003 | PASS | `ReportLifecycleTest#attachToCaseSetsCaseId` | |
| UNIT-004 | PASS | `ReportLifecycleTest#attachToCaseRejectsRelinkingToDifferentCase` | `SAF-DOM-004` |
| UNIT-005 | PASS | `ReportLifecycleTest#attachToCaseIsIdempotentForSameCase` | |
| UNIT-006 | PASS | `ReportLifecycleTest#requestMoreInfoDoesNotResolve` | INV-RPT-002 도메인 절반 |
| UNIT-007 | PASS | `ReportLifecycleTest#moreInfoRequestedReportCanStillResolve` | |
| UNIT-008 | PASS | `ReportLifecycleTest#resolveRejectsMoreInfoRequiredAsTerminalStatus` | 결함 수정 회귀 방지 |
| UNIT-009 | PASS | `ReportLifecycleTest#requestMoreInfoRejectsWhenAlreadyMoreInfoRequired` | |
| UNIT-010 | PASS | `ReportLifecycleTest#rejectsNonPositiveCaseId` | |
| UNIT-011 | PASS | `ReportCaseAndEvidenceTest#opensCaseWithDefaultSeverityAndQueue` | |
| UNIT-012 | PASS | `ReportCaseAndEvidenceTest#rejectsCaseWithoutExactlyOneTarget` | |
| UNIT-013 | PASS | `ReportCaseAndEvidenceTest#startReviewTransitionsToUnderReview` | |
| UNIT-014 | PASS | `ReportCaseAndEvidenceTest#resolveClosesCaseWithDecision` | |
| UNIT-015 | PASS | `ReportCaseAndEvidenceTest#resolvedCaseCannotBeResolvedAgain` | INV-RPT-007 도메인 절반, `SAF-DOM-005` |
| UNIT-016 | PASS | `ReportCaseAndEvidenceTest#resolvedCaseCannotStartReviewAgain` | |
| UNIT-017 | PASS | `ReportCaseAndEvidenceTest#contentHashIsOrderIndependent` | |
| UNIT-018 | PASS | `ReportCaseAndEvidenceTest#contentHashDiffersForDifferentBody` | |
| UNIT-019 | PASS | `ReportCaseAndEvidenceTest#rejectsNegativeEditCount` | `SAF-VAL-008` |
| UNIT-020 | PASS | `ReportCaseAndEvidenceTest#createsReportCaseEvent` | |
| UNIT-021 | PASS | `ReportCaseAndEvidenceTest#rejectsNonPositiveCaseIdForEvent` | |
| INT-001 | PASS | `ReportCaseFoundationIntegrationTest#concurrentCaseCreationForSameAnswerAllowsOnlyOneWinner` | INV-RPT-001, 2-way 동시성 |
| INT-002 | PASS | `#rejectsSecondOpenCaseForSameTarget` | |
| INT-003 | PASS | `#allowsNewCaseForTargetWithResolvedCase` | INV-RPT-007 |
| INT-004 | PASS | `#allowsExactlyOneSnapshotPerReport` | INV-RPT-003 |
| INT-005 | PASS | `#snapshotCannotBeUpdatedAfterCreation` | INV-RPT-004 |
| INT-006 | PASS | `#snapshotCannotBeDeletedAfterCreation` | INV-RPT-004, 기존 선례와 의도적으로 다른 지점 |
| INT-007 | PASS | `#caseEventCannotBeUpdatedOrDeletedAfterCreation` | INV-RPT-004 |
| INT-008 | PASS | `#snapshotSurvivesAnswerDeletionAndHasNoForeignKeyToTarget` | FK 부재를 카탈로그 조회로 직접 확인 |
| INT-009 | PASS | `#reasonSubReasonPairingIsEnforcedByCheckConstraint` | |
| INT-010 | PASS | `#unknownReasonCodeIsRejectedByCheckConstraint` | |
| INT-011 | PASS | `#moreInfoRequestedReportStillBlocksDuplicateReport` | INV-RPT-002 핵심 회귀 방지 |
| INT-012 | PASS | `#existingReportQueriesAreUnaffectedByNewColumns` | |
| INT-013 | PASS | `#notificationTargetCheckAllowsAtMostOneTarget` | |

## 5. Failures and diagnostics

구현 과정에서 발견해 즉시 수정한 3건. 최종 실행 결과에는 실패가 남아 있지 않다.

1. **`V16` 파일명 충돌.** 이 이슈의 마이그레이션을 `V16`으로 작성했으나, 브랜치
   분기 이후 별도로 병합된 `#110`이 이미 `V16__add_manual_review_priority_and_
   authority.sql`을 점유하고 있었다(`FlywayMigrationContractTest`가 감지).
   `V17__add_report_case_and_evidence_snapshot.sql`로 이름을 바꿔 해결했다.
2. **append-only 트리거가 테스트 fixture 정리를 막음.** `report_content_snapshot`/
   `report_case_event`에 대한 `@BeforeEach`의 `DELETE`가 새로 만든
   `enforce_report_evidence_immutability()` 트리거에 걸려 실패했다(의도한
   동작이 정확히 작동한 것이지만 정리 전략이 틀렸다). `TRUNCATE`로 바꿔
   해결했다 — `TRUNCATE`는 row-level `BEFORE DELETE` 트리거를 발동시키지 않는다.
3. **기존 테스트의 자유 문자열 사유 코드가 새 CHECK에 걸림.**
   `AnswerSafetyNotificationPersistenceIntegrationTest`가 `"ABUSE"`를 신고
   사유로 썼는데, 이는 새 8종 catalog 밖의 값이라 `ck_report_reason`이 거절했다.
   `"HATE_OR_HARASSMENT"`로 교체했다. (단위 테스트인
   `SafetyNotificationBoundaryTest`의 `"ABUSE"`는 DB를 거치지 않아 영향 없음 —
   의도적으로 그대로 뒀다.)

이와 별도로 `FlywayMigrationIntegrationTest`의 두 회귀 가드(전체 테이블 수 44,
FK/CHECK 제약 수 52/116)와 `AccountPersistenceIntegrationTest`의 테이블 수(44)를
V17이 실제로 추가한 값(테이블 +3, `report`/`notification` FK +2, `report`
CHECK +1)에 맞춰 갱신했다. 셋 다 코드 결함이 아니라 승인된 스키마 변경에 따른
정상적인 fixture 갱신이다.

## 6. Potential issues

### Application code

- `Report`는 `reasonCode`/`subReasonCode`를 Java 레벨에서 catalog 멤버십으로
  검증하지 않는다 — DB CHECK만이 최종 방어선이다(설계 문서 §3.2에 따른 의도적
  선택). 결과적으로 잘못된 코드가 애플리케이션 계층을 통과해 raw
  `DataIntegrityViolationException`으로만 드러난다. `#154`가 REST 계층에서
  `ReportReason`/`ReportSubReason` enum으로 역직렬화하면 이 경로는 자연스럽게
  막히지만, 그 전까지는 JDBC를 직접 쓰는 어떤 호출자도 이 구멍을 통과할 수
  있다.
- `ReportCase.requireStatus`가 "허용되지 않는 모든 상태 전이"에
  `REPORT_CASE_ALREADY_RESOLVED`를 재사용한다. 실제로 `UNDER_REVIEW`에서
  `startReview()`를 다시 호출하는 경우처럼 "이미 종결됨"이 아닌 상황도 같은
  코드로 보고된다 — `field`/`reason`으로 구분 가능하지만, 로그만 보고 원인을
  파악하려는 운영자에게는 코드 이름이 오해를 살 수 있다.

### Infrastructure and resource limits

- `enforce_report_evidence_immutability()`는 두 테이블의 모든 `UPDATE`·`DELETE`
  시도마다 PL/pgSQL 함수를 호출한다. 두 테이블 다 정상 운영에서는 INSERT만
  발생하므로 실질 오버헤드는 없다.

### Database and migrations

- **트리거가 합법적인 향후 삭제 경로를 막는다.** `report_content_snapshot`은
  `purge_after`/`legal_hold` 컬럼을 갖고 있고 설계 문서 §12는 "`purge_after`
  만료 스냅샷 정리 배치"를 `#157`의 범위로 명시한다. 그런데 이 이슈가 만든
  트리거는 **조건 없이** 모든 `DELETE`를 거부한다. `TRUNCATE`(전체 삭제)는
  가능하지만 `#157`이 필요로 하는 "만료된 행만 선택 삭제"는 현재 스키마로는
  **불가능하다.** `#157`은 세션 GUC(`current_setting`) 기반 예외 조건이나
  전용 정리 함수(`SECURITY DEFINER` + 트리거 우회) 중 하나를 트리거에
  추가해야 한다. 지금 막아 두지 않으면 이 사실을 놓치기 쉬우므로 명시적으로
  남긴다.
- `report_case`의 `severity`/`queue` 컬럼은 `NOT NULL`이지만 이 이슈는 항상
  `NORMAL`/`STANDARD`로만 채운다. `#156`이 실제 산출 로직을 추가할 때
  기존 열린 사건들의 값을 재계산할지(백필) 아니면 새로 열리는 사건부터만
  적용할지는 아직 결정되지 않았다.

### Concurrency and idempotency

- INT-001은 정확히 2개의 동시 트랜잭션만 검증한다. 실제 대량 신고
  상황(수십 개 동시 신고)에서의 인덱스 경합 동작은 다르지 않을 것으로
  예상되지만(부분 유일 인덱스는 N-way에도 같은 방식으로 동작), 실측하지는
  않았다.
- `Report.attachToCase`의 멱등성(UNIT-005)은 순수 도메인 객체 수준에서만
  검증했다. 두 프로세스가 동시에 같은 신고를 같은 사건에 연결하려는
  실제 DB 레벨 경쟁(`updateReport` 두 번 동시 호출)은 테스트하지 않았다 —
  `#154`가 실제 병합 서비스를 만들 때 함께 검증해야 한다.

### Transactions and event ordering

- 신고 저장과 증거 스냅샷 저장이 **같은 트랜잭션**에 있어야 한다는 요구
  (`INV-RPT-003`)는 스키마(FK, PK)가 그것을 지원한다는 것만 이 이슈에서
  증명했다. 실제로 두 저장을 하나의 트랜잭션으로 묶어 호출하는 서비스는
  `#154`가 아직 만들지 않았으므로, 그 서비스가 완성되기 전까지는
  `INV-RPT-003`이 애플리케이션 수준에서는 아직 강제되지 않는다.
- `ck_notification_target`이 세 대상 전부 `NULL`인 경우(값 0개)를 허용하는지는
  `num_nonnulls(...) <= 1` 논리상 자명하지만 별도로 테스트하지 않았다.

### External APIs

- 해당 없음 — 이 이슈는 외부 연동을 갖지 않는다.

### Failure recovery and reconciliation

- append-only 트리거 위반은 애플리케이션에 일반
  `DataIntegrityViolationException`으로만 전달된다. 도메인 오류 코드로 옮기는
  번역기는 이 스키마를 실제로 호출하는 이슈(`#154` 이후)가 추가해야 한다 —
  지금은 이 예외가 그대로 500으로 노출될 수 있다(GlobalExceptionHandler의
  기본 처리 경로 의존).
- Flyway 트랜잭션 경계에 기대어 V17의 부분 반영 가능성은 별도로 재현하지
  않았다(V1~V16과 동일한 신뢰 경계).

## 7. Regression and residual risk

- `V16` 파일명 충돌은 이번엔 로컬에서 빌드 시점에 잡혔지만, 여러 이슈가
  동시에 진행되는 동안 마이그레이션 버전 번호가 다시 충돌할 수 있다.
  `#154`~`#157`은 착수 시점에 `ls src/main/resources/db/migration`으로 최신
  버전 번호를 다시 확인해야 한다.
- **`#154`(신고 접수 API)와 `#155`(집계 제외·결과 알림) 이슈 본문에 "Foundation의
  V16 사용"이라는 문구가 있다 — 실제로는 V17이므로 이슈 본문 정정이
  필요하다.** (이 세션에서 GitHub 이슈 본문을 직접 정정했다 — 완료 보고 참고.)
- `report_case.severity`/`queue`가 항상 `NORMAL`/`STANDARD`인 상태로 시스템에
  존재할 수 있는 기간(이 이슈 머지 후 `#156` 머지 전)에는 `CRITICAL` 신고가
  들어와도 `URGENT` 대기열로 라우팅되지 않는다. 이 이슈만 단독 배포하면
  아직 아무 API도 이 값을 채우지 않으므로 실사용자 영향은 없다.
- §6의 append-only 트리거 삭제 불가 문제는 `#157` 착수 전 반드시 설계에
  반영해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-153-TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION.md`
- CI run: 로컬 실행만 수행함 (PR 생성 전, GitHub Actions 미실행)
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md` (신규 저장소의 JDBC 선택 근거)
- Design doc: `docs/product/ANSWER_REPORT_DESIGN.md`
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (§1 Unverified scope, §6)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (§6·§7에서 `#154`~`#157` 명시)
- [ ] 실행 결과와 PR 설명이 일치함 — PR 미생성으로 확인 보류
