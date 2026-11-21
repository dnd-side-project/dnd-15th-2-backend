# Test Report: TEST-PLAN-GH-365-PUSH-FID-PROVIDER

> Created at: `2026-11-21T17:57:17+09:00`
> GitHub Issue: `#365`
> Branch: `feat/gh-365-push-fid-provider`
> Commit: `02f71305`

## 1. Executive summary

- Result: `PASS` — Issue #365의 로컬 구현·승인된 27개 시나리오와 필수 검증.
- Tested scope: FID 등록·해지, FCM fid/HIGH 요청, token/fid 무효 기기 분류,
  프로필별 provider 누락·오설정, dev AES와 key 검증, OpenAPI 및 전체 회귀.
- Unverified scope: 실제 FCM·모바일 수신, cloud/배포, 혼합 profile 정책.
- Release recommendation: 로컬 게이트 통과. 독립 소스 검토에서 새 발견 사항 없음.
  PR·병합·배포 승인을 대신하지 않는다. commit/push/PR 생성은 수행하지 않았다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 21.0.13.0.3, JAVA_HOME을 Java 21 설치 경로로 지정 |
| Spring Boot | 3.5.16 |
| Gradle | wrapper 8.14.3 |
| Docker | server 29.6.2 |
| Database | 로컬 Testcontainers PostGIS, PostgreSQL 16 계열 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result / exit code | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| production compileJava | PASS / 0 | 해당 없음 | 4s | Gradle BUILD SUCCESSFUL |
| 지정 알림 단위 test --rerun | PASS / 0 | 357, failure/error/skip 모두 0 | 재실행 4s | 38개 notification 클래스 XML |
| 지정 통합 integrationTest --rerun 및 OpenAPI 생성 | PASS / 0 | 61, failure/error/skip 모두 0 | 재실행 17s | 지정 7개 클래스 XML, 생성물 diff 확인 |
| harness test-run: 전체 단위 | PASS / 0 | 1495, failure/error/skip 모두 0 | 37s | build/test-results/test, 203개 suite |
| harness test-run: 전체 통합 | PASS / 0 | 874, failure/error/skip 모두 0 | 2m 25s | build/test-results/integrationTest, 112개 suite |
| harness check | PASS / 0 | 정책 검사 | 미계측 | 메타데이터·민감정보·관례·workflow·label·Husky·baseline 검사 통과 |
| harness pr-ready --project-tests | PASS / 0 | Gradle check, Java architecture·Checkstyle·Spotless 포함 | Gradle check 5s | Local PR readiness checks passed |
| npm run hooks:validate | PASS / 0 | Husky 정책 | 미계측 | Husky validation passed |
| Java 13개 임시 stage pre-commit | PASS / 0 | staged Checkstyle·Java 규칙·메타데이터·민감정보 검사 | Gradle gate 8s | Pre-commit checks passed, 종료 후 index 비었음 |
| git diff --check | PASS / 0 | 공백 검사 | 미계측 | 공백 오류 없음 |

전체 수치는 XML 검색의 모든 페이지를 순회해 합산했다. 초기 진행 보고의 알림 단위
214개는 첫 20개 파일만 집계한 값이며, 정확한 알림 단위 수는 38개 클래스의 357개다.
테스트 수는 parameterized invocation을 포함하며 계획의 시나리오 ID 27개와 구별한다.

실행 명령은 다음과 같다. 모든 Gradle 실행은 Java 21을 사용했다.

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew test --tests 'com.dnd.qello.notification.*' --rerun --console=plain
env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./gradlew integrationTest --tests 'com.dnd.qello.PushDeviceRegistrationIntegrationTest' --tests 'com.dnd.qello.PushDeliveryDispatchIntegrationTest' --tests 'com.dnd.qello.DevProfileBootIntegrationTest' --tests 'com.dnd.qello.AccountWithdrawal*IntegrationTest' --tests 'com.dnd.qello.OpenApiSpecificationIntegrationTest' --rerun --console=plain
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness check
GIT_MASTER=1 env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness test-run --id TEST-PLAN-GH-365-PUSH-FID-PROVIDER
GIT_MASTER=1 env -u QELLO_TEST_COMPOSE -u DOCKER_HOST -u SSL_CERT_FILE -u SSL_CERT_DIR ./harness pr-ready --project-tests
npm run hooks:validate
GIT_MASTER=1 env -u SSL_CERT_FILE -u SSL_CERT_DIR python3 scripts/run-hook.py pre-commit
GIT_MASTER=1 git diff --check
```

pre-commit 전 변경 Java 13개만 stage했다. EXIT trap으로 같은 파일을 `git restore --staged`
처리한 뒤 빈 index를 확인했다. 기존 TASK·승인 계획은 stage하거나 수정하지 않았다.
포맷은 같은 Java 13개에만 `spotlessApply -PspotlessFiles=...`를 적용했다.
OpenAPI는 통합 테스트가 생성했으며 직접 편집하지 않았다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | PushDeviceRequestTest.convertsFidToExistingTokenCommand | 두 플랫폼 |
| UNIT-002 | PASS | PushDeviceRequestTest의 redaction·invalid 입력 메서드 | null/blank/UTF-8 상한/플랫폼 |
| UNIT-003 | PASS | FcmHttpV1PushProviderTest.sendsBearerPathAndAllowlistedPayload | fid/data/android·HIGH·token 부재 |
| UNIT-004 | PASS | FcmHttpV1PushProviderTest.mapsUnregisteredToInvalidToken | 기존 분류 유지 |
| UNIT-005 | PASS | FcmHttpV1PushProviderTest의 token/fid scoped·ambiguous·조건 불일치 메서드 | token/fid 모두, 보수적 영구 실패 유지 |
| UNIT-006 | PASS | PushConfigurationTest.devDefaultsToNoOpAndWiresRealTokenProtector | 실제 AES round-trip |
| UNIT-007 | PASS | PushConfigurationTest.devRejectsMissingOrBlankRequiredKeys, productionTokenKeyPropertiesFailFastWithoutRequiredKeys | key 원인 분리 |
| UNIT-008 | PASS | PushConfigurationTest.devRejectsInvalidKeyConfiguration | Base64/길이/pair/중복 ID |
| UNIT-009 | PASS | PushConfigurationTest.explicitNoOpStartsWithoutFcmPlaceholders | 실제 config data·scan |
| UNIT-010 | PASS | PushConfigurationTest.explicitFcmWiresOnlyFcmProvider | 가짜 credential 파싱만 |
| UNIT-011 | PASS | PushConfigurationTest.productionProfileFailsFastWithoutSecrets | FCM 누락/빈 값/파싱/timeout |
| UNIT-012 | PASS | PushConfigurationTest.testProfileStartsWithFakeProviderWithoutRealSecrets 및 scheduling 회귀 | test/local/integration |
| UNIT-013 | PASS | PushConfigurationTest.productionConfigurationWiresTokenProtectorWithRotatingKeys 및 key-ring 회귀 | current-write/previous-read |
| UNIT-014 | PASS | PushConfigurationTest.omittingAnyPolicyPropertyFailsFast | 다른 설정은 유효 |
| UNIT-015 | PASS | PushConfigurationTest의 key/credential redaction 메서드 | 안전한 실패 포함 |
| UNIT-016 | PASS | PushDeviceServiceTest, PushTokenProtectorTest | 기존 테스트 무수정 회귀 |
| UNIT-017 | PASS | PushConfigurationTest.nonDevelopmentProfilesRequireExplicitProvider | default/prod/production |
| UNIT-018 | PASS | PushConfigurationTest.rejectsInvalidProviderInEveryProfile | apns/빈 값/공백, 7개 profile |
| INT-001 | PASS | PushDeviceRegistrationIntegrationTest.registersOwnPushTokenWithoutLeakingPlaintext | fid·재등록·복호화·fingerprint |
| INT-002 | PASS | 같은 클래스의 본인/타인 해지 및 revokesMissingFidWithoutCreatingDevice | 204·멱등·취소 범위 |
| INT-003 | PASS | 같은 클래스의 validatesAuthenticationAndRedactsTokenAcrossBothEndpoints | 두 endpoint invalid·legacy-only·유효 대조 |
| INT-004 | PASS | PushDeliveryDispatchIntegrationTest의 실제 wire·invalidArgumentFromRealAdapterInvalidatesDeviceAndCancelsSiblings·ambiguousInvalidArgumentFromRealAdapterKeepsDeviceAndSiblingActive | adapter→DB, 기존 rollback/lease 회귀 |
| INT-005 | PASS | PushDeliveryDispatchIntegrationTest.groupedWirePayloadUsesDistinctCountAndOmitsPrivacySentinels | count=4·최소 data·성공 ID |
| INT-006 | PASS | DevProfileBootIntegrationTest의 devProfileContextLoads·registersAndRevokesFidWithAutoConfiguredProtector·health 메서드 | 자동 AES·noop·실제 HTTP |
| INT-007 | PASS | OpenApiSpecificationIntegrationTest.documentsPushDeviceNoContentResponsesWithoutSchema 및 생성 메서드 | 필드·required·인증·204·재생성 |
| INT-008 | PASS | PushDeviceRegistrationIntegrationTest의 concurrent registration/transfer·refresh 회귀 | 기존 lock/transaction |
| INT-009 | PASS | AccountWithdrawalIntegrationTest·AccountWithdrawalSweepIntegrationTest·AccountWithdrawalConcurrencyIntegrationTest | 기존 3개 클래스 무수정 회귀 |

## 5. Failures and diagnostics

- 초기 지정 통합 실행: 61개 중 1개 실패. dev 등록 테스트가 새 가짜 계정을 만들 때 필수
  `coarse_region_code`를 빠뜨린 fixture 결함이었다. 기준 데이터인 국가 코드를 넣어 수정했고
  같은 필수 명령 재실행에서 61개 모두 통과했다. production/DB 제약은 변경하지 않았다.
- 초기 harness check: 긴 parameterized annotation 때문에 validator의 @DisplayName 탐색
  범위를 벗어났다. FCM 테스트의 두 @DisplayName을 @ParameterizedTest 바로 앞에 배치해
  해결했다. validator 수정·assertion 완화·skip·suppression은 하지 않았다.
- Git pager가 빈 diff 출력에서 대기한 두 경우는 `q`로 닫아 원래 검사를 계속했다.
  이후 명령은 `GIT_PAGER=cat`을 사용했다. 중단된 검증을 통과로 계산하지 않았다.
- 최종 필수 검증 실패·skip은 0이다. jdtls/yaml-language-server가 설치되어 있지 않아
  LSP 진단은 미실행이며 컴파일·실제 Spring 기동·Checkstyle·Java 규칙으로 검증했다.

## 6. Potential issues

### Application code

- 요청의 외부 이름만 fid로 바꾸고 내부 PushDeviceCommand/PushToken은 유지했다.
  기존 validation 오류 field 이름도 token을 유지한다. 추가 rename은 하지 않았다.
- 결정 1·3은 PushConfiguration 생성자의 Environment 검증에 있다. 전역 YAML 기본값은
  미지정과 명시적 빈 값을 구별하기 어렵게 하므로 코드에서 프로필별 기본을 선택한다.
- PushProperties 자체와 FCM nested configuration에 provider 조건을 적용해 전역
  ConfigurationPropertiesScan이 noop에서 credential 검증을 켜지 않게 했다.

### Infrastructure and resource limits

- 실제 cloud·배포 검증은 미실행이다. #364 완료는 TASK가 제공한 선행 상태로만 사용했다.
  dev의 fcm 선택·worker 활성화는 #366에 남긴다. 이 작업에서 infra/.github는 변경하지 않았다.

### Database and migrations

- migration 없이 기존 ciphertext/fingerprint 컬럼을 사용한다. default-profile 통합 fixture는
  새 명시적 provider 계약을 만족하도록 application-default.yml에서 noop을 선택한다.

### Concurrency and idempotency

- 기존 동시 등록·소유권 이전·탈퇴 경합을 회귀 실행했다. 기존 경합 helper의 시간 지연은
  이번 변경에서 추가하거나 범위를 넓혀 정리하지 않았다. 신규 검증에는 sleep/polling이 없다.

### Transactions and event ordering

- 실제 provider 호출은 기존 transaction 외부 경계를 유지하며 invalid 처리·sibling 취소의
  원자성과 실패 rollback 회귀가 통과했다. 모호한 INVALID_ARGUMENT는 기기를 무효화하지 않는다.

### External APIs

- 결정 2는 FcmHttpV1PushProvider의 field violation 판정에 있다. 승인된 조건에서 token과
  fid를 동일 처리한다. 공식 FID 오류 field path는 UNKNOWN이며 live 오류 형태는 미검증이다.
- 실제 FCM/OAuth 갱신·모바일 수신 검증은 수행하지 않았다. 가짜 credential·로컬 HTTP만 사용했다.

### Failure recovery and reconciliation

- 기존 lease 만료·stale 결과·retry·부분 성공·rotation 테스트가 통과했다.
  noop의 Accepted/SENT는 실제 수신 보장이 아니며 비테스트 provider 누락을 기동 실패로 막는다.

## 7. Regression and residual risk

- 필수 로컬 검증과 독립 읽기 전용 소스 검토는 완료했다. 독립 검토자는 빌드·테스트 실행을
  주장하지 않았으며 실행 결과는 이 보고서의 실제 명령·XML 근거다.
- 실제 배포·실기기·혼합 profile 정책은 미검증이다. 관련 후속 활성화는 #366 범위이고,
  승인 게이트·권한·운영 값은 이번 변경으로 결정하거나 변경하지 않았다.
- 커밋·push·PR·Issue 코멘트, 실제 secret 조회, `.env` 읽기는 수행하지 않았다.

## 8. Artifacts

- Test plan: docs/test-plans/gh-365-TEST-PLAN-GH-365-PUSH-FID-PROVIDER.md (APPROVED)
- CI run: 미실행. 로컬 실행만 수행.
- Related ADR: docs/adr/0008-adopt-fcm-push-delivery-pipeline.md의 2026-11-21 갱신 이력.
- PR: 생성하지 않음.
- 생성물: docs/api/openapi.json.
- 실행 증거: build/test-results/test, build/test-results/integrationTest,
  build/reports/tests, staged Java convention 검사 결과.

이번 구현에서 변경한 파일 19개:

```text
src/main/java/com/dnd/qello/notification/web/request/PushDeviceRequest.java
src/main/java/com/dnd/qello/notification/web/NotificationApiSpec.java
src/main/java/com/dnd/qello/notification/push/fcm/FcmHttpV1PushProvider.java
src/main/java/com/dnd/qello/notification/config/PushConfiguration.java
src/main/java/com/dnd/qello/notification/config/PushProperties.java
src/main/java/com/dnd/qello/notification/config/PushTokenProperties.java
src/main/resources/application.yml
src/integrationTest/resources/application-default.yml
src/test/java/com/dnd/qello/notification/web/request/PushDeviceRequestTest.java
src/test/java/com/dnd/qello/notification/push/fcm/FcmHttpV1PushProviderTest.java
src/test/java/com/dnd/qello/notification/config/PushConfigurationTest.java
src/integrationTest/java/com/dnd/qello/PushDeviceRegistrationIntegrationTest.java
src/integrationTest/java/com/dnd/qello/PushDeliveryDispatchIntegrationTest.java
src/integrationTest/java/com/dnd/qello/DevProfileBootIntegrationTest.java
src/integrationTest/java/com/dnd/qello/OpenApiSpecificationIntegrationTest.java
docs/push-production-setup.md
docs/adr/0008-adopt-fcm-push-delivery-pipeline.md
docs/api/openapi.json
docs/reports/tests/gh-365-TEST-PLAN-GH-365-PUSH-FID-PROVIDER.md
```

기존 `TASK.md`와 승인된 테스트 계획은 이번 구현에서 변경하지 않았다.

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 실제 비밀정보가 없음
- [x] 미실행 범위와 LSP 진단 부재가 명시됨
- [x] 후속 dev 활성화 #366과 UNKNOWN/live 검증 잔여 범위 구분
- [ ] PR 설명 대조: PR 미생성으로 해당 단계 미수행
