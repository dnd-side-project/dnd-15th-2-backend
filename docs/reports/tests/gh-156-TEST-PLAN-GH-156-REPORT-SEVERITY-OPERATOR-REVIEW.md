# Test Report: TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW

> Created at: `2026-08-21T01:20:00+09:00`
> GitHub Issue: `#156`
> Branch: `feat/gh-156-report-severity-operator-review`
> Commit: 작업 중(아직 커밋 전) — base `bbecf3e`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 심각도 산출·큐 라우팅(순수 함수), `ReportCase` 도메인 전이
  (escalate/deescalate/requestMoreInfo), 신고 접수 시 승격(잠금 포함)과
  SLA 재계산, 자동 전역 숨김(신고자 수 임계·`ManualReviewCase` 상관관계),
  운영자 판정 REST API 5개 엔드포인트, 신규 세션 기반 보안 필터체인
  (`/api/v1/operator/**`)과 기존 JWT bearer 체인의 격리, 두 운영자 동시
  판정 방지.
- Unverified scope: §6·§7 참고 — SLA 알림의 실제 발송(범위 밖으로 확정),
  대기열 조회 API의 실제 실행계획(인덱스 미검증), N-way(3+) 동시 승격
  경합.
- Release recommendation: 병합 가능. 남은 위험은 §7에 기록.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Gradle 테스트 실행 JVM) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (로컬 Docker) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체 unit) | PASS | 783 | ~10s | `build/test-results/test/` |
| `./gradlew integrationTest` (전체 integration) | PASS | 581 | 7m18s | `build/test-results/integrationTest/` |
| `com.dnd.qello.safety.ReportCaseAndEvidenceTest`(unit, 신규 시나리오 포함) | PASS | 24 | 0.005s | `TEST-com.dnd.qello.safety.ReportCaseAndEvidenceTest.xml` |
| `com.dnd.qello.ReportCaseSeverityIntegrationTest`(신규) | PASS | 8 | 0.478s | `TEST-com.dnd.qello.ReportCaseSeverityIntegrationTest.xml` |
| `com.dnd.qello.OperatorReportCaseIntegrationTest`(신규) | PASS | 10 | 1.794s | `TEST-com.dnd.qello.OperatorReportCaseIntegrationTest.xml` |
| `*Flyway*`(V24 회귀) | PASS | 전체 | 17s | 별도 실행 로그 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~003 | PASS | `ReportCaseAndEvidenceTest#criticalSubReasonsProduceCriticalSeverity` 외 2건 | 심각도·큐 순수 함수 |
| UNIT-004~010 | PASS | `ReportCaseAndEvidenceTest#opensCaseWithCriticalSeverity` 외 6건 | escalate/deescalate/requestMoreInfo/withLinkedManualReviewCase |
| UNIT-011~012 | PASS | `ReportCaseAndEvidenceTest#slaPolicyRejectsNonPositiveDuration` 외 3건 | SlaPolicy/AutoSuppressionPolicy 값 검증 |
| UNIT-013(회귀) | PASS | `ReportCaseAndEvidenceTest` 기존 6건 갱신 | `ReportCase.open` 신규 시그니처 반영 |
| INT-001 | PASS | `ReportCaseSeverityIntegrationTest#newCaseOpensAsCriticalWhenSubReasonIsCsam` | |
| INT-002 | PASS | `#escalatesExistingNormalCaseWhenCriticalReportAttaches` | ESCALATED+REPORT_ATTACHED 둘 다 확인 |
| INT-003 | PASS | `#concurrentEscalationProducesExactlyOneEscalatedEvent` | 2-way 동시 승격 경합 |
| INT-004 | PASS | `#doesNotDeescalateWhenNormalReportAttachesToCriticalCase` | |
| INT-005 | PASS | `#autoSuppressesWhenDistinctReporterThresholdIsReached` + `#doesNotAutoSuppressBelowReporterThreshold` | 기본 임계값(5) 그대로 사용 |
| INT-006 | PASS | (INT-005에 통합) | 미도달 케이스 |
| INT-007 | PASS | `#autoSuppressesWhenManualReviewCaseIsOpen` | `linked_manual_review_case_id` 기록 확인 |
| INT-008 | PASS | `#doesNotAutoSuppressWhenManualReviewCaseIsResolvedAllow` | |
| INT-009 | 대체 확인 | (신규 테스트 없음, 아래 §6 참고) | 이 경로로는 실제 도달 불가 — 기존 `ReportCaseFoundationIntegrationTest#allowsNewCaseForTargetWithResolvedCase`(INV-RPT-007)가 이미 커버 |
| INT-010 | PASS | `OperatorReportCaseIntegrationTest#queueMarksOverdueCases` | |
| INT-011 | PASS | `#queueFiltersByQueueParameter` | |
| INT-012 | PASS | `#startReviewTransitionsCase` | |
| INT-013 | PASS | `#decideActionedHidesAnswerAndRecordsInternalNote` | |
| INT-014 | PASS | `#decideNoViolationDoesNotHideAnswer` | |
| INT-015 | PASS | `#requestMoreInfoKeepsCaseOpen` | |
| INT-016 | PASS | `#restoreBringsAnswerBackToPublished` | |
| INT-017 | PASS | `#concurrentDecisionResolvesOnce` | 200/400 정확히 하나씩, `moderation_review` 정확히 1건 |
| INT-018 | PASS | `#queueRequiresOperatorSession` | 세션 없음 → 401 |
| INT-019 | PASS | `#appAccessTokenCannotReachOperatorApi` | **이 계획에서 가장 중요한 시나리오** — 앱 JWT bearer로 발급한 토큰이 `/api/v1/operator/**`에서 거절됨을 확인, 신규 `operatorReportCaseSecurityFilterChain`이 `appApiSecurityFilterChain`보다 먼저 평가됨을 실증 |
| INT-020 | 코드 검사로 대체 | — | `moderation_review`는 `JdbcSafetyRepository.saveReview()`의 INSERT 한 곳에만 등장(`grep` 확인). 신고자 응답 DTO(`ReportSummaryResponse`/`ReportDetailResponse`)는 구조적으로 `internal_note` 필드가 없어 애초에 노출 불가 — 이 계약은 #156이 건드리지 않은 기존 코드라 신규 통합 테스트를 추가하지 않았다 |
| INT-021(회귀) | PASS | `ReportCaseFoundationIntegrationTest#allowsNewCaseForTargetWithResolvedCase`(#154, 기존) | INV-RPT-007 |

## 5. Failures and diagnostics

실패 없음. 구현 중 두 가지를 계획에서 조정했다:

- INT-009는 계획 당시 "자동 숨김된 사건에 새 신고가 오면 재개방이 아니라
  새 사건이 열리는지"를 직접 검증하려 했으나, `findViewableAnswer`가
  `status = 'PUBLISHED'`만 조회해 답변이 이미 `HIDDEN`이면 재신고 자체가
  `REPORT_TARGET_NOT_FOUND`로 막힌다는 것을 구현 중 발견했다. 이 경로로는
  시나리오 자체가 도달 불가능해 테스트를 추가하지 않고, 같은 불변식을
  이미 검증하는 기존 테스트로 대체했다(§4 참고).
- INT-006은 별도 시나리오로 만들지 않고 INT-005 테스트 파일에 두 번째
  `@Test`(`doesNotAutoSuppressBelowReporterThreshold`)로 병기했다 — 같은
  fixture 헬퍼를 재사용하는 것이 더 명확했다.

## 6. Potential issues

### Application code

- `OperatorReportCaseService.decide()`가 `MORE_INFO_REQUIRED`를 명시적으로
  거절한다(`/more-info` 전용 API로 분리) — 이 가드 자체는 테스트했지만
  (`decide` 호출 시 `MORE_INFO_REQUIRED`를 넘기는) 음성 테스트는 계획에
  없었다. 위험은 낮다(가드가 컴파일 타임이 아니라 런타임이라 실수로
  MORE_INFO_REQUIRED를 보내면 400은 나지만 그 400 자체를 검증하는 테스트는
  없음).

### Infrastructure and resource limits

- 없음 — 이 이슈는 외부 인프라 연동이 없다.

### Database and migrations

- `V24__add_report_case_sla_and_manual_review_link.sql`이
  `linked_manual_review_case_id`에 `filtering.manual_review_case`로의 FK를
  걸지 않았다(TASK.md ASSUMED 결정, opaque id). 스키마 레벨에서 참조
  무결성이 강제되지 않으므로, 향후 `manual_review_case` 행이 삭제되면
  (현재 저장소에 삭제 경로는 없음) `report_case.linked_manual_review_case_id`가
  고아 참조가 될 수 있다 — 현재는 이론적 위험이다.
- `report_case.sla_due_at NOT NULL` 컬럼을 기존 행 없는 전제로 바로
  추가했다(다른 V14/V20 마이그레이션과 같은 관례). 운영 배포 시점에 실제
  기존 행이 있는지는 이 계획 밖에서 재확인이 필요하다.

### Concurrency and idempotency

- INT-003(승격 경합)과 INT-017(판정 경합) 모두 2-way만 검증했다. 3개
  이상의 동시 요청(N-way)은 측정하지 않았다 — `#155`의 test-report도 같은
  한계를 이미 기록했다(선례 일관성 유지).
- `escalateIfMoreSevere`의 `findByIdForUpdate` 잠금과 `mergeCase`의 기존
  승자/패자 재조회 루프(`tryOpen` 실패 시 재시도)가 함께 동작할 때
  데드락 가능성을 이론적으로 검토했으나(다른 순서로 여러 락을 잡지
  않음을 코드 리뷰로 확인), 실제 데드락 로그·타임아웃 계측은 하지
  않았다.

### Transactions and event ordering

- `SafetyReportService.submit()` 안에서 `mergeCase`(승격 포함) →
  report/스냅샷 저장 → `autoSuppressionEvaluator.evaluate(...)`가 모두
  같은 `@Transactional` 경계에 있음을 코드로 확인했다. 자동 숨김
  트랜잭션 중간 실패(예: `SafetyCaseResolutionService.resolveCase` 내부의
  outbox 저장 실패)가 신고 저장까지 함께 롤백되는지는 `#154`의
  `blockAuthorFailureRollsBackReportSnapshotAndCase` 패턴을 이 이슈에서는
  재현하지 않았다 — `#155`가 이미 `resolveCase` 자체의 원자성을
  검증했다는 전제로 재검증을 생략했다(계획 §2 Excluded와 일치).

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- `OperatorReportCaseService.restore()`는 행 잠금 없이 동작한다(계획
  단계에서 의도적으로 범위 축소, TASK.md에 명시). 두 운영자가 동시에
  같은 사건을 복원 시도하면 `Answer.restore()`의 상태 가드(HIDDEN만
  허용)가 두 번째 시도를 막지만, 이 경로의 동시성은 이 계획에서
  테스트하지 않았다.

## 7. Regression and residual risk

- 전체 unit 783건, integration 581건 모두 통과 — 이번 변경이 기존 계약을
  깨지 않았다.
- 남은 위험(우선순위순): (1) INT-019가 검증한 보안 경계가 이 기능
  전체에서 가장 중요한 방어선이므로, 향후 `SecurityConfiguration`을
  건드리는 어떤 변경도 이 테스트의 회귀 여부를 반드시 확인해야 한다.
  (2) N-way 동시성 미측정(§6). (3) `linked_manual_review_case_id`의 FK
  부재(§6, ASSUMED 결정 — 사용자 재확인 필요하면 후속 이슈로).
- 후속 이슈 후보: SLA 실제 알림 발송(push/Slack), `operator_action_audit`
  확장(§ TASK.md Scope decision 3), N-way 동시성 실측.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-156-TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW.md`
- CI run: 아직 push 전 — PR 생성 후 채운다.
- Related ADR: `docs/adr/0006-split-operator-and-device-authentication.md`(신규 보안 필터체인 근거)
- PR: 아직 생성 전.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨(§1 Unverified scope, §6)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 아직 이슈를 만들지 않음, PR 리뷰 후 필요하면 생성
- [ ] 실행 결과와 PR 설명이 일치함 — PR 생성 시 재확인
