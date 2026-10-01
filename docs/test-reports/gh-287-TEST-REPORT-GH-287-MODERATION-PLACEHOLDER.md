# Test Report: TEST-PLAN-GH-287-MODERATION-PLACEHOLDER

> Created at: `2026-10-01T17:55:00+09:00`
> GitHub Issue: `#287`
> Branch: `feat/gh-287-moderation-placeholder-replacement`
> Commit: `미커밋 작업 트리 (base: origin/main e4ccc7a)`

## 1. Executive summary

- Result: `PARTIAL`
- Tested scope: `UnicodeTextNormalizer`, `ResourceLocalRuleEngine`, 두 config의 구현체 조립, 실제 구현체를 쓰는 `ModerationPipelineService` 경로. 단위 시나리오 UNIT-001~016이 통과했다.
- Unverified scope:
  - INT-001(`ModerationPipelineIntegrationTest`): Docker가 없어 실행하지 못했다(BLOCKED).
  - `./harness test-run`, `./harness check`, `./harness pr-ready --project-tests`: 이 PC의 환경 문제로 전체 `./gradlew test`가 29건 실패해 완료하지 못했다(아래 5절).
  - 보조 판정기 실제 구현: 공급자 미정으로 이번 범위에 없다. `UnavailableSecondaryModerationClient` 교체 완료 조건은 BLOCKED다.
- Release recommendation: 위 미실행 항목을 CI 또는 Docker·Python이 갖춰진 환경에서 확인한 뒤 병합한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Microsoft OpenJDK 21.0.12(Gradle 실행용으로 `JAVA_HOME` 지정) |
| Spring Boot | 3.5.16 |
| Database | 사용하지 않음(단위 테스트). 통합 테스트는 Testcontainers가 필요하나 Docker가 없었다 |
| Test runner | JUnit 5, Gradle 8.14.3, Windows 11 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests '*filtering*'` | PASS | filtering 패키지 전체 통과 | 11s | `build/test-results/test` |
| 신규·변경 클래스 | PASS | UnicodeTextNormalizerTest 8, ResourceLocalRuleEngineTest 9, NicknameModerationGateConfigTest 4, AnswerModerationExecutionConfigTest 6, MinimalModerationComponentsTest 4, ModerationPipelineServiceTest 13, NicknameSyncModerationGateTest 11 | - | 같은 경로 |
| `./gradlew test`(전체) | FAIL | 1097 중 29 실패 | 41s | 5절. 변경과 무관한 환경 실패 |
| `./gradlew integrationTest --tests '*ModerationPipelineIntegrationTest'` | BLOCKED | 0 실행 | 6s | Docker 클라이언트를 찾지 못해 initializationError |
| `./harness test-run` | FAIL | - | - | 전체 `test` 실패로 중단, 보고서 자동 생성 안 됨 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~007 | PASS | `UnicodeTextNormalizerTest` | NFKC, 숨은 문자·공백, null·빈 입력·미지원 ref 예외, NFD 한글, 멱등성 |
| UNIT-008~013 | PASS | `ResourceLocalRuleEngineTest` | 적중, 우회 변형, 깨진·없는 리소스 로드 실패, 미지원 ref, 로그 비노출, 빈 기본 규칙 |
| UNIT-014 | PASS | `NicknameModerationGateConfigTest` | 게이트의 주 pipeline이 새 구현체로 조립됨(리플렉션으로 필드 타입 확인) |
| UNIT-015 | PASS | `AnswerModerationExecutionConfigTest` | 답변 pipeline이 새 구현체로 조립됨 |
| UNIT-016 | PASS | `ModerationPipelineServiceTest` | 우회 표기 규칙 적중 시 공급자 미호출 BLOCK, 정규화 텍스트가 공급자에 전달됨 |
| UNIT-017 | PASS(기존 테스트) | `NicknameSyncModerationGateTest.bothUnavailableFailsClosedWithoutThrowing` | 신규 테스트 없이 기존 테스트가 같은 계약을 검증한다 |
| UNIT-018 | PASS | `MinimalModerationComponentsTest` | 삭제된 placeholder를 단언하던 UNIT-005·006을 제거하고 신규 테스트로 대체했다 |
| INT-001 | NOT_RUN | `ModerationPipelineIntegrationTest` | Docker 없음 |

## 5. Failures and diagnostics

전체 `./gradlew test` 29건 실패는 이 PC 환경 때문이며, 변경 파일과 관련이 없다.

- Python 스텁: `python3`가 Microsoft Store 스텁이라 `RepoMapToolTest`, `JavaConventionBaselineTest`의 외부 스크립트 호출이 종료 코드 9009로 실패한다.
- Windows 실행 형식: 테스트가 `./gradlew`를 직접 실행하는 `JavaStaticAnalysisRuleTest`, `JavaConventionBaselineTest`가 CreateProcess 193 오류로 실패한다.
- 줄바꿈: `core.autocrlf=true`로 CRLF 체크아웃이어서 파일 SHA-256을 비교하는 `FlywayMigrationContractTest`와 소스 문자열을 비교하는 `*PersistenceBoundaryTest` 계열이 실패한다.
- 위 실패를 `origin/main` 기준으로 다시 실행해 비교하지는 않았다. 원인 메시지가 환경 문제를 가리키는 것으로 판단했다.

## 6. Potential issues

### Application code

- 지원 ref는 `normalization-v1`, `local-rules-v1` 두 개다. 운영에서 `filter_release`를 만들 때 이 이름을 쓰지 않으면 해당 release의 모든 moderation이 `INVALID_TEXT`로 실패한다. 현재 저장소에는 seed된 release가 없다(확인함). release 생성 절차에 ref 이름 안내가 필요하다.
- 운영 규칙 집합(`local-rules-v1.rules`)은 비어 있다. 따라서 운영 판정은 이전과 동일하게 OpenAI 호출과 `FlaggedCategoryPolicyEngine`에 전적으로 의존한다.
- 정규화 실패(null·빈 입력)가 닉네임 게이트에서는 "주 판정기 오류"로 취급되어 보조 판정기로 넘어간다. 현재 보조 판정기는 항상 예외라 `REJECTED(UNAVAILABLE)`이다. 보조 판정기 실제 구현이 들어오면 빈 닉네임이 보조 판정으로 허용될 수 있는지 재검토해야 한다.
- 규칙 접기는 NFKC·소문자·글자숫자 외 제거까지만 한다. 시각적 동형 문자(키릴 문자 등) 매핑은 하지 않는다.

### Infrastructure and resource limits

- 규칙 리소스는 기동 시 한 번 읽어 불변으로 둔다. 규칙이 커지면 매칭이 선형 탐색이라 입력 길이와 규칙 수에 비례한다. 현재 규칙 수가 0이라 영향이 없다.

### Database and migrations

- 스키마·마이그레이션 변경이 없다.

### Concurrency and idempotency

- 정규화기와 규칙 엔진은 불변이라 동시 호출에 안전하다고 판단했다. 동시성 전용 테스트는 추가하지 않았고 `NicknameSyncModerationGateConcurrencyTest`는 통과했다.

### Transactions and event ordering

- 해당 없음. 트랜잭션 경계를 바꾸지 않았다.

### External APIs

- OpenAI 호출 코드는 변경하지 않았다. 실제 OpenAI 호출은 이번에도 검증하지 않았다.
- 한국어 입력에서 OpenAI Moderation의 정확도는 측정하지 않았다.

### Failure recovery and reconciliation

- 규칙 리소스가 깨지면 기동 시점에 실패한다(UNIT-010). `production.enabled=true` 배포 전에 기동 확인이 필요하다.

## 7. Regression and residual risk

- `PassthroughTextNormalizer`, `NoMatchLocalRuleEngine`을 삭제했고 프로덕션과 테스트에서 참조가 없다(`grep` 확인). `UnavailableSecondaryModerationClient`는 `NicknameModerationGateConfig`가 아직 사용한다.
- 미실행 항목(INT-001, 전체 `harness` 검증)은 Docker와 Python이 있는 환경에서 실행해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-287-TEST-PLAN-GH-287-MODERATION-PLACEHOLDER.md`
- CI run: 없음
- Related ADR: 없음
- PR: 없음

## 9. Reviewer checklist

- [ ] 보고서에 `.env` 값이나 비밀정보가 없음
- [ ] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
