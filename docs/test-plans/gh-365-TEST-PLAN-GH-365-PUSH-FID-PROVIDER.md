# Test Plan: TEST-PLAN-GH-365-PUSH-FID-PROVIDER

> Created at: `2026-11-21T08:28:03.083Z`
> GitHub Issue: `#365`
> Task: `TASK.md` / branch `feat/gh-365-push-fid-provider`
> Status: `APPROVED`
> 단위 18개 + 통합 9개 = 시나리오 27개. parameterized invocation 수가 아니라 시나리오 ID 수다.
> 이번 단계는 계획 작성만 수행한다. 아래 테스트와 검증 명령은 실행하지 않았다.

## 1. Objective

푸시 기기 등록·해지 입력을 FID로 바꾸고, 같은 값을 기존 암호화·fingerprint 저장 경로로
전달한다. FCM HTTP v1 요청은 `message.fid`와 `message.android.priority=HIGH`를 사용하며
기존 data allowlist와 무효 기기 처리를 보존하고, 사람 결정에 따라 `message.fid` 오류도
동일한 조건에서 무효 기기로 분류한다. provider 선택과 token 보호 설정을 분리해 dev는
보호 key가 없으면 실패하고, `noop`은 FCM credential 없이 기동하게 한다.
provider 미지정 시 test/local/integration/dev만 noop을 기본으로 사용하며 그 밖의
프로필은 기동에 실패한다. 명시한 provider 값은 noop/fcm만 허용한다.

실패 위험은 등록 API의 `NOT-INFRA-002`, 조용한 실제 발송 중단, FID·credential 유출,
무효 기기의 오판정과 기존 test profile fixture의 중복 bean이다.

## 2. Scope

### Included

- `PushDeviceRequest`의 JSON 필드·command 변환·redaction, 두 HTTP endpoint와 OpenAPI schema.
- `FcmHttpV1PushProvider`의 실제 직렬화 body 및 기존 응답 분류 회귀.
- `PushConfiguration`, `PushProperties`, `PushTokenProperties`의 provider/key 독립 조건.
- `application.yml`·`application-dev.yml`·`application-local.yml` 및 테스트 설정의 바인딩 영향.
- 기존 등록·해지·소유권 이전, dispatch, dev boot, 계정 탈퇴의 영향 분석과 회귀 계획.
- 이번 단계에서 수정하는 파일은 이 계획 하나뿐이다. `TASK.md`의 기존 변경을 보존한다.

### Excluded

- 테스트/production 코드, 설정, OpenAPI 산출물과 다른 문서의 이번 단계 수정 또는 재생성.
- DB 컬럼 이름 변경·migration, infra 변경, 클라우드 호출·배포·실기기 발송.
- dev의 `provider=fcm` 지정, worker scheduling 활성화(#366), APNs, 장기 미갱신 기기 정리.
- 내부 `PushToken`·`PushDeviceCommand.token`·DB 컬럼의 일괄 rename과 기존 테스트 구조 정리.
- commit, push, PR 생성, Issue/PR 코멘트. 승인 전 테스트 작성·실행.

## 3. Source requirements

### 인수 조건 추적

Issue 본문의 기능 완료 조건 4개를 AC1~AC4로 구분한다. 그 다음 체크 항목인 전체 검증
명령은 별도 G1이며, 기능 조건 하나로 합치거나 누락하지 않는다.

| Source | Requirement / acceptance criterion | Scenario |
| --- | --- | --- |
| Issue #365 AC1 / TASK | 요청 필드 `fid`, OpenAPI 재생성 diff | UNIT-001~002, INT-001~003, INT-007 |
| Issue #365 AC2 / TASK | `message.fid`, `android.priority=HIGH`, `message.token` 없음 | UNIT-003, INT-004~005 |
| Issue #365 AC3 / TASK | dev key 있음/없음, noop 무credential 기동, fcm 설정 누락 실패. 미지정·잘못된 provider는 아래 결정 적용 | UNIT-006~012, UNIT-017~018, INT-006 |
| Issue #365 AC4 / TASK | 기존 UNREGISTERED·INVALID_ARGUMENT 처리 유지 및 결정 2의 fid 분류 추가 | UNIT-004~005, INT-004 |
| TASK 사람 결정 1 (2026-11-21, @Byuntil) | test/local/integration/dev 미지정 → noop, 나머지 프로필 미지정 → provider 누락 원인으로 기동 실패 | UNIT-006, UNIT-009, UNIT-012, UNIT-017, INT-006 |
| TASK 사람 결정 2 (2026-11-21, @Byuntil) | 유효 allowlist payload의 INVALID_ARGUMENT + message.token 또는 message.fid → InvalidToken, 그 밖은 PermanentFailure | UNIT-005, INT-004 |
| TASK 사람 결정 3 (2026-11-21, @Byuntil) | noop/fcm 외 provider 값(빈 값 포함) → 기동 실패 | UNIT-018 |
| Issue #365 G1 / AGENTS §10 | harness check, pr-ready/project-tests, hooks 검증, diff 검사 | §9~10의 승인 후 검증 |
| TASK / ADR-0008 | 기존 암호화·HMAC·rotation, 최소 data, DB·transaction·lease 경계 유지 | UNIT-013~016, INT-001~005, INT-008~009 |

이하 표의 축약 ID에는 모두 `TEST-PLAN-GH-365-PUSH-FID-PROVIDER-` 접두사를 붙인다.

### 공식 문서 확인

확인일: 2026-11-21. Firebase 공식 REST reference를 직접 읽고 Context7의 동일 원문과
대조했다. 실제 FCM 요청이나 credential 검증은 하지 않았다.

| 항목 | 상태 | 근거와 계획에 적용할 계약 |
| --- | --- | --- |
| HTTP v1 발송 대상 `fid` | CONFIRMED | Firebase REST v1 `projects.messages`, Resource: Message의 target union에 `fid`가 있다. `token`은 deprecated이고 `fid` 사용을 안내한다. 요청에서 둘을 함께 보내지 않는다. |
| `android.priority` | CONFIRMED | 같은 reference의 `AndroidConfig.priority`는 `AndroidMessagePriority`이고 enum은 `NORMAL`, `HIGH`다. REST body에는 대문자 `HIGH`를 사용한다. Admin SDK의 소문자 표현과 혼동하지 않는다. |
| FID 오류의 field violation 이름 | UNKNOWN | 이번에 확인한 Message/priority reference는 FID 오류가 항상 어떤 field path로 반환되는지 보장하지 않는다. 공식 문서의 미확인 상태와 별개로 TASK 사람 결정 2는 `message.fid`가 반환된 경우 기존 token과 같은 조건의 InvalidToken 처리를 요구한다. |

공개 문서 출처:
`https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages`
의 `Message`, `AndroidConfig`, `AndroidMessagePriority` 절.
Android SDK 버전·deprecated 시점은 이번 계획의 판단에 필요하지 않아 별도 검증하지 않았다.

### 코드에서 확인한 현재 동작

- `PushDeviceRequest.toCommand()` → `PushDeviceCommand` → `PushToken` → `PushDeviceService`다.
  내부 값 객체는 null/blank와 UTF-8 4096 bytes 초과를 거부한다. FID 전용 길이/정규식은
  현재 없으므로 이번 계획이 새 제약을 만들지 않는다.
- 서비스의 Spring 생성자는 `ObjectProvider<PushTokenProtector>`가 비어 있으면
  `UnconfiguredPushTokenProtector`를 사용한다. 등록·해지는 그때 보호 오류로 실패한다.
- 현재 `ProductionPushConfiguration`은 `!test & !local & !integration & !dev` 조건에서
  FCM 설정과 token 설정을 함께 바인딩한다. `PushTokenProperties` 자체에도 같은 profile
  조건이 있다. dev 확장은 configuration과 properties 양쪽의 조건을 확인해야 한다.
- 현재 FCM body는 `token`·`data` 두 필드뿐이다. 오류 분류는 `UNREGISTERED` 및
  유효 allowlist payload의 `INVALID_ARGUMENT` + `message.token` field violation이다.
- 등록 repository는 user/platform 및 fingerprint advisory lock과 한 SQL 문장으로
  소유권 이전·기존 delivery 취소를 처리한다. 기존 `token_ciphertext`·`token_fingerprint`
  컬럼을 유지한다.

### 프로필별 token protector와 설정 경로

| Profile/context | 현재 provider / protector 확보 방식 | 계획상 유지·변경 |
| --- | --- | --- |
| dev | NoOp / bean 없음 → 서비스 fallback. dev YAML에는 push policy fixture만 있음 | dev key 검증·실제 AES bean 필수. provider는 기본 noop. dev boot 테스트에 가짜 key 3개 필요 |
| test | NoOp / 기본 bean 없음. `application-test.yml`의 가짜 key 값만으로 bean이 생기지 않음 | 기본 bean 없음 유지. 필요한 테스트만 명시적으로 test bean 제공 |
| local | NoOp / 기본 bean 없음. local YAML의 optional `.env` import는 보호 bean을 만들지 않음 | 기본 noop·보호 bean 제외 유지. 테스트에서 실제 `.env`를 읽지 않음 |
| integration | NoOp / 기본 bean 없음. 별도 `application-integration.yml`은 없음 | 기본 noop·보호 bean 제외 유지. `application-test.yml`과 혼동하지 않음 |
| default / prod / production 등 제외 프로필이 아닌 경우 | 실제 FCM + AES, 두 설정 모두 검증. 통합 default YAML은 가짜 key·policy만 제공 | AES 필수는 유지. provider를 명시해야 하며 미지정이면 provider 누락을 원인으로 기동 실패. 명시적 noop만 FCM credential 없이 기동 |

위 표는 프로필별 기대 동작만 정한다. `application*.yml` 중 어느 파일에 provider 기본값을
둘지는 production 구현 계약에서 정하며 이 계획에서 배치 위치를 지정하지 않는다.
빈 값은 미지정과 구별하며 모든 프로필에서 잘못된 provider 값으로 거부한다.

`PushDeviceRegistrationIntegrationTest.TestPushTokenConfiguration`과
`AccountWithdrawalConcurrencyIntegrationTest.TestConfig`는 test protector bean을 직접 제공한다.
`PushDeliveryDispatchIntegrationTest`는 실제 AES protector와 worker/provider를 직접 조립한다.
`AccountWithdrawalIntegrationTest`·`AccountWithdrawalSweepIntegrationTest`는 helper가 보호한
기기를 repository에 직접 저장하며 Spring protector bean에 의존하지 않는다.

### 기존 테스트 영향 목록

| 파일 / 기존 메서드 | 판정 | 승인 후 필요한 조치 |
| --- | --- | --- |
| `src/test/java/com/dnd/qello/notification/push/fcm/FcmHttpV1PushProviderTest.java` / `sendsBearerPathAndAllowlistedPayload` | 수정 필수 | 정확한 필드 집합을 `fid,data,android`로 변경하고 fid 값·HIGH·token 부재 검증 |
| 같은 파일 / `mapsUnregisteredToInvalidToken`, `mapsClearlyTokenScopedInvalidArgumentToInvalidToken`, `treatsPositiveCountAsAllowlistedForInvalidToken`, `keepsAmbiguousInvalidArgumentPermanent` | 기존 회귀 유지·fid 경우 추가 | `message.token` 오류 fixture를 일괄 fid 치환하지 않고 보존. 유효 allowlist payload의 `message.fid` 오류를 같은 조건으로 추가하고 모호한 오류의 PermanentFailure 유지 |
| `src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java` | 수정·확장 필수 | dev key 및 provider 행렬 추가. production/default 미지정 실패, 모든 프로필의 잘못된 값 실패 추가. FCM 검증 fixture는 명시적 `provider=fcm` 설정 |
| 같은 파일 / `productionProfileFailsFastWithoutSecrets`, `productionTokenKeyPropertiesFailFastWithoutRequiredKeys`, `omittingAnyPolicyPropertyFailsFast` | 실패 원인 분리 필수 | provider·policy·key·FCM 중 의도한 항목 하나만 빠지게 구성하고 원인 검증. key/policy 검증 시 유효 provider를 명시해 provider 누락의 거짓 양성 방지 |
| 같은 파일 / `productionConfigurationWiresTokenProtectorWithRotatingKeys`, test/integration 기동, test/local scheduling tests | 회귀 유지·조합 보강 | production AES는 noop에서도 존재. 제외 프로필은 기본 noop·보호 bean 없음, scheduling 테스트의 명시적 mock protector와 충돌 없음 |
| `src/integrationTest/java/com/dnd/qello/PushDeviceRegistrationIntegrationTest.java` | 수정 필수 | 모든 등록·해지 JSON과 인증 helper의 `token`을 `fid`로 전환. 잘못된 platform/blank/초과 길이 fixture도 전환해야 실제 해당 오류를 검사함 |
| `src/integrationTest/java/com/dnd/qello/PushDeliveryDispatchIntegrationTest.java` / `sendsThroughRealFcmAdapterAndPersistsSuccessName`, `groupedWirePayloadUsesDistinctCountAndOmitsPrivacySentinels` 및 invalid 처리 검증 | 수정·확장 필수 | 두 wire assertion을 fid·HIGH로 변경. 실제 adapter와 가짜 HTTP의 message.fid/token INVALID_ARGUMENT 분류를 worker·DB 상태 전이까지 검증하고 모호한 오류의 PermanentFailure 유지 |
| `src/integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java` / `devProfileContextLoads` 및 전체 context | 수정 필수 | 기존 “FCM/push 값 없이 기동” 계약은 key 필수화와 충돌. 가짜 AES/HMAC key·ID 주입, FCM 미주입 기동 및 실제 등록 경로 검증. health 기존 검증 유지 |
| `src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java` / `documentsPushDeviceNoContentResponsesWithoutSchema` | 확장 필수 | 기존 204 검증에 두 endpoint의 요청 schema `platform,fid`·required 집합·token 부재 검증을 추가하고 산출물 재생성 |
| `src/test/java/com/dnd/qello/notification/service/PushDeviceServiceTest.java` | 수정 불필요 / 회귀 | 내부 command/token API는 유지. HTTP 이름 변경만으로 기존 mock·reflection helper를 정리하지 않음 |
| `src/test/java/com/dnd/qello/notification/push/security/PushTokenProtectorTest.java` | 수정 불필요 / 회귀 | 독립 AES/HMAC 조립. 동일 값 보호·rotation·변조 거부 유지 |
| `src/integrationTest/java/com/dnd/qello/AccountWithdrawalIntegrationTest.java` | 수정 불필요 / 회귀 | 등록 endpoint JSON 사용 없음. 직접 저장된 기기 해지·delivery 취소 유지 |
| `src/integrationTest/java/com/dnd/qello/AccountWithdrawalSweepIntegrationTest.java` | 수정 불필요 / 회귀 | 직접 저장된 기기 및 탈퇴 rollback/sweep 검증 유지 |
| `src/integrationTest/java/com/dnd/qello/AccountWithdrawalConcurrencyIntegrationTest.java` | 수정 불필요 / 회귀 | test protector bean 유지, 서비스 command API 유지, bean 중복 및 탈퇴/재등록 lock 회귀 확인 |

전체 `src`에서 요청 타입·두 endpoint·wire 대상과 설정 사용처를 검색했다.
필수 수정 대상 기존 테스트는 위 6개 파일이며, 서비스·암호화·탈퇴 5개 파일은 수정 없이
회귀 실행 대상이다. 새 테스트 파일은 `PushDeviceRequestTest` 하나를 제안한다.

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| prod/default provider 누락을 noop으로 묵인 | 실제 발송 없이 Accepted/SENT 처리될 수 있음 | 중간 | P0 | 결정됨: 사람 결정 1에 따라 test/local/integration/dev 외 미지정은 기동 실패. UNIT-009·012·017, 명시적 fcm은 UNIT-010 |
| 잘못된 provider를 fallback으로 처리 | 오설정을 감추고 실제 발송 중단 또는 bean 누락 | 중간 | P0 | 결정됨: 사람 결정 3에 따라 noop/fcm 외 값과 빈 값은 기동 실패. UNIT-018 |
| dev key 필수화로 부팅 실패 | 자동 배포 실패·등록 불가 | 높음 | P0 | UNIT-006~008, INT-006. #364 완료는 TASK/지시의 제공 사실이며 이번 단계 live 재검증 아님 |
| YAML placeholder가 noop에서도 FCM 값을 요구 | 의도한 무credential 기동 불가 | 중간 | P0 | UNIT-009·011에서 실제 config data 바인딩 포함 |
| dev 조건 변경 중 test/local/integration 보호 bean까지 등록 | 중복 bean·credential 요구·전체 스위트 실패 | 중간 | P0 | UNIT-012, INT-001·009 |
| 요청 이름만 변경하고 validation fixture 미변경 | 다른 원인으로 400이 나도 회귀를 놓침 | 높음 | P0 | INT-003의 유효 fid 대조·원인 코드·저장 부재 |
| `message.fid` 오류의 조건 누락 또는 미처리 | 정상 기기 오무효화 또는 무효 기기 미정리 | 중간 | P0 | 결정됨: 사람 결정 2에 따라 유효 allowlist payload·INVALID_ARGUMENT·message.token/fid field violation일 때만 InvalidToken. 그 밖은 PermanentFailure. UNIT-005·INT-004. 공식 field path 근거는 UNKNOWN 유지 |
| 암호화·wire·로그에 FID 또는 credential 노출 | 개인정보·인증정보 노출 | 중간 | P0 | UNIT-002·015~016, INT-001·003·005 |
| 기존 test helper의 실제 시간/지연 의존 | 경합·timeout 검증의 간헐 실패 | 기존 코드에서 관찰 | P1 | §7의 실행 규율. 이번 단계 실행 결과로 주장하지 않음 |

## 5. Unit scenarios

구분: 신규는 새 동작의 검증, 수정은 기존 assertion/fixture의 계약 변경, 회귀는 기존 테스트
재사용이다. 같은 관찰을 새 메서드로 복제하지 말고 기존 테이블·assertion을 확장한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | ANDROID/IOS와 가짜 FID 요청 | `toCommand()` 호출 | platform과 입력값이 기존 PushToken으로 손실 없이 전달됨. 신규 `PushDeviceRequestTest` | P0 | E1 |
| UNIT-002 | 유효 FID sentinel 또는 null/blank/4096 bytes 초과/잘못된 platform | 요청 문자열화 또는 command 변환 | 문자열에 sentinel 없음, invalid 입력은 기존 validation 오류. prose를 고정하지 않고 redaction·오류 코드 검증. 신규 | P0 | E1 |
| UNIT-003 | 실제 adapter·로컬 fake HTTP·정상 data | send | message는 fid/data/android만, fid 일치, token 없음, android.priority HIGH, 기존 bearer/path/data 값 유지. 수정 | P0 | E2 |
| UNIT-004 | 기존 UNREGISTERED 오류 | send | 기존 InvalidToken 분류 유지. 회귀 | P0 | E2 |
| UNIT-005 | HTTP 400·INVALID_ARGUMENT, 유효 allowlist payload·양의 count, message.token/message.fid field violation 또는 field 누락/다른 field | send | token/fid 각각 InvalidToken. 기존 token·양의 count 회귀 유지. 모호한 경우와 다른 field는 PermanentFailure. 사람 결정 2의 fid 케이스 추가이며 실제 upstream field path 보장 아님 | P0 | E2 |
| UNIT-006 | dev·가짜 key 3개·유효 policy·기본 noop | context 기동, protector로 protect/decrypt | AES protector 단일 bean, round-trip 성공, FCM bean 없음. 신규 | P0 | E3 |
| UNIT-007 | UNIT-006에서 current ID/AES/HMAC 중 한 개씩 누락·blank | context 기동 | token 설정 바인딩을 원인으로 실패. policy 누락이나 FCM 누락의 거짓 양성 금지. 신규 parameterized | P0 | E3 |
| UNIT-008 | dev·non-Base64/32 bytes 아님/previous pair 불일치/ID 중복 | context 기동 | key 계약 원인으로 실패, 원문 없는 안전한 오류. 기존 key 검증 확장 | P0 | E3 |
| UNIT-009 | dev·provider 미지정, 또는 dev/prod/production/default·명시적 noop. 유효 key/policy·FCM 미주입 | 실제 application config data를 로딩해 context 기동 | noop provider 하나·Accepted, FCM properties/client/token-provider 없음, AES 존재. production/default 미지정은 UNIT-017에서 실패 검증 | P0 | E3 |
| UNIT-010 | dev 및 production·provider=fcm·가짜 유효 credential/key/policy | context 기동만 수행 | FCM adapter/access-token-provider 단일 bean, noop 없음, AES 존재. 토큰 갱신·send 호출 금지. 기존 wiring 확장 | P0 | E3 |
| UNIT-011 | provider=fcm·key/policy 유효, project-id/credential 중 하나 누락·blank 또는 malformed credential | 실제 config data 포함 context 기동 | FCM 바인딩 또는 credential 파싱 원인으로 실패. timeout 양수 검증도 기존 계약 유지. 신규/확장 | P0 | E3 |
| UNIT-012 | test/local/integration·유효 policy·provider 미지정, key/credential 없음 | context 기동; test/local은 기존 scheduling 조립도 실행 | 기본 noop 하나, 자동 AES 및 FCM bean 없음. 테스트가 제공한 protector만 사용. 기존 회귀 확장 | P0 | E3 |
| UNIT-013 | production·noop·current/previous 가짜 key | protect 및 이전 envelope decrypt | current-write/previous-read, 폐기 key 거부. 기존 rotation 회귀를 provider와 분리 | P0 | E3 |
| UNIT-014 | production의 나머지 설정 전부 유효, policy 5개 중 하나만 누락 | context 기동 | 의도한 policy 바인딩 실패. 기존 테스트 fixture 수정 | P1 | E3 |
| UNIT-015 | 가짜 credential/key 설정 객체 | toString 및 실패 결과 관찰 | raw fixture 값 없음, REDACTED 유지. 기존 회귀 | P0 | E3 |
| UNIT-016 | 기존 서비스 mock protector 및 실제 AES/HMAC 테스트 fixture | 등록·이전·해지, 반복 암호화·변조·rotation 기존 테스트 실행 | 저장 경계에 원문 대신 ciphertext/fingerprint만 전달. 암호화 불변식 유지. 기존 2개 클래스 재사용 | P0 | E4 |
| UNIT-017 | provider 미지정·기본 profile(default) 또는 prod/production. key/policy와 가짜 FCM 설정은 모두 유효 | 실제 application config data를 포함해 context 기동 | provider 미지정을 원인으로 기동 실패. key/policy/credential 오류로 우연히 실패하지 않음. 미지정 외 프로필의 기본 noop은 UNIT-006·009·012에서 대조 | P0 | E3 |
| UNIT-018 | test/local/integration/dev/default/prod/production에 provider=apns 또는 명시적 빈 값/공백. 필요한 나머지 설정은 유효 | context 기동 | 잘못된 provider 값을 원인으로 기동 실패. dev 등에서도 빈 값을 미지정 noop으로 취급하지 않음. 사람 결정 3 신규 parameterized | P0 | E3 |

## 6. Integration scenarios

모든 시나리오는 P0다. Setup/Action/Expected는 Given/When/Then에 대응한다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | MockMvc→controller→서비스→PostGIS (E5) | test profile의 기존 명시적 AES bean·가짜 FID·USER 인증 | fid JSON 등록·재등록 | 204/빈 body, ACTIVE 1행, 저장 ciphertext를 복호화하면 입력 FID, fingerprint 일치, 응답/로그 원문 없음 | 기존 region fixture 정리 |
| INT-002 | 해지 HTTP·repository·delivery (E5) | 본인/타인/없는/이미 해지된 기기, PENDING/FAILED/SENT | fid JSON으로 해지·반복 | 기존 멱등 204, 본인 기기만 REVOKED, PENDING/FAILED만 CANCELLED, SENT 보존 | 기존 정리 |
| INT-003 | 두 HTTP endpoint·오류 경계 (E5) | 인증 유무, fid null/누락/blank/초과 길이, invalid platform, legacy token-only | 각 요청 수행 | 무인증 401, invalid 인증 요청 400·기존 오류 코드, body/로그 sentinel 없음, invalid 저장 없음. 400만 보지 않고 유효 fid 성공 대조로 바인딩 오류 구분 | 기존 정리 |
| INT-004 | 복호화→실제 FCM adapter→fake HTTP→DB (E6) | 기존 single delivery wire fixture, 유효 allowlist payload, HTTP 400 INVALID_ARGUMENT의 message.fid/token field violation 및 모호한 오류 응답 | worker 직접 dispatch | 성공 wire는 fid/data/android·HIGH·token 부재·name 저장·SENT. fid/token 오류 각각 실제 adapter가 InvalidToken으로 분류해 device INVALID/current DEAD/sibling CANCELLED. 모호한 오류는 PermanentFailure로 current DEAD, 기기는 ACTIVE·미발송 sibling 보존. 기존 UNREGISTERED·원자성·rollback 회귀 유지 | 기존 fixture·trigger·HTTP/executor 종료 |
| INT-005 | 묶음 dispatch·FCM wire·privacy (E6) | 기존 알림 4건 fixture·fake HTTP | 기존 grouped wire dispatch | fid·HIGH, count=4와 기존 3개 data key, token/private sentinel 없음, member delivery 성공 ID 유지 | 기존 정리·HTTP/executor 종료 |
| INT-006 | 실제 dev Spring context·등록/해지 HTTP·PostGIS (E7) | dev YAML, 가짜 AES/HMAC key·ID, 기존 media/auth fixture. FCM 설정 및 test protector bean 없음 | context 기동·health·실제 fid 등록/해지 | 실제 dev AES로 204·저장/해지 성공, noop 하나·FCM bean 없음. health UP·기존 노출 제한 유지. 무key 실패는 UNIT-007 소유 | 이 클래스의 가짜 계정·기기 정리 |
| INT-007 | springdoc·공유 request schema·산출물 (E8) | 기존 OpenAPI 통합 context | 두 endpoint schema 검사 및 승인 후 재생성 | properties/required는 platform,fid, token 없음, 기존 인증·204 no-content 유지. 생성 산출물 diff 확인. 설명 문구 문자열 고정 테스트 금지 | 산출물은 승인된 변경으로 보존 |
| INT-008 | 등록·이전 repository lock/transaction (E5) | 기존 동시 재등록·ownership transfer·refresh fixture, 내부 PushToken 유지 | 기존 동시 작업 및 갱신 | ACTIVE 유일성·이전 소유 delivery 취소·같은 소유 refresh delivery 보존. 기존 latch 동기화, 새 중복 테스트 없음 | 기존 정리·executor 종료 |
| INT-009 | 탈퇴 요청·철회·sweep·푸시 재등록 경합 (E9) | AccountWithdrawal 3개 기존 클래스, race만 test protector bean | 기존 탈퇴·만료·rollback·경합 시나리오 실행 | 기존 기기 해지·미발송 취소·정합성 유지, bean 중복 없음. HTTP fid rename 대상 아님, 코드 수정 없음 | 기존 클래스별 DB·pause 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- INT-001/002/008/009는 실제 PostGIS·Flyway schema에서 검증한다. 새 migration은 없다.
- INT-004는 provider 호출 동안 DB transaction을 유지하지 않는 기존 검증, invalid 처리의
  실패 주입 rollback과 lease/fencing 회귀를 그대로 실행한다. FID 전환으로 DB 상태 의미를
  바꾸지 않는다.
- test profile 기본 protector 부재를 전역 fake bean으로 숨기지 않는다. dev 통합 검증은
  반드시 production configuration이 만든 실제 AES bean을 사용한다.

### Concurrency and idempotency

- 등록·이전 경합은 기존 latch/barrier와 bounded `Future.get`으로 관찰한다.
- 새 테스트는 고정 sleep·polling 없이 작업 시작 전 정확한 이벤트를 등록하고 기다린다.
- 기존 `AccountWithdrawalConcurrencyIntegrationTest`의 registrationDelay와 FCM fake server의
  지연/실시간 Retry-After 검증은 읽기 중 관찰한 기존 위험이다. timeout 자체를 검증하는
  경우 외의 시간 의존을 새 테스트에 복제하지 않는다. 기존 코드 정리는 이번 범위가 아니다.
  실행 시 재현 실패가 나면 원인과 범위를 보고하며 통과를 위해 기대값을 약화하지 않는다.

### External APIs

- UNIT-003 및 INT-004/005는 실제 RestClient 직렬화를 로컬 fake HTTP로 관찰한다. JSON
  소스 grep이나 mock이 요청값을 그대로 돌려주는 테스트로 대체하지 않는다.
- credential 파싱만 확인하는 UNIT-010/011은 OAuth 갱신이나 실제 FCM send를 호출하지 않는다.
- fid 오류의 실제 field path는 공식 문서에서 UNKNOWN이다. 사람 결정 2에 따라 유효
  allowlist payload의 INVALID_ARGUMENT + message.fid/token을 동일하게 InvalidToken으로
  처리하고 그 밖은 PermanentFailure로 유지한다. 문서 근거와 요구사항 결정을 구분한다.
- HIGH는 전달 우선순위 계약 검증이지 실기기 즉시 도착을 보장하는 테스트가 아니다.

### Failure recovery and reconciliation

- 기존 dispatch retry, 만료 lease 회수, stale 결과 거절, 부분 성공 기기 재전송 방지와
  invalid 처리 rollback을 INT-004/005 클래스의 회귀로 재사용한다.
- dev key 장애는 기동 실패로 감지하며 noop으로 보호 처리를 우회하지 않는다.
- 명시적 prod noop의 Accepted는 실제 FCM 성공 증거가 아니다. prod/default provider 누락은
  사람 결정 1에 따라 기동 실패로 감지한다. 배포·복구 실험은 이번 계획에 포함하지 않는다.

## 8. Test data and isolation

- FID fixture: 운영 발급값이 아닌 명백한 test-only sentinel. 길이/형식의 추가 제한을 가정하지
  않는다. 인증 device의 `installationId`와 FCM FID를 혼동해 다른 API를 바꾸지 않는다.
- key fixture: `PushConfigurationTest`의 기존 방식대로 32 bytes를 각각 고정된 서로 다른
  byte(예: current 0x11, previous 0x22, HMAC 0x33)로 채운 후 standard Base64로 인코딩한다.
  실제 secret 또는 `.env` 값을 읽거나 복사하지 않는다. key ID도 test-only 논리값이다.
- FCM credential: 기존 `productionSecretsAndPolicyFixtures`의 가짜 `authorized_user` 구조를
  재사용한다. 필수 필드는 모두 가짜 문자열이며 실제 service account/private key가 아니다.
  정상 파싱 fixture와 malformed JSON fixture를 분리하고 오류·로그에 원문이 나오지 않게 한다.
- policy fixture: 기존 테스트의 5개 값만 재사용하고 운영 권장값으로 취급하지 않는다.
- Database isolation: 기존 `PostgisContainerIntegrationTestSupport`의 JVM 공유 컨테이너,
  클래스 시작 전 baseline 복원 및 각 클래스 fixture cleanup을 사용한다. 공유 DB를 대상으로
  테스트 클래스를 임의 병렬화하지 않는다.
- Clock/randomness: 기존 fixed/mutable Clock을 재사용한다. AES nonce는 암호화 구현에 맡기고
  특정 ciphertext byte를 고정하지 않는다. 시간 경합을 새 sleep으로 만들지 않는다.
- External doubles/cleanup: 기존 HttpServer ephemeral port, 가짜 bearer 및 응답 사용.
  server·executor는 finally/AfterEach에서 종료하고 실패 주입 trigger·pause도 해제한다.
- 기존 `.env`, 실제 DB/FCM/AWS 연결, 원격 secret 조회는 금지한다. local 프로필 configuration
  테스트도 실제 local YAML의 `.env` import를 실행하지 않는다.

## 9. Execution contracts

아래는 사람 승인 후의 소유권이다. 이번 단계에서 실행 에이전트를 기동하지 않는다.
단위 경로의 `N`은 `src/test/java/com/dnd/qello/notification`,
통합 경로의 `I`는 `src/integrationTest/java/com/dnd/qello`를 뜻한다.

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | E1 요청 단위 | 신규 `N/web/request/PushDeviceRequestTest.java` | UNIT-001~002 | 해당 클래스 test |
| 1 | E2 FCM 단위 | 기존 `N/push/fcm/FcmHttpV1PushProviderTest.java` | UNIT-003~005 (결정 2의 fid 오류 추가) | 해당 클래스 test |
| 1 | E3 설정 단위 | 기존 `N/config/PushConfigurationTest.java` | UNIT-006~015, UNIT-017~018 (결정 1·3) | 해당 클래스 test |
| 2 | E4 단위 회귀 | 읽기/실행만: `N/service/PushDeviceServiceTest.java`, `N/push/security/PushTokenProtectorTest.java` | UNIT-016 | 두 클래스 test |
| 2 | E5 등록 통합 | 기존 `I/PushDeviceRegistrationIntegrationTest.java` | INT-001~003, INT-008 | 해당 클래스 integrationTest |
| 2 | E6 발송 통합 | 기존 `I/PushDeliveryDispatchIntegrationTest.java` | INT-004~005 (결정 2의 실제 adapter→DB 검증 포함) | 해당 클래스 integrationTest |
| 2 | E7 dev 통합 | 기존 `I/DevProfileBootIntegrationTest.java` | INT-006 | 해당 클래스 integrationTest |
| 3 | E8 스펙 통합 | 기존 `I/OpenApiSpecificationIntegrationTest.java`, 생성 `docs/api/openapi.json` | INT-007 | 해당 클래스 integrationTest와 산출물 diff |
| 3 | E9 탈퇴 회귀 | 읽기/실행만: `I/AccountWithdrawalIntegrationTest.java`, `I/AccountWithdrawalSweepIntegrationTest.java`, `I/AccountWithdrawalConcurrencyIntegrationTest.java` | INT-009 | 세 클래스 integrationTest |
| 4 | 오케스트레이터/독립 검증 | 구현 파일 검토 및 실행 증거, 승인 후 보고 산출물 | 전체 | 아래 저장소 게이트 |

Order는 의존 순서이지 공유 DB 테스트의 병렬 실행 승인이 아니다. production Java·YAML과
문서 구현은 TASK의 별도 실행 계약에 따르고 테스트 실행자의 소유 파일에 섞지 않는다.

승인 후 Java 21, Docker/Testcontainers 환경에서 해당 클래스 검증과 전체 회귀를 수행한다.
이 명령들은 계획이며 이번 단계 실행 증거가 아니다.

```bash
./gradlew test --tests 'com.dnd.qello.notification.*' --console=plain
./gradlew integrationTest --tests 'com.dnd.qello.PushDeviceRegistrationIntegrationTest' --tests 'com.dnd.qello.PushDeliveryDispatchIntegrationTest' --tests 'com.dnd.qello.DevProfileBootIntegrationTest' --tests 'com.dnd.qello.AccountWithdrawal*IntegrationTest' --tests 'com.dnd.qello.OpenApiSpecificationIntegrationTest' --console=plain
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness test-run --id TEST-PLAN-GH-365-PUSH-FID-PROVIDER
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests
npm run hooks:validate
GIT_MASTER=1 git diff --check
```

실행·보고 전 harness-test-run과 reporting 계약을 다시 읽는다. 장시간 명령은 monitor로
시작하고 종료 코드와 결과를 확인한다. 실패 시 assertion 완화·skip·suppression 없이
원인을 구분한다. fixture/production 결함은 담당 오케스트레이터에게 반환한다.

## 10. Completion criteria

### 이번 단계의 계획 작성 완료

- [x] Issue #365·현재 branch·TASK의 식별자 일치 확인.
- [x] 네 기능 인수 조건 및 별도 전체 검증 조건, 사람 결정 1~3 추적.
- [x] 단위 18개·통합 9개, 새 테스트/수정/회귀와 소유 파일 구분.
- [x] test/local/integration protector 부재 및 명시적 fixture 경로 기록.
- [x] 공식 FID·HIGH 확인과 field path UNKNOWN 유지, prod/default provider 필수화 및 잘못된 값 거부 결정 반영.
- [x] 상태 DRAFT 유지. 구현·테스트 실행·배포·commit/push/외부 쓰기 없음.

계획 생성은 `templates/test-plan.md`의 전 절을 이 파일에 구체화했다. 모든 파일 생성·수정은
apply_patch만 사용하라는 세션 지시에 따라 파일을 직접 쓰는 `harness test-plan` scaffold는
실행하지 않았다. `TASK.md`와 다른 파일은 변경하지 않는다.

### 사람 승인 후 구현 단계의 완료

- [x] 사람 결정 1~3의 반영을 오케스트레이터가 확인하고 계획 승인 표시 기록.
- [ ] 모든 P0 구현/회귀 및 P1 policy 실패 원인 분리.
- [ ] JUnit 5, 한국어 @DisplayName, 신규 클래스 실제 ISO 생성 시각·원본 시나리오 기록.
- [ ] 기존 클래스의 생성 시각은 보존하고 확장 시각·시나리오를 추가.
- [ ] 단위·통합 테스트와 저장소 게이트 통과, OpenAPI 재생성 diff 확인.
- [ ] DB·transaction·동시성·외부 API·장애 복구 및 privacy 결과 분석.
- [ ] 승인 후 templates/test-report.md 기반 실행 보고서 작성.

이번 단계에는 Gradle test/integrationTest/check, harness test-run/check/pr-ready,
hooks 검증, OpenAPI 재생성을 실행하지 않는다. 승인 전 계획 작업이라 제품 동작 검증은
미실행이다. 후속 실행 단계에서 환경 부족은 BLOCKED, 재현 실패는 FAIL로 보고한다.

## 11. Human approval

- Reviewer: @Byuntil (반영 확인: 오케스트레이터)
- Decision: `APPROVED`. 사람 결정 3건을 반영한 27개 시나리오와 §9 소유 파일로 테스트 작성·실행을 승인한다.
- Approved at: 2026-11-21 — https://github.com/dnd-side-project/dnd-15th-2-backend/issues/365#issuecomment-7060067551

### 결정됨 (2026-11-21, @Byuntil / TASK Objective)

1. provider 미지정: test/local/integration/dev만 noop 기본값을 사용한다. 그 밖의 프로필은
   provider를 명시하지 않으면 기동 실패한다. Issue의 전역 기본 noop 요구를 대체한다.
   추적: §3 결정 1, §4 첫 위험, UNIT-006·009·012·017, INT-006, §9 E3·E7.
2. FID 오류 분류: 유효 allowlist payload의 INVALID_ARGUMENT + message.token 또는
   message.fid field violation은 InvalidToken이며 그 밖의 INVALID_ARGUMENT는 PermanentFailure다.
   기존 token 회귀를 보존하고 fid 경우를 추가한다. 실제 upstream field path의 공식 문서
   근거는 UNKNOWN으로 유지한다. 추적: §3 결정 2/공식 문서 표, §4 오류 위험,
   UNIT-005·INT-004, §9 E2·E6.
3. 잘못된 provider: noop/fcm 외 값은 빈 값·공백을 포함해 기동 실패한다.
   추적: §3 결정 3, §4 provider 값 위험, UNIT-018, §9 E3.

### 승인 표시 확인 필요

27개 시나리오와 소유 파일에 대한 결정 반영 확인 및 승인 표시만 오케스트레이터에게 남긴다.
기본값을 배치할 YAML 파일 선택은 production 구현 계약이며 이 계획에서 결정하지 않는다.

### 가정과 미확인

- ASSUMED: 내부 PushToken/command·DB 이름은 유지한다. TASK의 DB 제외 범위와 현재 호출
  구조에 맞는 최소 변경 제안이며, HTTP 오류 field 이름까지 fid로 바꿀지는 별도 계약이 없다.
  오류 코드·비노출은 검증하되 새 오류 field 문자열을 임의로 고정하지 않는다.
- UNKNOWN: 혼합 profile의 상세 정책. 지원하지 않는 provider 값은 사람 결정 3에 따라
  기동 실패로 확정되었으며 미결 사항이 아니다.
- #364 apply·test-server 주입 완료는 현재 지시와 TASK가 제공한 선행 상태로만 사용한다.
  원격 실행 기록·현재 secret·서버 상태는 이번 단계에서 다시 확인하지 않았다.
