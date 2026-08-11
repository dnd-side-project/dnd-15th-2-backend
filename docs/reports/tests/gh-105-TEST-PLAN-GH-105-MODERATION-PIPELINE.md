# Test Report: TEST-PLAN-GH-105-MODERATION-PIPELINE

> Created at: `2026-08-11T22:30:00+09:00`
> GitHub Issue: `#105`
> Branch: `feat/gh-105-moderation-pipeline`
> Commit: `9d8e269` (테스트 실행 시점의 base; 이 보고서와 함께 커밋되는 변경은 아직 커밋되지 않음)

## 1. Executive summary

- Result: `PARTIAL`
- Tested scope: UNIT-001~UNIT-013 전체 13개, INT-001~INT-005 5개. 합계 18/19
  시나리오를 구현하고 통과시켰다.
- Unverified scope:
  - INT-006(Spring 빈 구성 수준의 실행 자원 격리)은 구현하지 않았다 — 이 이슈는
    `ModerationPipelineService`와 `OpenAiModerationProviderClient`를 의도적으로
    Spring 빈으로 등록하지 않으므로(§6 참고) 통합 수준에서 검증할 빈 구성 자체가
    없다. 동일 속성(인스턴스 간 실행 자원 비공유)은 UNIT-013이 순수 자바
    동시성 테스트로 대신 검증했다.
  - `INV-PIPE-001`, `002`, `004`는 GitHub Issue #105 본문과 저장소 어디에서도
    정의를 찾지 못해 커버리지를 주장하지 않는다(테스트 계획 §4/§11에 사람
    승인 시점부터 명시됨).
  - 실제 OpenAI 엔드포인트 대상 검증은 수행하지 않았다 — 모든 외부 API
    시나리오는 로컬 JDK `HttpServer`로 대체했다.
- Release recommendation: 이 이슈 범위(공통 pipeline 오케스트레이션) 내에서는
  merge 가능. 단, `ModerationPipelineService`는 Spring 빈이 아니므로 이 변경만
  으로는 어떤 실제 요청 경로도 동작하지 않는다 — 닉네임(#106)·답변(#107)이 실제
  `TextNormalizer`/`LocalRuleEngine`/`PolicyEngine` 구현체와 함께 배선해야
  사용자에게 영향을 준다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Temurin, Gradle toolchain) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine` (local Docker) |
| Test runner | JUnit 5 (Gradle `test`, `integrationTest`) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests "com.dnd.qello.filtering.moderation.*"` | PASS | 13 | ≈0.11s | `build/test-results/test/TEST-com.dnd.qello.filtering.moderation.ModerationPipelineServiceTest.xml`, `TEST-com.dnd.qello.filtering.moderation.openai.OpenAiModerationResponseMapperTest.xml` |
| `./gradlew integrationTest --tests "com.dnd.qello.ModerationPipelineIntegrationTest"` | PASS | 5 | 1.283s | `build/test-results/integrationTest/TEST-com.dnd.qello.ModerationPipelineIntegrationTest.xml` |
| `./harness pr-ready --project-tests` (전체 unit+integration+check) | PASS | 전체 스위트 (기존 테스트 포함) | ≈2m47s | 로컬 실행 로그(터미널) — CI 실행은 아직 없음 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `ModerationPipelineServiceTest#shortCircuitsOnLocalRuleBlock` | |
| UNIT-002 | PASS | `ModerationPipelineServiceTest#callsProviderWithNormalizedInputWhenRuleDoesNotMatch` | |
| UNIT-003 | PASS | `ModerationPipelineServiceTest#policyEngineOverridesFlaggedTrueToAllow` | |
| UNIT-004 | PASS | `ModerationPipelineServiceTest#policyEngineOverridesFlaggedFalseToBlock` | |
| UNIT-005 | PASS | `ModerationPipelineServiceTest#propagatesProviderTimeoutWithoutConvertingToVerdict` | |
| UNIT-006 | PASS | `ModerationPipelineServiceTest#propagatesProviderServerErrorWithoutConvertingToVerdict` | |
| UNIT-007 | PASS | `ModerationPipelineServiceTest#keepsRequestedReleaseIdAndActualModelSeparate` | |
| UNIT-008 | PASS | `ModerationPipelineServiceTest#exposesRuleProviderAndFinalDecisionSeparately` | |
| UNIT-009 | PASS | `ModerationPipelineServiceTest#propagatesContentTypeAndLanguageToPolicyEngine` | |
| UNIT-010 | PASS | `ModerationPipelineServiceTest#normalizesUsingReleaseScopedRef` | |
| UNIT-011 | PASS | `OpenAiModerationResponseMapperTest#mapsOfficialResponseShapeExactly` | 계획상 openai 패키지로 이동해 구현 |
| UNIT-012 | PASS | `OpenAiModerationResponseMapperTest#toleratesUndocumentedFields` | 계획상 openai 패키지로 이동해 구현 |
| UNIT-013 | PASS | `ModerationPipelineServiceTest#doesNotShareExecutionCapacityAcrossInstances` | INT-006의 구조적 속성을 이 시나리오가 대신 증명 |
| INT-001 | PASS | `ModerationPipelineIntegrationTest#persistsBlockDecisionWithReleaseAndActualModel` | |
| INT-002 | PASS | `ModerationPipelineIntegrationTest#persistsRuleBlockedDecisionWithoutCallingProvider` | |
| INT-003 | PASS | `ModerationPipelineIntegrationTest#doesNotPersistDecisionOnProviderTimeout` | |
| INT-004 | PASS | `ModerationPipelineIntegrationTest#keepsExactlyOneDecisionRowOnDuplicateExecution` | 계획은 "저장 거부 또는 멱등 반환 중 하나"를 허용했고, 실제 구현은 저장 거부(`DataIntegrityViolationException`) 쪽을 택함 |
| INT-005 | PASS | `ModerationPipelineIntegrationTest#doesNotPersistWhenFilterJobIdAbsent` | §2 설계 가정(조건부 영속화)을 그대로 검증 |
| INT-006 | 미실행 | — | §1 Unverified scope 참고. UNIT-013으로 대체 |

## 5. Failures and diagnostics

실행한 모든 시나리오가 첫 실행에서 통과했다. 재현된 실패는 없다.

## 6. Potential issues

### Application code

- `ModerationPipelineService`, `TextNormalizer`, `LocalRuleEngine`,
  `ModerationProviderClient`, `PolicyEngine`, `OpenAiModerationProviderClient`는
  모두 의도적으로 `@Component`/`@Service`가 아니다. 실제 `TextNormalizer`·
  `LocalRuleEngine`·`PolicyEngine` 구현체(정규화 규칙·고신뢰 사전·threshold)가
  아직 결정되지 않은 상태에서 이 서비스를 컴포넌트 스캔 대상으로 등록하면
  Spring 컨텍스트 전체가 기동 실패한다 — 최초 구현 시 `@Service`를 붙였다가
  이 문제를 발견해 제거했다(커밋에는 반영되지 않은 로컬 수정 이력). 이후
  #106/#107이 실제 구현체와 함께 명시적으로 `new`하거나 자체 `@Configuration`
  으로 배선해야 한다 — 후속 이슈에 이 제약을 명시적으로 인계해야 한다.
- `PolicyEngine`이 최종 판정을 전적으로 결정하므로, 이 서비스 자체의 정확도는
  전적으로 아직 구현되지 않은 `PolicyEngine`에 달려 있다. 이번 이슈의 테스트는
  오케스트레이션 순서만 검증하며 실제 오탐/미탐 품질은 검증 대상이 아니다.

### Infrastructure and resource limits

- `OpenAiModerationProviderClient`는 호출자가 넘긴 `RestClient`의 timeout 설정에
  전적으로 의존한다. 호출자가 timeout 없는 `RestClient`를 구성하면 공급자 응답이
  없을 때 무기한 대기할 수 있다 — 닉네임 동기 경로(#106)는 반드시 유한한
  timeout을 강제해야 한다. 이 이슈 자체는 timeout 값을 강제하는 장치를 두지
  않았다(설계상 그 값은 이슈 범위 밖).

### Database and migrations

- 이 이슈는 마이그레이션을 추가하지 않았다. 기존 `filter_decision`/`filter_job`/
  `filter_release` 스키마(#103/#104)를 그대로 사용했으며, FK·유일 인덱스 제약을
  실제 Postgres로 검증했다(INT-001, INT-004).

### Concurrency and idempotency

- INT-004는 동일 `filterJobId`+`attemptGeneration` 중복 실행이 DB 유일 인덱스에서
  `DataIntegrityViolationException`으로 거절됨을 확인했다. `ModerationPipelineService`
  는 이 예외를 잡아 "기존 결정을 조회해 반환"하는 멱등 처리로 바꾸지 않는다 —
  테스트 계획 §6 실행 계약에서 두 방식 모두 허용했고, 이번 구현은 더 단순한
  "던지고 호출자에게 위임" 쪽을 택했다. 실제 재시도 경로(#108)가 이 예외를
  캐치해 기존 행을 조회하는 로직이 필요한지는 그 이슈에서 결정해야 한다.
- UNIT-013은 손으로 만든 두 `ExecutorService`로 실행 자원 격리 "구조"만
  증명한다. 실제 닉네임·답변 경로가 서로 다른 스레드풀·커넥션 풀을 가진
  `RestClient`/`OpenAiModerationProviderClient` 인스턴스로 배선되는지는
  #106/#107 구현 후에만 확인할 수 있다.

### Transactions and event ordering

- `ModerationPipelineService.execute()`는 의도적으로 `@Transactional`을 두지
  않는다 — 공급자 HTTP 호출이 DB 트랜잭션 안에 갇히지 않게 하기 위해서다.
  `FilterDecision` 저장은 `JpaFilterDecisionRepository.save()`가 자신의
  트랜잭션 경계에서 처리한다. 이 설계는 코드 검토로 확인했지만 "HTTP 호출
  시점에 활성 트랜잭션이 없다"를 직접 단언하는 자동 테스트는 만들지 않았다 —
  후속 검증 후보로 남긴다.

### External APIs

- 모든 외부 API 시나리오는 로컬 JDK `HttpServer`로 대체했다. 실제 OpenAI
  엔드포인트의 gzip 압축, chunked 전송, 실제 4xx/5xx 오류 응답 본문 형태,
  실제 rate limit 헤더는 검증하지 않았다.
- `OpenAiModerationProviderClient`는 자체 재시도를 하지 않는다(의도된 설계 —
  재시도는 #108 소관). 모든 실패는 즉시 `MODERATION_PROVIDER_UNAVAILABLE`로
  전파된다.

### Failure recovery and reconciliation

- INT-003이 확인한 대로, timeout/error 시 `filter_decision` 행이 전혀 생성되지
  않는다 — 이후 재시도(다른 이슈 소관)가 같은 `filterJobId`+`attemptGeneration`
  으로 안전하게 재시도할 수 있는 기반은 마련됐다. 실제 재시도 스케줄링·backoff는
  이 이슈에 포함되지 않는다.

## 7. Regression and residual risk

- `FilteringErrorCode`에 `MODERATION_PROVIDER_UNAVAILABLE`(`FLT-EXT-001`) 값을
  추가했다 — 기존 코드 값은 변경하지 않았다(additive-only).
- 새로 추가한 클래스는 어디에서도 Spring 빈으로 등록되지 않으므로 기존 동작에
  대한 회귀 위험은 없다. `./harness pr-ready --project-tests`로 저장소 전체
  기존 테스트가 여전히 통과함을 확인했다.
- 잔여 위험: `INV-PIPE-001/002/004` 정의 미확인, `PolicyEngine`/`LocalRuleEngine`/
  `TextNormalizer`의 실제 내용 부재, 실 OpenAI 계약 미검증 — 모두 이 이슈의
  명시적 제외 범위이며 후속 이슈(#106, #107, #108)로 인계한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-105-TEST-PLAN-GH-105-MODERATION-PIPELINE.md`
- CI run: 아직 없음 (PR 생성 전, 로컬 실행만 수행)
- Related ADR: `dnd_production_planning/filtering_system/DESIGN.md` 결정 6~10
  (저장소 외부 기획 문서, 참고용)
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (INT-006)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 (§6 항목들을 #106/#107/#108에
      수동으로 연결 필요 — 이 세션에서는 GitHub Issue 코멘트를 추가하지 않았다)
- [ ] 실행 결과와 PR 설명이 일치함 (PR 생성 시 확인)
