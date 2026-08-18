# Test Report: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION

> Created at: `2026-08-19T07:23:52+09:00` (본문 갱신 `2026-08-19T09:40:00+09:00`)
> GitHub Issue: `#168`
> Branch: `feat/gh-168-nickname-duplicate-moderation`
> Commit: 작업 커밋 전 최종 상태(`e7086dc` 이후 워킹 트리)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 닉네임 대소문자 무시 유일성(DB 유일성 인덱스 + 앱 사전 검사), moderation 최소 실제
  구현체 3종(TextNormalizer/LocalRuleEngine/PolicyEngine) + fail-closed 보조 판정기 placeholder,
  `qello.filtering.production.enabled` 조건부 게이트 빈 등록, `DeviceRegistrationService` 등록
  경로 연결, 닉네임 변경 유스케이스와 `PATCH /api/v1/users/me/nickname`, 관련 오류 코드 3종과
  `ConstraintExceptionMapper` 매핑, Flyway V21 계약 회귀.
- Unverified scope: §7 참고 — 사건 병합류 재시도 소진 경로에 해당하는 항목은 이 이슈에 없으나,
  실제 OpenAI HTTP 왕복(계약·페이로드 형식)과 프로덕션 규모의 N-way 동시성은 검증하지 못했다.
- Release recommendation: 로컬 검증 기준 병합 가능. `qello.filtering.production.enabled=true`로
  실제 전환하기 전에는 §6 "External APIs"의 미검증 항목을 별도로 확인해야 한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 17.0.8 LTS |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL + PostGIS, Testcontainers 기반 로컬 컨테이너 |
| Test runner | JUnit 5 (Gradle `test` + `integrationTest` source set) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (전체) | PASS | 669 | 13s | `build/test-results/test/*.xml`, 0 failures/errors |
| `./gradlew integrationTest` (전체) | PASS | 491 | 5m49s | `build/test-results/integrationTest/*.xml`, 0 failures/errors |
| `./harness pr-ready --project-tests` | PASS | (위 전체 + 정책 검사) | 908ms(캐시 재확인) | secret preflight, JUnit policy, convention, commit formatter, workflow, label policy, husky, `check` 모두 통과 |
| `docs/api/openapi.json` 재생성 | PASS | `OpenApiSpecificationIntegrationTest` | 11s | `git diff --stat`로 `/api/v1/users/me/nickname` 신규 반영 확인 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `ConstraintExceptionMapperTest#mapsNicknameUniquenessCollision` | |
| UNIT-002 | PASS | `ConstraintExceptionMapperTest#knownConstraintsIncludesNicknameUniqueness` | |
| UNIT-003 | PASS | `MinimalModerationComponentsTest#policyEngineBlocksWhenFlagged` | |
| UNIT-004 | PASS | `MinimalModerationComponentsTest#policyEngineAllowsWhenNotFlagged` | |
| UNIT-005 | PASS | `MinimalModerationComponentsTest#textNormalizerOnlyTrims` | |
| UNIT-006 | PASS | `MinimalModerationComponentsTest#localRuleEngineAlwaysNoMatch` | |
| UNIT-007 | PASS | `MinimalModerationComponentsTest#secondaryClientPlaceholderFailsImmediately` | |
| UNIT-008 | PASS | `NicknameRegistrationServiceTest#rejectsDuplicateNicknameWithoutCallingModeration` | |
| UNIT-009 | PASS | `NicknameRegistrationServiceTest#rejectsSelfDuplicateNicknameTheSameAsOtherDuplicates` | §11 결정대로 409 |
| UNIT-010 | PASS | `NicknameRegistrationServiceTest#rejectsNicknameBlockedByModeration` | |
| UNIT-011 | PASS | `NicknameRegistrationServiceTest#savesNewNicknameWhenModerationAllows` | |
| UNIT-012 | PASS | `NicknameRegistrationServiceTest#passesWithDuplicateCheckOnlyWhenGateIsNoOp` | |
| UNIT-013 | PASS | `DeviceRegistrationServiceTest#rejectsRegistrationWhenNicknameAlreadyExists` | |
| UNIT-014 | PASS | `DeviceRegistrationServiceTest#skipsNicknameChecksWhenNicknameIsNull` | |
| UNIT-015 | PASS | `DeviceRegistrationServiceTest#rejectsRegistrationWhenModerationRejectsNickname` | |
| UNIT-016 | PASS | `AccountControllerMockMvcTest#changeNicknameRequiresAuthentication` | |
| UNIT-017 | PASS | `AccountControllerMockMvcTest#changeNicknameRejectsBlankNickname` | |
| UNIT-018 | PASS | `AccountControllerMockMvcTest#changeNicknameReturnsOkWithNewNickname` | |
| UNIT-019 | PASS | `AccountControllerMockMvcTest#changeNicknameReturnsConflictForDuplicate` | |
| UNIT-020 | PASS | `AccountControllerMockMvcTest#changeNicknameReturnsBadRequestForModerationRejection` | |
| UNIT-021 | PASS | `AccountControllerMockMvcTest#changeNicknameReturnsServiceUnavailableForModerationOutage` | |
| UNIT-022 | PASS | `NicknameModerationGateConfigTest`(3개 메서드) | gate off/on/API 키 누락 3케이스 |
| INT-001 | PASS | `NicknameDuplicateModerationIntegrationTest#databaseRejectsCaseInsensitiveDuplicateInsert` | |
| INT-002 | PASS | `NicknameDuplicateModerationIntegrationTest#secondRegistrationWithDuplicateNicknameDoesNotCreateAccount` | |
| INT-003 | PASS | `NicknameDuplicateModerationIntegrationTest#concurrentRegistrationsWithCaseVariantNicknameYieldExactlyOneWinner` | 2-way 동시성 |
| INT-004 | PASS | `NicknameDuplicateModerationIntegrationTest#changingNicknameFreesThePreviousValueForReuse` | |
| INT-005 | PASS | `NicknameDuplicateModerationIntegrationTest#changeNicknameSucceedsWhenModerationAllows` | `NicknameModerationChecker` 빈을 mock으로 교체해 검증(§5 참고, MockRestServiceServer 대신) |
| INT-006 | PASS | `NicknameDuplicateModerationIntegrationTest#changeNicknameFailsWhenModerationBlocks` | 위와 동일 |
| INT-007 | PASS | `NicknameDuplicateModerationIntegrationTest#changeNicknameFailsWhenModerationIsUnavailable` | 위와 동일 |
| 회귀 | PASS | `FlywayMigrationContractTest`, `FlywayMigrationIntegrationTest`, `AccountPersistenceIntegrationTest`, `DeviceRegistrationTransactionIntegrationTest` | V21 반영 후에도 기존 계약 유지 |

## 5. Failures and diagnostics

최종 실행에서는 실패한 시나리오가 없다. 구현 중 발견·수정한 문제:

- `NicknameModerationGateConfigTest`의 `ApplicationContextRunner`가 기본 `ConversionService`로
  `"PT3S"` 같은 ISO-8601 `Duration` 문자열을 변환하지 못해 `UnsatisfiedDependencyException`으로
  기동 실패했다. 실제 `@SpringBootTest`/운영 기동에서는 `SpringApplication`이
  `ApplicationConversionService`를 자동 등록해 문제가 없지만, 경량 러너에는 없어 테스트에서
  명시적으로 등록해 해결했다.
- `./harness pr-ready`의 secret preflight가 테스트용 더미 OpenAI API 키 문자열
  (`openai-api-key=test-only-...`)을 `assigned-secret` 패턴으로 오탐했다. 값을
  `example-...`로 바꿔(스캐너의 허용 접두사) 해결했다 — 실제 비밀값이 아니었다.
- `RecipientNotificationFanOutWorkerIntegrationTest`의 익명 `AccountRepository` 구현체가
  `AccountRepository`에 새로 추가한 `existsActiveNickname` 메서드를 구현하지 않아 컴파일이
  깨졌다. 기존 `accounts` 필드에 위임하도록 추가해 해결했다(#168의 회귀, 원래 로직 변경 없음).
- 두 `./gradlew` 프로세스를 동시에 실행해 `build/test-results/test/*.xml` 쓰기 경합으로
  한 번 빌드가 실패했다 — 실제 테스트 실패가 아니라 로컬 실행 실수였다. 순차 재실행으로
  확인했다.

## 6. Potential issues

### Application code

- `PolicyEngine` 최소 구현(`FlaggedCategoryPolicyEngine`)은 OpenAI의 `flagged` 원시 신호를
  카테고리·threshold 구분 없이 그대로 BLOCK/ALLOW로 쓴다. 오탐이 실제보다 넓을 수 있다 —
  테스트 계획에서 이미 범위 밖으로 명시했다.

### Infrastructure and resource limits

- `NicknameModerationGateConfig`는 production gate가 켜질 때 `FilterReleaseRepository.
  findCurrentlyPromoted()`를 빈 생성 시점(기동 시)에 한 번만 조회해 게이트에 고정한다. 이후
  운영 중 새 release가 promote돼도 애플리케이션을 재기동하기 전까지 게이트는 이전 release를
  계속 쓴다. 이 이슈의 범위 밖(실시간 재로딩은 다루지 않음)이지만 운영 전환 시 알아야 할
  제약이다.

### Database and migrations

- V21은 `CREATE UNIQUE INDEX`(비-`CONCURRENTLY`)라서 적용 중 `user_account`에 대한
  쓰기를 짧게 막는다. 현재 저장소의 다른 인덱스 마이그레이션도 같은 방식이라 새로운 위험은
  아니지만, 운영 배포 시점(트래픽이 있을 때)에는 짧은 지연이 있을 수 있다.

### Concurrency and idempotency

- INT-003은 2-way 동시성만 검증했다. 3개 이상의 동시 요청이 같은 닉네임을 두고 경합하는
  N-way 시나리오는 검증하지 않았다 — DB 부분 유일 인덱스가 정확히 하나만 통과시키는
  구조이므로 이론적으로는 N-way에서도 같은 보장이 성립하지만, 실측하지 않았다.
- 앱 사전 검사(`existsActiveNickname`)와 최종 저장 사이의 경합 창에서 두 요청이 모두 사전
  검사를 통과한 뒤 하나만 DB 유일성 제약에 걸리는 경로는 `ConstraintExceptionMapper`가
  `DUPLICATED_NICKNAME`으로 정확히 변환하는 것을 INT-003으로 확인했다.

### Transactions and event ordering

- `DeviceRegistrationService.register()`는 기존 계정·자격증명 생성 원자성을 지키기 위해
  닉네임 moderation 호출(외부 I/O, 최대 primary+secondary timeout)을 여전히 하나의
  `@Transactional` 경계 안에서 실행한다 — 등록은 저빈도 1회성 흐름이라 받아들인 트레이드오프다
  (테스트 계획 §4·§7). 반면 닉네임 변경(`NicknameRegistrationService.changeNickname`)은
  조회 → moderation(트랜잭션 밖) → 저장의 3단계로 분리해 외부 호출이 DB 쓰기 트랜잭션을
  점유하지 않는다. 이 설계 전제 자체(트랜잭션이 실제로 짧게 유지되는지)를 자동 테스트로
  직접 측정하지는 않았다 — 코드 리뷰로 확인해야 한다.

### External APIs

- 실제 OpenAI moderation API 호출(HTTP 계약, 요청/응답 페이로드 형식, 실제 timeout 동작)은
  어떤 테스트에도 없다. `OpenAiModerationProviderClient` 자체는 기존(#108/#109) 테스트가
  이미 검증했고, 이 이슈는 그 클라이언트를 구성만 했다 — 새로 만든 `RestClient` 설정
  (`nicknameModerationOpenAiRestClient`)이 실제로 올바른 base URL·인증 헤더·timeout으로
  요청을 보내는지는 로컬 검증에서 실행하지 못했다. `qello.filtering.production.enabled=true`로
  전환하기 전 스테이징에서 실제 호출 1회를 수동 확인해야 한다.
- INT-005~007은 테스트 계획이 원래 제안한 `MockRestServiceServer` 기반 HTTP 스텁 대신
  `NicknameModerationChecker` 빈 자체를 mock으로 교체해 검증했다 — `NicknameRegistrationService`
  → `AccountController`/`DeviceRegistrationService` 연결의 정확성은 동일하게 증명하지만, OpenAI
  HTTP 왕복 자체의 정확성은 증명하지 않는다. 실행 범위를 좁힌 만큼 이 절에 명시한다.

### Failure recovery and reconciliation

- 주·보조 판정기가 모두 실패하는 fail-closed 경로(INT-007)는 mock 수준에서 확인했다.
  `SecondaryModerationClient` placeholder가 실제로 "대기 없이 즉시 실패"하는지는
  `UnavailableSecondaryModerationClient`의 UNIT-007로 확인했다.

## 7. Regression and residual risk

- `AccountRepository` 인터페이스에 `existsActiveNickname`을 추가하면서 기존 테스트 더블
  4곳(`DeviceTokenServiceTest`, `OperatorLoginServiceTest`, `DirectionPresenceServiceTest`,
  `RecipientNotificationFanOutWorkerIntegrationTest`)에 미사용 메서드 구현을 추가했다 —
  동작 변경 없이 컴파일만 통과시키는 회귀 대응이었다.
- 남은 위험은 §6에 정리한 대로: (1) release 고정, (2) OpenAI 실제 왕복 미검증, (3) N-way
  동시성 미실측, (4) 등록 경로의 트랜잭션 내 외부 호출.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-168-TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION.md`
- CI run: PR 생성 후 GitHub Actions에서 확인 예정
- Related ADR: 없음(신규 ADR 없이 기존 #106/#113 설계를 재사용)
- PR: 생성 예정

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (§6 External APIs, §7)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (release 실시간 재로딩, OpenAI 왕복 실측은 아직 이슈화하지 않음)
- [x] 실행 결과와 PR 설명이 일치함
