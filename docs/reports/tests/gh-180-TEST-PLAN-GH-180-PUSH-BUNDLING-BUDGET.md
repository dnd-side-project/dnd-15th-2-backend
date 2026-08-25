# Test Report: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET

> Created at: `2026-08-25T16:21:00+09:00`
> GitHub Issue: `#180`
> Branch: `feat/gh-180-push-bundling-budget`
> Commit: `ab4ac81` (Tasks 1~7 working tree uncommitted; repository gate)

## 1. Executive summary

- Result: `PARTIAL`. Gradle unit/integration은 INT-020 수정 후 PASS. INT-020 수정 후 `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET`은 Gradle UP-TO-DATE SUCCESS 뒤 기존 보고서 overwrite 거절로 FAIL(exit 2). production readiness는 unverified.
- Tested scope: 승인된 GH-180 unit `UNIT-001`~`UNIT-018`과 integration `INT-001`~`INT-021` 대상 스위트, 전체 `./gradlew test integrationTest`, INT-020 수정 후 `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET`, `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`, `git diff --check`, secret/token 정적 검색.
- Unverified scope: 운영 정책 다섯 값(`bundle-window`, `max-delay`, `daily-limit`, `direction-reserved`, `recommendation-min-interval`)은 `UNKNOWN`. live FCM credential과 Android/iOS 실기기, #182 scheduler/polling.
- Release recommendation: 저장소 Gradle 스위트와 `pr-ready`는 통과했다. Step 4 `./harness test-run`은 기존 보고서 overwrite로 FAIL이라 TASK 마지막 항목을 체크하지 않는다. 운영값·live FCM·#182가 미검증이므로 production readiness를 PASS로 보지 않는다. 배포하지 않는다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle/JUnit stack 기준 `21.0.12.1`; toolchain `JavaLanguageVersion.of(21)`; shell default JDK는 Temurin `25.0.3` |
| Spring Boot | `3.5.16` |
| Database | PostgreSQL Testcontainers / integration profile. Docker daemon available |
| Test runner | JUnit 5 via Gradle |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests '*PushPolicyPropertiesTest' --tests '*PushGroupingPolicyTest' --tests '*PushSuppressionPolicyTest' --tests '*PushBudgetPolicyTest' --tests '*PushDispatchGroupStateTest' --tests '*PushPayloadFactoryTest' --tests '*FcmHttpV1PushProviderTest' --tests '*PushConfigurationTest' --console=plain` | PASS | 56 (XML `failures=0 errors=0 skipped=0`) | 2s | UNIT-001~018 대상 class XML |
| `./gradlew integrationTest --tests '*PushDispatchGroupMigrationIntegrationTest' --tests '*PushDispatchGroupingIntegrationTest' --tests '*PushDispatchSuppressionIntegrationTest' --tests '*PushDeliveryDispatchIntegrationTest' --tests '*PushDeliveryLeaseIntegrationTest' --console=plain` | PASS | 51 (XML `failures=0 errors=0 skipped=0`) | 27s | INT-001~019/021 대상 class XML |
| `./gradlew test --tests '*PushConfigurationTest' --console=plain` (fix rerun) | PASS | 17 | 2s | DisplayName 창 보정 후 UNIT-001 fail-fast 포함 |
| `./gradlew integrationTest --tests '*AccountPersistenceIntegrationTest' --tests '*NotificationPreferenceMigrationIntegrationTest' --tests '*QelloLocalProfileIntegrationTest' --console=plain` (fix rerun) | PASS | 18 (15+2+1) | 18s | catalog 54, V24→latest 4, local test fixture 주입 |
| `./gradlew test integrationTest --console=plain` (first run) | FAIL | `:test` 969 / 0 failed; `:integrationTest` 715 completed, 3 failed | 6m 2s | INT-020 초회. §5 |
| `./gradlew test integrationTest --console=plain` (after fix) | PASS | `:test` 969 / 0 failed; `:integrationTest` 715 / 0 failed | 6m 12s | INT-020. HTML successRate 100% |
| `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET` (pre-fix) | FAIL | unit UP-TO-DATE 969/0; integration 715 completed, 3 failed | unit 598ms; integration `BUILD FAILED in 6m 7s` | 초회 INT-020. scaffold 없음 |
| `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET` (after INT-020 fix) | FAIL | Gradle `:test` and `:integrationTest` UP-TO-DATE `BUILD SUCCESSFUL`; XML unit 969/0, integration 715/0 | unit 575ms; integration 450ms; harness total 2.07s | exit 2. `refusing to overwrite existing file: docs/reports/tests/gh-180-TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET.md`. Gradle 재실행이 아니라 UP-TO-DATE. 통과를 만들어 내지 않음 |
| `./harness check` (after fix) | PASS | 245 test files | <1s | JUnit policy 포함. 초회는 DisplayName 창으로 FAIL |
| `./harness pr-ready --project-tests` (after fix) | PASS | origin/main sync PASS; `./gradlew check` UP-TO-DATE | 5.9s | `Local PR readiness checks passed.` |
| `npm run hooks:validate` | PASS | Husky validation | <1s | exited 0 |
| `git diff --check` | PASS | whitespace check | <1s | no output |
| `git grep -nE '(BEGIN PRIVATE KEY\|ya29\\.\|AAAA[A-Za-z0-9_-]{20,})' -- ':!*.local.md'` | PASS with benign matches | 2 matches | <1s | `OpenApiSpecificationIntegrationTest` fixture 문자열, 기존 gh-179 보고서가 같은 명령을 인용. 실제 secret 0 |

대상 unit XML: `PushPolicyPropertiesTest` 4, `PushGroupingPolicyTest` 4, `PushSuppressionPolicyTest` 5, `PushBudgetPolicyTest` 4, `PushDispatchGroupStateTest` 3, `PushPayloadFactoryTest` 7, `FcmHttpV1PushProviderTest` 12, `PushConfigurationTest` 17.

대상 integration XML: migration 5, grouping 10, suppression 13, dispatch 19, lease 4.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| `UNIT-001` | PASS | `PushPolicyPropertiesTest`, `PushConfigurationTest` | fixture 보존, 경계값, 잘못된 필드 거절, 필수 주입 fail-fast |
| `UNIT-002` | PASS | `PushGroupingPolicyTest` | 답변/공감 분리 |
| `UNIT-003` | PASS | `PushGroupingPolicyTest` | 창 정각 합류, 직후 새 group |
| `UNIT-004` | PASS | `PushGroupingPolicyTest` | singleton, `collectUntil=createdAt` |
| `UNIT-005` | PASS | `PushGroupingPolicyTest` | 추천 cycle key |
| `UNIT-006` | PASS | `PushSuppressionPolicyTest` | global OFF 우선, quiet 보존 |
| `UNIT-007` | PASS | `PushSuppressionPolicyTest` | null quiet SEND, overnight DEFER |
| `UNIT-008` | PASS | `PushSuppressionPolicyTest` | DST gap/overlap 실제 Instant |
| `UNIT-009` | PASS | `PushSuppressionPolicyTest` | maxDelay 정각 DEFER, 초과 CANCEL |
| `UNIT-010` | PASS | `PushBudgetPolicyTest` | 일반 한도 `daily-reserved` |
| `UNIT-011` | PASS | `PushBudgetPolicyTest` | 방향글 total 우선권 |
| `UNIT-012` | PASS | `PushBudgetPolicyTest` | account zone local date, quiet zone 미사용 |
| `UNIT-013` | PASS | `PushSuppressionPolicyTest` | cycle 억제와 최소 간격 정각 |
| `UNIT-014` | PASS | `PushPayloadFactoryTest` | invalid member 제외, `count="2"`, 세 key |
| `UNIT-015` | PASS | `PushPayloadFactoryTest` | 기기 2대여도 logical count 1 |
| `UNIT-016` | PASS | `PushDispatchGroupStateTest` | 허용 전이와 generation fence |
| `UNIT-017` | PASS | `PushBudgetPolicyTest` | 이미 소비된 group은 재차감 없음 |
| `UNIT-018` | PASS | `PushPayloadFactoryTest`, `FcmHttpV1PushProviderTest` | 방향글/답변 payload와 양의 정수 count allowlist |
| `INT-001` | PASS | `PushDispatchGroupMigrationIntegrationTest` | V28 상태/count/member/completed_at 거절 |
| `INT-002` | PASS | `PushDispatchGroupingIntegrationTest` | 창 정각 한 group·member 3, 직전 claim 0 |
| `INT-003` | PASS | `PushDispatchGroupingIntegrationTest` | recipient/type별 3 group |
| `INT-004` | PASS | `PushDispatchGroupingIntegrationTest` | 동시 planner unique 오류 없음 |
| `INT-005` | PASS | `PushDispatchGroupingIntegrationTest`, `PushDeliveryLeaseIntegrationTest` | 동시 claim 중복 0, lease 회수, stale 0행 |
| `INT-006` | PASS | `PushDispatchSuppressionIntegrationTest` | quiet defer 후 종료 시각 재검사 발송 |
| `INT-007` | PASS | `PushDispatchSuppressionIntegrationTest` | maxDelay 초과 취소, 원장 보존 |
| `INT-008` | PASS | `PushDispatchSuppressionIntegrationTest` | global/type OFF 취소, quiet 세 필드 보존 |
| `INT-009` | PASS | `PushDispatchSuppressionIntegrationTest` | 동시 일반 reserve 정확히 3 |
| `INT-010` | PASS | `PushDispatchSuppressionIntegrationTest` | 방향글 예약량 2, total=5 |
| `INT-011` | PASS | `PushDispatchSuppressionIntegrationTest` | timezone별 budget date 분리 |
| `INT-012` | PASS | `PushDeliveryDispatchIntegrationTest` | 기기별 provider 1회, count=3, budget 1 |
| `INT-013` | PASS | `PushDeliveryDispatchIntegrationTest` | 유효 1개 count=1, notification 3 보존 |
| `INT-014` | PASS | `PushDeliveryDispatchIntegrationTest` | 부분 성공 retry, budget 1, COMPLETED |
| `INT-015` | PASS | `PushDeliveryDispatchIntegrationTest` | invalid token 기기 전체 미발송 취소 |
| `INT-016` | PASS | `PushDispatchSuppressionIntegrationTest` | 같은 cycle 1회, 간격 전 미전진, 정각 허용 |
| `INT-017` | PASS | `PushDeliveryDispatchIntegrationTest` | fake FCM wire `count="4"`, 세 data key |
| `INT-018` | PASS | `PushDispatchGroupingIntegrationTest` | 기존 delivery 편입, 유효 lease 제외 |
| `INT-019` | PASS | `PushDispatchGroupingIntegrationTest`, `PushDeliveryLeaseIntegrationTest` | EXPLAIN predicate/lock/Limit |
| `INT-020` | PASS | 전체 `./gradlew test integrationTest` | 수정 후 unit 969, integration 715, 0 fail. 초회 3건은 §5 |
| `INT-021` | PASS | `PushDispatchSuppressionIntegrationTest` | quiet 뒤 OFF/block/만료/해지 재검사 취소 |

## 5. Failures and diagnostics

오류의 유형, 재현 조건, 안전하게 정리한 메시지만 기록한다. 로그 원문에
민감정보가 있을 가능성이 있으면 첨부하지 않는다.

초회 전체 스위트에서 아래 4건이 실패했고, 테스트 계약·local fixture·DisplayName 창만 고친 뒤 재실행에서 모두 통과했다. `application.properties`에 운영 숫자 fallback을 넣지 않았다.

### INT-020 / `AccountPersistenceIntegrationTest` — resolved

- First command: `./gradlew test integrationTest --console=plain`
- Type: 구현 후 기존 catalog assertion 미갱신. Docker/Testcontainers 환경 문제가 아니다.
- Error summary: `expected: 51 but was: 54` at `startsWithFlywaySchemaValidationOnly`. V28이 group/member/budget 3테이블을 추가했다.
- Resolution: assertion을 54로 갱신. 재실행 15 tests PASS, 전체 INT-020 PASS.

### INT-020 / `NotificationPreferenceMigrationIntegrationTest` — resolved

- Type: V28 추가 뒤 “V24 → latest = 3 migrations” 단언이 깨짐.
- Error summary: `expected: 3 but was: 4`. V25~V28이 4건이다.
- Resolution: expected 4. 재실행 2 tests PASS.

### INT-020 / `QelloLocalProfileIntegrationTest` — resolved in test only

- Type: local 프로필이 필수 정책 Duration을 환경 변수 없이 바인딩하지 못함.
- Error summary: `bundle-window` placeholder를 Duration으로 변환하지 못함.
- Resolution: `@SpringBootTest(properties=…)`에 승인된 테스트 fixture `PT10M`/`PT8H`/`5`/`2`/`PT24H`만 주입. production `application.properties`는 변경하지 않음. 재실행 1 test PASS.
- Remaining: 테스트 밖 local 기동은 여전히 다섯 환경 변수가 필요하다. 운영 값은 `UNKNOWN`.

### `./harness check` — resolved

- Error summary: `PushConfigurationTest.java:237` `@ParameterizedTest` + 5줄 `@ValueSource`가 `@DisplayName`을 validator 창 밖으로 밀었다.
- Resolution: `@ValueSource`를 압축해 `@DisplayName`을 메서드 바로 위, `@ParameterizedTest` 6줄 안에 둠. `./harness check` PASS, 245 files.

### `./harness test-run`

- 초회는 위 3건으로 실패해 template scaffold를 만들지 않았다. 본 보고서는 `templates/test-report.md`를 직접 채웠다.
- INT-020 수정 후 재실행: `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET` (2026-08-25T16:44:57+09:00 시각 전후). `:test` `BUILD SUCCESSFUL in 575ms` UP-TO-DATE, `:integrationTest` `BUILD SUCCESSFUL in 450ms` UP-TO-DATE. harness `exit 2`: `refusing to overwrite existing file: .../gh-180-TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET.md`. Gradle/`pr-ready` 성공을 harness 성공으로 대체하지 않는다.

## 6. Potential issues

### Application code

- local 프로필은 다섯 정책 환경 변수가 없으면 컨텍스트가 기동하지 않는다. 통합 테스트만 fixture를 주입했고, 운영 기본값은 계약상 금지라 production fallback을 추가하지 않았다.
- `PushSuppressionPolicy` Spring bean은 `Clock.systemUTC()`를 쓴다. 테스트는 생성자로 fixture clock을 주입한다.
- `finalizeGroup`이 예외를 삼키면 group이 PROCESSING에 남을 수 있다(Task 5 관찰). lease 만료 회수로 완화되지만 운영 지표는 없다.
- `collectUngrouped`는 건너뛴 candidate도 처리 건수에 넣을 수 있다(Task 3 관찰).

### Infrastructure and resource limits

- integration은 Testcontainers PostgreSQL에 의존한다. 이번 실행은 Docker daemon이 살아 있었다.
- #182 전에는 dispatch worker 자동 polling이 없다. 명시 호출만 검증했다.
- 운영 정책 다섯 값과 FCM/token secret은 외부 주입이며 이번 이슈에서 값을 확정하지 않는다.

### Database and migrations

- V28 세 테이블·FK·check·index는 INT-001과 `FlywayMigrationIntegrationTest` 매니페스트로 검증됐다.
- `AccountPersistenceIntegrationTest` 테이블 수는 54, V24→latest migration 실행 수는 4로 맞춰 V28을 반영했다.
- INT-019 EXPLAIN은 작은 fixture에서 `enable_seqscan=off`를 쓴다. 운영 backlog의 실제 plan은 미검증이다.

### Concurrency and idempotency

- 창 편입·group claim·budget reserve 동시성은 latch와 독립 transaction으로 INT-004/005/009/010이 통과했다.
- 같은 cycle unique key와 `budget_consumed_at`으로 retry/다중 기기 중복 소비를 막는다.
- 추천 candidate skip이 `lockUngrouped` LIMIT를 소비하면 한 batch에서 편입이 미뤄질 수 있다(Task 3 관찰).

### Transactions and event ordering

- group claim과 terminal update만 짧은 DB transaction이다. token 복호화와 provider I/O는 transaction 밖이다.
- 예산은 첫 provider 호출 직전 같은 transaction에서 group `budget_consumed_at`과 함께 소비한다. 실패해도 복원하지 않는다.
- quiet defer는 budget/delivery를 쓰지 않고 group만 PENDING으로 되돌린다.

### External APIs

- 실제 FCM HTTP v1와 OAuth credential은 사용하지 않았다.
- local fake HTTP server와 기존 adapter로 allowlist·accepted name만 검증했다.
- Android/iOS foreground·background·종료·잠금화면 문구는 미검증이다.

### Failure recovery and reconciliation

- lease 만료 후 generation+1 회수와 stale terminal 0행은 INT-005가 통과했다.
- invalid token은 해당 기기 미발송을 모든 group에서 취소하고 다른 기기·notification은 보존한다(INT-015).
- provider 수락 뒤 terminal 전 crash는 #179와 같은 at-least-once 중복 위험이 남는다.
- 예산 소비 후 provider 실패는 그 날짜 한도를 되돌리지 않는다.

## 7. Regression and residual risk

- GH-180 승인 P0/P1 시나리오(UNIT-001~018, INT-001~019/021)는 대상 스위트에서 통과했다.
- 전체 회귀 INT-020은 초회 3건 실패 뒤 catalog/local-fixture 보정으로 unit 969·integration 715, 0 fail로 통과했다.
- `./harness check`와 `./harness pr-ready --project-tests`는 DisplayName 창 보정 후 PASS.
- INT-020 수정 후 `./harness test-run --id TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET`은 Gradle UP-TO-DATE SUCCESS 뒤 기존 보고서 overwrite 거절로 FAIL(exit 2). TASK 마지막 completion checkbox는 이 명령이 통과하지 않아 미체크다.
- 운영 다섯 값 `UNKNOWN`, live FCM/mobile, #182 scheduler가 닫히기 전에는 production readiness를 주장하지 않는다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-180-TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET.md`
- CI run: 로컬 Gradle/harness. CI 없음
- Related ADR: `docs/adr/0008-adopt-fcm-push-delivery-pipeline.md`
- Design: `docs/superpowers/specs/2026-08-25-push-bundling-budget-design.md`
- PR: 없음 (commit/push/PR 금지)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함
