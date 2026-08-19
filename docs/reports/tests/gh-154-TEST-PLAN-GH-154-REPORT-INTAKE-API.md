# Test Report: TEST-PLAN-GH-154-REPORT-INTAKE-API

> Created at: `2026-08-18T23:33:18+09:00`
> GitHub Issue: `#154`
> Branch: `feat/gh-154-report-intake-api`
> Commit: `4b8bc4e` (pre-commit — 이 보고서는 커밋 전 작업 트리 기준이다)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 테스트 계획의 UNIT-001~020(21개 테스트 메서드로 구현),
  INT-001~015(16개 테스트 메서드 + 기존 `OpenApiSpecificationIntegrationTest`
  재실행) 전부 구현·실행. `ReportSubmission` 도메인 값 객체,
  `SafetyReportService`(자기 신고 거절·rate limit·열람 자격·멱등/병합/억제
  분기·`blockAuthor` 트랜잭션 통합), `ReportTargetRepository`(답변/질문글/
  사용자 열람 자격+콘텐츠 조회), `ReportCaseRepository.tryOpen/
  findOpenByTarget`(사건 병합), `SafetyRepository` 3개 신규 메서드,
  `safety/web` REST 계층 8개 엔드포인트, 신규 오류 코드 6개.
- Unverified scope: 이 이슈의 승인된 범위 밖이라 의도적으로 실행하지 않았다.
  - 집계 제외 2계층, 결과 알림 fan-out(`#155`).
  - 심각도 산출, 대기열 라우팅, 운영자 판정 API(`#156`).
  - rate limit·설명 길이 상한의 실제 운영 수치 검증 — 테스트는 주입한
    임의값(10건/시간, 500자)의 경계만 확인했다.
  - 사용자 신고 "관계 확인" 기준의 실제 제품 정책 검증 — 이 이슈는
    ASSUMED로 채택한 정의(직접 송수신 또는 같은 질문글의 co-recipient)로만
    검증했다.
  - 실제 HTTP 요청(JWT 인증 포함)을 통한 전체 스택 e2e 검증 — 차단/조회
    엔드포인트는 컨트롤러가 감싸는 서비스 메서드를 직접 호출해 검증했고,
    HTTP 계약(상태 코드·인증 401·요청 검증 400)은 MockMvc-standalone
    단위 테스트로 별도 검증했다. 이 분리는 저장소의 기존 관례
    (`AnswerSubmissionApiMockMvcTest`+`AnswerSubmissionApiIntegrationTest`,
    `AppealApiSpec`+`AppealCaseIntegrationTest`)를 그대로 따른 것이다.
- Release recommendation: 로컬 검증(`./harness pr-ready --project-tests`,
  `./harness check`, 전체 unit·integration)이 모두 통과했다. §6에 기록한
  잠재 문제 중 사건 병합 재시도 소진 시나리오(`SAF-INFRA-002`)는 실제로
  재현하지 못했다는 점을 명시한다.

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
| `./gradlew test` (전체) | PASS | 643, 실패 0 | 15.4s | `build/test-results/test/*.xml` |
| `./gradlew integrationTest` (전체) | PASS | 493, 실패 0 | 40.7s | `build/test-results/integrationTest/*.xml` |
| `./gradlew test --tests "com.dnd.qello.safety.*"` | PASS | 21(신규), 실패 0 | — | `TEST-...ReportSubmissionTest.xml`, `TEST-...ReportResponseContractTest.xml`, `TEST-...SafetyControllerMockMvcTest.xml` |
| `./gradlew integrationTest --tests "com.dnd.qello.ReportIntakeApiIntegrationTest"` | PASS | 16, 실패 0 | — | `TEST-com.dnd.qello.ReportIntakeApiIntegrationTest.xml` |
| `./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest"` | PASS | 재생성 확인 | — | `docs/api/openapi.json` 갱신됨(git diff로 확인) |
| `./harness check` | PASS | — | — | 규약·훅·라벨·워크플로 검사 전부 통과 |
| `./harness test-run --id TEST-PLAN-GH-154-REPORT-INTAKE-API` | PASS | 위 전체 재확인 | 10m 27s | 이 보고서 스캐폴드 생성 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `ReportSubmissionTest#acceptsSexualContentWithCsam` | |
| UNIT-002 | PASS | `ReportSubmissionTest#acceptsViolenceWithCredibleThreat` | |
| UNIT-003 | PASS | `ReportSubmissionTest#rejectsMismatchedPairing` | `SAF-VAL-007` |
| UNIT-004 | PASS | `ReportSubmissionTest#acceptsReasonWithoutSubReason` | |
| UNIT-005 | PASS | `ReportSubmissionTest#rejectsOtherWithoutDetail` | `SAF-VAL-006`, null과 공백 둘 다 검증 |
| UNIT-006 | PASS | `ReportSubmissionTest#acceptsOtherWithDetail` | |
| UNIT-007 | PASS | `ReportSubmissionTest#detailIsOptionalForNonOtherReasons` | |
| UNIT-008 | PASS | `ReportSubmissionTest#rejectsDetailExceedingMaxLength` | |
| UNIT-009 | PASS | `ReportResponseContractTest#reasonResponseHasExactlyFourFields` | |
| UNIT-010 | PASS | `ReportResponseContractTest#onlyOtherRequiresDetail` | |
| UNIT-011 | PASS | `ReportResponseContractTest#receiptResponseHasExactlyFiveFields` | INV-RPT-005 |
| UNIT-012 | PASS | `ReportResponseContractTest#summaryAndDetailResponsesExposeNoInternalFields` | INV-RPT-005 |
| UNIT-013 | PASS | `SafetyControllerMockMvcTest#reportAnswerRequiresAuthentication` | |
| UNIT-014 | PASS | `SafetyControllerMockMvcTest#reportAnswerRejectsMissingReasonCode` | |
| UNIT-015 | PASS | `SafetyControllerMockMvcTest#reportAnswerReturnsCreatedForNewReport` | |
| UNIT-016 | PASS | `SafetyControllerMockMvcTest#reportAnswerReturnsOkForAlreadyReceived` | |
| UNIT-017 | PASS | `SafetyControllerMockMvcTest#reportReasonsReturnsCatalog` | |
| UNIT-018 | PASS | `SafetyControllerMockMvcTest#findReportReturnsNotFoundForMissingOrForeignReport` | |
| UNIT-019 | PASS | `SafetyControllerMockMvcTest#blockPassesAuthenticatedUserAsBlocker`, `#releaseBlockReturnsOk` | 2개 메서드로 분리 구현 |
| UNIT-020 | PASS | `SafetyControllerMockMvcTest#blockRejectsSelfBlock` | |
| INT-001 | PASS | `ReportIntakeApiIntegrationTest#submitCreatesReportSnapshotCaseAndEventAtomically` | INV-RPT-003 |
| INT-002 | PASS | `#resubmitReturnsExistingOpenReport` | |
| INT-002b | PASS | `#resubmitIsIdempotentWhenExistingReportAwaitsMoreInfo` | Foundation INV-RPT-002 API 레벨 회귀 확인 |
| INT-003 | PASS | `#concurrentReportsFromDifferentReportersMergeIntoOneCase` | INV-RPT-001, 2-way 동시성 |
| INT-004 | PASS | `#resubmitAfterResolutionIsSuppressedWhenContentUnchanged` | |
| INT-004b | PASS | `#resubmitAfterResolutionCreatesNewCaseWhenContentChanged` | |
| INT-005 | PASS | `#reportingUnviewableAnswerIsNotFound` | |
| INT-006 | PASS | `#reportingOwnAnswerIsRejected` | `SAF-DOM-003` |
| INT-007 | PASS | `#exceedingRateLimitIsRejected` | `SAF-APP-004` |
| INT-008 | PASS | `#blockAuthorOptionCreatesBlockInSameTransaction` | |
| INT-009 | PASS | `#reportingUnrelatedUserIsNotFound` | |
| INT-010 | PASS | `#reportingMatchedUserSucceedsAndCapturesNickname` | |
| INT-011 | PASS | `#reportingPostSucceeds` | |
| INT-012 | PASS | `#findMyReportsReturnsOwnReportsInDescendingOrder` | |
| INT-013 | PASS | `#findReportReturnsNotFoundForNonOwner` | 404(403 아님) |
| INT-014/015 | PASS | `#blockAndReleaseServiceCallsSucceed` | 계획의 MockMvc HTTP 경로 대신 서비스 직접 호출로 구현(§5 참고) |
| INT-015(OpenAPI) | PASS | `OpenApiSpecificationIntegrationTest`(기존, 재실행) | `docs/api/openapi.json` 재생성·커밋 대상 |
| INT-016 | PASS | `#concurrentReportsFromSameReporterAreIdempotent` | PR #167 리뷰 후속(Major), 같은 신고자 동시 재신고 |
| INT-017 | PASS | `#concurrentReportsFromSameReporterEnforceRateLimitAtomically` | PR #167 리뷰 후속(Major), rate limit 원자성 |
| INT-018 | PASS | `#blockAuthorFailureRollsBackReportSnapshotAndCase` | PR #167 리뷰 후속(Major), blockAuthor 실패 시 전체 롤백 |

## 5. Failures and diagnostics

구현 과정에서 발견해 즉시 수정한 4건. 최종 실행 결과에는 실패가 남아 있지 않다.

1. **`SELECT_VIEWABLE_ANSWER`가 답변 작성자 본인만 열람 가능하게 만드는
   조인 버그.** 초기 구현은 신고 대상 답변의 소유 `post_recipient` 행에
   그대로 조인해 열람 자격을 판정했다 — 그 결과 그 답변을 쓴 recipient
   자신만 조건을 만족하고, 발신자나 다른 recipient는 항상 대상을 찾지
   못했다. `PostAnswerQuerySql.CAN_VIEW_ANSWERS_SQL`처럼 조회자 소유의
   `post_recipient` 행을 독립적인 `EXISTS` 서브쿼리로 분리해 해결했다.
2. **통합 테스트 fixture가 답변을 `PUBLISHED`로 전이하지 않음.** 초기
   fixture는 `Answer.submit(...)`만 호출해 상태가 `SUBMITTED`로 남아
   있었다 — `SELECT_VIEWABLE_ANSWER`는 `PUBLISHED`만 대상으로 하므로
   모든 답변 신고 시나리오가 "대상 없음"으로 실패했다. `Answer.restore(...)`
   로 `PUBLISHED` 상태를 직접 구성하도록 fixture를 고쳤다.
3. **`post_recipient.status = 'ANSWERED'`를 raw SQL로 직접 삽입하면
   `enforce_post_recipient_capacity_release` 지연 트리거가 거절함.**
   `ANSWERED`는 `capacity_released_at`이 같은 문장이 아니라 별도 갱신으로
   함께 기록돼야 하는 제약이 있다(#93/#94). fixture는 `AVAILABLE` +
   만료 전 조건으로 우회했다 — 이 값은 열람 자격 판정에는 동등하게
   작동한다.
4. **사용자 신고 관계 판정이 co-recipient 관계를 인정하지 않음.** 초기
   구현은 신고자-대상 간 직접 발신자/수신자 관계만 확인해, 같은 질문글의
   서로 다른 recipient끼리는 신고할 수 없었다. `#79` 이후 recipient 간
   답변 상호 열람이 이미 허용된 점을 반영해 co-recipient 관계도 인정하도록
   `SELECT_VIEWABLE_USER`를 넓혔다(ASSUMED, §6 참고).

## 6. Potential issues

### Application code

- `mergeCase`의 재시도 소진 경로(`SAF-INFRA-002`)는 코드로는 존재하지만
  실제로 재현하는 테스트를 작성하지 않았다 — 승자의 사건이 재조회 시점
  사이에 종결되는 경쟁은 매우 좁은 시간창이 필요해 결정론적으로
  재현하기 어렵다. 코드 리뷰로만 검증했다.
- `ReportSubmission`의 사유-하위사유 조합 검증과 Foundation의
  `ck_report_sub_reason` DB CHECK가 같은 규칙을 두 곳에서 유지한다.
  둘 중 하나만 바뀌면 조용히 어긋날 수 있다 — 두 규칙이 항상 함께
  바뀌어야 한다는 사실이 코드 주석 외에는 강제되지 않는다.
- 사용자 신고 "관계" 판정(co-recipient 포함)은 제품 정책이 확정되면
  다시 좁혀야 할 수 있는 ASSUMED 결정이다(설계 문서 §2 Excluded).

### Infrastructure and resource limits

- rate limit 카운트 쿼리(`countReportsByReporterSince`)는 `report.created_at`
  전체 스캔이 아니라 `reporter_id` 조건과 함께 실행되므로, `report` 테이블에
  `reporter_id` 인덱스가 없으면 신고자가 늘어날수록 이 쿼리 비용이
  커진다. 현재 `report` 테이블에는 `reporter_id`를 선두 컬럼으로 하는
  인덱스가 없다(기존 인덱스는 `target_user_id`/`direction_post_id`/
  `answer_id` 기준). 트래픽이 커지면 `report (reporter_id, created_at)`
  인덱스 추가를 검토해야 한다.

### Database and transactions

- 신고 접수 트랜잭션은 `report` INSERT → 사건 병합(`tryOpen`/
  `findOpenByTarget`) → `attachToCase` → 스냅샷 INSERT → 이벤트 INSERT →
  (옵션) 차단 INSERT까지 하나의 `@Transactional` 경계 안에 있다. `blockAuthor`
  옵션이 `SafetyService.block(...)`을 호출할 때 Spring 프록시 전파
  (`REQUIRED`, 기본값)로 같은 트랜잭션에 합류함을 INT-008로 확인했다.
  다만 INT-008은 성공 경로만 확인했다 — 차단 삽입이 실패할 때 report·
  스냅샷·사건까지 실제로 롤백되는지는 이 실행 당시 별도로 검증하지
  않았고, §1 요약에 그대로 PASS로만 표기해 커버리지를 과장했다(PR #167
  리뷰 지적). PR #167 리뷰 후속으로 `INT-018`
  (`#blockAuthorFailureRollsBackReportSnapshotAndCase`)을 추가해 실패
  주입 후 `report`/`report_content_snapshot`/`report_case` 행이 모두
  없음을 확인했다.
- `findMostRecentClosedReport`는 `resolved_at DESC, id DESC` 정렬이다.
  같은 신고자가 같은 대상을 여러 번 신고·종결한 이력이 쌓이면 항상
  "가장 최근에 종결된" 건과만 비교한다 — 더 오래된 종결 건과 내용이
  같아도 억제되지 않는다. 이는 설계상 의도된 동작이지만 명시적으로
  검증하는 시나리오는 없다.

### Concurrency and idempotency

- INT-003은 정확히 2명의 동시 신고자만 검증한다. Foundation의 자체
  검증과 마찬가지로 N-way 동시성은 부분 유일 인덱스가 같은 방식으로
  처리할 것으로 예상되지만 실측하지 않았다.
- (해소, PR #167 리뷰 후속) 같은 신고자가 같은 대상에 정말로 동시에
  요청하는 경쟁은 `SafetyReportService.submit`이 `acquireReporterSubmissionLock`
  (`pg_advisory_xact_lock`)으로 신고자 단위 제출을 직렬화해 없앴다 —
  두 번째 트랜잭션은 첫 번째가 커밋한 뒤에야 `findOpenReport`를 실행하므로
  `uq_open_report_*` 위반 자체가 더 이상 발생하지 않는다.
  `INT-016`(`#concurrentReportsFromSameReporterAreIdempotent`)으로 검증했다.

### Transactions and event ordering

- (해소, PR #167 리뷰 후속) rate limit 카운트(`countReportsByReporterSince`)와
  신고 저장이 분리된 연산이라 동시 요청이 함께 한도를 통과할 수 있던
  경합은 `acquireReporterSubmissionLock`으로 없앴다 — 같은 신고자의 두
  트랜잭션이 더 이상 동시에 카운트를 읽지 않는다. `INT-017`
  (`#concurrentReportsFromSameReporterEnforceRateLimitAtomically`)로,
  한도 직전 동시 요청 중 정확히 1건만 성공함을 검증했다.

### External APIs

- 해당 없음 — 이 이슈는 외부 연동을 갖지 않는다.

### Failure recovery and reconciliation

- 사건 병합 재시도 소진(`SAF-INFRA-002`)이 실제 사용자에게 어떻게
  보이는지(재시도 안내 문구, 클라이언트의 재시도 정책)는 이 이슈
  범위에서 다루지 않았다 — 발생 자체가 극히 드물 것으로 예상된다.

## 7. Regression and residual risk

- `SELECT_VIEWABLE_ANSWER`/`SELECT_VIEWABLE_USER`는 이번에 새로 만든
  쿼리이며 `PostAnswerQuerySql`/`FeedScopeSql`의 기존 열람 자격 규칙을
  재사용했지만 완전히 동일한 코드 경로는 아니다 — 두 규칙이 갈라지면
  "피드에서는 보이는데 신고는 안 되는" 또는 그 반대 불일치가 생길 수
  있다. 향후 `FeedScopeSql`이 바뀌면 이 파일도 함께 검토해야 한다.
- `#155`(집계 제외·결과 알림)는 이 이슈가 만든 `SafetyReportService.submit`
  의 반환값과 `report_case_event`를 그대로 소비할 것으로 설계됐다 —
  인터페이스가 실제로 맞는지는 `#155` 착수 시 재확인이 필요하다.
- `#156`(심각도·대기열)이 `ReportSubmission.subReason()`을 읽어 severity를
  산출할 예정이다. 이 이슈는 `ReportCase.open()`이 항상 `NORMAL`/`STANDARD`로
  여는 것을 그대로 두었다 — `#156` 착수 시 `mergeCase`가 severity를
  주입받도록 확장해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-154-TEST-PLAN-GH-154-REPORT-INTAKE-API.md`
- CI run: 로컬 실행만 수행함 (PR 생성 전, GitHub Actions 미실행)
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md`(신규 저장소의 JDBC 선택 근거),
  `docs/adr/0005-api-success-response-contract.md`(`ApiResponse` 계약)
- Design doc: `docs/product/ANSWER_REPORT_DESIGN.md`
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (§1 Unverified scope, §6)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (§7에서 `#155`·`#156` 명시)
- [ ] 실행 결과와 PR 설명이 일치함 — PR 미생성으로 확인 보류
