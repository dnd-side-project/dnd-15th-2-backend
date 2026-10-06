# Test Report: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT

> Created at: `2026-10-06T14:57:36+09:00`
> GitHub Issue: `#315`
> Branch: `feat/gh-315-auth-nickname-rate-limit`
> Commit: `53af273` 기준 코드. 테스트는 커밋 전 같은 작업 트리에서 실행했다(base `18b1adc`)

## 1. Executive summary

- Result: `PARTIAL`. 승인된 계획의 단위 15개, 통합 12개 시나리오는 모두 통과했다. 다만 이 PC(Windows)에서는 전체
  단위·통합 실행에 #312가 고친 Windows 환경 실패가 남아 있어, `./harness test-run`과
  `./harness pr-ready --project-tests`가 끝까지 통과하지 못했다(5절).
- Tested scope:
  - 메모리 고정 윈도 카운터와 클라이언트 주소 키
  - 기기 등록·토큰 재발급·운영자 로그인의 IP 단위 429(`AUT-APP-007`)
  - 닉네임 변경 시도 한도(`ACC-APP-003`)와 변경 주기(`ACC-APP-004`)
  - `V31` 마이그레이션, 같은 사용자의 동시 닉네임 변경
  - 설정 주입과 `application.yml` 기본값, OpenAPI 429 문서화
- Unverified scope:
  - 다중 인스턴스와 재시작 후 카운터. Issue 제외 범위이며, 메모리 카운터라 재시작하면 초기화된다.
  - 프록시나 LB 뒤에서의 클라이언트 IP 판별
  - 운영 한도 수치의 적정성(통신사 공유 IP 실측)
  - 실제 OpenAI 호출
- Release recommendation: 기능 시나리오 기준으로 병합할 수 있다. 다만 Linux CI의 `check`(단위·통합 전체) 통과를 병합
  조건으로 둔다. 이 PC의 환경 실패 14건은 #312(PR #314)가 머지되면 사라진다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| OS | Windows 11 Enterprise, Git Bash |
| Java | OpenJDK 21.0.12 (Microsoft build) |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 (wrapper) |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` |
| Test runner | JUnit 5 |
| Python | 3.12. `origin/main`에는 #312의 Windows 수정이 없다. Gradle과 하네스의 `python3` 호출을 위해 세션 임시 venv의 `python3.exe`를 PATH 앞에 두고 실행했다(저장소 파일 변경 없음) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체 단위) | FAIL (환경 요인 12건) | 1176 실행, 1164 통과, 12 실패 | 1m 17s | 실패 12건은 모두 #312 보고서 5절의 Windows 기존 실패와 같은 테스트다(5절) |
| 이번 변경과 관련된 단위 15개 클래스 | PASS | 97 | — | 신규 `RateLimitPolicyTest`·`FixedWindowRateLimiterTest`·`ClientAddressKeyTest`·`AccountNicknameChangeTest`·`RateLimitDefaultsTest`, 수정 `NicknameRegistrationServiceTest`·`DeviceRegistrationServiceTest`·`AccountControllerMockMvcTest`·`FlywayMigrationContractTest`, 컨벤션 `ProductionConventionRatchetTest`·`JavaConventionArchitectureTest`·`JavaSourceConventionTest`·`ChangedJavaTypesTest`, 회귀 `AccountTest`·`AccountProfileImageTest` |
| `./gradlew integrationTest` (전체 통합, 1차) | FAIL | 782 실행, 778 통과, 4 실패 | 15m 26s | 2건은 이번 변경이 원인이었고 고쳤다. 2건은 Windows 환경 요인이다(5절) |
| 1차에서 실패한 마이그레이션 테스트 재실행 | PASS | 3개 클래스 | 54s | `NotificationPreferenceMigrationIntegrationTest`, `QuestionProposalDeleteMuteMigrationIntegrationTest`, `FlywayMigrationIntegrationTest` |
| 새 통합 테스트와 회귀 대상 14개 클래스 | PASS | 126 | — | `AuthRateLimitIntegrationTest` 5, `NicknameChangeLimitIntegrationTest` 5, `OpenApiSpecificationIntegrationTest` 13, 운영자 로그인 헬퍼를 쓰는 `AppealCase`·`FilterReleaseRegistry`·`ManualReviewPriority`·`OperatorReportCase`·`SnapshotHealthMigration`·`OperatorLogin`, `DeviceAuth`, `DeviceRegistrationTransaction`, `NicknameDuplicateModeration`, `AccountPersistence`, `FlywayMigration` |
| `./gradlew check --continue -x test -x integrationTest` | PASS | — | 22s | Checkstyle, `validateJavaConventionBaseline`, `javaConventionArchitectureTest`, `javaConventionSourceTest`, `spotlessCheck`, `javaConventionCheck` |
| `./harness check` | PASS | — | — | secret preflight, JUnit 정책, workflow·label·husky 검증 |
| `git diff --check` | PASS | — | — | |
| `./harness test-run --id TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT` | FAIL (환경 요인) | — | 1m 19s | 단위 단계에서 위 12건으로 중단되어 보고서 골격을 만들지 못했다. 이 보고서는 같은 `templates/test-report.md`로 직접 작성했다 |
| Git pre-commit 훅 | 우회 7건(수동 실행 PASS) | — | — | Java가 포함된 커밋 7개(`256d3c9`, `1e35bd4`, `bf54aab`, `299bb3c`, `9b38c9f`, `18de4da`, `53af273`)는 훅의 `./gradlew` 호출이 Windows에서 `WinError 193`으로 실패해 `--no-verify`로 커밋했다(사용자 승인). 커밋마다 `scripts/run-hook.py`의 pre-commit·commit-msg 단계를 같은 순서로 직접 실행했고 모두 통과했다. Gradle만 `gradlew.bat`으로 바꿔 불렀다. 문서·설정 커밋은 훅을 켠 채로 통과했다 |
| `./harness pr-ready --project-tests` | 미실행 | — | — | 내부의 `./gradlew check`가 같은 단위 실패 12건에서 멈추는 것이 확정적이다. 대신 `check`의 구성 태스크를 위 세 줄로 나눠 실행했다 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-001 | PASS | `RateLimitPolicyTest` 3건 | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-002 | PASS | `FixedWindowRateLimiterTest.allowsUpToLimitAndRejectsNext` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-003 | PASS | `FixedWindowRateLimiterTest.countsKeysIndependently` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-004 | PASS | `FixedWindowRateLimiterTest.startsNewWindowExactlyAfterWindowElapses` | `window - 1ns`는 거절, `window`는 허용 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-005 | PASS | `FixedWindowRateLimiterTest.allowsExactlyLimitUnderConcurrency` | 32 스레드·200 요청 중 정확히 10건 허용 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-006 | PASS | `FixedWindowRateLimiterTest.sweepsExpiredKeysAfterWindow`, `keepsKeysStillInsideWindow` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-007 | PASS | `ClientAddressKeyTest` 6건 | `X-Forwarded-For`·`X-Real-IP` 무시, IPv6 /64, IPv4-mapped, 빈 주소 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-008 | PASS | `AccountNicknameChangeTest` 3건 | null / 주기 안 / 경계 시각 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-009 | PASS | `NicknameRegistrationServiceTest.rejectsChangeInsideCooldownBeforeExternalCalls`, `rechecksCooldownInsideWriteTransaction` | 저장 트랜잭션 안 재확인과 rollback까지 확인 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-010 | PASS | `NicknameRegistrationServiceTest.rejectsWhenAttemptLimitExceeded`, `countsAttemptsPerUser` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-011 | PASS | `NicknameRegistrationServiceTest.recordsChangedAtOnSuccess`, `AccountNicknameChangeTest.changeNicknameRecordsChangedAt`, `changeNicknameRequiresChangedAt` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-012 | PASS | `NicknameRegistrationServiceTest.failedAttemptsCountButDoNotStartCooldown` | 409·503 실패 뒤 세 번째는 `ACC-APP-003`, 변경 시각은 null 유지 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-013 | PASS | `NicknameRegistrationServiceTest.ensureAvailableIgnoresChangeAttemptLimit`, `AccountNicknameChangeTest.createdUserHasNoNicknameChangeHistory`, `otherTransitionsKeepNicknameChangedAt` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-014 | PASS | `AccountControllerMockMvcTest.changeNicknameReturnsTooManyRequestsForAttemptLimit`, `changeNicknameReturnsTooManyRequestsForCooldown` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-015 | PASS | `RateLimitDefaultsTest` 2건 | `application.yml`만 property source로 두고 production record로 바인딩 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-001 | PASS | `AuthRateLimitIntegrationTest.rejectsDeviceRegistrationOverLimit` | 계정·자격증명 2건만 생성 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-002 | PASS | `AuthRateLimitIntegrationTest.countsByConnectionAddressOnly` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-003 | PASS | `AuthRateLimitIntegrationTest.rejectsTokenReissueOverLimit` | `last_used_at` 미갱신 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-004 | PASS | `AuthRateLimitIntegrationTest.rejectsOperatorLoginOverLimitWithoutTouchingCredential` | 세션 미생성, `failed_attempt_count` 2 유지 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-005 | PASS | `AuthRateLimitIntegrationTest.allowsAgainAfterWindowElapses` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-006 | PASS | `NicknameChangeLimitIntegrationTest.registrationNicknameDoesNotStartCooldown` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-007 | PASS | `NicknameChangeLimitIntegrationTest.enforcesCooldownWithoutCallingModeration` | moderation 호출 시점에 활성 트랜잭션이 없음도 단언 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-008 | PASS | `NicknameChangeLimitIntegrationTest.countsRejectedAttemptsTowardLimit` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-009 | PASS | `NicknameChangeLimitIntegrationTest.migrationAddsNullableTimestampColumn` | |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-010 | PASS | `NicknameChangeLimitIntegrationTest.serializesConcurrentChangesForSameUser` | 정확히 하나만 반영 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-011 | PASS | 전체 `integrationTest`와 회귀 대상 클래스 | 429 실패 없음. 남은 실패는 5절의 환경 요인 2건 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-012 | PASS | `OpenApiSpecificationIntegrationTest.documentsRateLimitResponses` | `docs/api/openapi.json` 재생성 |

## 5. Failures and diagnostics

### 이번 변경이 원인이었고 고친 실패

| 실패한 테스트 | 원인 | 조치 |
| --- | --- | --- |
| `NotificationPreferenceMigrationIntegrationTest.preservesEnabledAndDropsLegacyQuietValues` | V24에서 최신까지 실행된 migration 수를 6으로 고정 단언. V31 추가로 7이 됐다 | #180·#294·#310과 같은 방식으로 7로 올렸다 |
| `QuestionProposalDeleteMuteMigrationIntegrationTest.existingProposalGetsDefaultsAfterV30` | V29에서 최신까지 실행 수를 1로 단언 | 대상 버전을 `"30"`으로 고정했다. 이후 migration이 추가돼도 V30만 검사한다 |
| `FlywayMigrationIntegrationTest`(1차 부분 실행) | `LATEST_MIGRATION_VERSION = 30` | 31로 올렸다(코드 주석의 지침) |
| `FlywayMigrationContractTest`(단위) | migration 파일 목록 고정 | `V31__add_user_account_nickname_changed_at.sql`을 추가했다 |

### Windows 환경 요인 실패 (이번 변경과 무관)

- 실패한 명령: `./gradlew test`, `./gradlew integrationTest`, `./harness test-run`
- 오류 요약:
  - 단위 12건
    - 경로 구분자: `AccountPersistenceBoundaryTest`, `AnswerJdbcBoundaryTest` 2건, `DirectionPersistenceBoundaryTest` 2건,
      `QuestionPersistenceBoundaryTest`
    - `./gradlew` 셸 스크립트 직접 실행: `JavaConventionBaselineTest` 2건, `JavaStaticAnalysisRuleTest` 2건
    - 심볼릭 링크·shebang: `RepoMapToolTest` 2건
  - 통합 2건: `StructuredLoggingProfileIntegrationTest`. 하위 JVM 명령줄 길이 제한(`CreateProcess error=206`)
- 근거:
  - 모두 #312 보고서 5절의 "변경 전 Windows 실패" 표에 있는 테스트다.
  - 예를 들어 `AccountPersistenceBoundaryTest`는 `path.toString().contains("/account/")`라서 Windows에서 account 자신의
    JPA 파일까지 검사 대상에 들어간다. account 밖에서 `account.repository.jpa`를 참조하는 파일은 0개다.
- 재현 조건: `origin/main` 기준 브랜치를 Windows(Git Bash)에서 실행
- 미검증 범위: 위 14개 테스트의 본래 검증 대상(다른 기능의 경계 규칙 등). 이번 변경은 해당 경계를 건드리지 않는다.
- 남은 위험: Linux CI에서 통과를 확인하기 전까지는 이 14건을 실패로 본다.
- 후속 검증 방법:
  - PR CI의 `check`
  - #314 머지 후 이 브랜치를 rebase하고 이 PC에서 `./harness pr-ready --project-tests` 재실행

## 6. Potential issues

### Application code

- 카운터는 인스턴스 메모리에 있다. 재시작하거나 배포하면 초기화되고, 인스턴스를 늘리면 실제 한도가 인스턴스 수만큼
  커진다. 지금은 앱이 한 대라 수용했고, `AUTH_DESIGN.md` 8.2절에 전환 조건을 적었다.
- 클라이언트 IP는 연결 주소만 쓴다. 앞단에 프록시나 LB를 두면 모든 요청이 한 주소로 보여 전체가 429를 받는다.
  프록시를 도입하는 Issue에서 `server.forward-headers-strategy`와 신뢰할 프록시 범위를 함께 설정해야 한다.
- 모바일 통신사 NAT처럼 여러 사용자가 IP 하나를 공유하면, 기기 등록 IP당 1시간 10회에 정상 사용자가 걸릴 수 있다.
  운영 지표로 조정해야 한다.
- D1에 따라 본문 검증(`@Valid`) 단계에서 400으로 끝난 요청은 IP 한도에 세지 않는다.
- `Account.updateProfile`은 닉네임을 바꿔도 변경 시각을 기록하지 않는다. 지금 이 메서드를 부르는 곳은
  `NicknameRegistrationService`뿐이고 이 서비스는 `changeNickname`을 쓴다. 나중에 프로필 수정 API가 `updateProfile`로
  닉네임을 바꾸면 주기를 우회할 수 있으니, 그 작업에서 `changeNickname`을 쓰도록 리뷰해야 한다.

### Infrastructure and resource limits

- 만료 키는 window마다 한 번 정리하지만, 한 window 안의 서로 다른 키 수에는 상한이 없다. 많은 IP에서 동시에 요청하면
  카운터 맵이 커진다. 키 하나는 수백 바이트 수준이다. 키 수 상한이나 공유 저장소 전환은 다중 인스턴스 전환 때 함께 다룬다.

### Database and migrations

- `V31`은 nullable 컬럼만 추가하고 backfill하지 않는다. 기존 계정은 모두 첫 변경을 바로 할 수 있다.
- 다른 브랜치에서 `V31`을 쓰면 버전이 충돌한다. 작성 시점의 열린 PR(#314)에는 migration이 없다.
- migration 수를 고정 단언하는 테스트 하나(`NotificationPreferenceMigrationIntegrationTest`)는 migration을 추가할 때마다 고쳐야 한다.

### Concurrency and idempotency

- 같은 사용자의 동시 변경은 하나만 반영된다(INT-010). 진 쪽은 저장 시점에 따라 `ACC-APP-004`(429)나
  `CMN-DOM-003`(낙관적 잠금 충돌)을 받는다. `docs/error-codes.md`에 기록했다.
- 카운터 원자성은 `ConcurrentHashMap.compute`로 보장하고, UNIT-005로 확인했다.

### Transactions and event ordering

- `NicknameRegistrationService`는 컨벤션 ratchet에 맞춰 클래스 단위 `@Transactional(readOnly = true)`를 달았다.
  `changeNickname`은 `NOT_SUPPORTED`로 시작하고, 저장만 `TransactionTemplate`으로 연다.
- 등록 경로의 `ensureAvailable`은 등록 트랜잭션에 합류한다. 이 동작은 `DeviceRegistrationTransactionIntegrationTest`와
  `NicknameDuplicateModerationIntegrationTest`가 통과해 확인됐다.

### External APIs

- 한도 초과와 주기 위반 요청에는 moderation을 호출하지 않는다(INT-007, INT-008).
- 사용자당 하루 10회로 사용자 한 명의 비용은 묶이지만, 계정 수가 늘면 비용 총량도 커진다. 계정 생성 속도는
  기기 등록 IP 한도가 1차로 막는다.

### Failure recovery and reconciliation

- 재시작 직후에는 IP 카운터가 비어 있다. 변경 주기는 DB(`nickname_changed_at`)에 있어 재시작과 무관하다.

## 7. Regression and residual risk

- #312(PR #314)와 `DeviceAuthController`, `DeviceAuthApiSpec`, `DeviceRegistrationServiceTest`, `docs/api/openapi.json`이
  겹친다. 먼저 머지된 쪽에 맞춰 rebase할 때 충돌을 손으로 풀어야 한다. 새 통합 테스트의 등록 요청에는
  `coarseRegionCode`를 넣었는데, #312 이후에도 Jackson이 모르는 필드를 무시하므로 그대로 동작한다.
- 컨벤션 ratchet 때문에 수정한 Java 파일 전체가 Spotless 형식으로 다시 정리됐다. `Account.java`처럼 diff가 큰 파일이 있다.
- 계획의 소유 파일 목록 밖에서 다음 파일을 고쳤다.
  - `auth/web/AuthRequestRateLimiter.java`(신규 production)
  - migration 수·목록을 고정한 테스트 4개(5절)

## 8. Artifacts

- Test plan: `docs/test-plans/gh-315-TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT.md`
- CI run: PR 생성 후 기록
- Related ADR: 없음. 설계 기록은 `docs/product/AUTH_DESIGN.md` 8.2절
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
