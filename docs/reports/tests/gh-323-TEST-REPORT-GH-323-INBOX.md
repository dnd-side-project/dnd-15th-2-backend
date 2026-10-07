# Test Report: TEST-PLAN-GH-323-INBOX

> Report ID: `TEST-REPORT-GH-323-INBOX`
> Created at: `2026-10-07T21:58:31+09:00`
> GitHub Issue: `#323`
> Branch: `feat/gh-323-inbox-category-removal`
> Commit: `1a3b1254` (base HEAD; tested changes are uncommitted)
> Task ID: `GH-323-INBOX-CATEGORY-REMOVAL`
> Design ID: 해당 없음

## 1. Executive summary

- Result: `PASS` — 승인된 구현과 모든 필수 로컬 검증 완료. 독립 검증·사람의 승인과 구분한 실행 결과다.
- Tested scope: category 없는 HTTP 목록, 내부 ALL 합집합, 미답변 칩, 소유권·가시성·엄격한 만료 경계, 방향 필터, 5개 초과 전체 반환과 정렬, 동일 snapshot, 기존 API 회귀와 생성 OpenAPI.
- Full suites: 단위 1,225개 + 통합 794개 = 2,019개, 실패·오류·skip 각 0.
- Unverified scope: 독립 검증자의 최종 보고와 사람의 최종 리뷰는 부모 워크플로에서 별도 수행한다. CI·배포·실제 운영 호출은 수행하지 않음. 차단된 필수 로컬 검사는 없음.
- Release recommendation: 필수 로컬 검증을 통과했으며 독립 검증 후 사람 리뷰로 진행한다. 이 보고서는 설계·구현의 자체 승인이 아님.

## 2. Environment

런타임·일반 환경 설명만 기록하며 자격 증명과 운영 식별자는 포함하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Eclipse Temurin 21.0.12.1 |
| Spring Boot | 3.5.16 |
| Gradle | Wrapper 8.14.3 |
| Database | 실제 PostgreSQL/PostGIS Testcontainers, synthetic fixtures |
| Test runner | JUnit 5, Gradle test / integrationTest source sets |
| Clock | 기존 테스트의 고정 Instant 및 서버 Clock 경계 유지 |
| Execution roles | 실행 에이전트가 소스·보고서 작성; 부모가 승인된 privileged Gradle 명령 실행; 독립 검증자는 별도 역할 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| 수정 전 집중 `test` + `integrationTest` (5 inbox 클래스) | PASS | 5개 클래스 기준선 통과 | 39s | `/private/tmp/gh323-baseline.log` |
| HTTP 계약 RED: `test --tests '*InboxWebContractTest'` | EXPECTED RED | 3 / 1 expected failure | 1s | `/private/tmp/gh323-red-http.log` |
| 혼합 상태 RED: 집중 5개 클래스, `--continue` | EXPECTED RED | 44 / 10 expected failures / 0 errors | 원문 요약 참고 | `/private/tmp/gh323-red-behavior.log` |
| `spotlessJavaApply` + 집중 GREEN (5개 클래스) | PASS | 44 / 0 failures / 0 errors | 원문 요약 참고 | `/private/tmp/gh323-focused-green.log`, JUnit XML |
| `./harness test-run --id TEST-REPORT-GH-323-INBOX` → `./gradlew test` | PASS | 188 classes / 1225 tests / 0 failures / 0 errors / 0 skipped | 39s | `/private/tmp/gh323-full-test-run.log`, `build/test-results/test/TEST-*.xml` |
| 같은 harness test-run → `./gradlew integrationTest` | PASS | 103 classes / 794 tests / 0 failures / 0 errors / 0 skipped | 7m 12s | 같은 로그, `build/test-results/integrationTest/TEST-*.xml` |
| `./harness check` | PASS | 정책 검사 전체 통과 | 해당 없음 | `/private/tmp/gh323-harness-check.log` |
| `npm run hooks:validate` | PASS | Husky 검증 통과 | 해당 없음 | `/private/tmp/gh323-hooks-validate.log` |
| `git diff --check` | PASS | 공백 검사 통과 | 해당 없음 | 명령 exit 0 |
| 생성 OpenAPI 비교 (HEAD 대비 구조 및 sent 도달 schema 비교) | PASS | inbox query는 directionSegmentKey만; 인증/status 유지; sent 전부 동일 | 해당 없음 | `docs/api/openapi.json`, 실행된 Python 구조 비교 |
| `./harness pr-ready --project-tests` | PASS | origin/main 동기화 검사 + harness 전체 + Gradle check 통과 | Gradle check 7s | `/private/tmp/gh323-pr-ready.log` |

Raw test logs may contain synthetic runtime connection details; they remain local, are not pasted into this report, and must be reviewed/redacted before sharing. XML aggregate counts and sanitized outcomes are the recorded evidence.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-323-INBOX-UNIT-001 | PASS | InboxApiMockMvcTest.listDelegatesFiltersAndReturnsPrivacySafeApiResponse | category 없는 요청이 ALL/N으로 위임; AVAILABLE·ANSWERED status 유지 및 내부 식별자·정확 좌표 비노출 |
| TEST-PLAN-GH-323-INBOX-UNIT-002 | PASS | InboxApiMockMvcTest.ignoresCategoryParameterAndListsAllDirections; listDelegatesFiltersAndReturnsPrivacySafeApiResponse | 방향 생략/null 및 N 유지; 알 수 없는 category 요청도 binding 없이 ALL 조회 |
| TEST-PLAN-GH-323-INBOX-UNIT-003 | PASS | InboxWebContractTest.declaresApprovedMappingsAndOnlyQueryFilters; InboxApiMockMvcTest 전체 회귀 | 목록 signature에서 category 제거, 선택 방향 유지; 인증·미디어·상세·명령 응답 회귀 및 생성 스펙 인증 확인 |
| TEST-PLAN-GH-323-INBOX-INT-001 | PASS | InboxQueryIntegrationTest.allIncludesOnlyOwnedEligibleStatuses | AVAILABLE/DISCOVERED/OPENED/SKIP_PENDING/ANSWERED만 포함; 타인 및 종료 상태 제외 |
| TEST-PLAN-GH-323-INBOX-INT-002 | PASS | InboxQueryIntegrationTest.allAppliesStrictExpiryBoundaryToBothCategories | 미답변·답변 완료 각각 만료 직전 포함, 동일 및 직후 제외; worker 미실행 |
| TEST-PLAN-GH-323-INBOX-INT-003 | PASS | InboxDirectionChipIntegrationTest.allChipsCountOnlyUnansweredAcrossAllDirections; allWithOnlyAnsweredHasNoChips | 같은 방향 미답변/ANSWERED 및 답변 완료 전용 방향; 칩은 미답변만, 답변 완료만 있으면 빈 배열 |
| TEST-PLAN-GH-323-INBOX-INT-004 | PASS | InboxDirectionChipIntegrationTest.allChipsCountOnlyUnansweredAcrossAllDirections | 방향 생략/N/공백/알 수 없는 키; 필터는 카드에만 적용, 칩은 전체 미답변 |
| TEST-PLAN-GH-323-INBOX-INT-005 | PASS | InboxQueryIntegrationTest.allReturnsUnboundedMixedCardsInStableOrder | 미답변 3 + 답변 완료 3 총 6개 전체 반환; matched_at DESC, 동률 id DESC |
| TEST-PLAN-GH-323-INBOX-INT-006 | PASS | InboxListIsolationIntegrationTest.chipCountIgnoresRowsCommittedAfterFindInbox; filteredListChipsStayOnTheSameSnapshot | 최초 미답변/ANSWERED 목록 뒤 새 미답변 별도 commit; 같은 호출의 칩 snapshot 유지 |
| TEST-PLAN-GH-323-INBOX-INT-007 | PASS | InboxQueryIntegrationTest.allPreservesVisibilityForBothCategories; 기존 InboxApiIntegrationTest 및 ReportSuppressionIntegrationTest 회귀 | ALL 대표 차단·삭제·비공개 fixture; 기존 양방향 차단·계정 gate·신고 답변 집계·개인정보 조건 유지 |
| TEST-PLAN-GH-323-INBOX-INT-008 | PASS | 변경 없는 OpenApiSpecificationIntegrationTest 및 생성 artifact 구조 비교 | generator가 기록한 inbox category 제거, 방향·인증·status 유지; 다른 API paths와 sent 도달 schema가 HEAD와 동일 |

기존 내부 UNANSWERED/ANSWERED 분리, 방향 구간 경계, 상세 열람/넘김/되돌리기, 실패 rollback, 미디어 및 공감 집계 테스트를 전체 suite에서 함께 실행했다. JUnit 테스트 정책 검사는 변경 클래스의 기존 정확한 생성 시각과 추가 시나리오 기록 및 DisplayName을 확인했다.

## 5. Failures and diagnostics

- 초기 sandbox 실행은 Gradle wrapper cache lock의 쓰기 권한으로 차단되었다. 승인된 escalation으로 기준선 실행을 완료했다. 환경 차단을 RED로 계산하지 않았다.
- 자식의 추가 privileged RED 요청이 approval 대기 중 취소되어 실행되지 않았다. 부모가 동일 명령을 실행하고 실제 DB RED 결과를 전달했다. 이후 privileged Gradle 실행은 부모가 담당했다.
- HTTP RED: category 없는 list signature가 없어 계약 테스트가 실패했다.
- 전체 RED: web contract 3/1, MockMvc 10/3, query 15/3, chips 13/2, isolation 3/1. ALL 위임과 답변 완료 합집합이 없어 발생한 예상 실패였으며 환경 오류·fixture 제약 오류는 0이었다.
- GREEN 이후 full unit/integration에 실패·오류·skip 없음. 실패 suppress, hook 우회, 소스 외 승인 게이트 변경 없음.
- Worktree git status에서 fsmonitor IPC 경고가 관찰되었다. 읽기/공백 확인은 command-local `git -c core.fsmonitor=false`로 수행했으며 저장소 설정을 변경하지 않았다.
- 재검증: Java 21/Docker 환경에서 위 집중·전체 명령을 재실행하고 JUnit XML과 생성 스펙을 확인한다.

## 6. Potential issues

### Application code

- HTTP 기본 목록에 ANSWERED가 추가되므로 클라이언트가 cards.status로 화면을 구분해야 한다. #323의 승인된 동작이며 응답 필드는 유지된다.
- ALL은 명시적 다섯 상태만 허용하고 기존 내부 두 category 경로도 유지한다. 이번 변경에서 추가 결함은 발견되지 않았다.

### Infrastructure and resource limits

- 새로운 리소스·권한·인프라 변경 없음. 검증에는 Docker 및 테스트 컨테이너 메모리/디스크가 필요하다.
- 페이지네이션 없는 결과는 답변 후 슬롯 반환으로 5개를 넘을 수 있다. 6개 synthetic fixture를 검증했으며 대규모 성능 benchmark는 승인 범위 밖이다. #323 범위의 잔여 운영 위험으로 기록한다.

### Database and migrations

- schema·migration·state 변경 없음. 기존 소유권, 공개/삭제/차단, 만료 SQL과 제약을 유지했다.
- Testcontainers 실제 DB로 status timestamps 및 DELETED fixture 제약을 확인했다. 테스트 reset은 기존 isolated fixture lifecycle을 따랐다.

### Concurrency and idempotency

- 목록과 칩 사이의 별도 transaction commit을 실제 DB로 검증했다. ALL 카드와 UNANSWERED 칩의 서로 다른 상태 범위가 한 snapshot을 공유한다.
- 조회는 쓰기·슬롯 변경·신규 멱등 처리 없음. 대량 동시 요청 성능 검증은 포함하지 않았다.

### Transactions and event ordering

- 기존 REPEATABLE_READ/read-only 경계와 단일 Clock 시각 유지. 답변·넘김 상태 전이와 이벤트 발행은 변경하지 않았다.
- 기존 상세 projection 실패 rollback 회귀도 실행했다. 새로운 transaction/event 위험은 관찰되지 않았다.

### External APIs

- 새 외부 연동 없음. 기존 media 테스트 double과 synthetic fixture만 사용했다. 실제 운영 자격 증명이나 API 호출 없음.
- 외부 timeout/retry 정책 변경은 범위 밖이다.

### Failure recovery and reconciliation

- 복구는 기능 변경 commit revert로 기존 category HTTP 계약을 되돌리는 방식이다. DB 데이터 복구·migration·대사 작업은 필요하지 않다.
- 최종 PR readiness 또는 독립 검증에서 실패가 나오면 결과를 갱신하고 수정 후 해당 검사를 재실행해야 한다. 발견된 추가 결함/후속 Issue는 현재 없음.

## 7. Regression and residual risk

- 전체 2,019개 테스트가 통과했다. 기존 sent API production 및 응답을 수정하지 않았다.
- OpenAPI 변경 path는 `/api/v1/direction/inbox` 하나, 변경 schema는 InboxListingResponse 하나다. Sent operations 및 그들이 참조하는 모든 schema는 HEAD와 구조적으로 같다.
- 클라이언트 status 분류 및 unbounded 결과 크기 위험은 #323 승인 범위에 포함된다. 운영 부하·프론트 배포·CI는 이 로컬 검증의 증거에 포함되지 않는다.
- 실행 단계 필수 검사 전부 PASS이며 failed_checks / blocked_checks는 빈 목록이다. 독립 검증과 사람의 리뷰 결과는 부모가 별도 보고한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-323-TEST-PLAN-GH-323-INBOX.md` (사용자 승인 기록 포함)
- Task contract: `TASK.md`
- Generated API: `docs/api/openapi.json` (직접 편집 없음)
- Machine results: `build/test-results/test/`, `build/test-results/integrationTest/`
- Operational handoff: `.superpowers/sdd/gh-323-TEST-PLAN-GH-323-INBOX/task-1-report.md`
- Local sanitized outcomes: 위 execution 표의 로컬 로그 경로; 원문 공유 금지
- Related ADR: ADR-0002 (accepted JPA/JDBC 경계); ADR-0005 (proposed 성공 응답; 기존 코드와 승인된 TASK 계약을 기준으로 유지)
- CI run: 미실행
- PR: 미생성; commit/push도 수행하지 않음

## 9. Reviewer checklist

- [x] 보고서에 .env 값이나 비밀정보·운영 식별자가 없음
- [x] 미실행·대기 검증과 운영 범위가 명시됨
- [x] 잠재 문제와 범위상 잔여 위험은 #323에 연결됨; 새 결함/후속 Issue 없음
- [x] PR readiness 최종 결과 반영
- [x] 독립 검증 결과 반영 (명세·품질·증거 모두 PASS; 사람 최종 리뷰는 GitHub PR에서 별도 수행)
- [ ] PR 설명과 실행 결과 일치 확인 (PR 미생성)

## 10. Commit handoff and Hook exception

- 2026-10-07 부모 세션에서 커밋 3개·PR 초안·Java 구현 커밋 한 개의 Hook 예외를 제시한 뒤 사용자가 “PR 올려줘”로 진행을 지시했다. PR은 Ready for review, 리뷰어는 `@tkv00`, GitHub Project 상태는 In Progress 유지다.
- 계약·계획 commit: `66f8b01`. 구현·테스트·생성 OpenAPI commit: `5b77a69`. 위 전체 suite가 확인한 production/test 내용은 구현 commit에 포함된다.
- 첫 문서 커밋은 일반 `git commit`으로 실행했지만 worktree에 Husky runner가 설치되지 않아 Hook이 실행되지 않았다. `npm ci`의 기존 prepare 절차로 runner를 설치했다. 해당 commit은 `git show --format= --check HEAD`, `python3 scripts/run-hook.py commit-msg /private/tmp/gh323-commit1-message.txt`, 전체 `python3 scripts/preflight.py` 및 하네스 검사로 보완했고 전부 exit 0이었다. 저장소 정책·Hook 코드는 변경하지 않았다.
- Java 구현 commit은 linked worktree Hook의 Git 환경이 `ChangedJavaTypesTest`의 임시 Git 저장소에 전파되는 위험을 피하려고 `--no-verify`를 한 번 사용했다. 우회 Hook은 `pre-commit`·`commit-msg`이며 `prepare-commit-msg`는 정상 실행했다.
- 동일 staged 검사 명령: `env -u GIT_DIR -u GIT_WORK_TREE -u GIT_INDEX_FILE -u GIT_COMMON_DIR JAVA_HOME=<java-21-home> python3 scripts/run-hook.py pre-commit`. 결과 exit 0: branch/convention, staged 공백·민감정보, 실제 `javaConventionStagedCheck`와 scoped Spotless·architecture, JUnit policy 전부 PASS. 로그: `/private/tmp/gh323-manual-pre-commit.log` (원문 공개 금지).
- 동일 메시지 검사 명령: `python3 scripts/run-hook.py commit-msg /private/tmp/gh323-commit2-message.txt`. 실제 구현 commit 메시지의 formatter·branch/type/issue 검사 전부 PASS, exit 0.
- 남은 위험: 한 commit은 Hook 자체 강제가 아니라 동일 검사 수동 실행 증거로 보완했다. 저장소의 Git 환경 전파 문제는 이번 기능 승인 범위 밖이며 수정하지 않았다. 이후 문서 commit 및 pre-push는 설치된 정상 Hook을 실행한다. GitHub CI와 사람 리뷰는 별도 확인한다.
