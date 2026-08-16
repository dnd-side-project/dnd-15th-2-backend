# Test Plan: TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION

> Created at: `2026-08-15T23:46:33+09:00`
> GitHub Issue: `#109`
> Status: Approved

## 1. Objective

OpenAI moderation snapshot 단위 장애를 개별 `FilterJob` 실패와 분리해 판단하고,
운영자 승인이 있을 때만 사전 검증된(pre-approved) release로 emergency
migration하는 안전장치를 검증한다.

- snapshot 단위 health 판정이 개별 job 실패가 아니라 실사용자와 분리된
  synthetic probe 결과로만 이루어진다(신설 `INV-HLT-001`). 깨지면 일시적인
  job 실패(네트워크 잡음, 개별 콘텐츠 이슈)만으로 snapshot 전체가 장애로
  오판정될 수 있다.
- 429/5xx/timeout/network 같은 재시도 가능 실패나 분류 불가능한 오류가 자동으로
  영구 장애(`PERMANENT_CONFIRMED`)를 만들지 않는다(`INV-HLT-002`, `INV-HLT-004`,
  이슈 본문 완료 조건 1번). 깨지면 일시적 공급자 부하로도 정상 snapshot이
  자동 폐기될 수 있다.
- 인증·권한·결제·quota·invalid request 같은 사용자(우리) 측 설정 오류가
  target snapshot 자체의 장애 증거로 절대 집계되지 않는다(`INV-HLT-003`,
  이슈 본문 완료 조건 2번). 깨지면 우리 쪽 API 키·과금 문제를 OpenAI snapshot
  장애로 오분류해 불필요한 emergency migration을 유발할 수 있다.
- 운영자의 명시적 확인 없이는 어떤 자동 경로도 `PERMANENT_CONFIRMED`에
  도달하거나 emergency migration을 실행할 수 없다(`INV-HLT-005`, 이슈 본문
  완료 조건 3번). 깨지면 일시적 장애만으로 시스템이 스스로 release를 바꿔
  검증되지 않은 snapshot으로 실사용자 트래픽을 이관할 수 있다.
- emergency migration 이후 이전 generation에서 뒤늦게 도착한 결과가 job
  상태를 바꾸지 못한다(`INV-REL-010`, 이슈 본문 완료 조건 4번 — 이미
  `FilterJob.recordAutomatedAttempt`/`applyAutomatedDecision`의
  `STALE_ATTEMPT_GENERATION` 검사가 존재하며 이 이슈가 실제로 그 fencing을
  소비하는 첫 호출자가 된다). 깨지면 장애 snapshot에 발행됐던 낡은 판정이
  migration 후에도 뒤늦게 적용돼 최신 상태를 덮어쓸 수 있다.

## 2. Scope

### Included

- `ModerationFailureClassification`(신규 enum): HTTP status와 OpenAI 응답의
  `error.code`를 근거로 `RATE_LIMITED`/`SERVER_ERROR`/`TIMEOUT_OR_NETWORK`/
  `NON_TARGET_CLIENT_ERROR`/`UNKNOWN`으로 분류. `OpenAiModerationProviderClient`/
  `OpenAiModerationResponseMapper` 확장 — 기존 429 처리
  (`ModerationRateLimitedException`, `#108`)와 `FLT-EXT-001`
  (`MODERATION_PROVIDER_UNAVAILABLE`) 계약은 그대로 유지하고 분류 정보만
  추가로 노출한다.
- `SnapshotHealth`(신규 도메인, `modelSnapshot` 문자열 keyed): `HEALTHY`/
  `DEGRADED`/`PERMANENT_SUSPECTED`/`PERMANENT_CONFIRMED`/`RECOVERED` 상태.
  전이는 synthetic probe 기록으로만 발생한다.
- Target/control synthetic probe 기록 서비스 메서드: 실사용자 요청 경로와
  분리된 별도 진입점. control probe가 실패하면 공급자 전역 장애로 간주해
  해당 회차를 target snapshot 자체의 실패 증거로 집계하지 않는다.
- 실패 분류별 반영 규칙: `NON_TARGET_CLIENT_ERROR`·`UNKNOWN`은 어떤 경우에도
  `PERMANENT_SUSPECTED` 후보 증거로 집계되지 않는다. target-only
  `SERVER_ERROR`/`TIMEOUT_OR_NETWORK`의 지속(횟수·기간, 정확한 임계값은
  주입 값)만 증거로 누적된다.
- Evidence 보관: 공식 공지 여부(운영자 수기 플래그), target-only 실패
  지속 기간·횟수, recovery 신호(target probe 재성공 시 즉시 `HEALTHY`로
  복귀하며 누적 증거 초기화).
- `PERMANENT_CONFIRMED` 운영자 승인: `FilterReleaseRegistryService.promote`/
  `rollback`과 동일한 패턴(`operatorUserId` 필수, append-only 감사 이력
  테이블)으로 `SnapshotHealthService`가 `PERMANENT_SUSPECTED` →
  `PERMANENT_CONFIRMED` 전이를 운영자 호출로만 허용. 이 전이를 노출하는
  REST endpoint 1개(권한 세부는 기존 인증 인프라의 로그인 사용자 식별만
  재사용 — 세부 role 정책은 이슈가 미결정으로 남긴 영역).
- Emergency migration: `PERMANENT_CONFIRMED` snapshot과 status가 `CANARY`
  또는 `ROLLED_BACK`인 대상 release가 모두 확인될 때만 실행 가능.
  - `FilterJob.migrateToRelease(long newFilterReleaseId, Instant now)`(신규
    도메인 메서드): `AUTOMATED` 상태에서만 허용하며 `filterReleaseId` 재배정과
    `attemptGeneration + 1`을 한 번의 전이로 처리한다. 기존
    `recordAutomatedAttempt`/`applyAutomatedDecision`의
    `STALE_ATTEMPT_GENERATION` 검사가 그대로 상속되어 `INV-REL-010`을
    보장한다(worker 코드 변경 없음 — `AnswerModerationExecutionWorker`는
    이미 매 처리마다 `job.filterReleaseId()`로 release를 다시 조회한다).
  - 대상 release는 기존 `promote()`/`rePromote()` 경로로 승격한다(새 전이
    로직 추가 없음).
  - 영향받는 모든 `AUTOMATED` job을 원자적으로(단일 트랜잭션) 이관한다.
    `RESOLVED`/`RETRY_EXHAUSTED`/`MANUAL_REVIEW_REQUIRED` job은 건드리지
    않는다.
  - 원본 release·대상 release·snapshot health id·operator·시각을 별도
    감사 테이블(`snapshot_emergency_migration_history`)에 기록해 lineage를
    보존한다. 살아있는 `FilterJob.filterReleaseId` 필드 자체는 이관 후
    새 release를 가리키도록 바뀐다 — 과거 이력은 감사 테이블이 전담한다.
- DB migration(V15): `snapshot_health`, `snapshot_health_probe_result`,
  `snapshot_emergency_migration_history` 테이블.
- 단위·PostgreSQL 통합(동시성 포함) 테스트와 테스트 보고서.

### Excluded

- 상태별 window, threshold, probe cadence, alert 정책의 실제 운영 수치 —
  전부 미결정이며 생성자/설정 주입 값으로만 존재한다(이슈 본문 "제외").
- 운영자 승인 endpoint의 세부 권한(role) 정책 — 이슈 본문이 미결정으로
  명시.
- synthetic probe를 실제로 주기적으로 실행하는 scheduler와 Spring 배선 —
  `#105`~`#108`과 동일하게 `#113` production gate로 이연.
- 공식 공지 수집을 위한 외부 연동(RSS, 웹훅 등) — 운영자가 boolean/timestamp로
  직접 기록하는 수기 플래그만 다룬다.
- `PERMANENT_CONFIRMED`에서 다시 `HEALTHY`/`PERMANENT_SUSPECTED`로 되돌리는
  운영자 번복(un-confirm) 경로 — 이슈 범위 밖.
- emergency migration의 역방향(migration 이후 원래 release로 되돌리는 것) —
  기존 `FilterReleaseRegistryService.rollback()`을 별도 운영자 조작으로
  재사용할 수 있으나 이 이슈가 새로 다루지 않는다.
- Slack/알림 연동 — `#111` 범위.
- `ManualReviewCase` 우선순위/band — `#110` 범위.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #109 | 429/5xx/timeout/network/알 수 없는 오류가 자동 영구 장애를 만들지 않는다 (`INV-HLT-002/003/004`) |
| GitHub Issue #109 | 인증·권한·결제·quota·invalid request가 영구 snapshot 폐기로 오분류되지 않는다 (`INV-HLT-003`) |
| GitHub Issue #109 | 운영자 확인 없는 emergency migration이 불가능하다 (`INV-HLT-005`) |
| GitHub Issue #109 | 이전 generation의 늦은 결과가 상태를 바꾸지 못한다 (`INV-REL-010`) |
| 기존 코드: `FilterJob.advanceAttemptGeneration` doc | "emergency migration으로 세대를 올린다... 이전 세대의 진행 중이던 attempt는 이 시점부터 전부 비권위가 된다" — #106/#107이 정의만 하고 호출자가 없던 hook |
| 기존 코드: `AnswerModerationExecutionWorker.applyVerdict` 주석 | `payload.attemptGeneration()` vs job 현재 세대 비교가 "emergency migration으로 세대가 넘어간 뒤 도착한 낡은 결과"를 걸러내기 위해 이미 설계됨 |
| 기존 코드: `FilterReleaseRegistryService.promote/rollback` | 운영자 승인 필수 상태 전이의 기존 패턴(`operatorUserId`, 감사 이력) |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 사용자 측 오류(인증/quota)가 target 장애로 오분류돼 불필요한 emergency migration 유발 | High — 검증되지 않은 release로 실트래픽 이관 | Medium | P0 | UNIT: NON_TARGET_CLIENT_ERROR가 증거로 미집계 |
| control probe도 실패하는 공급자 전역 장애를 snapshot 고유 장애로 오판 | High — 정상 snapshot을 폐기 | Medium | P0 | UNIT: target-only 조건 강제 |
| 운영자 승인 없이 자동으로 `PERMANENT_CONFIRMED` 도달 | Critical — 승인 게이트 무력화 | Low(설계상 차단 목표) | P0 | UNIT+구조: `PERMANENT_CONFIRMED`로 가는 유일한 경로가 명시적 서비스 호출 |
| emergency migration 중 일부 job만 이관되고 트랜잭션 실패 | High — release 매핑 불일치 상태 | Low | P0 | INT: 원자성(all-or-nothing) 검증 |
| migration 이후 낡은 generation의 pipeline 결과가 새 상태를 덮어씀 | High — 오판정 유실 | Medium | P0 | UNIT+INT: `STALE_ATTEMPT_GENERATION` fencing |
| 동시 probe 기록이 SnapshotHealth 행 갱신을 유실 | Medium — 증거 카운트 오차 | Medium | P1 | INT: `FOR UPDATE` 직렬화 |
| 동일 snapshot에 대한 emergency migration 중복 실행 | Medium — 이미 이관된 job 재이관 시도 | Low | P1 | INT: 멱등/중복 실행 가드 |
| pre-approved 대상 release가 실제로는 CANARY/ROLLED_BACK이 아닌데 이관 허용 | High — 미검증 release로 이관 | Low | P0 | UNIT: 상태 가드 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | OpenAI 401/403 응답(auth/permission) | 클라이언트가 실패를 분류 | `NON_TARGET_CLIENT_ERROR` 반환 | P0 | Feature executor |
| UNIT-002 | OpenAI 402 또는 `insufficient_quota` error code 응답 | 분류 | `NON_TARGET_CLIENT_ERROR` 반환 | P0 | Feature executor |
| UNIT-003 | OpenAI 400 `invalid_request_error` 응답 | 분류 | `NON_TARGET_CLIENT_ERROR` 반환 | P0 | Feature executor |
| UNIT-004 | OpenAI 500/502/503/504 응답 | 분류 | `SERVER_ERROR` 반환 | P0 | Feature executor |
| UNIT-005 | 커넥션 timeout/network 예외 | 분류 | `TIMEOUT_OR_NETWORK` 반환 | P0 | Feature executor |
| UNIT-006 | 429 응답(기존 `ModerationRateLimitedException` 경로) | 분류 | 기존 `RATE_LIMITED` 계약이 그대로 유지됨(회귀 없음) | P0 | Feature executor |
| UNIT-007 | 알 수 없는/파싱 불가 오류 body | 분류 | `UNKNOWN` 반환 | P0 | Feature executor |
| UNIT-008 | `HEALTHY` 상태 SnapshotHealth | target probe 성공 기록 | 상태 유지, 증거 미누적 | P1 | Feature executor |
| UNIT-009 | `HEALTHY` 상태, target probe `SERVER_ERROR` + control probe 성공 | 기록 | target-only 실패로 1건 누적 | P0 | Feature executor |
| UNIT-010 | target probe `SERVER_ERROR` + control probe도 `SERVER_ERROR` | 기록 | 공급자 전역 장애로 간주, target-only 증거 미누적(`INV-HLT-006`) | P0 | Feature executor |
| UNIT-011 | target probe `NON_TARGET_CLIENT_ERROR` 반복 기록 | 기록 | 어떤 반복 횟수에서도 `PERMANENT_SUSPECTED` 후보 증거로 미집계(`INV-HLT-003`) | P0 | Feature executor |
| UNIT-012 | target probe `UNKNOWN` 반복 기록 | 기록 | 증거로 미집계, 자동 영구 장애 미생성(`INV-HLT-004`) | P0 | Feature executor |
| UNIT-013 | target-only `SERVER_ERROR`/`TIMEOUT_OR_NETWORK`가 주입된 threshold(횟수·기간) 도달 | 기록 | `PERMANENT_SUSPECTED`로 전이 | P0 | Feature executor |
| UNIT-014 | `PERMANENT_SUSPECTED` 상태에서 target probe 성공 | recovery 기록 | `HEALTHY`로 복귀, 누적 증거 초기화(`INV-HLT-007`) | P1 | Feature executor |
| UNIT-015 | `PERMANENT_SUSPECTED` 상태 | `operatorUserId`로 confirm 호출 | `PERMANENT_CONFIRMED` 전이, 감사 이력 기록(`INV-HLT-005`) | P0 | Feature executor |
| UNIT-016 | `HEALTHY`/`DEGRADED` 상태(아직 SUSPECTED 아님) | confirm 호출 | 거절(`InvalidSnapshotHealthStatus` 류 예외) — SUSPECTED를 우회해 바로 CONFIRMED로 갈 수 없음 | P0 | Feature executor |
| UNIT-017 | 정적 분석: `SnapshotHealth` 공개 API 전수 | `PERMANENT_CONFIRMED`로 전이하는 메서드 목록 확인 | 운영자 confirm 메서드 외에는 존재하지 않음(자동 경로 없음, `INV-HLT-005`) | P0 | Feature executor |
| UNIT-018 | `AUTOMATED` 상태 `FilterJob`, 새 releaseId | `migrateToRelease(newReleaseId, now)` 호출 | `filterReleaseId` 갱신, `attemptGeneration + 1`, status는 `AUTOMATED` 유지 | P0 | Feature executor |
| UNIT-019 | `RESOLVED`/`MANUAL_REVIEW_REQUIRED`/`RETRY_EXHAUSTED` 상태 `FilterJob` | `migrateToRelease` 호출 | `INVALID_JOB_STATUS`로 거절 | P0 | Feature executor |
| UNIT-020 | migration 전 attemptGeneration(N)으로 도착한 낡은 결과, job은 이미 migration으로 N+1 | `applyAutomatedDecision(N, ...)` 호출 | `STALE_ATTEMPT_GENERATION` 거절(`INV-REL-010`) | P0 | Feature executor |
| UNIT-021 | snapshot이 `PERMANENT_SUSPECTED`(아직 CONFIRMED 아님) | emergency migration 서비스 호출 | 거절 | P0 | Feature executor |
| UNIT-022 | `PERMANENT_CONFIRMED` snapshot, 대상 release status가 `CANDIDATE`/`SHADOW`/`PROMOTED` | emergency migration 호출 | 거절(`CANARY`/`ROLLED_BACK`만 허용) | P0 | Feature executor |
| UNIT-023 | `PERMANENT_CONFIRMED` snapshot, 대상 release가 source와 동일 | emergency migration 호출 | 거절(no-op 가드) | P1 | Feature executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | `SnapshotHealthService`, PostgreSQL | 동일 snapshot에 target probe 2건을 별도 스레드에서 동시 기록 | 동시 실행 | 두 건 모두 반영(유실 없음), `FOR UPDATE` 직렬화 확인 | 테스트 트랜잭션/스키마 정리 |
| INT-002 | `SnapshotHealthEmergencyMigrationService`, `FilterJobRepository`, PostgreSQL | source release에 `AUTOMATED` job 5개, `RESOLVED` job 2개 존재, snapshot `PERMANENT_CONFIRMED`, 대상 release `CANARY` | emergency migration 실행 | `AUTOMATED` 5개만 새 releaseId로 이관 + generation+1, `RESOLVED` 2개는 미변경, 감사 이력 1건 생성 | 테스트 데이터 정리 |
| INT-003 | 위와 동일, 마이그레이션 중간에 강제 오류(rollback 유도) | 트랜잭션 실패 시나리오 | 부분 이관 없이 전체 rollback(원자성) | 정리 |
| INT-004 | `SnapshotHealthEmergencyMigrationService`, PostgreSQL | 동일 source snapshot에 대해 emergency migration을 동시에 2회 호출 | 동시 실행 | 한 번만 성공하거나 멱등하게 수렴, job이 이중 이관(generation이 2 이상 증가)되지 않음 | 정리 |
| INT-005 | `AnswerModerationExecutionWorker`, `SnapshotHealthEmergencyMigrationService`, PostgreSQL | migration으로 job의 filterReleaseId가 새 release로 변경된 상태 | worker가 이 job을 다음 배치에서 처리 | worker 코드 변경 없이 새 release의 modelSnapshot으로 pipeline 호출(`callPipelineBounded`의 기존 `findById(job.filterReleaseId())` 재사용 확인) | 정리 |
| INT-006 | `AnswerModerationExecutionWorker`, PostgreSQL | migration 이전 generation으로 이미 dispatch된 pipeline 결과가 migration 이후 뒤늦게 도착 | 결과 적용 시도 | `STALE_ATTEMPT_GENERATION`으로 거절, job 상태 불변(`INV-REL-010`) | 정리 |
| INT-007 | 운영자 승인 REST endpoint, PostgreSQL | `PERMANENT_SUSPECTED` snapshot 존재 | 인증된 운영자 요청으로 confirm 호출 | 200과 함께 `PERMANENT_CONFIRMED` 반영, 감사 이력에 operator/시각 기록 | 정리 |
| INT-008 | 운영자 승인 REST endpoint | 미인증 요청 | confirm 호출 | 401/403 거절, 상태 미변경 | 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- emergency migration의 영향받는 job 전체 이관 + 감사 이력 insert는 단일
  트랜잭션으로 묶여 all-or-nothing이어야 한다(INT-002, INT-003).
- `snapshot_health` 행 갱신은 `SELECT ... FOR UPDATE`로 직렬화한다
  (`FilterReleaseRetryGate`, `#108`과 동일 패턴, INT-001).

### Concurrency and idempotency

- 동시 probe 기록(INT-001), 동시 emergency migration 호출(INT-004)이
  카운트 유실이나 이중 이관을 만들지 않는다.
- 운영자 confirm 호출의 중복(같은 operator가 실수로 두 번 클릭)도 멱등하게
  처리한다 — UNIT 레벨에서 `PERMANENT_CONFIRMED` 상태에 다시 confirm 호출 시
  거절 또는 멱등 반환 여부를 결정하고 테스트로 고정한다(구현 시 확정).

### External APIs

- OpenAI 응답 분류는 실제 네트워크 호출 없이 mock/stub `RestClient` 또는
  `HttpClientErrorException`/`HttpServerErrorException` fixture로만
  검증한다(UNIT-001~007). 실제 OpenAI 호출은 이 이슈 범위에서 발생시키지
  않는다.

### Failure recovery and reconciliation

- migration 도중 프로세스 크래시 시 부분 반영이 없어야 한다(INT-003, 트랜잭션
  경계로 보장).
- migration 이후 늦게 도착하는 결과의 안전한 거절(INT-006)이 재처리 유실로
  이어지지 않는지 확인한다 — 거절된 이벤트가 outbox 상 어떻게 종결되는지는
  기존 `isJobStateRace` 패턴(`#107`/`#108`)을 재사용해 명시한다.

## 8. Test data and isolation

- Fixtures: `FilterRelease`(다양한 status 조합), `FilterJob`(AUTOMATED/RESOLVED/
  MANUAL_REVIEW_REQUIRED/RETRY_EXHAUSTED 혼합), `SnapshotHealth`(HEALTHY/
  PERMANENT_SUSPECTED/PERMANENT_CONFIRMED 각 상태) 빌더.
- Database isolation: 기존 `#108` 통합 테스트와 동일하게 Testcontainers
  PostgreSQL, 클래스별 격리된 Spring context.
- Clock/randomness: 고정 `Clock`(`Instant.now(clock)` 주입) 재사용 — 실제
  wall-clock 의존 없음.
- External API doubles: `RestClient`/`ModerationProviderClient`는 mock 또는
  경량 stub만 사용. 실제 OpenAI 자격 증명은 어디에도 사용하지 않는다.
- Cleanup: 각 통합 테스트가 자신이 만든 `filter_release`/`filter_job`/
  `snapshot_health`/`snapshot_emergency_migration_history` 행을 트랜잭션
  롤백 또는 명시적 삭제로 정리한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `src/main/resources/db/migration/V15__*.sql`, `ModerationFailureClassification`, `SnapshotHealth`, `SnapshotHealthPolicy`, `SnapshotHealthProbeResult`, `SnapshotEmergencyMigrationHistoryEntry`(도메인), `FilterJob.migrateToRelease`, repository 계층, `SnapshotHealthService`, `SnapshotHealthEmergencyMigrationService`, 운영자 승인 REST endpoint, `OpenAiModerationProviderClient`/`OpenAiModerationResponseMapper` 확장 | UNIT-001~023, INT-001~008 | `INV-HLT-001`~`007` 및 `INV-REL-010` 검증, 기존 `#107`/`#108`/`FilterRelease` 승격 계약과의 호환성 리뷰 |

## 10. Completion criteria

- [x] 모든 P0 시나리오 구현
- [x] 모든 테스트 메서드에 `@DisplayName`
- [x] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [x] 단위 테스트 통과
- [x] 통합 테스트 통과
- [x] 잠재 문제 분석
- [x] 테스트 보고서 생성 —
      `docs/reports/tests/gh-109-TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION.md`

## 11. Human approval

- Reviewer: tkv00
- Decision: Approved
- Approved at: `2026-08-15T23:59:00+09:00`
