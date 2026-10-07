# Test Plan: TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400

> Created at: `2026-10-07T10:54:18+09:00`
> GitHub Issue: `#318`
> Status: Approved

## 1. Objective

production moderation 게이트가 켜진 환경에서 정규화 결과가 빈 문자열인 닉네임(U+200B, U+FEFF, 변경 경로의 U+3000)이
503 `ACC-INFRA-001`이 아니라 400 `ACC-VAL-002`로 거절되는지 검증한다. 그와 함께 판정할 수 없는 경우는 지금처럼
fail-closed 503으로 남는지도 확인한다.

실패하면 두 가지 문제가 생긴다. 사용자는 입력 오류인데도 "검증 서비스 장애"를 보고 같은 값을 반복해서 재시도한다.
운영에서는 입력 오류가 공급자 장애로 집계되어 보조 판정기 전환과 503 경보가 오염된다. 반대로 분류를 너무 넓게 잡으면
서버 설정 오류나 공급자 장애가 400으로 숨겨진다. 그러면 판정 불가를 사용자 잘못으로 돌리게 되고 장애도 감지되지 않는다.

## 2. Scope

### Included

- `UnicodeTextNormalizer`: 정규화 후 빈 입력만 전용 하위 예외로 던지는지 확인한다. 오류 코드·field는 그대로이고 원문은 노출하지 않는다.
  null 입력과 지원하지 않는 ref는 전용 예외가 아니어야 한다.
- `ModerationPipelineRequest`(D4): 공백만 있는 원문은 같은 전용 예외로, null 원문은 일반 예외로 거절하는지 확인한다.
  U+3000처럼 `isBlank()`가 true인 입력은 정규화기보다 먼저 이 생성자에서 거절된다.
- `NicknameSyncModerationGate`: 전용 예외만 `Reason.INVALID_INPUT`으로 분류하고 보조 판정기를 부르지 않는지 확인한다.
  같은 오류 코드라도 일반 `FilteringException`이면, 지원하지 않는 ref·예상하지 못한 예외와 마찬가지로 기존 보조 판정기 경로를 탄다.
- `NicknameRegistrationService.rejectionFor`: `INVALID_INPUT`이 `ACC-VAL-002`(400, field `nickname`)로 매핑되고 저장하지 않는지 확인한다.
- 실제 정규화기·파이프라인·게이트·`GatedNicknameModerationChecker`·서비스를 조합해, 변경 경로와 등록 경로 진입점
  (`ensureAvailable`)에서 400이 나오고 공급자·보조 판정기가 호출되지 않는지 확인한다.
- 기존 게이트·정규화기·답변 moderation 테스트 회귀

### Excluded

- 답변 moderation 경로(`AnswerModerationExecutionWorker`)의 예외 처리 변경. Issue 제외 범위이며 기존 테스트로 회귀만 확인한다.
- 보조 판정기 실제 구현(#298)
- #317 범위(닉네임 저장·중복 검사 정규화)와 그 테스트 파일(`NicknameRegistrationServiceTest`, `DeviceRegistrationServiceTest`)
- production gate를 켠 `@SpringBootTest` 통합 테스트(D1 참고)
- 실제 OpenAI 호출. 공급자는 test double로 대체한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #318 | 게이트가 켜진 상태에서 U+200B 닉네임으로 변경·등록하면 400이고 보조 판정기는 호출되지 않는다 |
| GitHub Issue #318 | 공급자 timeout/error, 예상하지 못한 예외, 지원하지 않는 `normalizationRef`는 계속 503이다 |
| GitHub Issue #318 | 기존 `IllegalStateException` → 보조 판정기 전환 테스트(`NicknameSyncModerationGateTest`)를 그대로 둔다 |
| TASK.md Explicit exclusions | 오류 코드 값만으로 입력 오류를 판별하지 않는다 |
| 기존 동작 | 게이트는 `Future.get()`으로 주 판정기를 기다리므로 파이프라인 예외는 `ExecutionException`의 cause로 들어온다 |
| 기존 동작 | 정규화는 공급자 호출보다 먼저 실행된다(`ModerationPipelineService.execute`). 빈 입력이면 OpenAI는 원래 호출되지 않는다 |
| 기존 동작 | 등록 경로는 `nickname.isBlank()`가 false일 때 `ensureAvailable`을 부른다. U+200B·U+FEFF는 이 검사를 통과한다 |
| 기존 동작 | `REQUIRED_VALUE_MISSING`은 `FilterDecision`·`AppealCase` 등 파이프라인 밖의 서버 측 검증에서도 쓰인다 |
| 기존 동작(구현 중 발견) | `ModerationPipelineRequest` 생성자는 `rawContent.isBlank()`를 일반 `FilteringException`으로 거절한다. 이 생성은 게이트의 executor 작업 안에서 일어나므로, U+3000은 정규화기에 닿기 전에 503 경로로 빠진다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 정규화 후 빈 입력이 여전히 503으로 응답 | 중간 | 높음(현재 동작) | P0 | 조합 테스트에서 400 `ACC-VAL-002` (UNIT-009) |
| 오류 코드로 판별해 서버 쪽 `REQUIRED_VALUE_MISSING`이 400으로 숨겨짐 | 높음 | 중간 | P0 | 같은 코드의 일반 `FilteringException`은 보조 판정기 경로로 간다 (UNIT-004) |
| 지원하지 않는 `normalizationRef`(서버 설정 오류)가 400이 됨 | 높음 | 중간 | P0 | 실제 정규화기 + 잘못된 ref면 UNAVAILABLE (UNIT-005) |
| 게이트가 `ExecutionException`을 풀지 않아 분류가 동작하지 않음 | 중간 | 중간 | P0 | 실제 executor로 감싼 경로에서 INVALID_INPUT (UNIT-003) |
| 입력 오류인데 보조 판정기를 호출함 | 중간 | 중간 | P0 | 보조 판정기 호출 수 0 (UNIT-003, UNIT-009) |
| `isBlank()` 입력(U+3000)이 정규화기 전 요청 검증에서 일반 예외가 되어 503 | 중간 | 높음(구현 중 재현) | P0 | 요청 생성 시 전용 예외 (UNIT-010), 게이트·조합 경로에서 U+3000이 INVALID_INPUT·400 (UNIT-003, UNIT-009) |
| 기존 fail-closed 경로(공급자 장애, 예상하지 못한 예외) 회귀 | 높음 | 낮음 | P0 | 기존 게이트·동시성 테스트 무수정 통과 (UNIT-006) |
| null 입력(호출자 버그)이 400으로 분류됨 | 중간 | 낮음 | P1 | null은 전용 예외가 아니다 (UNIT-002) |
| 예외 메시지에 닉네임 원문 노출 | 중간 | 낮음 | P1 | 메시지에 원문 없음 (UNIT-001) |
| 답변 경로의 `catch (FilteringException)` 동작 변경 | 중간 | 낮음 | P1 | 하위 타입이라 기존 catch에 걸린다. 기존 답변 테스트 통과로 회귀를 확인한다 (UNIT-006) |
| #317과 같은 파일·같은 hunk를 고쳐 rebase 충돌 | 낮음 | 중간 | P2 | 테스트를 새 클래스에 두고, `NicknameRegistrationService`는 `rejectionFor`만 수정한다 |

## 5. Unit scenarios

구현 전제: 전용 예외는 `filtering.error.EmptyNormalizedTextException`(`FilteringException` 하위, 코드
`REQUIRED_VALUE_MISSING`, field `rawContent`)이다. 게이트는 주 판정기 예외의 cause가 이 타입일 때만
`Rejected(INVALID_INPUT)`을 반환한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-001 | 실제 `UnicodeTextNormalizer`, ref `normalization-v1` | `""`, `"   "`, U+200B, U+FEFF, U+3000, U+200B+U+200C+U+200D를 각각 정규화 | `EmptyNormalizedTextException`이 나온다. `FilteringException`이기도 하고 코드는 `REQUIRED_VALUE_MISSING`이며 메시지에 원문이 없다 | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-002 | 실제 정규화기 | null 입력과 지원하지 않는 ref(`norm-v1`, null)로 정규화 | `FilteringException`(각각 `REQUIRED_VALUE_MISSING`, `INVALID_TEXT`)이 나오고 `EmptyNormalizedTextException`은 아니다 | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-003 | 실제 정규화기·`ModerationPipelineService`·실제 executor. release ref는 `normalization-v1`. 호출 수를 세는 공급자·보조 판정기 double | `gate.evaluate`에 U+200B, U+FEFF, U+3000을 각각 입력 | `Rejected(INVALID_INPUT)`. 공급자·보조 판정기 호출 0회 | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-004 | 공급자가 일반 `FilteringException(REQUIRED_VALUE_MISSING)`을 던지고 보조 판정기는 실패 | `gate.evaluate("닉네임후보")` | 보조 판정기 1회 호출 후 `Rejected(UNAVAILABLE)`. 같은 코드라도 INVALID_INPUT이 아니다 | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-005 | 실제 정규화기 + release ref `norm-v1`(지원하지 않음), 실패하는 보조 판정기 | `gate.evaluate("닉네임후보")` | 보조 판정기 1회 호출 후 `Rejected(UNAVAILABLE)` | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-006 | 기존 `NicknameSyncModerationGateTest`·`NicknameSyncModerationGateConcurrencyTest`·`UnicodeTextNormalizerTest`·`ModerationPipelineServiceTest`·답변 moderation 테스트 | 수정 없이 `./gradlew test` | 모두 통과. `IllegalStateException` → 보조 판정기 전환 테스트는 무수정 | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-007 | `Rejected(INVALID_INPUT)`을 반환하는 checker double, Mockito `AccountRepository`(변경 가능 계정) | `changeNickname(1, "x")`와 `ensureAvailable("x", "ko-KR")` | `AccountException(REQUIRED_VALUE_MISSING)`. field는 `nickname`, `httpStatus`는 400. `updateProfile` 미호출 | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-008 | 각 `Reason` 값을 반환하는 checker double | `ensureAvailable` | BLOCKED_BY_PRIMARY·SECONDARY → `ACC-DOM-005`, UNAVAILABLE → `ACC-INFRA-001`, INVALID_INPUT → `ACC-VAL-002`. 모든 enum 값이 매핑된다 | P1 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-009 | 실제 정규화기·파이프라인·게이트·`GatedNicknameModerationChecker`·`NicknameRegistrationService` 조합, 호출 수를 세는 공급자·보조 판정기 double, Mockito `AccountRepository` | 변경 경로 `changeNickname`에 U+200B·U+3000, 등록 경로 진입점 `ensureAvailable`에 U+200B 입력 | 모두 `ACC-VAL-002`(400). 공급자·보조 판정기 호출 0회, `updateProfile` 미호출 | P0 | Unit executor |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-UNIT-010 | 실제 release(`normalization-v1`) | `""`, `"   "`, U+3000, 공백·탭·개행+U+3000, null 원문으로 `ModerationPipelineRequest` 생성 | blank는 `EmptyNormalizedTextException`(코드 `REQUIRED_VALUE_MISSING`, field `rawContent`), null은 전용 예외가 아닌 `FilteringException` | P0 | Unit executor |

## 6. Integration scenarios

새 통합 시나리오는 두지 않는다(D1). 기존 통합 테스트 전체를 회귀로 돌린다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400-INT-001 | 기존 통합 테스트 전체(PostgreSQL Testcontainers) | 변경 없음 | `./harness pr-ready --project-tests` | 통과. 닉네임 중복·moderation·변경 한도 통합 테스트(NoOp/mock checker) 동작이 바뀌지 않는다 | 기존 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- 변경 경로: INVALID_INPUT은 저장 트랜잭션(`writeTransaction`) 전에 거절되므로 쓰기가 없다(UNIT-007, UNIT-009의 `updateProfile` 미호출).
- 등록 경로: `ensureAvailable`은 등록 트랜잭션 안에서 실행된다. 예외가 계정 저장 전에 나므로 롤백 동작은 기존과 같다.
  새 테스트는 두지 않는다.
- 시도 한도: 시도 한도 검사가 moderation보다 먼저이므로 INVALID_INPUT 시도도 1회로 센다(#315 D2). 동작 변경이 없어 새 시나리오는 두지 않는다.

### Concurrency and idempotency

- 게이트의 executor·timeout 구조는 바꾸지 않는다. `NicknameSyncModerationGateConcurrencyTest`로 회귀만 확인한다(UNIT-006).
- UNIT-003은 실제 `ExecutorService`를 써서 `ExecutionException` 포장을 재현한다.

### External APIs

- 공급자(OpenAI)와 보조 판정기는 호출 수를 세는 double이다. 빈 입력에서 둘 다 0회인지 UNIT-003·UNIT-009로 확인한다.
- 공급자 장애(UNIT-004 변형, 기존 테스트)는 계속 보조 판정기로 넘어간다.

### Failure recovery and reconciliation

- fail-closed 불변식: 어떤 경로도 판정 불가를 ALLOWED로 바꾸지 않는다. INVALID_INPUT도 `Rejected`다(UNIT-003).
- 서버 설정 오류(잘못된 ref)와 예상하지 못한 예외는 503으로 남아 운영 경보에 잡힌다(UNIT-005, 기존 테스트).

## 8. Test data and isolation

- Fixtures: 닉네임 원문은 테스트 상수(U+200B, U+FEFF, U+3000, `닉네임후보`)만 쓴다. 계정은 `Account.restore`로 만든 변경 가능 계정
  (`nicknameChangedAt` null)이다.
- Database isolation: 단위 테스트만 추가하므로 DB를 쓰지 않는다. `AccountRepository`는 Mockito mock이다.
- Clock/randomness: `Clock.fixed`
- External API doubles: 공급자·보조 판정기·`FilterDecisionRepository`는 테스트 내부 fake다. 공급자 fake는 호출 수를 기록하고,
  `FilterDecisionRepository.save` 호출은 실패로 처리한다.
- Cleanup: 게이트 테스트의 executor는 `@AfterEach`에서 `shutdownNow()`

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Implementation executor | `src/main/java/com/dnd/qello/filtering/error/EmptyNormalizedTextException.java`(신규), `filtering/moderation/UnicodeTextNormalizer.java`, `filtering/moderation/ModerationPipelineRequest.java`(D4), `filtering/moderation/NicknameSyncModerationGate.java`, `filtering/moderation/NicknameModerationOutcome.java`, `account/service/NicknameRegistrationService.java`(`rejectionFor`와 클래스 주석만) | — | `./gradlew compileJava javaConventionCheck` |
| 2 | Unit executor | `src/test/java/com/dnd/qello/filtering/moderation/UnicodeTextNormalizerTest.java`(시나리오 추가), `src/test/java/com/dnd/qello/filtering/moderation/NicknameSyncModerationGateTest.java`(시나리오 추가, 기존 메서드 무수정), `src/test/java/com/dnd/qello/account/service/NicknameInvalidInputRejectionTest.java`(신규, UNIT-007~009), `src/test/java/com/dnd/qello/filtering/moderation/ModerationPipelineServiceTest.java`(UNIT-010 추가) | UNIT-001~010 | `./gradlew test` |
| 3 | Integration executor | `docs/reports/tests/gh-318-TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400.md` | INT-001, 전체 | `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |

- #317 세션이 `NicknameRegistrationServiceTest`, `DeviceRegistrationServiceTest`, `ensureAvailable` 본문을 소유한다. 이 계획은
  그 파일과 hunk를 수정하지 않는다.
- 기존 테스트 클래스에 시나리오를 추가하면 그 클래스 헤더의 `Source scenario`에 이 계획의 ID와 추가 시각을 덧붙인다
  (`NicknameModerationGateConfigTest`와 같은 형식).
- Gradle 명령은 JDK 21(`JAVA_HOME`)로 실행한다. `pr-ready`는 Docker가 필요하고 오래 걸리므로 백그라운드로 실행한다.

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
| D1 | "게이트가 켜진 상태"의 검증 수준 | production gate를 켠 `@SpringBootTest`는 만들지 않고, 실제 정규화기·파이프라인·게이트·checker·서비스를 조합한 단위 테스트(UNIT-009)로 대신한다. gate를 켜려면 답변 moderation 설정, 기동 시 PROMOTED release, API 키까지 갖춰야 하고 별도 컨텍스트가 하나 더 떠 CI 시간이 늘어난다. 오류 코드를 HTTP 400으로 바꾸는 부분은 기존 핸들러 테스트가 다룬다 |
| D2 | 등록 경로 검증 위치 | `DeviceRegistrationService` 대신 그 서비스가 부르는 `ensureAvailable`에서 검증한다. #317이 `DeviceRegistrationService`의 빈 값 판단과 그 테스트 파일을 고치므로 충돌을 피하기 위해서다 |
| D3 | null 입력 분류 | 정규화기의 null 입력은 호출자 버그로 보고 전용 예외로 만들지 않는다(503 유지). 요청 DTO의 `@NotBlank`가 먼저 막는다 |
| D4 | 요청 생성 시 blank 원문(구현 중 추가) | `ModerationPipelineRequest`가 null이 아닌 blank 원문을 `EmptyNormalizedTextException`으로 거절한다. null은 D3처럼 일반 예외로 남긴다 |

- Reviewer: 사용자(@tkv00)
- Decision: 승인. D1~D3 권장안 확정. 구현 중 발견한 D4도 권장안으로 승인(2026-10-07)
- Approved at: 2026-10-07
