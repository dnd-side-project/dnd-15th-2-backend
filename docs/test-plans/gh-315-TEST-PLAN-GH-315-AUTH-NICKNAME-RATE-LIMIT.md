# Test Plan: TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT

> Created at: `2026-10-06T14:02:30+09:00`
> GitHub Issue: `#315`
> Status: Approved

## 1. Objective

인증 없이 열린 기기 등록·토큰 재발급·운영자 로그인과 OpenAI moderation을 부르는 닉네임 변경이
설정으로 주입한 한도를 넘으면 429로 거절되는지, 그리고 F01 닉네임 변경 주기가 지켜지는지 검증한다.

실패하면 스크립트로 계정을 무제한 만들어 차단(F08)과 안전 필터(F10)를 우회할 수 있다. 닉네임 변경을
반복하면 moderation 비용이 무한정 늘고, 운영자 비밀번호도 IP 제한 없이 대입할 수 있다. 반대로 한도를
잘못 적용하면 정상 사용자가 429를 받거나 기존 통합 테스트가 대량으로 실패한다.

## 2. Scope

### Included

- 메모리 고정 윈도 카운터의 한도·키 분리·윈도 경과·동시성·만료 키 정리
- 클라이언트 키 산출: 연결 주소 사용, `X-Forwarded-For` 무시, IPv6 /64 단위 묶음(D4)
- 기기 등록·토큰 재발급·운영자 로그인의 IP 단위 429(`AUT-APP-007`)와 거절 시 부수 효과 없음
- 닉네임 변경 시도 한도 429(`ACC-APP-003`)와 변경 주기 429(`ACC-APP-004`)
- 주기 판정 규칙(첫 변경 허용, 경계 시각 허용)과 성공 시 `nickname_changed_at` 기록
- 한도 초과·주기 위반 시 moderation 미호출
- `V31` 마이그레이션의 nullable 컬럼과 기존 행 호환
- 같은 사용자의 동시 닉네임 변경 직렬화
- 한도 값의 설정 주입과 `application.yml` 기본값
- 네 경로 ApiSpec의 429 응답 문서화
- 기존 통합 테스트 회귀(test 프로필 한도 상향)

### Excluded

- 다중 인스턴스 간 카운터 공유, 재시작 후 카운터 유지(Issue 제외 범위, 재시작 시 초기화를 수용)
- 프록시·LB 뒤의 forwarded header 신뢰 설정(Issue 제외 범위)
- `Retry-After` 헤더(Issue 제외 범위)
- 실제 OpenAI 호출. moderation은 test double로 대체한다.
- 운영 한도 수치의 적정성(부하·통신사 공유 IP 실측). 기본값은 TASK.md 결정값을 그대로 쓴다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #315 | 네 경로 모두 한도를 넘은 요청이 429와 해당 오류 코드로 거절된다 |
| GitHub Issue #315 | 한도 값은 설정으로 주입되고 테스트에서 바꾼 값이 반영된다 |
| GitHub Issue #315 | 주기 안의 닉네임 재변경은 429이고 닉네임이 바뀌지 않으며 moderation을 호출하지 않는다 |
| GitHub Issue #315 | 윈도가 지나면 다시 허용된다(Clock 기반 검증) |
| GitHub Issue #315 | 가입 시 지정한 닉네임은 주기에 넣지 않는다. 중복·moderation 거절로 실패한 시도도 센다 |
| TASK.md Decisions | 기본값: 등록 10회/1시간, 재발급 60회/1시간, 운영자 로그인 20회/15분, 닉네임 시도 10회/1일, 주기 30일 |
| `AUTH_DESIGN.md` §8.2 | 1차 필수 방어는 IP 단위 등록 rate limit |
| `BACKEND_ROADMAP.md` F01 | 닉네임 변경 제한, 차단 ID 유지 |
| 기존 동작 | 운영자 로그인은 계정 단위 5회 실패 시 423 잠금. 닉네임 변경은 moderation을 DB 트랜잭션 밖에서 호출한다(#168) |
| 기존 동작 | `user_account.version`(`@Version`)이 동시 수정 중 뒤늦은 쓰기를 거절하고 `GlobalExceptionHandler`가 응답으로 바꾼다 |
| 인프라 현황 | 앱은 EC2 1대에서 컨테이너 포트를 직접 노출한다. 앞단 프록시가 없어 연결 주소가 곧 클라이언트 IP다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 한도 판정 경합으로 동시 요청이 한도를 넘어 통과 | 높음 | 중간 | P0 | 동시 요청 중 정확히 한도만큼만 허용 (UNIT-005) |
| `X-Forwarded-For`를 신뢰해 헤더 위조로 IP 한도 우회 | 높음 | 중간 | P0 | 헤더를 바꿔도 같은 키로 집계 (UNIT-007, INT-002) |
| 기존 통합 테스트(운영자 로그인 헬퍼 7개 클래스, 기기 등록 다수)가 기본 한도에 걸려 실패 | 높음 | 높음 | P0 | test 프로필 상향 후 전체 `integrationTest` 통과 (INT-011) |
| 주기 위반·한도 초과에서도 moderation을 호출해 비용 발생 | 높음 | 중간 | P0 | moderation 호출 수 검증 (UNIT-009, UNIT-010, INT-007, INT-008) |
| 거절된 요청이 계정·자격증명·세션을 남김 | 높음 | 낮음 | P0 | 거절 후 행 수와 세션 불변 (INT-001, INT-003, INT-004) |
| 한도 초과 로그인 요청이 실패 카운터를 올려 피해자 계정 잠금에 악용 | 중간 | 중간 | P1 | 429 거절 시 `failed_attempt_count` 불변 (INT-004) |
| 가입 시 닉네임이 주기에 포함되어 가입 직후 첫 변경이 막힘 | 중간 | 중간 | P1 | 가입 계정 `nickname_changed_at` NULL, 첫 변경 성공 (UNIT-013, INT-009) |
| 같은 사용자의 동시 변경이 둘 다 주기 검사를 통과 | 중간 | 낮음 | P1 | 정확히 하나만 반영 (INT-010) |
| 만료 키가 남아 메모리가 계속 증가 | 중간 | 중간 | P1 | 윈도 경과 후 추적 키 수 감소 (UNIT-006) |
| IPv6 주소 회전으로 IP 한도 우회 | 중간 | 낮음 | P1 | 같은 /64는 같은 키 (UNIT-007) |
| moderation 503(UNAVAILABLE)이 주기를 시작시켜 사용자가 30일 잠김 | 중간 | 낮음 | P1 | 실패 시 `nickname_changed_at` 불변 (UNIT-012) |
| 앞단 프록시 도입 시 모든 요청이 한 IP로 보여 전체 429 | 높음 | 낮음 | P2 | 테스트 대상 아님. AUTH_DESIGN에 전제와 전환 조건 기록 |
| `V31` 버전이 다른 브랜치와 충돌 | 낮음 | 낮음 | P2 | 커밋 전 `origin/main`·열린 PR의 migration 번호 확인 |

## 5. Unit scenarios

구현 전제: 카운터는 `Clock`을 주입받는 고정 윈도 방식이다. 윈도는 키별 첫 요청 시각에서 시작하고 `시작 + window`
시점부터 새 윈도가 된다. 한도 정책은 `ReportRateLimitPolicy`처럼 compact constructor에서 검증하는 record다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-001 | 한도 0 이하 또는 window가 null·0·음수 | 정책 생성 | 예외로 거절된다 | P1 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-002 | 한도 3, 고정 Clock | 같은 키로 4회 요청 | 1~3회 허용, 4회째 거절 | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-003 | 키 A의 한도 소진 | 키 B로 요청 | 키 B는 허용 | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-004 | 키 A의 한도 소진 | Clock을 `window - 1ns`, 이어서 `window`만큼 이동 | 전자는 거절, 후자는 허용되고 새 윈도 카운트가 1부터 시작 | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-005 | 한도 10, 고정 Clock | 32개 스레드가 같은 키로 동시에 총 200회 요청(CountDownLatch로 동시 시작) | 허용 수가 정확히 10 | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-006 | 서로 다른 키 1,000개 사용 | Clock을 window 이상 이동한 뒤 다음 요청으로 정리 실행 | 추적 키 수가 정리 대상만큼 줄어든다(패키지 내부 조회 메서드로 확인) | P1 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-007 | 요청의 연결 주소와 `X-Forwarded-For` 헤더 | 클라이언트 키 산출 | IPv4는 주소 그대로, `X-Forwarded-For`는 무시, IPv6는 같은 /64면 같은 키이고 다른 /64면 다른 키 | P1 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-008 | 주기 30일 | 마지막 변경 시각이 null / `now - 30일 + 1s` / `now - 30일`인 계정의 변경 가능 판정 | 허용 / 거절 / 허용 | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-009 | 주기 안의 계정, moderation·repository mock | `changeNickname` | `ACC-APP-004` 예외, 중복 검사·moderation·저장 미호출 | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-010 | 사용자 시도 한도 소진 | `changeNickname` | `ACC-APP-003` 예외, 계정 조회·moderation·저장 미호출 | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-011 | 변경 가능 계정, moderation 허용 | `changeNickname` | 저장되는 계정의 마지막 변경 시각이 Clock의 now | P0 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-012 | 시도 한도 2, 중복 1회 + moderation UNAVAILABLE 1회 | 세 번째 `changeNickname` | 앞 두 번은 409·503 계열 예외이고 마지막 변경 시각 미저장, 세 번째는 `ACC-APP-003` | P1 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-013 | 시도 한도를 이미 소진한 상태 | 등록 경로 `ensureAvailable` 호출과 `Account.createUser` | 한도 영향 없이 검사가 수행되고 생성 계정의 마지막 변경 시각은 null | P1 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-014 | standalone MockMvc와 서비스 mock이 `ACC-APP-003`·`ACC-APP-004`를 던짐 | `PATCH /api/v1/users/me/nickname` | 429와 각 오류 코드 본문 | P1 | Unit executor |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-UNIT-015 | `src/main/resources/application.yml` | YAML 로더로 한도 키 읽기 | 다섯 기본값이 TASK.md 결정값과 같다 | P2 | Unit executor |

## 6. Integration scenarios

공통 전제: 한도 검증 클래스는 `@TestPropertySource`로 낮은 한도를 주입해 별도 컨텍스트로 띄운다. 같은 클래스 안에서
카운터가 이어지지 않도록 테스트마다 다른 클라이언트 주소(`remoteAddr`)나 다른 사용자를 쓴다. 시간 이동은 클래스별
`@Primary` 가변 Clock으로 한다(`Answer125MutableClock`과 같은 형태).

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-001 | MockMvc, 기기 등록, PostgreSQL | 등록 한도 2/1h, 주소 A | 주소 A로 서로 다른 installationId 3회 등록 | 1·2회 201, 3회 429 `AUT-APP-007`. `user_account`·`device_credential` 행이 2건만 증가 | `@BeforeEach` 테이블 정리 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-002 | MockMvc, 기기 등록 | 주소 A 한도 소진 | 주소 B로 등록, 주소 A에 다른 `X-Forwarded-For` 헤더를 붙여 등록 | 주소 B는 201, 헤더를 붙인 주소 A는 429 | 동일 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-003 | MockMvc, 토큰 재발급 | 등록된 기기, 재발급 한도 2/1h, 주소 C | 올바른 자격증명으로 3회 재발급 | 1·2회 200, 3회 429 `AUT-APP-007`. 3회째 `last_used_at` 미갱신 | 동일 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-004 | MockMvc, 운영자 로그인, Spring Session | 시드 운영자, 로그인 한도 2/15m, 주소 D | 잘못된 비밀번호 2회 후 올바른 비밀번호 1회 | 1·2회 401, 3회 429 `AUT-APP-007`. 세션 쿠키 없음, `failed_attempt_count`는 2 | 동일 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-005 | MockMvc, 기기 등록, 가변 Clock | 주소 E 한도 소진 | Clock을 윈도만큼 이동 후 등록 | 201 | Clock 원복 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-006 | 기기 등록, `Account` 저장 | 닉네임을 지정해 등록 | 저장된 행 조회 | `nickname_changed_at`이 NULL | 테이블 정리 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-007 | MockMvc, 닉네임 변경, 호출 수를 세는 moderation double, 가변 Clock | 주기 30일, 시도 한도 상향, 등록 사용자 | 변경 → 즉시 재변경 → Clock 30일 이동 후 재변경 | 200 → 429 `ACC-APP-004`(닉네임 유지) → 200. moderation 호출은 2회, `nickname_changed_at`은 두 성공 시각 | 동일 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-008 | MockMvc, 닉네임 변경, 거절을 반환하는 moderation double | 시도 한도 2/1d | 같은 사용자가 3회 변경 요청 | 1·2회 400 `ACC-DOM-005`, 3회 429 `ACC-APP-003`. moderation 호출 정확히 2회, `nickname_changed_at` NULL 유지 | 동일 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-009 | Flyway, PostgreSQL | V31 적용된 스키마 | `information_schema.columns` 조회와 V31 이전 형태로 직접 insert한 행 조회 | 컬럼은 nullable `timestamp with time zone`, 기존 형태 행은 NULL | 동일 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-010 | 닉네임 변경 서비스, 두 스레드, 지연시키는 moderation double | 변경 가능한 사용자 | 서로 다른 닉네임으로 동시 변경 | 정확히 하나만 성공, 다른 하나는 `ACC-APP-004` 또는 낙관적 잠금 충돌. 최종 닉네임은 성공한 값 | 동일 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-011 | 기존 통합 테스트 전체 | `src/integrationTest/resources/application-test.yml`에 한도 상향값 추가 | `./gradlew integrationTest` | 기존 테스트가 429 없이 통과 | 없음 |
| TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT-INT-012 | springdoc, `OpenApiSpecificationIntegrationTest` | 갱신된 ApiSpec | 스펙 생성 | 네 경로(`POST /api/v1/auth/devices`, `POST /api/v1/auth/token`, `POST /admin/login`, `PATCH /api/v1/users/me/nickname`)에 429 응답이 있고 `docs/api/openapi.json`과 일치 | 없음 |

## 7. Cross-cutting scenarios

### Database and transactions

- 닉네임 변경의 moderation 호출은 DB 트랜잭션 밖에서 일어나야 한다(#168). INT-007의 moderation double이 호출
  시점에 `TransactionSynchronizationManager.isActualTransactionActive()`가 false임을 기록하고 단언한다(P1).
- 주기 판정은 moderation 전(비용 절감)과 저장 트랜잭션 안(경합 방어) 두 번 수행한다. 저장 트랜잭션 안 판정과
  `@Version`이 INT-010의 근거다.
- 운영자 로그인의 IP 한도 검사는 자격증명 검증과 실패 기록 트랜잭션보다 먼저 실행된다(INT-004).

### Concurrency and idempotency

- 카운터 원자성: UNIT-005.
- 같은 사용자 동시 닉네임 변경: INT-010.
- 등록 경합(`uq_active_device_installation`)은 기존 테스트가 담당하며 이번 변경으로 동작이 바뀌지 않는다.

### External APIs

- OpenAI moderation은 모든 테스트에서 test double이다. 한도 초과·주기 위반 경로에서 호출 0회를
  UNIT-009, UNIT-010, INT-007, INT-008로 확인한다.

### Failure recovery and reconciliation

- moderation UNAVAILABLE(503)과 중복(409)은 시도로 세지만 주기를 시작하지 않는다(UNIT-012).
- 재시작 시 카운터 초기화는 수용한 위험이며 테스트하지 않는다. 보고서의 남은 위험에 기록한다.

## 8. Test data and isolation

- Fixtures: 국가 `KR` 행(기존 `DeviceAuthIntegrationTest`와 같은 방식), 시드 운영자는 테스트 전용 bcrypt 해시,
  클라이언트 주소는 문서용 대역(`192.0.2.0/24`, `198.51.100.0/24`, `2001:db8::/32`)만 쓴다.
- Database isolation: `PostgisContainerIntegrationTestSupport` 상속, `@BeforeEach`에서 `device_credential`,
  `operator_credential`, `user_account` 정리. 한도 클래스는 `@TestPropertySource` 때문에 별도 컨텍스트로 뜬다.
- Clock/randomness: 단위는 고정·가변 Clock 직접 주입, 통합은 클래스별 `@Primary` 가변 Clock.
- External API doubles: `@Primary` `NicknameModerationChecker` mock(호출 수 기록, 허용·거절·지연 응답).
- Cleanup: 정리 SQL과 Clock 원복. 메모리 카운터는 컨텍스트 단위라 테스트마다 다른 키를 써서 격리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Implementation executor | `src/main/java/com/dnd/qello/common/ratelimit/**`(신규), `auth/config/` 한도 설정 클래스(신규), `auth/web/DeviceAuthController.java`, `auth/web/DeviceAuthApiSpec.java`, `auth/web/OperatorLoginController.java`, `auth/web/OperatorLoginApiSpec.java`, `auth/error/AuthErrorCode.java`, `account/domain/Account.java`, `account/repository/jpa/AccountJpaEntity.java`, `account/repository/jpa/AccountJpaMapper.java`, `account/repository/AccountRepository.java`·`jpa/JpaAccountRepository.java`(필요 시), `account/service/NicknameRegistrationService.java`, `account/config/` 닉네임 변경 설정 클래스(신규), `account/error/AccountErrorCode.java`, `account/web/AccountApiSpec.java`, `src/main/resources/db/migration/V31__add_user_account_nickname_changed_at.sql`, `src/main/resources/application.yml`, `config/java-conventions/baseline.json`(JAVA-CONV-0021 해소), `docs/error-codes.md`, `docs/product/AUTH_DESIGN.md`, `docs/product/data-model/direction_communication.dbml` | — | `./gradlew compileJava javaConventionCheck` |
| 2 | Unit executor | `src/test/java/com/dnd/qello/common/ratelimit/**`(신규), `src/test/java/com/dnd/qello/account/domain/AccountNicknameChangeTest.java`(신규), `src/test/java/com/dnd/qello/account/service/NicknameRegistrationServiceTest.java`, `src/test/java/com/dnd/qello/auth/service/DeviceRegistrationServiceTest.java`(생성자 변경 반영만), `src/test/java/com/dnd/qello/account/web/AccountControllerMockMvcTest.java`, `src/test/java/com/dnd/qello/config/RateLimitDefaultsTest.java`(신규) | UNIT-001~015 | `./gradlew test` |
| 3 | Integration executor | `src/integrationTest/java/com/dnd/qello/AuthRateLimitIntegrationTest.java`(신규), `src/integrationTest/java/com/dnd/qello/NicknameChangeLimitIntegrationTest.java`(신규), `src/integrationTest/resources/application-test.yml`, `src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java`, `docs/api/openapi.json`(테스트가 재생성) | INT-001~012 | `./gradlew integrationTest` |
| 4 | Integration executor | `docs/reports/tests/gh-315-TEST-PLAN-GH-315-AUTH-NICKNAME-RATE-LIMIT.md` | 전체 | `./harness check`, `./harness pr-ready --project-tests` |

- `NicknameRegistrationService`를 수정하면 changed-file ratchet이 적용된다. 생성자는 `@RequiredArgsConstructor`로 바꾸고
  baseline의 `JAVA-CONV-0021`을 지운다. moderation을 트랜잭션 밖에 두는 현재 설계는 JAVA_CONVENTIONS의
  "직접 트랜잭션을 여는 Service" 형태(클래스 read-only, `changeNickname`은 `NOT_SUPPORTED`)로 맞춘다.
- 이 PC는 `origin/main` 기준이라 #312의 Windows 수정이 없다. 하네스는 `python scripts/harness.py`로 실행하고,
  `python3` 별칭 때문에 실패한 검증은 보고서에 환경 요인으로 따로 적는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

승인 시 함께 확정할 결정(권장안):

| ID | 결정 | 권장안 |
| --- | --- | --- |
| D1 | IP 한도가 세는 요청 | 본문 검증을 통과해 컨트롤러에 도달한 요청을 성공·실패와 무관하게 센다 |
| D2 | 닉네임 변경 판정 순서 | 시도 한도(메모리) → 계정 조회·주기 → 중복 → moderation → 저장. 주기 위반 요청도 시도 1회로 센다 |
| D3 | 주기 경계 | 마지막 변경 시각 + 주기 시점부터 허용한다 |
| D4 | IPv6 키 | /64 접두사 단위로 묶는다(현재 인프라는 IPv4만 쓰지만 비용이 작다) |

- Reviewer: 사용자(@tkv00)
- Decision: 승인. D1~D4 권장안 확정
- Approved at: 2026-10-06
