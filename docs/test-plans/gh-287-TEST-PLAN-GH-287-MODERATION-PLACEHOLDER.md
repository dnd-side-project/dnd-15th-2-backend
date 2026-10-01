# Test Plan: TEST-PLAN-GH-287-MODERATION-PLACEHOLDER

> Created at: `2026-10-01T14:53:01+09:00`
> GitHub Issue: `#287`
> Status: Approved

## 1. Objective

닉네임·답변 moderation의 `PassthroughTextNormalizer`와 `NoMatchLocalRuleEngine`을 실제 구현으로 교체했을 때
다음이 보장되는지 검증한다.

- 정규화·로컬 규칙 단계가 어떤 입력에서도 ALLOW로 새지 않는다(null·빈 입력·알 수 없는 ref는 예외).
- 규칙 원문·사용자 입력 원문·API 키가 로그와 예외 메시지에 남지 않는다.
- 두 config(`NicknameModerationGateConfig`, `AnswerModerationExecutionConfig`)가 새 구현체를 조립하고
  `production.enabled=true`에서 기동한다.

실패하면 우회 표기(zero-width 문자 삽입 등)가 정규화에서 걸러지지 않거나, 잘못된 ref 하나로 모든 moderation이
기본 허용되거나 전체 실패한다.

## 2. Scope

### Included

- 정규화 `v1` 구현체(`TextNormalizer`)와 로컬 규칙 엔진(`LocalRuleEngine`)의 단위 테스트
- 두 config의 구현체 교체 검증(`ApplicationContextRunner`)
- `ModerationPipelineService`와 새 구현체를 함께 쓰는 단락(BLOCK)·공급자 위임 경로
- 기존 `MinimalModerationComponentsTest`의 UNIT-005·UNIT-006(placeholder 동작을 단언)의 처리
- 보조 판정기: 공급자 중립 계약(timeout/error 시 `SECONDARY_MODERATOR_UNAVAILABLE`) 검증

### Excluded

- 실제 OpenAI 호출(외부 네트워크). `ModerationProviderClient`는 테스트 더블로 대체한다.
- 보조 판정기 실제 공급자 구현과 그 연동 테스트(공급자 미결정, UNKNOWN)
- 운영 금칙어 목록(빈 목록으로 시작, `TASK.md` 설계 결정)
- `OpenAiModerationProviderClient`, `FlaggedCategoryPolicyEngine` 변경
- 스케줄러·Slack·metric exporter

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #287 | `TextNormalizer`·`LocalRuleEngine` 구현체의 입력→출력 케이스를 단위 테스트로 검증하고 빈 입력·null·비정상 유니코드에서 ALLOW로 새지 않는다 |
| GitHub Issue #287 | API 키와 사용자 콘텐츠 원문이 로그·예외 메시지·metric tag에 나타나지 않는다 |
| GitHub Issue #287 | 세 placeholder가 프로덕션 조립에서 참조되지 않는다(`grep`) |
| GitHub Issue #287 | `NicknameModerationGateConfigTest`, `AnswerModerationExecutionConfigTest`, `ModerationPipelineIntegrationTest` 통과 |
| `TASK.md` 설계 결정 | 정규화 v1: NFKC, zero-width·제어 문자 제거, 공백 축소, trim. null·빈 결과·알 수 없는 ref는 `FilteringException` |
| `TASK.md` 설계 결정 | 로컬 규칙은 `localRulesetRef`로 classpath 리소스를 선택하는 엔진만, 운영 목록은 빈 상태. 로그·metric에는 `ruleId`만 |
| `LocalRuleVerdict` | `blocked=true`이면 `ruleId`가 필수다 |
| `ModerationPipelineService` | 로컬 BLOCK은 공급자 호출 없이 단락된다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 정규화가 null·빈 입력을 그대로 통과시켜 공급자에 빈 문자열이 전달되고 ALLOW가 된다 | 빈 닉네임·답변이 검사 없이 허용된다 | 중 | P0 | UNIT-003, UNIT-004 |
| 알 수 없는 `normalizationRef`·`localRulesetRef`를 조용히 무시한다(기본 허용) | 운영자의 ref 오타로 전 구간 필터가 꺼진다 | 중 | P0 | UNIT-005, UNIT-011 |
| 알 수 없는 ref를 예외로 처리하면 release 생성 시 오타 하나가 닉네임·답변 전체 장애가 된다 | 가용성 저하 | 중 | P1 | UNIT-005, 가정 A-1 |
| 정규화가 공급자 입력을 과도하게 변형한다(구분자·동형 문자 제거) | OpenAI 판정 정확도 저하 | 낮 | P1 | UNIT-002 |
| 로컬 규칙 매칭 로그에 규칙 원문·입력 원문이 남는다 | `INV-CMP-001`, `INV-CMP-002` 위반 | 낮 | P0 | UNIT-012 |
| 로컬 규칙 로드 실패(리소스 누락·형식 오류)에서 규칙 없음으로 기동한다 | 필터 비활성 상태로 운영 | 중 | P0 | UNIT-010 |
| 두 config가 여전히 placeholder를 조립한다 | #287 완료 조건 미충족 | 중 | P0 | UNIT-013, UNIT-014 |
| 보조 판정기 timeout/error가 ALLOW로 바뀐다 | 닉네임 fail-closed 위반(`INV-NICK-003`) | 낮 | P0 | UNIT-015 |
| 기존 UNIT-005·006이 삭제된 placeholder를 계속 참조해 컴파일 실패 | 빌드 실패 | 높 | P0 | UNIT-016 |

## 5. Unit scenarios

규칙 ID와 리소스 이름은 구현 시 확정한다. 아래 `ref`는 테스트 내부 상수다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-001 | 지원되는 정규화 ref, 전각 문자("ＡＢＣ")와 호환 문자가 섞인 입력 | `normalize` 호출 | NFKC로 정규화된 문자열을 반환한다 | P0 | Executor-A |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-002 | zero-width 문자(U+200B 등)·제어 문자·연속 공백이 섞인 입력, 내부 구분자("a.b")·동형 문자 | `normalize` 호출 | zero-width·제어 문자는 제거되고 연속 공백은 하나로 줄며 앞뒤가 trim된다. 구분자와 동형 문자는 변경되지 않는다 | P0 | Executor-A |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-003 | 입력 null | `normalize` 호출 | `FilteringException(REQUIRED_VALUE_MISSING)`을 던진다 | P0 | Executor-A |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-004 | 공백·zero-width 문자만 있는 입력 | `normalize` 호출 | 정규화 결과가 비어 `FilteringException(REQUIRED_VALUE_MISSING)`을 던진다 | P0 | Executor-A |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-005 | 지원되지 않는 `normalizationRef`(null 포함) | `normalize` 호출 | `FilteringException`을 던지고 메시지에 입력 원문이 없다 | P0 | Executor-A |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-006 | 한글 자모 분리 입력(NFD) | `normalize` 호출 | 조합형(NFC) 한글로 정규화된다 | P1 | Executor-A |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-007 | 정규화가 이미 끝난 입력 | `normalize`를 두 번 호출 | 결과가 동일하다(멱등) | P1 | Executor-A |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-008 | 테스트 리소스의 규칙 집합(가짜 규칙, `ruleId=R-TEST-1`) | 규칙에 걸리는 입력으로 `evaluate` | `blocked=true`, `ruleId=R-TEST-1`을 반환한다 | P0 | Executor-B |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-009 | 같은 규칙 집합 | 구분자·대소문자·동형 문자로 변형한 우회 입력으로 `evaluate` | 비교용 접기로 같은 규칙에 매칭된다 | P0 | Executor-B |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-010 | 형식이 깨진 규칙 리소스 또는 존재하지 않는 리소스 | 엔진 생성(로드) | 기동 시점에 예외로 실패하고 빈 규칙으로 대체하지 않는다 | P0 | Executor-B |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-011 | 지원되지 않는 `localRulesetRef`(null 포함) | `evaluate` 호출 | `FilteringException`을 던진다 | P0 | Executor-B |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-012 | 로그 appender를 붙인 엔진과 매칭되는 입력 | `evaluate` 호출 | 로그와 예외 메시지에 규칙 원문·입력 원문이 없고 `ruleId`만 있다 | P0 | Executor-B |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-013 | 운영 기본 규칙 집합(빈 목록) | 임의 입력으로 `evaluate` | `noMatch`를 반환한다(공급자 판정에 위임) | P0 | Executor-B |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-014 | `ApplicationContextRunner`, `production.enabled=true`, 키 설정 | `NicknameModerationGateConfig` 기동 | 컨텍스트가 기동하고 게이트가 새 정규화·규칙 구현체로 조립된다(placeholder 타입이 아니다) | P0 | Executor-C |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-015 | 같은 러너로 `AnswerModerationExecutionConfig` | 기동 | `answerModerationPipelineService`가 새 구현체로 조립된다. 키가 비어 있으면 기동이 실패한다 | P0 | Executor-C |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-016 | 테스트 더블 공급자, 새 정규화·규칙 구현체, 테스트 규칙 집합 | `ModerationPipelineService.execute` | 규칙 매칭 시 공급자 호출 없이 BLOCK, 매칭 없으면 정규화된 텍스트가 공급자에 전달된다 | P0 | Executor-C |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-017 | 보조 판정기가 timeout/error를 내는 더블 | 주 판정기 실패 후 `NicknameSyncModerationGate` 실행 | `REJECTED(UNAVAILABLE)`이며 ALLOW·BLOCK으로 바뀌지 않는다 | P0 | Executor-C |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-UNIT-018 | 기존 `MinimalModerationComponentsTest`의 UNIT-005·UNIT-006 | 구현 교체 후 | 삭제된 placeholder 대신 새 구현체를 단언하도록 갱신하거나 새 테스트로 대체된다 | P0 | Executor-A |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-287-MODERATION-PLACEHOLDER-INT-001 | 기존 `ModerationPipelineIntegrationTest`(`src/integrationTest`) | 새 구현체 조립, Testcontainers DB, 공급자 더블 | 기존 시나리오 재실행 | 모두 통과하고 `filter_decision` 저장 동작이 변하지 않는다 | 기존 테스트 정리 방식을 따른다 |

신규 통합 테스트는 만들지 않는다. 기존 테스트가 새 구현체로도 통과하는지만 확인한다.

## 7. Cross-cutting scenarios

### Database and transactions

- 스키마·마이그레이션 변경이 없다. `filter_decision` 저장은 기존 pipeline 경로를 따르며 INT-001로 회귀만 확인한다.

### Concurrency and idempotency

- 정규화기와 규칙 엔진은 불변 상태만 갖는다. 규칙 집합은 기동 시 한 번 로드하고 이후 읽기 전용이다.
  동시 호출 테스트는 `NicknameSyncModerationGateConcurrencyTest`가 기존에 다룬다. 새 구현체로 그 테스트가 통과하는지 확인한다.
- UNIT-007로 정규화 멱등성을 확인한다.

### External APIs

- OpenAI 호출은 `ModerationProviderClient` 테스트 더블로 대체한다. 이번 변경은 호출 로직을 건드리지 않는다.
- 보조 판정기는 공급자가 정해지지 않아 계약 수준(UNIT-017)까지만 검증한다.

### Failure recovery and reconciliation

- 규칙 리소스 로드 실패는 기동 실패로 드러난다(UNIT-010). 규칙 없음으로 조용히 대체하지 않는다.
- 알 수 없는 ref는 요청 시점에 `FilteringException`이다. 이 예외가 닉네임·답변 경로에서 어떻게 처리되는지
  (닉네임은 `REJECTED(UNAVAILABLE)`, 답변은 retry) 구현 전에 코드 경로를 다시 확인한다(가정 A-2).

## 8. Test data and isolation

- Fixtures: 테스트 전용 규칙 리소스(`src/test/resources`)에 가짜 규칙만 둔다. 실제 금칙어를 쓰지 않는다.
- Database isolation: 단위 테스트는 DB를 쓰지 않는다. INT-001은 기존 Testcontainers 설정을 따른다.
- Clock/randomness: 고정 `Clock`을 쓴다. 정규화·규칙에는 시간·난수 의존이 없다.
- External API doubles: `ModerationProviderClient`, `SecondaryModerationClient`는 수동 더블이다.
- Cleanup: 상태를 공유하지 않는다. 로그 appender는 테스트 종료 시 분리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다. 테스트 키는 `example-key-for-unit-test` 같은 값만 쓴다.

가정(승인 시 확인 필요):

- A-1: 지원하는 ref 목록과 이름(예: 정규화 `v1`용 ref, 빈 규칙 집합용 ref)은 구현 단계에서 상수로 정한다.
  운영 release가 이 ref를 사용하도록 release 생성 쪽 확인이 필요하다. 현재 저장소에는 seed된 release가 없다(확인함).
- A-2: 알 수 없는 ref의 예외가 닉네임·답변 경로에서 fail-closed로 끝난다는 점은 구현 전에 코드로 재확인한다.
- A-3: 기존 config 테스트가 쓰는 ref("norm-v1", "ruleset-v1")는 빈 조립만 검증해 영향이 없다고 본다. 구현 후 확인한다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor-A (정규화) | `src/main/java/com/dnd/qello/filtering/moderation/` 정규화 구현체, `src/test/java/com/dnd/qello/filtering/moderation/UnicodeTextNormalizerTest.java`, `MinimalModerationComponentsTest.java` | UNIT-001~007, UNIT-018 | `./gradlew test --tests '*UnicodeTextNormalizerTest' --tests '*MinimalModerationComponentsTest'` |
| 2 | Executor-B (로컬 규칙) | 로컬 규칙 엔진 구현체, `src/main/resources/filtering/rulesets/**`, `src/test/resources/filtering/**`, `src/test/java/com/dnd/qello/filtering/moderation/ResourceLocalRuleEngineTest.java` | UNIT-008~013 | `./gradlew test --tests '*ResourceLocalRuleEngineTest'` |
| 3 | Executor-C (조립·pipeline) | `NicknameModerationGateConfig.java`, `AnswerModerationExecutionConfig.java`, 두 config 테스트, `ModerationPipelineServiceTest.java`, `NicknameSyncModerationGateTest.java` | UNIT-014~017, INT-001 | `./gradlew test --tests '*ModerationGateConfigTest' --tests '*AnswerModerationExecutionConfigTest' --tests '*ModerationPipelineServiceTest'`, `./gradlew integrationTest --tests '*ModerationPipelineIntegrationTest'` |
| 4 | 검증(독립) | 없음(읽기 전용) | 전체 | `./gradlew check`, `./harness check`, `./harness pr-ready --project-tests`, `git diff --check`, placeholder 참조 `grep` |

실행 순서는 A, B, C 순이다. C는 A·B의 구현체가 있어야 조립할 수 있다. 세 실행자가 같은 파일을 수정하지 않는다.
보조 판정기 실제 공급자 구현은 이 계획에 포함하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성
- [ ] `PassthroughTextNormalizer`, `NoMatchLocalRuleEngine`이 프로덕션 조립에서 참조되지 않음(`grep`)

실패 판단: 위 명령 중 하나라도 실패하거나, P0 시나리오가 미구현이거나, 로그에서 원문·키가 발견되면 FAIL이다.
보조 판정기 실제 구현이 없어 `UnavailableSecondaryModerationClient` 교체 항목은 BLOCKED로 보고한다.

## 11. Human approval

- Reviewer: tkv00 (세션 채팅에서 "승인"이라고 직접 답변)
- Decision: Approved (가정 A-1~A-3은 구현 단계에서 확인)
- Approved at: 2026-10-01
