# Test Report: TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING

> Created at: `2026-10-02T16:52:25+09:00`
> GitHub Issue: `#301`
> Branch: `feat/gh-301-question-recommendation-listing`
> Commit: `b8ec5a3` (작업 내용은 아직 커밋하지 않은 상태에서 실행했다)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 추천 질문 조회 서비스의 계정 자격·Clock 기준 조회, HTTP 응답 필드와 401, ApiSpec·Controller 분리, PostgreSQL 활성 기간 경계 필터, 추천 질문 id로 질문 전송
- Unverified scope: 실제 security filter chain을 거친 401(standalone MockMvc는 컨트롤러의 `AuthenticatedUserId.require` 경로만 검증한다). `/api/**` 인증은 기존 `appApiSecurityFilterChain`이 담당한다.
- Release recommendation: 병합 가능. 아래 잠재 문제 중 오류 메시지 문구는 후속 판단이 필요하다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Gradle toolchain) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostGIS(local Docker) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests 'com.dnd.qello.question.*'` | PASS | question 패키지 | 5s | 로컬 실행 |
| `./gradlew integrationTest --tests 'com.dnd.qello.QuestionRecommendationIntegrationTest'` | PASS(2차) | 2 | 9s | 1차 실패는 5절 |
| `./harness test-run --id TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING` | PASS | unit 1,089 / integration 749, 실패·오류·skip 0 | 6m 22s | `build/test-results` 집계 |
| `./harness pr-ready --project-tests` (3차) | PASS | unit 1,089 / integration 749, 실패·오류·skip 0 | 6m 44s | 1·2차는 5절 |
| `npm run hooks:validate`, `git diff --check` | PASS | - | - | 로컬 실행 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `QuestionRecommendationServiceTest.returnsAssignableQuestionsAtClockInstant`, `returnsEmptyListWhenNoQuestionIsActive` | |
| UNIT-002 | PASS | `QuestionRecommendationServiceTest.rejectsOperatorAccount`, `rejectsInactiveUserAccount`, `rejectsMissingAccount` | D-1 확정에 따라 포함 |
| UNIT-003 | PASS | `QuestionRecommendationApiMockMvcTest.returnsRecommendedQuestions` | 내부 필드 미노출 단언 포함 |
| UNIT-004 | PASS | `QuestionRecommendationApiMockMvcTest.requiresAuthentication` | |
| UNIT-005 | PASS | `QuestionProposalWebContractTest.keepsRecommendationApiBoundaryTypesSeparated` | |
| INT-001 | PASS | `QuestionRecommendationIntegrationTest.returnsOnlyQuestionsActiveAtNowInIdOrder` | 경계 7종 |
| INT-002 | PASS | `QuestionRecommendationIntegrationTest.recommendedQuestionIsAcceptedBySubmission` | |

## 5. Failures and diagnostics

- INT-002 1차 실행이 `DirectionException`(방향 필수 값 누락)으로 실패했다. 원인은 테스트 fixture가 `bodyText`를 null로 넘긴 것이며, 질문 전송 정책(`DirectionPostPolicy.validateContent`)이 본문을 요구한다. fixture에 본문을 넣어 재실행했고 통과했다. production 코드는 바꾸지 않았다.
- `pr-ready --project-tests` 1차는 spotless 위반으로 실패했다. fixture를 sed로 고친 줄이 포맷 규칙과 달랐다. `spotlessApply` 후 재실행했다.
- `pr-ready --project-tests` 2차는 테스트 환경 문제로 멈췄다.
  - 실패한 명령: `./harness pr-ready --project-tests`의 `integrationTest`
  - 오류 요약: 통합 테스트 JVM이 Spring 컨텍스트 시작 중 Flyway의 PostgreSQL 접속 인증 응답을 기다리며 58분간 진행하지 않았다(thread dump: `ConnectionFactoryImpl.doAuthentication`의 socket read). DB에는 활성 쿼리와 lock 대기가 없었다.
  - 재현 조건: 재현하지 못했다. 같은 코드로 직후 실행한 3차는 6m 44s에 통과했다.
  - 조치: 이 worktree에서 띄운 테스트 JVM과 `pr-ready` 프로세스만 종료하고 재실행했다.
  - 미검증 범위: 없음(3차에서 전체 통과).
  - 남은 위험: 테스트 데이터소스에 connect/socket timeout이 없어 같은 상황에서 CI도 job timeout까지 멈출 수 있다. 이번 Issue 범위 밖이다.

## 6. Potential issues

### Application code

- 계정 자격 오류에 기존 `PROPOSER_ACCOUNT_NOT_FOUND`·`PROPOSER_ACCOUNT_NOT_ELIGIBLE`을 재사용한다(D-1 "findMine과 같은 오류"). 응답 메시지가 "질문을 제안할 수 없습니다"라서 추천 조회 문맥과 맞지 않는다. 전용 오류 코드 추가 여부는 사람의 판단이 필요하다.
- 계정 자격 검사가 `QuestionProposalApplicationService`와 중복된다. 추천 배정 구현 시 공통화를 검토한다.

### Infrastructure and resource limits

- 활성 질문 풀 전체를 페이지 없이 반환한다. 풀은 운영자 승인 수로 제한되지만 상한이 없다. 추천 배정 전환 시 cycle당 배정 수로 제한된다.

### Database and migrations

- 스키마 변경 없음. ACTIVE 질문은 `approved_by`(user_account FK)가 필요해 fixture에서 승인자 계정을 먼저 만든다.

### Concurrency and idempotency

- 읽기 전용 GET이며 해당 없음.

### Transactions and event ordering

- 클래스 단위 `@Transactional(readOnly = true)`. 쓰기와 이벤트 발행 없음(INT-001에서 행 수 불변 확인).

### External APIs

- 해당 없음.

### Failure recovery and reconciliation

- 목록 조회와 전송 사이에 질문 기간이 끝나면 전송이 `QUESTION_NOT_ACTIVE`로 거부된다. operation description에 재조회를 안내했다.

## 7. Regression and residual risk

- 기존 단위·통합 테스트 전체가 통과했다. 기존 엔드포인트 동작 변경은 없다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-301-TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING.md`
- CI run: 미실행(PR 생성 전)
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
