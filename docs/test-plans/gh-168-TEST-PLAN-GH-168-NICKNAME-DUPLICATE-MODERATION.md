# Test Plan: TEST-PLAN-GH-168-NICKNAME-DUPLICATE-MODERATION

> Created at: `2026-08-19T01:10:00+09:00`
> GitHub Issue: `#168`
> Status: Approved

## 1. Objective

닉네임 등록·변경이 (1) 대소문자 무시 중복을 만들지 않고 (2) production gate가
켜진 경우 moderation을 실제로 통과해야 반영되는지 검증한다. 실패 시 위험은
크게 세 가지다.

- 중복 검사가 없으면 동일 표시명 계정이 무한히 생겨 사용자 식별·신고·차단이
  꼬인다.
- moderation이 연결되지 않거나 fail-open으로 새면, 이미 구현된 #106의
  fail-closed 계약(`INV-NICK-001`~`007`)이 실제 서비스 진입 지점에서는
  지켜지지 않는 것과 같다.
- 외부 OpenAI 호출을 DB 트랜잭션 안에서 하면 커넥션 풀이 외부 API 지연만큼
  묶여, 무관한 다른 요청까지 함께 느려지거나 실패한다.

## 2. Scope

### Included

- `AccountErrorCode.DUPLICATED_NICKNAME`(가칭, 실제 이름은 구현 시 확정)와
  `ConstraintExceptionMapper` 매핑.
- 신규 최소 moderation 구현체 3종(`TextNormalizer`/`LocalRuleEngine`/
  `PolicyEngine`)과 `SecondaryModerationClient` fail-closed placeholder.
- `NicknameSyncModerationGate`를 구성하는 Spring 설정과, `qello.filtering.
  production.enabled`(#113) 조건부 빈 등록.
- `DeviceRegistrationService.register()`의 닉네임 중복·moderation 연결.
- 신규 닉네임 변경 유스케이스와 `PATCH /api/v1/users/me/nickname`.
- `user_account.nickname` 유일성 제약(신규 마이그레이션)과 동시성 방어.
- `docs/api/openapi.json`, `docs/error-codes.md` 갱신.

### Excluded

- `PolicyEngine`의 카테고리별 threshold·언어별 정책, `LocalRuleEngine`의 실제
  사전 — 최소 동작 버전만 검증한다.
- 독립 보조 판정기의 실제 공급자 — placeholder가 즉시 실패하는 동작만
  검증한다.
- 닉네임 변경 rate limit(정책 미정).
- OpenAI 실제 프로덕션 엔드포인트 호출 — 모든 테스트는 fake/stub로 대체한다.
- `NicknameSyncModerationGate` 자체의 내부 timeout/보조 전환 로직 — #106
  (`TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER`)에서 이미 검증됐으므로 이 계획은
  "연결"만 검증하고 게이트 내부를 재검증하지 않는다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #168 | 중복 검사(대소문자 무시) 없이 등록·변경 불가. production gate가 켜져 있으면 moderation 실제 통과 필요. 게이트가 꺼져 있으면 중복 검사만 적용. |
| #106 `NicknameSyncModerationGate` | 주 판정기 명시적 BLOCK은 확정 결과(보조 미호출). timeout/error만 보조로 전환. 주·보조 모두 실패 시 REJECTED(UNAVAILABLE). 최초 설정과 변경이 같은 fail-closed 경로를 공유해야 한다(완화된 별도 분기 금지). |
| #113 `FilteringProductionGate` | `qello.filtering.production.enabled=false`가 기본값. 켜져 있을 때만 확인 항목을 검사한다 — 꺼진 상태에서 시스템이 구동을 막지 않아야 한다. |
| `AccountRepository`/`JpaAccountRepository` | `updateProfile`은 관리 엔티티 조회 후 Dirty Checking에 위임하며 `@Version` 낙관적 락으로 동시 수정 충돌을 409로 변환한다. |
| `ConstraintExceptionMapper` | 신규 제약 이름을 `knownConstraints()`와 `map()` 양쪽에 등록해야 `DataIntegrityViolationException`이 기능 오류로 변환된다. |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 앱 레벨 중복 검사와 DB insert 사이 경합 창(TOCTOU)에서 동시에 같은 닉네임을 선점 | 대소문자만 다른 중복 닉네임 생성 | Medium | P0 | INT-003 (2-way 동시성) |
| moderation 게이트 호출을 DB 트랜잭션 안에서 실행 | 외부 API 지연만큼 커넥션 풀 점유, 무관한 요청 지연·실패 | Medium | P0 | 코드 리뷰 + INT-005/INT-006(설계 준수 여부는 자동 테스트로 완전히 증명하기 어려움 — §7에 위험으로 남긴다) |
| `qello.filtering.production.enabled` 조건부 빈 등록을 잘못 구성 | 게이트가 꺼져 있어야 할 로컬/테스트 환경에서 실제 OpenAI 호출 시도, 또는 반대로 운영에서 게이트가 등록되지 않아 moderation 없이 통과 | High(설정 오류 시 영향 큼) | P0 | UNIT-022 |
| `DeviceRegistrationService`의 기존 "체크 후 삽입" installationId 패턴처럼, 닉네임 사전 조회 후 실제 저장까지 사이에 다른 요청이 끼어듦 | 계정은 생성되지만 중복 닉네임 | Medium | P0 | INT-003 |
| moderation 판정 실패(UNAVAILABLE) 시 사용자에게 노출되는 오류가 "콘텐츠 거부"와 구분되지 않음 | 사용자가 재시도해도 되는 상황(서비스 장애)과 닉네임을 바꿔야 하는 상황(정책 위반)을 구분 못함 | Low | P1 | UNIT-020, UNIT-021 |
| `PolicyEngine` 최소 구현이 지나치게 단순(flagged 여부만 사용)해 오탐/미탐이 실제보다 많음 | 정상 닉네임 거부 또는 부적절한 닉네임 통과 | Medium(정교화는 범위 밖) | P2 | 자동 테스트 대상 아님 — Explicit exclusions에 기록, 별도 이슈 필요 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | `ConstraintExceptionMapper` | 닉네임 유일성 제약 이름으로 `map()` 호출 | `AccountErrorCode.DUPLICATED_NICKNAME`과 field `"nickname"`을 반환 | P0 | Executor A |
| UNIT-002 | `ConstraintExceptionMapper` | `knownConstraints()` 호출 | 신규 제약 이름을 포함 | P0 | Executor A |
| UNIT-003 | `PolicyEngine` 최소 구현 | `ModerationProviderResult.flagged()==true`로 `decide()` 호출 | `FilterVerdict.BLOCK` 반환 | P0 | Executor A |
| UNIT-004 | `PolicyEngine` 최소 구현 | `flagged()==false`로 `decide()` 호출 | `FilterVerdict.ALLOW` 반환 | P0 | Executor A |
| UNIT-005 | `TextNormalizer` 최소 구현 | 앞뒤 공백이 있는 문자열로 `normalize()` 호출 | trim된 문자열을 그대로 반환(추가 규칙 없음) | P1 | Executor A |
| UNIT-006 | `LocalRuleEngine` 최소 구현 | 임의 문자열로 `evaluate()` 호출 | 항상 `LocalRuleVerdict.noMatch()` | P1 | Executor A |
| UNIT-007 | `SecondaryModerationClient` placeholder | `moderate()` 호출 | 대기 없이 즉시 `FilteringException(SECONDARY_MODERATOR_UNAVAILABLE)`을 던짐 | P0 | Executor A |
| UNIT-008 | 닉네임 서비스, 이미 존재하는(대소문자 다른) 닉네임 | 새 계정 등록 시도 | `DUPLICATED_NICKNAME` 예외, moderation 게이트는 호출되지 않음(호출 카운트 0) | P0 | Executor B |
| UNIT-009 | 닉네임 서비스, 현재 소유자가 이미 가진 닉네임과 완전히 동일한 값으로 변경 요청 | 변경 호출 | `DUPLICATED_NICKNAME`(409) — 다른 사용자와의 중복과 동일하게 취급, no-op 성공 경로를 두지 않는다 | P0 | Executor B |
| UNIT-010 | 닉네임 서비스, 게이트가 `Rejected(BLOCKED_BY_PRIMARY)` 반환하도록 구성 | 변경 호출 | 새 오류 코드 예외, `AccountRepository.updateProfile()` 미호출 | P0 | Executor B |
| UNIT-011 | 닉네임 서비스, 게이트가 `Allowed` 반환 | 변경 호출 | `AccountRepository.updateProfile()`이 새 닉네임으로 정확히 1회 호출 | P0 | Executor B |
| UNIT-012 | 닉네임 서비스, `NicknameSyncModerationGate` 빈이 없음(gate off로 구성) | 등록·변경 호출 | 중복 검사만 수행되고 정상 반영, moderation 관련 협력자 미호출 | P0 | Executor B |
| UNIT-013 | `DeviceRegistrationService`, 닉네임이 이미 존재 | `register()` 호출 | 계정·자격증명 모두 생성되지 않음(`accountRepository.accounts`, `credentialRepository.byId` 비어 있음) | P0 | Executor B |
| UNIT-014 | `DeviceRegistrationService`, 닉네임 `null` | `register()` 호출 | 중복 검사·moderation 모두 건너뛰고 정상 등록(기존 동작 회귀 확인) | P0 | Executor B |
| UNIT-015 | `DeviceRegistrationService`, 게이트가 거부 반환하도록 구성 | 닉네임과 함께 `register()` 호출 | 계정·자격증명 모두 생성되지 않음 | P0 | Executor B |
| UNIT-016 | `AccountController` MockMvc, 인증 없음 | `PATCH /api/v1/users/me/nickname` 호출 | 401, 서비스 미호출 | P0 | Executor C |
| UNIT-017 | `AccountController` MockMvc, 닉네임 필드 blank | 인증된 요청으로 호출 | 400, 서비스 미호출 | P0 | Executor C |
| UNIT-018 | `AccountController` MockMvc, 서비스가 성공 반환하도록 mock | 정상 요청 | 200, 응답 본문에 새 닉네임만 포함(다른 사용자 식별자 없음) | P0 | Executor C |
| UNIT-019 | `AccountController` MockMvc, 서비스가 `DUPLICATED_NICKNAME` 던지도록 mock | 요청 | 409 | P0 | Executor C |
| UNIT-020 | `AccountController` MockMvc, 서비스가 moderation 거부(BLOCK) 오류를 던지도록 mock | 요청 | 400, 오류 코드가 "콘텐츠 거부"로 식별 가능 | P0 | Executor C |
| UNIT-021 | `AccountController` MockMvc, 서비스가 moderation UNAVAILABLE 오류를 던지도록 mock | 요청 | 503, 오류 코드가 "일시 장애"로 식별 가능(400과 구분) | P1 | Executor C |
| UNIT-022 | Spring `ApplicationContextRunner`, `qello.filtering.production.enabled=false`/`true` 각각 구성 | 컨텍스트 로드 | `false`면 `NicknameSyncModerationGate` 빈 부재, `true`면(필요한 협력자를 테스트 이중체로 채운 뒤) 빈 존재 | P0 | Executor A |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | Flyway V21, PostgreSQL | 마이그레이션 적용 | 대소문자만 다른 닉네임 두 개를 직접 insert | 두 번째 insert가 유일성 제약 위반으로 실패 | 트랜잭션 롤백 |
| INT-002 | `DeviceRegistrationService`(JPA), PostgreSQL, gate off | 계정 A를 닉네임 "여름"으로 등록 | 계정 B를 닉네임 "여름"으로 등록 시도 | `DUPLICATED_NICKNAME`, `user_account` 행 수 등록 전후 불변 | TRUNCATE 또는 트랜잭션 롤백 |
| INT-003 | `DeviceRegistrationService`(JPA), PostgreSQL, gate off | 서로 다른 installationId, 대소문자만 다른 같은 닉네임("여름"/"여름" 또는 "Summer"/"summer") | 두 등록을 동시에(2-way) 실행 | 정확히 하나만 성공, 나머지는 `DUPLICATED_NICKNAME`(앱 사전 검사 또는 DB 제약 중 하나가 최종적으로 막음) | TRUNCATE |
| INT-004 | 닉네임 변경 서비스(직접 호출, MockMvc 아님), JPA, PostgreSQL, gate off | 계정 A 등록 완료 | 닉네임을 새 값으로 변경 | 변경 성공, 이전 닉네임이 다른 계정에서 재사용 가능해짐(다른 계정이 그 값으로 등록 성공) | TRUNCATE |
| INT-005 | 닉네임 변경 서비스, gate on(테스트 프로필에서만 `qello.filtering.production.enabled=true`), `MockRestServiceServer`로 OpenAI 엔드포인트 스텁 | flagged=false 스텁 구성 | 정상 닉네임으로 변경 요청 | 실제 게이트 빈을 통해 스텁 서버까지 호출되고 성공 | 스텁 검증 후 리셋 |
| INT-006 | 위와 동일 | flagged=true(카테고리 하나 이상) 스텁 구성 | 변경 요청 | 거부, `Account.updateProfile` 미호출(DB 값 불변 조회로 확인) | 스텁 검증 후 리셋 |
| INT-007 | 위와 동일, OpenAI 스텁이 500/timeout 반환하도록 구성, 보조 판정기는 placeholder(항상 실패) | 변경 요청 | UNAVAILABLE로 fail-closed 거부, DB 값 불변 | 스텁 리셋 |

## 7. Cross-cutting scenarios

### Database and transactions

- INT-001~INT-004는 신규 유일성 제약이 기존 `FlywayMigrationContractTest`(정확한 파일 목록)와
  `FlywayMigrationIntegrationTest`(버전별 카탈로그·누적 제약 수)를 함께 갱신해야 통과한다 —
  두 파일을 수정하지 않고 새 마이그레이션만 추가하면 반드시 실패한다.
- moderation 게이트 호출(외부 OpenAI, 최대 primary+secondary timeout 합)을 계정 갱신
  `@Transactional` 경계 **밖에서** 수행해야 한다는 것이 설계 전제다. 이를 어기면 그 시간만큼
  DB 커넥션이 점유된다. 이 전제 자체를 자동 테스트로 직접 증명하기는 어렵다 — 구현 시
  "게이트 호출 → (통과 시) 별도의 짧은 트랜잭션에서 중복 재확인 + 저장"의 두 단계 구조를
  코드 리뷰로 확인하고, §4 위험 목록에 남긴다.
- 낙관적 락(`@Version`) 충돌 시나리오는 이 이슈의 필수 범위가 아니다(동시에 서로 다른
  속성을 수정하는 경우는 기존 `JpaAccountRepository` 주석이 이미 다루는 문제이며, 닉네임
  단일 필드 갱신 경합은 INT-003/INT-004의 유일성 제약이 대신 방어한다).

### Concurrency and idempotency

- INT-003이 핵심이다. `ReportIntakeApiIntegrationTest`의 2-way 동시성 테스트 패턴(두
  스레드를 `CountDownLatch`로 동시 시작, `CompletableFuture`로 결과 수집)을 재사용한다.
- UNIT-009(자기 자신의 기존 닉네임으로 "변경")는 사람 결정이 필요하다 — §11 참고.

### External APIs

- OpenAI 호출은 모든 자동 테스트에서 `MockRestServiceServer` 또는 게이트 수준 fake로
  대체한다. 실제 네트워크 호출은 어떤 테스트에도 없다.
- `SecondaryModerationClient` placeholder는 재시도나 대기 없이 즉시 실패해야 한다(UNIT-007) —
  그렇지 않으면 주 판정기 timeout 시 사용자 체감 지연이 두 배가 된다.

### Failure recovery and reconciliation

- INT-007은 주·보조 판정기가 모두 실패하는 경로를 검증한다. 이 상태에서 사용자는 재시도
  안내(503)를 받아야 하며, 부분적으로 반영된 닉네임이 남지 않아야 한다.
- production gate가 켜진 채로 배포됐는데 OpenAI API 키가 비어 있는 등 기동 자체가 실패하는
  경로는 이미 `FilteringProductionGate.verifyConfirmations()`(#113)가 다루므로 이 계획에서
  재검증하지 않는다.

## 8. Test data and isolation

- Fixtures: `DeviceRegistrationServiceTest`의 `FakeAccountRepository`/
  `FakeCountryCatalogRepository`/`FakeDeviceCredentialRepository` 패턴을 재사용하고,
  닉네임 조회를 지원하도록 최소 확장한다.
- Database isolation: 통합 테스트는 `PostgisContainerIntegrationTestSupport`를 확장하고,
  각 테스트 종료 시 TRUNCATE로 정리한다(기존 `AccountPersistenceIntegrationTest` 패턴 확인).
- Clock/randomness: `Clock.fixed(...)`만 사용한다. 실제 시각에 의존하는 검증을 만들지 않는다.
- External API doubles: `MockRestServiceServer`(Spring Test) 또는 `ModerationProviderClient`
  fake. 실제 OpenAI 엔드포인트·API 키를 어떤 테스트에도 사용하지 않는다.
- Cleanup: `.env` 값이나 실제 API 키를 테스트 코드·로그에 남기지 않는다. 테스트 프로필의
  API 키는 더미 문자열을 사용한다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor A (moderation 최소 구현체 + 설정) | `src/main/java/.../filtering/moderation/*`(신규 3파일), `src/main/java/.../filtering/config/NicknameModerationGateConfig.java`(신규), `src/main/java/.../account/error/AccountErrorCode.java`, `src/main/java/.../common/error/ConstraintExceptionMapper.java`, 대응 단위 테스트 | UNIT-001~007, UNIT-022 | `./gradlew test --tests "com.dnd.qello.filtering.moderation.*" --tests "com.dnd.qello.common.error.*"` |
| 2 | Executor B (도메인/서비스, Executor A 완료 후 시작) | `src/main/resources/db/migration/V21__*.sql`, `src/main/java/.../account/repository/*`(확장), `src/main/java/.../account/service/*`(신규), `src/main/java/.../auth/service/DeviceRegistrationService.java`, 대응 단위·통합 테스트 | UNIT-008~015, INT-001~004 | `./gradlew test --tests "com.dnd.qello.account.*" --tests "com.dnd.qello.auth.service.*"`, `./gradlew integrationTest --tests "com.dnd.qello.NicknameDuplicateModerationIntegrationTest"` |
| 3 | Executor C (REST 계층, Executor B 완료 후 시작) | `src/main/java/.../account/web/*`(신규), 대응 MockMvc 테스트, `docs/api/openapi.json`, `docs/error-codes.md` | UNIT-016~021, INT-005~007 | `./gradlew test --tests "com.dnd.qello.account.web.*"`, `./gradlew integrationTest --tests "com.dnd.qello.OpenApiSpecificationIntegrationTest"` |

세 실행 에이전트가 같은 파일을 수정하지 않도록 순서대로 진행한다. 소규모 작업이므로 한
세션에서 순차 실행해도 무방하다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

결정 사항(2026-08-19 사용자 확정):

1. **UNIT-009** — 자기 자신의 기존 닉네임과 완전히 동일한 값으로 "변경" 요청도
   다른 사용자와의 중복과 동일하게 `DUPLICATED_NICKNAME`(409)로 취급한다. no-op
   성공 경로를 두지 않는다.
2. **오류 코드** — `DUPLICATED_NICKNAME`(ACC-APP-002, 409),
   `NICKNAME_REJECTED_BY_MODERATION`(ACC-DOM-005, 400),
   `NICKNAME_MODERATION_UNAVAILABLE`(ACC-INFRA-001, 503) 3종을 이 안대로 확정한다.
   실제 구현 시 `docs/error-codes.md`에 이미 사용 중인 번호와 충돌하지 않는지만
   재확인한다.
3. **PATCH 엔드포인트 경로** — `PATCH /api/v1/users/me/nickname`으로 확정한다.

- Reviewer: 사용자(tkv00)
- Decision: Approved
- Approved at: 2026-08-19
