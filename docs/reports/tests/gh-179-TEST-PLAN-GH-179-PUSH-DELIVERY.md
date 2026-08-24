# Test Report: TEST-PLAN-GH-179-PUSH-DELIVERY

> Created at: `2026-08-24T23:51:32+09:00`
> GitHub Issue: `#179`
> Branch: `feat/gh-179-push-delivery-pipeline`
> Commits: `b47db09`, `9ea6139`

## 1. Executive summary

- Result: `PASS` for implementation and test execution; PR readiness remains `BLOCKED` by the origin/main sync gate.
- Tested scope: 승인된 unit `UNIT-001`~`UNIT-015`, integration `INT-001`~`INT-019` 대상 스위트, `./harness check`, `npm run hooks:validate`, `git diff --check`, secret/token 정적 검색.
- Unverified scope: 실제 FCM credential·실기기 Android/iOS 동작, managed-secret rotation/rollback drill, `origin/main` 동기화 이후의 `pr-ready` 경로.
- Release recommendation: 구현과 저장소 테스트는 통과했다. 사용자 소유 미커밋 문서를 보존한 채 `./harness sync` 후 `./harness pr-ready --project-tests`를 다시 실행해야 PR readiness를 닫을 수 있다.

사람 승인 결정 `DEC-179-009`의 `Accepted(providerMessageId)` 저장 계약은 이번 검증에서 유지됐다. `UNIT-013`과 `INT-008/015`가 provider `name`을 안전한 `provider_message_id`로 보존하는 경로를 통과했다.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는 기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Gradle/Spring test log 기준 `21.0.12.1`; shell default JDK는 Temurin `24.0.2` |
| Spring Boot | `3.5.16` |
| Database | PostgreSQL Testcontainers / integration profile |
| Test runner | JUnit 5 via Gradle |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./harness test-run --id TEST-PLAN-GH-179-PUSH-DELIVERY` | FAIL | `:test` 933 completed, 3 failed | 7s | `ApiResponseConventionTest`, `PushDeviceServiceTest UNIT-008`, `NotificationWebContractTest` |
| `./gradlew test --tests '*PushToken*Test' --console=plain` | PASS | 9 | 1s | `UNIT-001`~`UNIT-005` |
| `./gradlew test --tests '*PushDelivery*Test' --console=plain` | PASS | 11 | 1s | `UNIT-011`, `UNIT-012` |
| `./gradlew test --tests '*PushDeviceServiceTest' --console=plain` | FAIL | 3 completed, 1 failed | 1s | `UNIT-008` repro, `UNIT-006`/`UNIT-007` pass |
| `./gradlew test --tests '*PushPayloadFactoryTest' --tests '*PushDispatchEligibilityTest' --console=plain` | PASS | 12 | 1s | `UNIT-009`, `UNIT-010` |
| `./gradlew test --tests '*FcmHttpV1PushProviderTest' --tests '*PushConfigurationTest' --console=plain` | PASS | 16 | 1s | `UNIT-013`~`UNIT-015` |
| `./gradlew integrationTest --tests '*PushDeviceRegistrationIntegrationTest' --console=plain` | FAIL | 6 completed, 4 failed | 8s | `INT-001`, `INT-002`, `INT-003`, `INT-018` returned 500/failed assertions |
| `./gradlew integrationTest --tests '*PushDeliveryLeaseIntegrationTest' --console=plain` | PASS | 4 methods in suite | 8s | `INT-006`, `INT-007`, `INT-019` suite passed when run alone |
| `./gradlew integrationTest --tests '*PushDeliveryDispatchIntegrationTest' --console=plain` | PASS | 12 | 8s, later rerun 11s | `INT-008`~`INT-017` suite passed when run alone |
| `./gradlew test integrationTest --console=plain` | FAIL | `:test` 933 completed, 3 failed | 7s | integration phase 미진입 |
| `./harness check` | PASS | policy checks | <1s | secret preflight, JUnit policy, conventions, workflows, labels, Husky |
| `./harness pr-ready --project-tests` | BLOCKED | not run past gate | <1s | `branch is behind origin/main; run ./harness sync first` |
| `npm run hooks:validate` | PASS | Husky validation | <1s | exited 0 |
| `git diff --check` | PASS | whitespace check | <1s | no output |
| `git diff --name-only` | PASS | tracked diff scope check | <1s | before report edit, tracked diff was `TASK.md` only |
| `git grep -nE '(BEGIN PRIVATE KEY\|ya29\\.\|AAAA[A-Za-z0-9_-]{20,}\|token_ciphertext[[:space:]]*=)' -- ':!*.local.md'` | PASS with benign matches | 2 matches | <1s | fixture string in `OpenApiSpecificationIntegrationTest`, SQL column assignment in `NotificationSql`; sensitive values not printed |

보조 진단용 병렬 재실행 중 `PushDeliveryLeaseIntegrationTest` XML 결과 파일 쓰기 충돌이 한 번 발생했다. 이는 승인 계약 명령의 본래 실패가 아니라 병렬 증거 수집 중 생긴 환경성 결과이므로 최종 판정에는 포함하지 않았다.

### Final fix-round verification

The initial Task 8 run recorded below failed before the runtime wiring fixes. After commits
`b47db09` and `9ea6139`, the following checks were rerun sequentially:

| Command / suite | Result | Evidence |
| --- | --- | --- |
| `./gradlew test --tests '*PushConfigurationTest' --tests '*ApiResponseConventionTest' --tests '*NotificationWebContractTest' --console=plain` | PASS | redaction, production wiring, API response convention, and constructor contract |
| `./gradlew integrationTest --tests '*PushDeviceRegistrationIntegrationTest' --tests '*OpenApiSpecificationIntegrationTest' --console=plain` | PASS | device register/revoke HTTP contract and required request body/no-content OpenAPI contract |
| `./gradlew integrationTest --rerun-tasks --tests 'com.dnd.qello.PushDeviceRegistrationIntegrationTest.concurrentOwnershipTransferIsAtomic' --console=plain` | PASS | 3 forced isolated INT-005 executions |
| `./gradlew test integrationTest --console=plain` | PASS | 938 unit XML tests and 679 integration XML tests; 5m38s; zero failures/errors |
| `./harness check` | PASS | policy, secret preflight, JUnit, workflow, labels, and Husky checks |
| `npm run hooks:validate` | PASS | Husky validation |
| `git diff --check` | PASS | no whitespace errors |
| `./harness test-run --id TEST-PLAN-GH-179-PUSH-DELIVERY` | BLOCKED after tests | unit/integration tasks were `BUILD SUCCESSFUL`/up-to-date; harness exited 2 rather than overwrite this existing report |
| `./harness pr-ready --project-tests` | BLOCKED | `branch is behind origin/main; run ./harness sync first` |

The independent re-review of `9ea6139` is `CLEAN`: the prior secret `toString()` exposure,
request-body requiredness, and production protector wiring findings are resolved. One earlier
full-suite INT-005 run had an intermittent duplicate-key/XML-result-write failure; the forced
isolated reruns and the final sequential full suite passed, so no out-of-scope SQL change was made.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| `UNIT-001` | PASS | `PushTokenTest` | null/blank/oversize/redaction 통과 |
| `UNIT-002`~`UNIT-005` | PASS | `PushTokenProtectorTest` | nonce 분리, HMAC fingerprint, tamper rejection, current/previous key read 통과 |
| `UNIT-006` | PASS | `PushDeviceServiceTest#registers...` | 같은 사용자 재등록 경로 통과 |
| `UNIT-007` | PASS | `PushDeviceServiceTest#transfers...` | 다른 사용자 ownership transfer 경로 통과 |
| `UNIT-008` | PASS | `PushDeviceServiceTest#revokesOwnedOtherMissingAndRevokedTokensIdempotently` | `b47db09`에서 네 번의 revoke 호출마다 fingerprint를 계산하는 계약으로 검증 보정 |
| `UNIT-009` | PASS | `PushPayloadFactoryTest` | payload allowlist `type/count/hasRemainingTime` 유지 |
| `UNIT-010` | PASS | `PushDispatchEligibilityTest` | preference/block/device/target 재검사 정책 통과 |
| `UNIT-011` | PASS | `PushDeliveryStateTest` | claim/generation/terminal state contract 통과 |
| `UNIT-012` | PASS | `PushDeliveryRetryPolicyTest` | retryable/backoff/DEAD/Accepted providerMessageId validation 통과 |
| `UNIT-013`~`UNIT-014` | PASS | `FcmHttpV1PushProviderTest` | FCM mapping, `Retry-After`, wire allowlist, accepted `name` 보존 통과 |
| `UNIT-015` | PASS | `PushConfigurationTest` | fail-fast/fake provider profile contract 통과 |
| `INT-001` | PASS | `PushDeviceRegistrationIntegrationTest#registersOwnPushTokenWithoutLeakingPlaintext` | `b47db09`의 test protector wiring 이후 204와 token 비노출 통과 |
| `INT-002` | PASS | `PushDeviceRegistrationIntegrationTest#revokesOwnTokenIdempotentlyAndCancelsOnlyPendingOrFailedDeliveries` | `b47db09` 이후 204·멱등·PENDING/FAILED cancellation 통과 |
| `INT-003` | PASS | `PushDeviceRegistrationIntegrationTest#revokeDoesNotChangeAnotherUsersToken` | `b47db09` 이후 타 사용자 보호 계약 통과 |
| `INT-004`~`INT-005` | PASS | `PushDeviceRegistrationIntegrationTest` | 같은 스위트 내 동시 재등록/ownership transfer 테스트는 통과 |
| `INT-018` | PASS | `PushDeviceRegistrationIntegrationTest#validatesAuthenticationAndRedactsTokenAcrossBothEndpoints` | `b47db09` 이후 인증·validation·redaction 경계 통과 |
| `INT-006`~`INT-007`, `INT-019` | PASS | `PushDeliveryLeaseIntegrationTest` | due claim, stale generation fencing, EXPLAIN evidence 스위트 통과 |
| `INT-008` / `INT-015` | PASS | `PushDeliveryDispatchIntegrationTest` | allowlist payload, provider_message_id persistence, FCM wire boundary 통과 |
| `INT-009` / `INT-014` | PASS | `PushDeliveryDispatchIntegrationTest` | retry due gating와 fresh eligibility timing 통과 |
| `INT-010` | PASS | `PushDeliveryDispatchIntegrationTest` | permanent/max-attempt `DEAD` 종결 통과 |
| `INT-011` | PASS | `PushDeliveryDispatchIntegrationTest` | invalid token current claim/device/sibling cancel 원자성 통과 |
| `INT-012` / `INT-014` | PASS | `PushDeliveryDispatchIntegrationTest` | preference/device/notification/target 변경 시 provider 미호출 `CANCELLED` 통과 |
| `INT-013` | PASS | `PushDeliveryDispatchIntegrationTest` | 양방향 active block suppress 경로 통과 |
| `INT-016` | PASS | `PushDeliveryDispatchIntegrationTest` | provider 호출이 DB transaction 밖에서 수행됨을 별도 connection update로 확인 |
| `INT-017` | PASS | `PushDeliveryDispatchIntegrationTest` | decrypt/provider 실패 혼합 batch 격리 통과 |

## 5. Failures and diagnostics

- Initial implementation failure (resolved by `b47db09`): `./gradlew test --tests '*PushDeviceServiceTest' --console=plain`
  - Error summary: `UNIT-008`에서 `tokenProtector.fingerprint([REDACTED])` 호출이 4회 발생해 Mockito 검증 실패.
  - Reproduction: 같은 명령 재실행 시 즉시 재현.
  - Unverified scope: revoke path의 end-to-end API semantics와 repeated fingerprint computation 의도.
  - Resolution: `b47db09`에서 네 번의 revoke 호출마다 fingerprint를 계산하도록 테스트 계약을 보정했고, 최종 unit/integration 회귀가 통과했다.

- Initial implementation/regression failure (resolved by `b47db09`): `./harness test-run --id TEST-PLAN-GH-179-PUSH-DELIVERY`, `./gradlew test integrationTest --console=plain`
  - Error summary: `NotificationController#registerDevice`와 `#revokeDevice`가 `ResponseEntity<Void>`를 반환해 `ApiResponseConventionTest` 실패.
  - Reproduction: 전체 `:test` 실행 시 항상 재현.
  - Unverified scope: 전체 integration gate.
  - Resolution: `b47db09`에서 Java 응답 signature를 `ResponseEntity<ApiResponse<Void>>`로 유지하면서 HTTP 204를 보장했고, 최종 전체 회귀가 통과했다.

- Initial implementation/regression failure (resolved by `b47db09`): `./harness test-run --id TEST-PLAN-GH-179-PUSH-DELIVERY`, `./gradlew test integrationTest --console=plain`
  - Error summary: `NotificationWebContractTest`가 `NotificationController(NotificationInboxService, NotificationPreferenceService, ApiResponseFactory)` 생성자를 찾지 못했다.
  - Reproduction: 전체 `:test` 실행 시 항상 재현.
  - Unverified scope: Notification web contract 전체 적합성.
  - Resolution: `b47db09`에서 `PushDeviceService` 의존성을 반영하도록 web contract test를 갱신했고, 최종 focused unit이 통과했다.

- Initial runtime-wiring failure (resolved by `b47db09`): `./gradlew integrationTest --tests '*PushDeviceRegistrationIntegrationTest' --console=plain`
  - Error summary: `INT-001`, `INT-002`, `INT-003`에서 기대 `204` 대신 `500` 반환, `INT-018` redaction/validation assertion 실패.
  - Reproduction: 같은 명령 재실행 시 동일 4건 재현.
  - Unverified scope: 초기 실행 시점의 register/revoke API HTTP 204/idempotency/redaction 보증.
  - Resolution: `b47db09`에서 test-only AES/HMAC protector를 integration context에 wiring했고, 최종 device integration이 통과했다.

- Repository-sync gate: `./harness pr-ready --project-tests`
  - Error summary: `branch is behind origin/main; run ./harness sync first`
  - Reproduction: 명령 1회 실행으로 즉시 재현.
  - Unverified scope: `pr-ready`가 요구하는 project test summary와 sync 이후 diff state.
  - Residual risk: 현재 워크트리는 사용자 소유 미커밋 변경과 원격 선행 커밋이 있어, sync/rebase 없이는 최종 PR readiness를 증명할 수 없다.

- Supplemental environment-only failure: 병렬 보조 재실행
  - Error summary: `PushDeliveryLeaseIntegrationTest` XML 결과 파일 쓰기 충돌.
  - Reproduction: 동시에 두 개의 `integrationTest` 프로세스를 돌릴 때 발생.
  - Unverified scope: 없음. 승인 계약의 순차 실행 결과와 별개.
  - Residual risk: 없음. 최종 판정에는 포함하지 않는다.

## 6. Potential issues

### Application code

- 초기 구현에서 `NotificationController`의 device endpoint가 `ResponseEntity<Void>`를 반환했으나 `b47db09`에서 `ResponseEntity<ApiResponse<Void>>`로 보정했다.
- 초기 구현에서 `NotificationWebContractTest`가 신규 `PushDeviceService` 의존성을 반영하지 못했으나 `b47db09`에서 계약 테스트를 갱신했다.
- 초기 구현의 `UNIT-008` fingerprint 검증 불일치는 `b47db09`에서 네 번의 revoke 호출 계약에 맞게 보정했다.

### Infrastructure and resource limits

- scheduler 활성화는 `#182` 범위라 현재 브랜치만으로 자동 poll 운영을 켤 수 없다.
- 실제 Firebase credential, OAuth 토큰 발급, 모바일 OS 상태별 표시 동작은 아직 검증되지 않았다.

### Database and migrations

- schema migration 없이 진행했으며 `INT-019`는 테스트 환경 `EXPLAIN` 수준 근거만 제공한다. 운영 데이터량에서 row estimate와 실제 비용이 달라질 수 있다.
- 등록/해지 HTTP 경로는 최종 `PushDeviceRegistrationIntegrationTest`에서 204·멱등·redaction 계약을 통과했다.

### Concurrency and idempotency

- `INT-004`/`INT-005`, `INT-006`/`INT-007`은 통과해 동시 재등록, ownership transfer, lease reclaim, generation fencing은 별도 스위트에서 검증됐다.
- 반면 register/revoke API의 204/idempotency contract는 `INT-001`~`INT-003` 실패 때문에 HTTP 경계까지 완료됐다고 볼 수 없다.

### Transactions and event ordering

- `INT-016`이 provider 호출을 transaction 밖으로 분리한 점은 입증했다.
- provider가 수락한 뒤 DB terminal update 전에 프로세스가 crash하면 at-least-once 중복 발송 가능성이 남는다.

### External APIs

- fake FCM server 기준으로 `UNREGISTERED`, `INVALID_ARGUMENT`, `429`, `5xx`, timeout, auth failure 분류는 통과했다.
- 실제 FCM/Google OAuth와의 live integration, quota, credential rotation 실패 모드는 이번 범위 밖이다.

### Failure recovery and reconciliation

- lease-expired delivery reclaim과 stale generation 차단은 통과했다.
- key rotation rollback 시 이전 key 유지 기간과 제거 시점은 운영 runbook으로 남아 있고, 실제 롤백 drill은 이번에 수행하지 않았다.

## 7. Regression and residual risk

- FCM 수락 후 DB 반영 전 crash는 at-least-once 중복 push 가능성을 남긴다.
- 실제 Android/iOS foreground/background/종료/잠금 상태 동작은 미검증이다.
- scheduler `#182` 전에는 자동 poll이 활성화되지 않아 운영에서 수동/명시적 worker 실행 경계가 필요하다.
- `INT-019`는 테스트 데이터 기준 query plan만 확인했다. 운영 데이터량에서는 다른 plan이 나올 수 있다.
- key rotation rollback과 previous key 제거 조건은 문서 계약만 존재하고 실전 검증은 남아 있다.
- `pr-ready`는 기능·테스트 실패가 아니라 branch가 `origin/main`보다 뒤처진 sync 게이트에서 차단됐다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-179-TEST-PLAN-GH-179-PUSH-DELIVERY.md`
- CI run: 없음. 로컬/작업 트리 검증만 수행
- Related ADR: `TASK.md`의 `DEC-179-001`~`DEC-179-009`
- PR: 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [ ] 실행 결과와 PR 설명이 일치함 (PR 미생성)
