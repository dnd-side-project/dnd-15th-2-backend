# Test Report: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API

> Created at: `2026-08-14T00:48:51+09:00`
> GitHub Issue: `#121`
> Branch: `feat/gh-121-active-user-presence-api`
> Commit: `WORKTREE (미커밋)`

## 1. Executive summary

- Result: `PASS`
- Tested scope: presence 정책 설정·도메인·service, JWT 본인 API, 조건부 PostgreSQL/PostGIS
  UPSERT, preview/matching 후보 회귀, OpenAPI 생성과 위치 정보 비노출
- Unverified scope: 실제 모바일 위치 수집 주기, 운영 rate limit, 배포·운영 모니터링,
  장기간 만료 행 정리 정책
- Release recommendation: 로컬 필수 검증과 독립 리뷰 통과 후 병합 가능

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Java 21 toolchain, OpenJDK 25.0.3 launcher |
| Spring Boot | 3.5.16 |
| Database | Testcontainers PostgreSQL 16 / PostGIS 3.5 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests 'com.dnd.qello.direction.*' --no-daemon` | PASS | 83 | 약 4초 | Gradle XML: failures/errors 0 |
| `./gradlew integrationTest --tests 'com.dnd.qello.ActiveUserPresenceApiIntegrationTest' --no-daemon` | PASS | 6 | 약 11초 | Gradle XML: failures/errors 0 |
| `./gradlew integrationTest --tests 'com.dnd.qello.ActiveUserPresenceConfigurationIntegrationTest' --tests 'com.dnd.qello.ActiveUserPresenceUpdateIntegrationTest' --tests 'com.dnd.qello.DirectionPreviewIntegrationTest' --tests 'com.dnd.qello.OpenApiSpecificationIntegrationTest' --no-daemon` | PASS | 15 | 약 27초, Gradle XML: failures/errors 0 |
| `./harness check` | PASS | — | — | secret/JUnit/convention/workflow/label/Husky checks passed |
| `./harness pr-ready --project-tests` | PASS | repository suite | 약 2분 35초 | `check` + unit/integration tests passed |
| `npm run hooks:validate` / `git diff --check` | PASS | — | — | Husky validation and whitespace checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~003, UNIT-011 | PASS | `ActiveUserPresenceTest`, `DirectionPresenceServiceTest` | 좌표·정확도·관측 시각·발신/수신 의미 분리 |
| UNIT-004~005, UNIT-007, UNIT-010 | PASS | `ActiveUserPresenceWebContractTest`, `ActiveUserPresenceApiIntegrationTest` | nullable Boolean, JWT subject, DTO privacy와 API 경계 |
| UNIT-006 | PASS | `DirectionPresenceServiceTest` | ACTIVE USER 및 서버 권위 지역만 허용 |
| UNIT-008 | PASS | `ActiveUserPresenceUpdateIntegrationTest` | affected row가 `applied`로 전달됨 |
| UNIT-009, INT-012 | PASS | `DirectionPresencePropertiesTest`, `ActiveUserPresenceConfigurationIntegrationTest` | 네 설정의 binding·override·관계 fail-fast 및 TTL 계산 |
| INT-001~005, INT-009 | PASS | `ActiveUserPresenceApiIntegrationTest` | 본인 소유권, 인증/계정 상태, validation, stale no-op, 서버 지역 |
| INT-006 | PASS | `ActiveUserPresenceUpdateIntegrationTest.concurrentUpdatesKeepNewestObservation` | 두 thread 경합 후 newest-wins |
| INT-007~008 | PASS | `ActiveUserPresenceUpdateIntegrationTest`, `DirectionPreviewIntegrationTest` | `expires_at == at` 경계·만료/수신 거부 후보 제외 |
| INT-010 | PASS | `ActiveUserPresenceUpdateIntegrationTest.databaseFailureDoesNotPartiallyUpdateExistingPresence` | FK 제약 실패 후 기존 행 보존과 조건부 UPSERT 원자성 |
| INT-011 | PASS | `OpenApiSpecificationIntegrationTest.documentsActiveUserPresenceContract` | PUT, Bearer, 필드 제한, response privacy와 artifact 생성 |
| INT-013 | PASS | `DirectionPreviewIntegrationTest.receiveDeniedSenderCanPreviewButIsNotCandidate` | 수신 거부 발신자 preview 허용, 후보 제외 |

## 5. Failures and diagnostics

- TDD RED 1: production 타입이 없는 상태에서 `compileTestJava`가
  `DirectionPresenceProperties`, `DirectionPresenceService`, 현재 위치/수신 자격 메서드 부재로 실패했다.
- TDD RED 2: 초기 무조건 UPSERT가 equal 관측에도 `applied=true`를 반환해
  `ActiveUserPresenceUpdateIntegrationTest.onlyAppliesNewerObservation`이 실패했다.
- TDD RED 3: endpoint 부재 상태에서 presence API 정상 호출이 성공 상태가 아니어서 실패했다.
- 위 실패는 각각 최소 policy/service/domain 구현, 별도 `UPSERT_IF_NEWER`, ApiSpec/Controller 구현 후 GREEN으로 전환했다.
- 테스트 자체 assertion과 import 오류 두 건은 테스트 코드 교정 후 다시 실행했다. 제품 결함으로 분류하지 않았다.
- 한 번의 병렬 integrationTest 실행에서는 공유 Gradle XML writer 충돌이 발생했으나,
  대상 suite를 단독 재실행했고 각 assertion은 모두 통과했다. 최종 `pr-ready --project-tests`도
  단일 실행으로 통과했다.

## 6. Potential issues

### Application code

- `receiveAllowed`는 현재 위치 유효성과 분리했다. 기존 `isCurrentAt`은 수신 자격 의미를
  보존하고 발신 경로만 `hasCurrentLocationAt`을 사용한다.
- API request `toString()`은 전체를 redaction하며 성공 response는 `applied`만 반환한다.

### Infrastructure and resource limits

- 인프라 변경과 외부 리소스 사용은 없다. 운영 rate limit과 지표/알람은 Issue 범위 밖이다.

### Database and migrations

- migration은 추가하지 않았다. 기존 `active_user_presence` schema와 제약을 그대로 사용한다.
- 기존 `save`의 무조건 UPSERT는 fixture/기존 호출 호환을 위해 유지했고 API만
  `saveIfNewer`와 별도 조건부 SQL을 사용한다.

### Concurrency and idempotency

- `location_at` 비교가 PostgreSQL의 행 충돌 처리 안에서 실행되어 동시 요청 순서와 무관하게
  가장 최신 관측만 남는다. 동일 관측 시각은 결정적 no-op이다.

### Transactions and event ordering

- presence 갱신은 Account 조회와 한 UPSERT를 같은 Spring transaction에서 실행한다.
- Outbox/이벤트 발행은 없으므로 외부 side effect 순서와 보상은 필요 없다.

### External APIs

- 외부 API 호출은 없다. JWT 검증은 Spring Security test support로 격리했다.

### Failure recovery and reconciliation

- 실패한 갱신은 저장 전 validation 또는 원자적 UPSERT 실패로 DB를 변경하지 않는다.
- 만료 행 정리 job은 없으며 후보 SQL이 `expires_at > at`을 강제한다.

## 7. Regression and residual risk

- `DirectionPreviewIntegrationTest`, `DirectionPostgisPersistenceIntegrationTest`,
  `DirectionRecipientSelectionIntegrationTest`, `DirectionMatchingWorkerIntegrationTest`와 관련 unit 회귀가 통과했다.
- 연결 단절과 같은 실제 장애 복구 및 API 외부 응답까지의 장애 주입은 미실행이다. FK 제약
  실패 시 기존 행을 보존하는 DB rollback 경계는 통합 테스트로 확인했고, 운영 장애 복구는
  관측으로 보완해야 한다.
- 모바일 앱이 24시간 내 현재 위치를 갱신하지 않으면 마지막 위치 기준으로 매칭될 수 있다.
  MVP 승인 정책이며 설정으로 독립 조정 가능하다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-121-TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API.md`
- Generated OpenAPI: `docs/api/openapi.json`
- CI run: 미실행 (PR/원격 CI는 생성하지 않음)
- Related ADR: 해당 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 Issue 필요 여부가 기록됨
- [ ] 실행 결과와 PR 설명이 일치함 — PR 미생성
