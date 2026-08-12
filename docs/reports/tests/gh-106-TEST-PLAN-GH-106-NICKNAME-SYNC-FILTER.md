# Test Report: TEST-PLAN-GH-106-NICKNAME-SYNC-FILTER

> Created at: `2026-08-12T10:45:00+09:00`
> GitHub Issue: `#106`
> Branch: `feat/gh-106-nickname-sync-filter`
> Commit: `54c9fb7`

## 1. Executive summary

- Result: `BLOCKED`
- Tested scope: UNIT-001~UNIT-011 전체 11개, INT-001~INT-004 전체 4개. 합계
  15/15 시나리오를 구현하고 통과시켰다.
- Blocking reason: 이 이슈는 애초에 독립 보조 판정기의 실제 공급자와 주
  판정기와의 공통 장애 영역 확정을 명시적으로 production 차단 게이트로 남겼다
  (`TASK.md` Explicit exclusions, `INVARIANTS.md` §11). `SecondaryModerationClient`
  는 fake로만 검증됐고 `INV-NICK-004`(보조 판정기의 실제 독립성)는 구조
  검증(별도 인스턴스·별도 executor 경계)만 완료했다 — 실제 독립성은 사람이
  공급자를 확정한 뒤에만 검증 가능하므로 최종 상태를 `PASS`가 아니라
  `BLOCKED`로 보고한다.
- Unverified scope:
  - `SecondaryModerationClient`의 실제 구현체(공급자) — 이 이슈 범위 밖.
  - 동기 timeout, 예약 용량(executor pool size), quota 구체 수치 — 미결정.
    테스트는 임의의 짧은 값(수백 ms)으로 검증했을 뿐 운영 기본값을 주장하지
    않는다.
  - `Account.createUser`/`Account.updateProfile`에서 이 게이트를 실제로
    호출하는 배선 — Account/Auth 담당 영역, 이 이슈 제외 범위.
  - `INV-NICK-004`(보조 판정기가 주 판정기와 실제 공통 장애 영역이 없는지) —
    fake는 항상 구조적으로 독립적이므로 실제 공급자 확정 전에는 검증 불가.
- Release recommendation: 이 이슈 범위(닉네임 동기 게이트 오케스트레이션) 내
  에서는 기능적으로 merge 가능한 상태다. 단, `NicknameSyncModerationGate`는
  Spring 빈이 아니고 실제 호출 지점에 연결되지 않았으므로 이 변경만으로는
  사용자에게 영향을 주지 않는다 — Account/Auth 담당이 #73 등 실제 호출부에
  배선해야 한다. `SecondaryModerationClient`의 실제 구현체 확정 전에는
  production에서 이 게이트를 활성화할 수 없다(운영 활성화 게이트).

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Temurin, Gradle toolchain) |
| Spring Boot | 3.5.16(게이트 자체는 Spring 컨텍스트 불필요) |
| Database | 사용하지 않음 — 게이트는 항상 `filterJobId` 없이(ephemeral) 호출돼 `filter_decision` 영속화 경로를 타지 않는다 |
| Test runner | JUnit 5 (Gradle `test`) |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests "com.dnd.qello.filtering.moderation.NicknameSyncModerationGateTest"` | PASS | 11 | ≈0.1s | `build/test-results/test/TEST-com.dnd.qello.filtering.moderation.NicknameSyncModerationGateTest.xml` |
| `./gradlew test --tests "com.dnd.qello.filtering.moderation.NicknameSyncModerationGateConcurrencyTest"` | PASS | 4 | ≈1.4s(실제 timeout 경계를 재는 시나리오 포함) | `build/test-results/test/TEST-com.dnd.qello.filtering.moderation.NicknameSyncModerationGateConcurrencyTest.xml` |
| `./gradlew test --tests "com.dnd.qello.filtering.moderation.*"` (패키지 전체, #105 회귀 포함) | PASS | 43 | ≈10s | 로컬 실행 로그(터미널) |
| `./harness check` | PASS | — (정적 정책 검사) | 수 초 | 로컬 실행 로그(터미널) |
| `git diff --check` | PASS(공백 오류 없음) | — | 즉시 | exit code 0 |
| `npm run hooks:validate` | **미실행** | — | — | `#105` 보고서(`docs/reports/tests/gh-105-TEST-PLAN-GH-105-MODERATION-PIPELINE.md` §3)와 동일한 로컬 WSL/Windows Python 상호운용 문제 — `npm`/`node`가 Windows `node.exe`로 실행되는데 그 프로세스의 `python3` 탐색이 Microsoft Store App Execution Alias에 막힌다. 저장소 코드와 무관. `./harness check`의 `Husky validation passed`로 Husky hook 설정 자체는 별도 확인됨 |
| `./harness pr-ready --project-tests`(전체 unit+integration+check) | PASS | 480(전체 스위트, `#106` 신규 15개 포함, 실패 0, 에러 0) | 12m31s | 로컬 실행 로그(터미널) — CI 실행은 아직 없음 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `NicknameSyncModerationGateTest#allowedByPrimaryDoesNotCallSecondary` | |
| UNIT-002 | PASS | `NicknameSyncModerationGateTest#explicitPrimaryBlockIsFinal` | 보조가 ALLOW로 구성돼도 무시됨을 함께 확인 |
| UNIT-003 | PASS | `NicknameSyncModerationGateTest#primaryTimeoutInvokesSecondaryExactlyOnce` | |
| UNIT-004 | PASS | `NicknameSyncModerationGateTest#secondaryAllowAfterPrimaryTimeoutResultsInAllowed` | |
| UNIT-005 | PASS | `NicknameSyncModerationGateTest#secondaryBlockAfterPrimaryTimeoutResultsInRejected` | |
| UNIT-006 | PASS | `NicknameSyncModerationGateTest#bothUnavailableFailsClosedWithoutThrowing` | |
| UNIT-007 | PASS | `NicknameSyncModerationGateTest#nonTimeoutPrimaryFailureAlsoFallsBackToSecondary` | |
| UNIT-008 | PASS | `NicknameSyncModerationGateTest#outcomeIsAlwaysExhaustivelyAllowedOrRejected` | sealed interface exhaustive switch로 구조적 방어 확인 |
| UNIT-009 | PASS | `NicknameSyncModerationGateTest#gateInstanceIsStatelessAcrossSequentialCalls` | |
| UNIT-010 | PASS | `NicknameSyncModerationGateTest#callsPrimaryPipelineWithEphemeralNicknameRequest` | `UnusedFilterDecisionRepository`가 `save()` 호출 시 `AssertionError`를 던지므로, 예외 없이 끝난 것 자체가 `filterJobId==null`의 증거 |
| UNIT-011 | PASS | `NicknameSyncModerationGateTest#initialAndChangeCallsShareTheSameFailClosedPath` | 계획은 "초기/변경 구분 파라미터 존재 여부 확인"을 가정했으나, 실제로는 그런 파라미터를 두지 않는 설계가 더 안전하다고 판단해 구현 — 두 번의 독립 호출이 동일하게 처리됨을 확인하는 것으로 대체(§6 Potential issues 참고) |
| INT-001 | PASS | `NicknameSyncModerationGateConcurrencyTest#primaryTimeoutCutsOverToSecondaryWithoutWaitingFullDelay` | 주 판정기가 3초 지연하도록 구성해도 200ms timeout에서 끊고 전환됨(실측 1.5초 미만) |
| INT-002 | PASS | `NicknameSyncModerationGateConcurrencyTest#answerPathExecutorSaturationDoesNotDelayNicknameGate` | `#105` UNIT-013보다 강한 증거 — 실제 `NicknameSyncModerationGate` 인스턴스 사용 |
| INT-003 | PASS | `NicknameSyncModerationGateConcurrencyTest#bothTimeoutsFailClosedWithinFiniteBudget` | 주·보조 모두 3초 지연이어도 150ms+150ms 예산 안에서 fail-closed 반환(실측 2초 미만) |
| INT-004 | PASS | `NicknameSyncModerationGateConcurrencyTest#excessConcurrentRequestsQueueWithoutLeakingOrLoss` | 2-thread 전용 executor에 6개 동시 요청 — 모두 유실 없이 완료 |

## 5. Failures and diagnostics

실행한 모든 시나리오가 첫 실행에서 통과했다. 재현된 실패는 없다.

## 6. Potential issues

### Application code

- `NicknameSyncModerationGate`는 의도적으로 `@Component`/`@Service`가 아니다
  — `ModerationPipelineService`(`#105`)와 동일한 이유로, 답변 경로와 실행
  자원을 공유하지 않으려면 호출자가 전용 `ExecutorService`·주 판정기
  인스턴스로 직접 생성해야 한다.
- 테스트 계획 §5 UNIT-011의 원래 설계 가정("게이트 API가 최초/변경을 구분하는
  파라미터를 받는지 확인")은 구현 과정에서 더 안전한 대안으로 대체했다 —
  게이트에는 애초에 최초/변경을 구분하는 분기나 파라미터가 없다.
  `evaluate(nickname, language)` 하나만 있고, 실패 시 항상 동일하게
  `REJECTED`를 반환한다. 완화된 별도 경로가 코드에 존재하지 않으므로
  `INV-NICK-006`/`INV-NICK-007`을 구조적으로 만족한다 — "최초 설정 실패"와
  "변경 실패"를 구분해 다르게 처리하는 로직 자체가 없다는 뜻이다. 이 결정은
  이슈 범위나 승인된 설계 가정 1~3을 벗어나지 않는 구현 세부 조정으로 판단해
  별도 재승인 없이 진행했다.

### Infrastructure and resource limits

- 게이트의 `primaryTimeout`/`secondaryTimeout`은 `Future#get(timeout)`으로
  강제된다 — 주 판정기 내부 `RestClient`의 자체 timeout 설정과 무관하게
  독립적으로 동작한다(defense in depth, `#105` 보고서 §6 "Infrastructure and
  resource limits"에서 지적된 위험을 이 이슈가 실제로 완화한다).
- `Future#cancel(true)`는 실행 중인 작업에 인터럽트를 보낼 뿐이다 — 실제
  provider 호출(예: HTTP 요청)이 인터럽트에 즉시 반응하지 않으면 백그라운드
  스레드가 게이트의 timeout 이후에도 잠시 더 실행될 수 있다. 게이트
  호출자에게는 이미 응답이 반환됐으므로 사용자 체감에는 영향이 없지만,
  전용 executor의 스레드가 일시적으로 점유 상태로 남을 수 있다 — 실제
  `RestClient`도 자체 timeout을 구성해야 이 잔여 점유 시간이 짧아진다(운영
  배선 시 반드시 확인해야 할 항목, `#106`이 직접 강제하지는 않는다).
- 정확한 timeout·executor pool size·quota 수치는 미결정이며(`TASK.md`,
  `INVARIANTS.md` §11) 이 이슈는 configuration 자리(생성자 파라미터)만 두고
  운영 기본값을 하드코딩하지 않았다.

### Database and migrations

- 해당 없음 — 이 이슈는 마이그레이션이나 DB 접근을 추가하지 않는다. 게이트는
  항상 `filterJobId` 없이 pipeline을 호출해 `filter_decision` 저장 경로를
  타지 않는다(UNIT-010이 검증).

### Concurrency and idempotency

- INT-002/INT-004가 실행 자원 격리와 게이트의 동시 호출 안전성을 다룬다.
- 게이트는 상태를 갖지 않는다(생성자 주입 필드만 보유, mutable 필드 없음) —
  코드 검토로 확인했다. 동시 호출 시 내부 상태 경합 가능성은 구조적으로
  없다.
- 재시도는 하지 않는다 — 주→보조 순차 호출은 재시도가 아니라 서로 다른
  판정기로의 단발 전환이다. 답변 경로의 retry 정책(`#108` 소관)과 이 이슈는
  무관하다.

### Transactions and event ordering

- 해당 없음 — 게이트는 트랜잭션을 열지 않는다.

### External APIs

- 주 판정기의 실제 OpenAI 호출은 `#105`에서 이미 검증됨 — 이 이슈는 fake
  `ModerationProviderClient`만 사용했다.
- `SecondaryModerationClient`는 실제 구현체가 없다 — 모든 시나리오가 fake만
  사용했다. 실제 공급자 연동 검증과 주 판정기와의 공통 장애 영역 분석은
  production 차단 게이트로 남아 있다(`TASK.md` Explicit exclusions).

### Failure recovery and reconciliation

- 게이트는 자동 재시도를 하지 않는다 — `REJECTED(UNAVAILABLE)`은 최종
  결과이며 호출자(또는 사용자의 재요청)가 다시 게이트를 호출하는 것으로만
  복구된다. 게이트 내부에 재시도 루프가 없음을 코드 검토로 확인했다.
- INT-003이 fail-closed 결과가 유한 시간 안에 반환됨을 확인해, 호출자가
  무기한 블로킹되지 않고 명확한 실패로 서비스 진입을 차단할 수 있는 기반을
  보장한다.

## 7. Regression and residual risk

- `FilteringErrorCode`에 `SECONDARY_MODERATOR_UNAVAILABLE`(`FLT-EXT-002`)
  값을 추가했다 — 기존 코드 값은 변경하지 않았다(additive-only).
- 새로 추가한 클래스는 어디에서도 Spring 빈으로 등록되지 않고 기존 호출
  지점에 연결되지 않았으므로 기존 동작에 대한 회귀 위험은 없다.
  `./harness pr-ready --project-tests`로 저장소 전체 기존 테스트(465개)가
  여전히 통과함을 확인했다.
- 잔여 위험: `SecondaryModerationClient`의 실제 구현체 부재, `INV-NICK-004`
  (보조 판정기의 실제 독립성) 미검증, 동기 timeout·예약 용량 구체 수치
  미확정, `Account.createUser`/`updateProfile` 실제 배선 부재 — 모두 이
  이슈의 명시적 제외 범위이며 후속 이슈로 인계한다.
- `npm run hooks:validate` 미실행(§3) — 로컬 WSL/Windows Python 상호운용
  문제로 판단되며 저장소 변경과는 무관하다. CI 또는 다른 환경에서 후속 확인
  필요.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-106-GH-106-NICKNAME-SYNC-FILTER.md`
- CI run: 아직 없음 (PR 생성 전, 로컬 실행만 수행)
- Related ADR: `dnd_production_planning/filtering_system/DESIGN.md` 결정
  3~6, 8 (저장소 외부 기획 문서, 참고용)
- PR: 아직 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 검증이 명시됨(`npm run hooks:validate`, §3)
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨(§6 항목들, 특히
      `SecondaryModerationClient` 실제 구현체와 `Account.createUser`/
      `updateProfile` 배선을 후속 이슈에 수동으로 연결 필요 — 이 세션에서는
      GitHub Issue 코멘트를 추가하지 않았다)
- [ ] 실행 결과와 PR 설명이 일치함(PR 생성 시 확인)
- [ ] `SecondaryModerationClient` 실제 공급자와 `INV-NICK-004`(주 판정기와의
      공통 장애 영역 부재)가 사람에 의해 확정·승인됨 — 이 항목이 완료되기
      전까지 본 보고서 상태는 `BLOCKED`로 유지(production 활성화 게이트)
