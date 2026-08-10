# Test Report: TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE

> Created at: `2026-08-10T21:59:34+09:00`
> GitHub Issue: `#96`
> Branch: `test/gh-96-inbox-detail-scope`
> Commit: `a791c2a` (implementation and tests verified)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 계획의 UNIT-001~003, INT-001~008과 전체 unit/integration 회귀
- Unverified scope: CI workflow와 실제 API controller 경로는 이슈 범위 밖이며 실행하지 않음
- Release recommendation: 테스트 기준으로 구현 검토 가능. 커밋·PR 전 독립 검증과 사람 리뷰 필요.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 25.0.3 LTS |
| Spring Boot | 저장소 Gradle 설정 기준 |
| Database | PostgreSQL/PostGIS Testcontainers |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` | PASS | 182 passed, 0 failed | 3s | `build/test-results/test/*.xml`; focused `build/test-results/test/TEST-com.dnd.qello.feed.FeedPersistenceBoundaryTest.xml` |
| `./gradlew test --tests '*FeedPersistenceBoundaryTest'` | PASS | 6 passed, 0 failed | 1s | `build/test-results/test/TEST-com.dnd.qello.feed.FeedPersistenceBoundaryTest.xml` |
| `./gradlew integrationTest` | PASS | 196 passed, 0 failed | 1m 42s build | `build/test-results/integrationTest/*.xml`; focused `build/test-results/integrationTest/TEST-com.dnd.qello.InboxDetailScopeIntegrationTest.xml` and `TEST-com.dnd.qello.InboxQueryIntegrationTest.xml` |
| `./gradlew compileIntegrationTestJava` | PASS | — | — | `build/classes/java/integrationTest` produced; compilation completed before focused run |
| `git diff --check` | PASS | — | — | no whitespace errors |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-UNIT-001 | PASS | `FeedPersistenceBoundaryTest#recipientViewPolicyUsesExplicitAtAndPreservesAnsweredAfterExpiry` | ANSWERED 예외와 `:at` 바인딩 확인 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-UNIT-002 | PASS | `FeedPersistenceBoundaryTest#detailAndAnswerQueriesShareRecipientViewPolicy` | 목록·상세 공통 post visibility와 답변 공통 eligibility 확인 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-UNIT-003 | PASS | `FeedPersistenceBoundaryTest#detailContractRequiresAtAndOptionalResult` | 명시적 `Instant at` 및 Optional 반환 계약 확인 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-001 | PASS | `InboxDetailScopeIntegrationTest#skippedDetailIsEmpty` | SKIPPED 상세 차단 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-002 | PASS | `InboxDetailScopeIntegrationTest#expiredWithoutAnswerIsEmptyAndAtIsExplicit` | EXPIRED 및 만료 경계 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-003 | PASS | `InboxDetailScopeIntegrationTest#answeredDetailAndAnswersRemainVisibleAfterExpiry` | ANSWERED 사후 열람 유지, 목록 만료 계약 회귀 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-004 | PASS | `InboxDetailScopeIntegrationTest#skipPendingRemainsVisibleBeforeExpiry` | SKIP_PENDING 상세·목록·답변 자격 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-005 | PASS | `InboxDetailScopeIntegrationTest#activeSenderBlockHidesDetailAndReleaseRestoresIt` | 활성 차단 및 해제 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-006 | PASS | `InboxDetailScopeIntegrationTest#nonexistentAndUnauthorizedDetailsAreIndistinguishable` | 존재 여부와 권한 차이 비노출 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-007 | PASS | `InboxDetailScopeIntegrationTest#inactiveAndDeletedPostsAreHidden` | 비활성·삭제 질문글 차단 |
| TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE-INT-008 | PASS | `InboxDetailScopeIntegrationTest#statusAndTimeMatrixMatchesRecipientEligibilityPolicy` | 8개 recipient 상태 × 만료 전후 매트릭스 |

## 5. Failures and diagnostics

- 최초 샌드박스 실행의 `./harness test-run`은 Gradle wrapper cache의 lock 파일
  쓰기 권한 거부로 중단되었다. 동일 명령을 승인된 workspace 권한으로 재실행했고
  단위·통합 전체가 PASS했다. 이 환경 실패는 코드 실패로 분류하지 않는다.
- 테스트 실패·컴파일 실패·Testcontainers 기동 실패는 재현되지 않았다.
- `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`는
  최종 작업 디렉터리에서 실행해 PASS했다. 이 로컬 명령은 CI artifact를 만들지
  않으므로 Gradle 결과는 위 XML 경로를 증거로 사용한다.

## 6. Potential issues

### Application code

- 상세 조회에 `Instant at`을 명시적으로 전달하고, 공통 feed scope SQL fragment를
  목록·상세·답변 경로에서 재사용한다.

### Infrastructure and resource limits

- Testcontainers/PostGIS 로컬 자원에 의존한다. CI 자원 부족 여부는 별도 확인이
  필요하다.

### Database and migrations

- 기존 PostgreSQL/PostGIS schema와 migration만 사용했다. migration 변경과 운영
  백필은 실행하지 않았다.

### Concurrency and idempotency

- 이번 변경은 read-only query라 신규 write idempotency는 없다. 공유 컨테이너
  격리 때문에 통합 테스트는 병렬 실행하지 않았다.

### Transactions and event ordering

- 조회는 기존 `readOnly` transaction 경계를 유지한다. 상태 전이·Outbox·이벤트
  순서는 변경하지 않았다.

### External APIs

- 외부 API 연동 없음.

### Failure recovery and reconciliation

- 조회 변경이므로 보상·백필·대사 작업은 범위 밖이다. 자격 없는 ID는 예외 대신
  빈 결과로 처리되는 것을 통합 테스트로 확인했다.

## 7. Regression and residual risk

- 전체 단위 182건과 통합 196건이 통과했다. 기존 `InboxQueryIntegrationTest`
  11건과 `PostAnswerQueryIntegrationTest` 7건도 회귀 통과했다.
- 구조적 SQL/reflection 단위 가드는 구현 구조에 민감하므로 SQL fragment 이름이나
  메서드 계약을 바꾸는 후속 작업에서는 해당 테스트와 계획을 함께 갱신해야 한다.
- 아직 컨트롤러가 없어 HTTP response shape와 인증 wiring은 검증하지 않았다.
- 공통 SQL fragment의 실제 실행은 PostgreSQL/PostGIS 통합 테스트로 확인했지만,
  향후 상태 추가 시 정책 fragment와 매트릭스 테스트를 함께 갱신해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-96-TEST-PLAN-GH-96-INBOX-DETAIL-SCOPE.md`
- CI run: 실행하지 않음
- Related ADR: `docs/adr/0002-jpa-jdbc-boundary.md`
- PR: 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — 후속 Issue 없음
- [ ] 실행 결과와 PR 설명이 일치함 — PR 미생성
