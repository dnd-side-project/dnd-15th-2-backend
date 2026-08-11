# Test Report: TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION

> Created at: `2026-08-10T23:22:49+09:00`
> GitHub Issue: `#97`
> Branch: `feat/gh-97-recipient-filter-limit-distribution`
> Commit: `not committed`

## 1. Executive summary

- Result: `PASS`
- Tested scope: #97 후보 필터, 공정 정렬, 발송별 최대 10명 설정, 슬롯 예약 실패 후순위 보충, 양방향 답변 열람 차단
- Unverified scope: 전체 integrationTest 안정 실행, 동시 send 시나리오, 실패 주입 rollback과 EXPLAIN planner 증거
- Release recommendation: Issue #97 완료 조건과 targeted/project readiness 검증은 통과했다. 동시 send·rollback·EXPLAIN은 후속 관측성 보강 대상으로 남긴다.

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | 21.0.12 |
| Spring Boot | 3.5.16 |
| Database | PostgreSQL/PostGIS Testcontainers (`postgis:16-3.5-alpine`) |
| Test runner | JUnit 5 / Gradle |
| Host note | ARM64 Docker에서 amd64 PostGIS image emulation 경고가 있었음 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew test --tests ...DirectionRecipientSelectionPropertiesTest --tests ...DirectionRecipientSelectionBoundaryTest` | PASS | 5 | <1s | Gradle BUILD SUCCESSFUL |
| `./gradlew test` | PASS | Gradle test task | <1s reported as up-to-date | Gradle BUILD SUCCESSFUL |
| `./gradlew integrationTest --tests ...DirectionRecipientSelectionIntegrationTest --tests ...PostAnswerQueryIntegrationTest` | PASS | 12 | 약 13s | Gradle BUILD SUCCESSFUL |
| `./harness test-run --id TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION` | PARTIAL | 201 total, 21 failed | 약 2m 37s | 기존 인증 통합 테스트에서 Flyway DB connection timeout |
| `./harness pr-ready --project-tests` | PASS | 전체 project tests | 약 1m 51s | Local PR readiness checks passed |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| ...-UNIT-001, UNIT-005 | PASS | `DirectionRecipientSelectionPropertiesTest` 및 `DirectionRecipientSelectionIntegrationTest` | 기본값 10, 시스템 내부 설정값 예외 없음, 사용자별 수신 상한과 발송별 상한의 분리 |
| ...-UNIT-002~004 | PASS | `DirectionRecipientSelectionBoundaryTest` | ACTIVE 계정, 양방향 block, fairness order, 설정 key 계약 |
| ...-INT-001 | PASS | `DirectionRecipientSelectionIntegrationTest.excludesInactiveAndBidirectionallyBlockedCandidates` | 양방향 active block·BLOCKED·DELETED·released block 검증 |
| ...-INT-002 | PASS | `DirectionRecipientSelectionIntegrationTest.sendStopsAfterTenSuccessfulReservations` | 최대 10명 확정 및 recipient count 검증 |
| ...-INT-003 | PASS | `DirectionRecipientSelectionIntegrationTest.ordersCandidatesByFairnessThenDistance` | 최근 수신 횟수·마지막 수신 시각·거리 순서 검증 |
| ...-INT-004 | PASS | `DirectionRecipientSelectionIntegrationTest` | Spring 설정 binding과 기본값 10 검증 |
| ...-INT-005 | PASS | `PostAnswerQueryIntegrationTest.blocksAnswerVisibilityInEitherBlockDirection` | sender↔viewer 양방향 차단 및 release 검증 |
| ...-INT-006 | PASS | `DirectionRecipientSelectionIntegrationTest.sendSkipsFullRecipientsWithoutConsumingPostLimit` | 슬롯 예약 실패 후보를 건너뛰고 후순위 후보 확정 |
| ...-INT-007 | NOT_RUN | EXPLAIN planner 시나리오 | 별도 실행 필요 |
| ...-INT-008 | NOT_RUN | 동시 send·rollback 시나리오 | 기존 #94 동시 예약 테스트와 별개로 추가 실행 필요 |

## 5. Failures and diagnostics

전체 하네스 integrationTest에서 기존 `DeviceAuthIntegrationTest`와
`OperatorLoginIntegrationTest` 등 21개 테스트가 Spring context 초기화 중
Flyway connection timeout으로 실패했다. 오류는 PostgreSQL Testcontainer 연결
획득 단계의 `SQL State 08001` 및 `SocketTimeoutException`이며, #97 테스트가
실패한 것이 아니다. #97 전용 통합 테스트와 `PostAnswerQueryIntegrationTest`는
같은 실행 환경에서 별도 호출 시 통과했다.

초기 #97 통합 실행에서는 테스트 fixture의 `DELETED` 계정에 `deleted_at`이
없고 발신자 presence가 누락되어 실패했다. 두 fixture 오류를 수정한 뒤 관련
통합 테스트는 통과했다.

## 6. Potential issues

### Application code

- 후보 SQL은 공정성 순으로 전체 후보를 조회한 뒤 서비스가 예약 성공자 10명에서
  순회를 멈춘다. 후보 풀이 매우 커지면 결과 집합과 정렬 비용이 증가할 수 있어
  INT-007 EXPLAIN 및 운영 metric 검증이 필요하다.
- 발송별 최대 10명과 사용자별 미처리 수신 상한 5개는 별도 설정으로 구현했다.

### Infrastructure and resource limits

- ARM64 Docker에서 amd64 PostGIS image emulation 경고가 있었다. 전체 suite에서
  컨테이너가 많아질 때 DB 연결 timeout 위험이 재현되었으므로 CI 자원과 container
  lifecycle을 확인해야 한다.

### Database and migrations

- Flyway migration과 schema/index는 변경하지 않았다.
- `user_account.status`, `user_block` 양방향 조건, `recipient_receive_state`
  정렬 join은 실제 PostgreSQL/PostGIS에서 실행 확인했다.

### Concurrency and idempotency

- 기존 `reserve()` 원자성 테스트는 별도 suite에 있으나, #97의 발송별 10명 제한과
  두 sender 동시 send를 결합한 INT-008은 미실행이다.

### Transactions and event ordering

- `send()`의 recipient 선정과 저장은 기존 transaction 경계 안에서 동작하지만,
  recipient insert 실패를 주입한 rollback 증거는 미확보이다.

### External APIs

- 외부 API 연동은 변경하지 않았고, 이 계획의 외부 API 시나리오는 해당 없음이다.

### Failure recovery and reconciliation

- 성공 경로에서 `post_recipient` 수와 예약 결과를 확인했다.
- 실패 후 `active_unhandled_count`와 실제 recipient 행의 대사를 자동 검증하는
  시나리오는 미실행이다.

## 7. Regression and residual risk

- 양방향 차단·비활성 계정·공정 정렬·최대 10명·답변 열람 차단은 targeted
  PostgreSQL/PostGIS 테스트로 통과했다.
- 첫 전체 integrationTest 실행은 환경성 DB connection timeout으로 PARTIAL이었으나,
  후속 `pr-ready --project-tests`에서 전체 project tests가 통과했다.
- 후보 수가 수천 명 이상인 운영 상황의 정렬·DB 비용과 설정값 변경 효과는 아직
  운영 metric이 없어 검증되지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-97-TEST-PLAN-GH-97-RECIPIENT-FILTER-LIMIT-DISTRIBUTION.md`
- Test report: this file
- CI run: not created
- Related ADR: existing direction communication data-model/permission documents; no new ADR
- PR: not created

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨
- [ ] 잠재 문제에 후속 GitHub Issue가 연결됨
- [x] 실행 결과와 현재 변경 상태가 일치함
