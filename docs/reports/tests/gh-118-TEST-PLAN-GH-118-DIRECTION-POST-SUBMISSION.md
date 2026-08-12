# Test Report: TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION

> Created at: `2026-08-12T14:30:29+09:00`
> GitHub Issue: `#118`
> Branch: `feat/gh-118-direction-post-submit`
> Commit: `094c248`

## 1. Executive summary

- Result: `PASS`
- Tested scope: 질문글 제출의 post·audience·matching Outbox 원자 기록, #115 fingerprint 멱등성, 동시 재시도, `match_round = 1`, 좌표 비노출, 제출 경로의 수신자·슬롯 미변경, Outbox 실패 rollback
- Unverified scope: 매칭 worker의 후보 재계산·수신자 확정·슬롯 예약, Outbox lease/claim, Controller, 인앱·외부 Push
- Release recommendation: #118 범위는 병합 검토 가능. worker와 lease는 #120/#119의 별도 검증 후 연결한다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | Gradle toolchain 21; local launcher Temurin 25.0.3 |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 |
| Database | Testcontainers PostgreSQL with PostGIS and Flyway migrations |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | ---: | --- | --- |
| `./gradlew test --no-build-cache --no-parallel --max-workers=1` | PASS | 245 | 4s | `build/test-results/test/` |
| `./gradlew clean integrationTest --rerun-tasks --no-build-cache --no-parallel --max-workers=1` | PASS | 238 | 2m 11s | 31 XML suites under `build/test-results/integrationTest/` |

모든 실행에서 failures, errors, skipped는 0건이었다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `DirectionPostSubmissionServiceTest.submissionDoesNotConfirmRecipients` | post·audience·Outbox만 저장하고 후보 조회를 호출하지 않음 |
| UNIT-002 | PASS | `DirectionPostSubmissionServiceTest.sameFingerprintRetryReturnsExistingSubmission` | 기존 post와 빈 수신자 목록 반환, 신규 저장 없음 |
| UNIT-003 | PASS | `DirectionPostSubmissionServiceTest.differentFingerprintReuseIsRejected` | `IDEMPOTENCY_KEY_REUSED`, 신규 쓰기 없음 |
| UNIT-004 | PASS | `DirectionPostSubmissionServiceTest.matchingEventUsesApprovedRoundAndSafePayload` | round 1·stable dedup·정확 좌표 비포함 |
| UNIT-005 | PASS | `DirectionPostSubmissionServiceTest.inactiveQuestionIsRejectedBeforeWrites`; `DirectionMatchingContractIntegrationTest.rollsBackPostAndAudienceWhenMatchingOutboxFails` | 사전 검증과 PostgreSQL rollback을 각각 확인 |
| INT-001 | PASS | `DirectionMatchingContractIntegrationTest.persistsRequestFingerprintAndRestoresIt`; direction persistence regressions | post·audience·matching Outbox 각 1행, recipient/receive state 0행 |
| INT-002 | PASS | `DirectionMatchingContractIntegrationTest.returnsSameResultAndRejectsDifferentFingerprint` | 동일 재시도는 row 수 유지, 다른 fingerprint는 충돌 |
| INT-003 | PASS | `concurrentSameFingerprintRequestsReturnOneLogicalResult`; `concurrentDifferentFingerprintRequestsRejectSecondLogicalIntent` | 동시 동일 요청 1 logical result, 상이 요청 1 성공·1 충돌 |
| INT-004 | PASS | `DirectionMatchingContractIntegrationTest.enforcesMatchingRoundUniquenessAndCoarsePayload` | round/dedup unique와 coarse-only payload 확인 |
| INT-005 | PASS | `DirectionMatchingContractIntegrationTest.rollsBackPostAndAudienceWhenMatchingOutboxFails` | Outbox trigger 실패 뒤 orphan 행 없음 |
| P1 regression | PASS | `DirectionPostDistanceBandIntegrationTest`, `DirectionPostgisPersistenceIntegrationTest`, `DirectionRecipientSelectionIntegrationTest`, `ReceiveStateReservationIntegrationTest`, `ReceiveSlotReleaseIntegrationTest` | #118 제출 경로가 후보·수신 슬롯을 동기 생성하지 않는 계약으로 회귀 정리 |

## 5. Failures and diagnostics

실패한 테스트는 없다. 전체 통합 실행은 초기 회귀 assertion 1건을 비동기 제출 계약에 맞게 수정한 뒤 재실행했고, 최종 실행은 238/238 PASS였다.

## 6. Potential issues

### Application code

- `SendResult.recipients`는 기존 호출 호환성을 위해 유지되지만 제출 단계에서는 항상 빈 목록이다. #120 worker 경계에서 확정 수신자를 별도 조회하거나 결과 계약을 정리해야 한다.

### Infrastructure and resource limits

- 로컬 Testcontainers 실행 결과만 검증했다. 운영 worker 처리량·재시도 지연·알림 provider 가용성은 검증 범위가 아니다.

### Database and migrations

- 새로운 migration은 추가하지 않았고 #115의 V12 계약을 사용했다. 실제 운영 schema drift는 배포 환경에서 별도 확인해야 한다.

### Concurrency and idempotency

- 동일/상이 fingerprint 동시 요청과 DB unique 최종 방어선을 검증했다. 다중 worker의 lease fencing은 #119 범위다.

### Transactions and event ordering

- post → audience → matching Outbox 순서와 Outbox 저장 실패 rollback을 실제 PostgreSQL에서 확인했다. 외부 발행 이후의 at-least-once 중복은 worker/consumer 검증 대상이다.

### External APIs

- Controller, in-app notification, external Push provider를 호출하지 않았다.

### Failure recovery and reconciliation

- Outbox 저장 실패 뒤 post·audience·Outbox 고아 행이 없음을 확인했다. 매칭 worker 장애 후 lease 만료 회수와 recipient reconciliation은 후속 Issue에서 검증한다.

## 7. Regression and residual risk

저장소 단위 245건과 통합 238건이 통과했다. 남은 위험은 #118이 의도적으로 제외한 worker/lease/push 경계이며, 제출 Outbox를 실제 소비해 수신자·슬롯을 만드는 흐름은 후속 구현이 연결되기 전까지 미검증이다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-118-TEST-PLAN-GH-118-DIRECTION-POST-SUBMISSION.md`
- CI run: 로컬 실행만 수행; CI URL 없음
- Related ADR: #115 matching fingerprint/lease contract
- PR: 생성하지 않음

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨
- [x] 실행 결과와 PR 설명이 일치함
