# Test Report: TEST-PLAN-GH-212-INBOX-LIST-ISOLATION

> Created at: `2026-09-02T19:45:00+09:00`
> GitHub Issue: `#212`
> Branch: `fix/gh-212-inbox-list-isolation`
> Commit: `46377d8` (HEAD of `origin/main`; implementation is uncommitted)

## 1. Executive summary

- Result: `PASS`
- Tested scope: `InboxApplicationService.list()` REPEATABLE_READ 시작, 목록/칩 snapshot
  동시성, 상세 OPENED rollback, class read-only ratchet, TX-003 same-class 한정,
  기존 수신함 목록·칩 HTTP 단위/통합 회귀, `javaConventionCheck`, `./harness check`
- Unverified scope: 전체 `./gradlew check`, `./harness pr-ready --project-tests`,
  원격 GitHub Actions
- Release recommendation: 로컬 구현 검증은 통과했다. 커밋은 별도 사람 승인 후
  `/harness-commit`으로 진행한다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Toolchain 21 (`build.gradle`) |
| Spring Boot | 3.5.x |
| Database | Docker Testcontainers PostgreSQL/PostGIS (`PostgisContainerIntegrationTestSupport`) |
| Test runner | JUnit 5 |
| Gradle | wrapper |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Annotation + application unit | PASS | 11 then 4+7 after UNIT-006 | ~2s | `./gradlew test --tests '*InboxApplicationServiceTransactionBoundaryTest' --tests '*InboxApplicationServiceTest'` |
| Isolation integration (before fix) | RED then used as evidence | 3, 2 failed | ~11s | INT-001 chip count 2; INT-002 chips `N,S`; INT-003 PASS |
| Isolation integration (after fix) | PASS | 3 | ~8s | `./gradlew integrationTest --tests '*InboxListIsolationIntegrationTest'` |
| Inbox API integration regression | PASS | `InboxApiIntegrationTest` | included in 13s integration run | `./gradlew integrationTest --tests '*InboxApiIntegrationTest'` |
| Convention ratchet | PASS | architecture + baseline + spotless | ~7s | `./gradlew javaConventionCheck` after TX-003 same-class fix |
| Inbox unit glob | PASS | `*Inbox*` | ~2s | `./gradlew test --tests '*Inbox*'` |
| Harness | PASS | n/a | under 1s | `./harness check` |
| Path diff | PASS | n/a | n/a | production 변경은 `InboxApplicationService.java`만. migration/controller/`InboxQueryService` 없음 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `InboxApplicationServiceTransactionBoundaryTest#listStartsRepeatableRead` | 수정 전 isolation `DEFAULT`로 RED, 수정 후 GREEN |
| UNIT-002 | PASS | `classIsReadOnlyDefault` | 수정 전 class annotation null로 RED |
| UNIT-003 | PASS | `transactionalMethodsArePublicAndNotSelfInvoked` | 수정 전부터 public |
| UNIT-004 | PASS | `usesConstructorInjectionOnly` | `@RequiredArgsConstructor` 유지 |
| UNIT-005 | PASS | `InboxApplicationServiceTest#listUsesSingleServerInstant` | 기존 회귀 |
| UNIT-006 | PASS | `detailThrowsWhenProjectionMissingAfterOpen` | 수정 전부터 GREEN |
| UNIT-007 | PASS | `rejectsUnknownAccount` / `rejectsIneligibleAccount` | 기존 회귀 |
| INT-001 | PASS | `InboxListIsolationIntegrationTest#chipCountIgnoresRowsCommittedAfterFindInbox` | 수정 전 expected 1L but was 2L |
| INT-002 | PASS | `filteredListChipsStayOnTheSameSnapshot` | 수정 전 chips `N,S` |
| INT-003 | PASS | `detailProjectionFailureRollsBackOpened` | 수정 전후 GREEN. OPENED 미커밋 |
| INT-004 | PASS | `javaConventionCheck`, `JavaConventionArchitectureTest#acceptsCollaboratorTransactionCall` | TX-003을 같은 클래스 self-invocation으로 한정한 뒤에야 ratchet 통과 |
| INT-005 | PASS | `InboxApiMockMvcTest`, `InboxApiIntegrationTest` | 카테고리 분리·칩 필터 의미 유지 |

## 5. Failures and diagnostics

수정 전 INT-001/002 RED는 테스트 훅 오류가 아니라 바깥 `READ_COMMITTED`가
`countByDirection`에 새 커밋을 노출한 결과다. 카드 목록은 `findInbox` 시점 값이라
기존 1건이었고, 칩만 2 또는 `S`가 추가됐다.

`javaConventionCheck` 첫 실행은 `QELLO-JAVA-TX-003` 5건으로 실패했다.
`InboxApplicationService`가 `InboxQueryService.list`와 `PostRecipientService`의
`@Transactional` 메서드를 호출하기 때문이다. 문서상 TX-003은 같은 클래스
self-invocation이다. 규칙을 origin/target owner가 같을 때만 실패하도록 고쳤다.
`SelfInvokingTransactionalService`는 계속 실패하고, collaborator fixture는 통과한다.

spotless는 static import 순서와 체인 들여쓰기를 고친 뒤 통과했다.

## 6. Potential issues

### Application code

- `InboxQueryService.list()`는 여전히 method-level `REPEATABLE_READ`를 가진다.
  직접 호출 경로의 방어일 뿐이고, 바깥 wrapper가 다시 `READ_COMMITTED`를 열면
  같은 결함이 재발한다. Wave 1B feed seam 분리가 이 wrapper를 옮길 때 회귀
  테스트 `InboxListIsolationIntegrationTest`를 유지해야 한다.

### Infrastructure and resource limits

- 원격 GitHub Actions는 실행하지 않았다.

### Database and migrations

- schema, migration, `InboxQuerySql`을 변경하지 않았다.

### Concurrency and idempotency

- 검증은 한 `list()` 호출 안의 두 SELECT 사이에 다른 트랜잭션이 commit하는
  시나리오다. skip/open 레이스는 기존 `InboxCommandConcurrencyIntegrationTest`
  소유로 남긴다.

### Transactions and event ordering

- class-level `readOnly = true`와 method-level write `@Transactional`이 공존한다.
  INT-003과 기존 상세 열람 통합 테스트가 write 커밋/롤백을 확인한다.
- PostgreSQL 읽기 전용 REPEATABLE_READ라 serialization failure는 이 경로에서
  재현하지 않았다.

### External APIs

- HTTP 경로와 오류 코드를 바꾸지 않았다. FCM/OAuth/S3를 호출하지 않았다.

### Failure recovery and reconciliation

- 상세 projection 실패는 OPENED를 남기지 않는다. 목록 조회는 읽기 전용이라
  복구 대상 write가 없다.

## 7. Regression and residual risk

- 전체 `./gradlew check`와 `./harness pr-ready --project-tests`는 이 보고서
  시점에 실행하지 않았다. 커밋 전 `/harness-commit` 또는 PR 준비에서 돌린다.
- TX-003 의미 축소는 Wave 0 ratchet의 오탐을 고친 것이다. 같은 클래스
  self-invocation은 계속 실패한다.
- 새 `LEGACY`와 isolation용 dummy `JUSTIFIED_EXCEPTION`은 추가하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-212-TEST-PLAN-GH-212-INBOX-LIST-ISOLATION.md`
- Implementation plan: `docs/superpowers/plans/2026-09-02-inbox-list-isolation.md`
- CI run: not run
- Related ADR: `DEC-212-001` through `DEC-212-005` in `TASK.md`
- PR: not created

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 (Wave 1B draft `PVTI_lADOBD3v1M4BfKwozg5C8eE`)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 미생성)
