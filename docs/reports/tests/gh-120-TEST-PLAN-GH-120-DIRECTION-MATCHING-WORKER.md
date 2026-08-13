# Test Report: TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER

> Created at: `2026-08-13T17:22:51+09:00`
> GitHub Issue: `#120`
> Branch: `feat/gh-120-direction-matching-worker`
> Commit: `01fc92c`

## 1. Executive summary

- Result: `PASS`
- Tested scope: #120 matching worker, PostgreSQL/PostGIS selection, moderation/deadline gates,
  receive-state locking, Outbox fencing, rollback, replay and concurrent workers.
- Unverified scope: scheduler/polling/production activation and #137 moderation provider flow;
  these remain intentionally outside #120.
- Release recommendation: #120 implementation is ready for review. Production activation remains
  gated on #137 and the caller that supplies the batch command.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Gradle | 8.14.3 |
| Database | PostgreSQL 16.14 with PostGIS in Testcontainers |
| Test runner | JUnit 5 |
| Testcontainers | 1.21.4 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --rerun-tasks` | PASS | 274 passed, 0 failed | 5s | Gradle test task |
| `./gradlew integrationTest --tests com.dnd.qello.DirectionMatchingWorkerIntegrationTest` | PASS | 15 passed | 8s | worker integration XML |
| `./gradlew integrationTest --tests com.dnd.qello.DirectionMatchingWorkerConcurrencyIntegrationTest` | PASS | 3 passed | 8s | concurrency integration XML |
| #118/#119 + spatial regressions | PASS | 26 passed | 22s | `DirectionMatchingContractIntegrationTest`, `OutboxLeaseIntegrationTest`, `DirectionRecipientSelectionIntegrationTest`, `DirectionPostgisPersistenceIntegrationTest` |
| `./harness check` | PASS | policy gates passed | <1s | harness output |
| `./harness pr-ready --project-tests` | PASS | 260 integration + 274 unit | 2m22s | local PR readiness output |
| `npm run hooks:validate` / `git diff --check` | PASS | — | <1s | command exit status |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| INT-001~009, INT-013~015 | PASS | `DirectionMatchingWorkerIntegrationTest` (15 methods) | Matching, spatial boundary, fairness/limit, state init/replay, moderation, deadline, zero candidates, rollback, batch isolation, privacy |
| INT-010~012 | PASS | `DirectionMatchingWorkerConcurrencyIntegrationTest` (3 methods) | Same-event claim, common last slot, stale lease fencing |
| UNIT-001~003 | PASS | `DirectionPostMatchingTest` (4 methods) | ACTIVE gate, deadline A, moderation fail-closed |
| UNIT-004~007 | PASS | `DirectionMatchingWorkerTest` (5 methods) | Claim type, command validation, retryable/permanent/stale classification |
| #118/#119 regression | PASS | Existing contract/lease suites | Submission boundary, coarse payload, round uniqueness, lease generation and retry behavior |

## 5. Failures and diagnostics

초기 집중 실행에서 두 구현/fixture 문제가 재현됐다.

1. 후보 수가 `maxRecipients`보다 적을 때 `subList` 종료 인덱스를 상한으로 사용해
   `IndexOutOfBoundsException`이 발생했다. `min(maxRecipients * 3, candidates.size())`로
   수정했고 0명·1명·다수 후보 통합 테스트가 통과했다.
2. wrap-around fixture의 sender presence가 게시글 `submittedAt`보다 늦고, 최소거리
   경계 fixture가 PostGIS 부동소수 오차에 걸렸다. sender 시각과 fixture 거리를 조정했다.

최종 실행에서 재현 가능한 실패는 없다.

## 6. Potential issues

### Application code

- Worker는 scheduler/polling을 포함하지 않으며 `BatchCommand` 호출 경계를 요구한다.
- `SKIP LOCKED`로 인해 동시 경합 시 발송별 목표 인원보다 적게 채워질 수 있다. 추가
  매칭 라운드는 #120 범위에 포함하지 않았다.

### Infrastructure and resource limits

- Testcontainers의 PostGIS 이미지는 로컬 arm64에서 amd64 에뮬레이션으로 실행됐다.
  집중 실행은 통과했지만 전체 `pr-ready`는 2분 22초가 걸렸다.

### Database and migrations

- 신규 Flyway migration/index는 추가하지 않았다. 기존 V12 Outbox lease/round unique,
  recipient unique/check 제약을 재사용했다.

### Concurrency and idempotency

- 같은 event claim은 한 worker만 점유했고, 공통 마지막 slot은 한 번만 예약됐다.
- stale worker의 domain transaction은 source complete fencing 실패와 함께 rollback됐다.

### Transactions and event ordering

- post lock → 실행 시점 후보 재계산 → receive-state lock → recipient/state/confirmed
  Outbox → ACTIVE → source complete 순서가 통합 테스트에서 확인됐다.
- confirmed Outbox 삽입 trigger 실패 시 partial write가 남지 않았다.

### External APIs

- 외부 moderation·Push·Notification API 호출은 없다. #137은 moderation 상태 공급자,
  #123은 confirmed Outbox 소비자로 남겨두었다.

### Failure recovery and reconciliation

- PENDING/REVIEW_HELD는 retryable, 손상 event/없는 post는 permanent DEAD, stale lease는
  별도 회수 결과로 분류됐다.
- 실패 trigger 후 recipient, active/recent count, confirmed Outbox, post ACTIVE가 모두
  0/원상태로 복구됐다.

## 7. Regression and residual risk

- PASS: 전체 단위 274개, 전체 통합 260개.
- 실제 운영 scheduler identity·lease/retry 숫자와 #137 production activation은 미결정/제외다.
- #127 합성 대규모 데이터 EXPLAIN·처리량 검증은 이번 Issue에서 실행하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-120-TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER.md`
- CI run: local `./harness pr-ready --project-tests` PASS
- Related ADR: #120 설계 문서의 matching worker transaction/fencing 계약
- PR: not created (별도 승인 필요)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨
- [x] 실행 결과와 PR 설명이 일치함
