# Test Report: TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400

> Created at: `2026-10-07T11:33:43+09:00`
> GitHub Issue: `#318`
> Branch: `fix/gh-318-nickname-empty-input-400`
> Commit: 검증은 커밋 전 작업 트리(base `origin/main` `aae33fa`)에서 실행했다. 그 내용이 `4a1c1ef`·`e9fb75b`·`31ca449`에 그대로 커밋됐다

## 1. Executive summary

- Result: `PASS`. 승인된 계획의 단위 시나리오 10개(구현 중 추가한 UNIT-010 포함)가 모두 통과했다.
  `./harness test-run`으로 실행한 전체 단위 1,190건(skipped 2)과 통합 784건도 실패 없이 통과했다.
- Tested scope:
  - 정규화 후 빈 입력과 blank 요청 원문은 입력 오류 전용 예외(`EmptyNormalizedTextException`)로 구분된다.
    null·지원하지 않는 ref는 전용 예외가 아니다.
  - 게이트는 전용 예외만 `INVALID_INPUT`으로 분류하고 공급자·보조 판정기를 부르지 않는다. 같은 오류 코드의 일반 예외와
    잘못된 ref는 계속 보조 판정기 → UNAVAILABLE(503) 경로를 탄다.
  - 서비스는 `INVALID_INPUT`을 `ACC-VAL-002`(400, field `nickname`)로 매핑하고 저장하지 않는다. 모든 `Reason` 값이 매핑된다.
  - 실제 정규화기·파이프라인·게이트·checker·서비스 조합: 변경 경로의 U+200B·U+3000과 등록 경로 진입점(`ensureAvailable`)의
    U+200B가 400이고 외부 호출은 0회다.
- Unverified scope:
  - production gate(`qello.filtering.production.enabled=true`)를 켠 Spring 컨텍스트와 HTTP 응답. 계획 D1에 따라 단위 조합
    테스트로 대신했다. 오류 코드를 HTTP 상태로 바꾸는 부분은 기존 `GlobalExceptionHandlerTest`가 다룬다.
  - `DeviceRegistrationService.register` 전체 경로. 계획 D2에 따라 그 서비스가 부르는 `ensureAvailable`에서 검증했다.
  - 실제 OpenAI 호출과 보조 판정기 실제 구현(#298)
- Release recommendation: 병합할 수 있다. macOS·Linux 결과는 PR CI의 `check`로 확인한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| OS | Windows 11 Enterprise, Git Bash, 저장소 내 별도 git worktree |
| Java | OpenJDK 21.0.12 (Microsoft build, Gradle toolchain) |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 (wrapper) |
| Database | Testcontainers `postgis/postgis:16-3.5-alpine`(통합 회귀만 사용) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test` (대상: `com.dnd.qello.filtering.*`, `com.dnd.qello.account.*`, `DeviceRegistrationServiceTest`) | PASS | 299, 실패 0 | 23s | `build/test-results/test` |
| `./harness test-run --id TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400` 단위(`./gradlew test`) | PASS | 1,190, skipped 2, 실패 0 | 16m 35s(단위·통합 합계) | `build/test-results/test` |
| `./harness test-run` 통합(`./gradlew integrationTest`) | PASS | 784, 실패 0 | 위와 같음 | `build/test-results/integrationTest` |
| `./gradlew compileJava javaConventionCheck` | 최초 FAIL → `spotlessApply` 후 PASS | — | — | 5절 |
| `./harness pr-ready --project-tests` | PASS | `./harness check` 전체와 Gradle `check`. `test`·`integrationTest`는 직전 `test-run`과 입력이 같아 UP-TO-DATE로 결과를 재사용했다 | 22s, 주석 수정 후 재실행 11s | "Local PR readiness checks passed." 주석 수정 후 `compileJava`는 재실행됐지만 테스트 태스크는 UP-TO-DATE였다(바이트코드 동일) |
| `npm run hooks:validate`, `git diff --check` | PASS | — | — | "Husky validation passed." |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `UnicodeTextNormalizerTest.throwsDedicatedExceptionWhenNormalizedContentIsEmpty` | `""`, 공백, U+200B, U+FEFF, U+3000, zero-width 조합. 메시지는 코드 기본 메시지라 원문이 없다 |
| UNIT-002 | PASS | `UnicodeTextNormalizerTest.nullContentAndUnsupportedRefAreNotInputErrors` | 비게 될 입력이라도 ref 오류가 먼저 판정된다 |
| UNIT-003 | PASS | `NicknameSyncModerationGateTest.emptyAfterNormalizationIsRejectedAsInvalidInputWithoutSecondary` | 실제 executor로 `ExecutionException` 포장을 거친다. 보조 판정기를 ALLOW로 두어 호출됐다면 결과가 뒤집히게 했다 |
| UNIT-004 | PASS | `NicknameSyncModerationGateTest.sameErrorCodeFromAnotherStageIsNotInvalidInput` | 같은 `REQUIRED_VALUE_MISSING`의 일반 예외는 UNAVAILABLE |
| UNIT-005 | PASS | `NicknameSyncModerationGateTest.unsupportedNormalizationRefStaysUnavailable` | 입력이 U+200B여도 잘못된 ref는 UNAVAILABLE, 공급자 0회 |
| UNIT-006 | PASS | 기존 게이트·동시성·정규화기·파이프라인·답변 moderation 테스트 | 기존 테스트 메서드는 수정하지 않았다. `IllegalStateException` → 보조 판정기 전환 테스트 유지 |
| UNIT-007 | PASS | `NicknameInvalidInputRejectionTest.invalidInputMapsToRequiredValueMissing` | 변경·등록 진입점 모두 `ACC-VAL-002`, `updateProfile` 미호출 |
| UNIT-008 | PASS | `NicknameInvalidInputRejectionTest.everyRejectionReasonMapsToAnErrorCode` | 기대 매핑의 키가 `Reason.values()`와 같은지도 확인한다 |
| UNIT-009 | PASS | `NicknameInvalidInputRejectionTest.productionGateCompositionRejectsInvisibleOnlyNicknameAsBadRequest` | D4 적용 전에는 U+3000에서 보조 판정기가 1회 호출되어 실패했다(5절) |
| UNIT-010 | PASS | `ModerationPipelineServiceTest.blankRawContentIsInputErrorButNullIsNot` | 구현 중 추가(D4) |
| INT-001 | PASS | 기존 통합 테스트 전체 | 784건 |

## 5. Failures and diagnostics

- 구현 중 재현한 결함(D4). 첫 구현은 정규화기·게이트·서비스만 고쳤다. 그 상태에서 UNIT-003은 U+3000이 `Allowed`로,
  UNIT-009는 U+3000에서 보조 판정기 호출 1회로 실패했다.
  - 원인: `ModerationPipelineRequest` 생성자가 `rawContent.isBlank()`를 일반 `FilteringException(REQUIRED_VALUE_MISSING)`으로
    거절한다. U+3000은 `isBlank()`가 true여서 정규화기에 닿기 전에 이 검사에 걸린다. 생성은 게이트의 executor 작업 안에서
    일어나므로 게이트는 이를 주 판정기 장애로 보고 보조 판정기로 넘어간다. 운영에서는 503이 된다.
  - 조치: 사용자 승인(D4)을 받아, 생성자가 null이 아닌 blank 원문을 `EmptyNormalizedTextException`으로 거절하게 했다.
    수정 후 두 시나리오가 통과했다.
  - U+200B·U+FEFF는 `isBlank()`가 false라서 정규화기에서 걸린다. 수정 전 코드에서 이 입력이 503이 되는 경로는 Issue 분석과
    코드 확인으로 판단했다. 수정 전 코드에서는 새 테스트가 컴파일되지 않아 실행으로 재현하지는 않았다.
- 커밋 중 테스트 환경 문제(구현 결함 아님). worktree에서 Java 파일을 staging하고 `git commit`을 실행하자,
  pre-commit의 `javaConventionStagedCheck`에서 `ChangedJavaTypesTest` 4건이 실패하고 커밋이 중단됐다.
  - 원인: 이 테스트는 `ProcessBuilder`로 `git init`·`add`·`commit`·`update-ref`를 호출하는데, 훅 환경의 `GIT_DIR`·
    `GIT_INDEX_FILE`을 그대로 물려받는다. 그래서 명령이 임시 디렉터리가 아니라 이 저장소를 대상으로 실행됐다.
  - 영향과 복구: 공용 git 설정에 `core.bare=true`가 써져 메인 checkout이 bare로 인식됐다(#317 세션이 false로 복구).
    `refs/remotes/origin/main`이 약 2초 동안 가짜 커밋을 가리켰다가 fetch로 원격 값에 돌아왔다. 이 브랜치에 생긴
    가짜 `seed` 커밋은 사용자 승인 후 `git reset --mixed`로 제거했다. 작업 트리 파일은 손상되지 않았다
    (`origin/main`과 비교해 확인). 로컬 `main`과 다른 브랜치는 바뀌지 않았다.
  - 재현 조건: linked worktree에서 Java 파일이 포함된 커밋을 훅을 켠 채로 실행. 같은 검사를 훅 밖에서
    (`scripts/run-hook.py pre-commit` 직접 실행) 돌리면 통과한다. 메인 checkout의 커밋에서는 재현되지 않았다(확정 아님).
  - 훅 우회: 사용자 승인에 따라 Java가 포함된 커밋 3개는 같은 pre-commit·commit-msg 검사를 훅 밖에서 직접 실행해
    통과를 확인한 뒤 `--no-verify`로 커밋했다. 우회한 훅은 `pre-commit`, `commit-msg`다. 수동 검증 명령은
    `python scripts/run-hook.py pre-commit`와 `python scripts/run-hook.py commit-msg <message-file>`이고 결과는 모두
    "checks passed"였다. 남은 위험: 테스트 격리 버그가 고쳐지기 전까지는 worktree에서 Java를 커밋할 때마다 같은
    손상이 생길 수 있다. 별도 Project draft로 추적한다.
- `javaConventionCheck`의 spotless 위반. 수정한 기존 파일(`NicknameSyncModerationGate`, `ModerationPipelineRequest` 등)은
  ratchet에 따라 파일 전체에 formatter가 적용된다. `spotlessApply`가 생성자 매개변수와 이어지는 줄의 들여쓰기를 바꿨다.
  동작에는 영향이 없다.

## 6. Potential issues

### Application code

- `ModerationPipelineRequest`는 답변 경로와 함께 쓴다. blank 답변 원문도 이제 `EmptyNormalizedTextException`이 된다.
  하위 타입이고 오류 코드·field가 같아서, 답변 경로의 `catch (FilteringException)` 동작과 기존 테스트 결과는 같다.
- `DomainException` 주석은 기능 패키지마다 예외 하나를 원칙으로 둔다. 이번 하위 타입은 오류 코드로는 구분할 수 없는 입력 오류를
  타입으로 구분하려는 예외다. 이유는 클래스 주석에 적었다.
- #317이 머지되면 `ensureAvailable`이 moderation 전에 정규화·빈 값 검사를 한다. 그러면 닉네임 경로에서 이 게이트 분기에 닿는
  입력은 줄어든다. 이 수정은 그 전단 검사와 게이트의 정규화 규칙(NFC와 NFKC)이 어긋날 때의 방어선으로 남는다.

### Infrastructure and resource limits

- 해당 없음. 빈 입력은 이제 보조 판정기 작업을 executor에 제출하지 않으므로 닉네임 전용 pool 사용이 조금 준다.

### Database and migrations

- 해당 없음. 스키마 변경이 없다.

### Concurrency and idempotency

- 게이트의 executor·timeout 구조는 바꾸지 않았다. `NicknameSyncModerationGateConcurrencyTest`가 통과했다.

### Transactions and event ordering

- 변경 경로에서 INVALID_INPUT은 저장 트랜잭션 전에 거절된다. 등록 경로는 등록 트랜잭션 안에서 계정 저장 전에 예외가 나서
  기존처럼 롤백된다.
- 닉네임 변경 시도 한도는 moderation보다 먼저 세므로 INVALID_INPUT 시도도 1회로 센다(#315 D2와 같음).

### External APIs

- 빈 입력에서 OpenAI 공급자와 보조 판정기 호출이 0회다(UNIT-003, UNIT-009). 공급자 장애는 계속 보조 판정기로 넘어간다.

### Failure recovery and reconciliation

- fail-closed 불변식은 그대로다. INVALID_INPUT도 `Rejected`이고, 판정 불가를 ALLOWED로 바꾸는 경로는 없다.
- 운영 관측: 입력 오류가 더는 `ACC-INFRA-001`로 집계되지 않는다. 503 경보에서 입력 오류 잡음이 빠진다.

## 7. Regression and residual risk

- 답변 moderation 경로의 같은 예외 처리(`AnswerModerationExecutionWorker`)는 Issue 제외 범위다. 이번 변경으로 동작은 바뀌지 않았다.
- #317과 `NicknameRegistrationService`를 함께 수정한다. 이 브랜치는 `rejectionFor`만, #317은 `ensureAvailable` 본문을 고치므로
  hunk가 다르다. 먼저 머지된 쪽에 맞춰 나머지가 `./harness sync`로 rebase한다.
- 변경 파일에 보이지 않는 문자(U+200B, U+FEFF, U+3000, U+00A0, 방향 제어 문자)가 없음을 `grep -P`로 확인했다. 테스트 입력은
  코드 포인트 상수(`(char) 0x200B`)로만 만든다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-318-TEST-PLAN-GH-318-NICKNAME-EMPTY-INPUT-400.md`
- CI run: PR 생성 후 확인
- Related ADR: 없음
- PR: 생성 전

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
