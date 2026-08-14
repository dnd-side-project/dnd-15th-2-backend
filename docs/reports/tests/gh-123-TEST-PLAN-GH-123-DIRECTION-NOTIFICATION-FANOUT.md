# Test Report: TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT

> Created at: `2026-08-14T18:16:02+09:00`
> GitHub Issue: `#123`
> Branch: `feat/gh-123-direction-notification-fanout`
> Base commit: `a8e307d` (implementation changes are currently uncommitted)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 승인된 #123 persistence, worker, PostgreSQL transaction, retry, privacy,
  GLOBAL handoff와 concurrency 시나리오 및 기존 #119/#120/#121 회귀
- Unverified scope: FCM/APNs 호출, 실제 Push dispatch, scheduler activation, #127 대규모 성능
- Release recommendation: 코드 리뷰와 목적별 커밋 후 PR 검토 가능

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | Temurin OpenJDK 25.0.3 LTS |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL/PostGIS Testcontainers; 로컬 Docker 환경 |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests RecipientNotificationFanOutWorkerTest --tests DirectionMatchingWorkerTest --max-workers=1 --no-daemon --rerun-tasks` | PASS | 37 | 약 7초 | `docs/reports/tests/gh-123-TEST-EVIDENCE.md`, Gradle XML 2개 |
| 관련 integrationTest 묶음(신규 persistence/fan-out/concurrency + #119/#120/#121 회귀) | PASS | 93 | 약 41초 | `docs/reports/tests/gh-123-TEST-EVIDENCE.md`, Gradle XML 7개 |
| `./harness test-run --id TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT` | PASS | 700 (최종 XML 합계; 이전 clean run 699) | 약 2분 35초 | `docs/reports/tests/gh-123-TEST-EVIDENCE.md`, Gradle XML aggregate |
| `./harness pr-ready --project-tests` | PASS | 프로젝트 검사 | 약 1초 | Local PR readiness checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001~010 | PASS | `RecipientNotificationFanOutWorkerTest` | claim/filter, 권위 aggregate, eligibility, dedup, retry/dead/stale, malformed lease fencing, Clock |
| INT-001~012, INT-017~019, INT-024~025, INT-027~029 | PASS | `RecipientNotificationFanOutWorkerIntegrationTest` | PostgreSQL fan-out, suppression, rollback, batch isolation, privacy, GLOBAL handoff, snapshot |
| INT-013~016, INT-026 | PASS | `RecipientNotificationFanOutWorkerConcurrencyIntegrationTest` | claim 경합, stale lease, logical dedup, commit ordering, device snapshot |
| INT-020~023 | PASS | `NotificationFanOutPersistenceIntegrationTest` | preference default, ACTIVE device, insert-if-absent, PostRecipient lock |
| #119/#120/#121 regression | PASS | `OutboxLeaseIntegrationTest`, `DirectionMatchingWorker*`, `AnswerSafetyNotificationPersistenceIntegrationTest` | 기존 lease/matching/safety 경계 보존 |

## 5. Failures and diagnostics

최종 실행에서 재현 가능한 제품·환경 실패는 없었다. 통합 테스트 초기 실행에서
fixture가 source event를 중복 생성해 일부 테스트가 실패했으나 fixture를 수정한 뒤
동일 명령을 재실행해 40개 전부 통과했다. 초기 실패는 제품 코드 실패로 보고하지 않는다.
최종 lease-fencing 보완 후 `harness test-run`을 재실행했을 때도 unit/integration Gradle
작업은 성공했으며, 이미 존재하는 완료 보고서를 덮어쓰지 않는 scaffold guard 때문에
wrapper만 exit 2를 반환했다. 이는 테스트 실패가 아니다.

## 6. Potential issues

### Application code

- 최종 관련 테스트에서 재현 가능한 application code failure는 없었다. Provider dispatch
  직전 PushDevice 재검증은 후속 worker 경계로 남아 있다.

### Infrastructure and resource limits

- 테스트 컨테이너는 로컬 Docker의 amd64 PostGIS 이미지를 arm64 환경에서 에뮬레이션해
  실행했으므로 느린 CI 호스트에서는 timeout 여유가 필요하다.

### Database and migrations

- 신규 migration은 없고 기존 unique/FK/check 제약을 사용했다. 전체 Flyway/관련 회귀는
  `harness test-run`에서 통과했다.

### Concurrency and idempotency

- 동일 source claim, stale lease generation, 서로 다른 source logical dedup과
  PostRecipient lock ordering을 검증했다. device revoke가 active-device 조회 뒤
  commit되면 PENDING Delivery가 남을 수 있으며 실제 dispatch 직전 재검증이 후속 경계다.

### Transactions and event ordering

- Notification/Delivery 생성과 fenced source complete는 event transaction에 묶였다.
  complete 0행·insert failure는 domain write를 rollback하고 retry/DEAD로 분류했다.

### External APIs

- Provider mock/실제 FCM/APNs 호출은 범위 밖이며 실행하지 않았다.

### Failure recovery and reconciliation

- retryable/permanent/stale lease와 partial fan-out 보충을 검증했다. 운영 reconciliation
  job과 scheduler activation은 별도 작업으로 남아 있다.

## 7. Regression and residual risk

- Provider dispatch와 #127 대규모 성능(EXPLAIN/10,000명)은 미실행이며, PR에 잔여 위험으로
  명시해야 한다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-123-TEST-PLAN-GH-123-DIRECTION-NOTIFICATION-FANOUT.md`
- Durable command/test evidence: `docs/reports/tests/gh-123-TEST-EVIDENCE.md`
- Gradle XML evidence: `build/test-results/test/` and `build/test-results/integrationTest/` (paths enumerated in the evidence file)
- CI run: 로컬 검증 완료; 원격 CI는 PR 생성 후 확인
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨 — Provider/#127 후속 Issue 번호 미확정
- [x] 실행 결과와 PR 설명이 일치함
