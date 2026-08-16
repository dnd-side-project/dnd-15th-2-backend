# GitHub Issue #109 Task Contract

> Generated at: `2026-08-15T21:26:19+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Snapshot health와 emergency migration`
- GitHub Issue: `#109`
- Branch: `feat/gh-109-snapshot-health-migration`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION`
- Test plan approval: `APPROVED` — 사용자가 2026-08-15 구현을 승인했다.
- Confirmed policy: emergency migration 대상이 되는 "사전 승인 release"는
  기존 `FilterReleaseStatus`에 새 상태를 추가하지 않고 `CANARY` 또는
  `ROLLED_BACK`을 재사용한다(사용자 승인, 2026-08-15). `rollback()`과 동일한
  "이미 한 번 완전히 검증된 release만 재사용" 불변식을 그대로 상속한다.

## Objective

- OpenAI moderation snapshot 단위 장애를 개별 `FilterJob` 실패와 분리해
  판단하고, 운영자 승인이 있을 때만 사전 검증된 release로 emergency
  migration하는 안전장치를 구현한다.
- 429/5xx/timeout/network/알 수 없는 오류가 자동으로 영구 장애를 만들지
  않게 하고(`INV-HLT-002`, `INV-HLT-004`), 인증·권한·결제·quota·invalid
  request 같은 우리 측 오류가 target snapshot 장애 증거로 오분류되지 않게
  한다(`INV-HLT-003`).
- `PERMANENT_CONFIRMED`와 emergency migration 실행은 운영자의 명시적 확인
  없이는 어떤 자동 경로로도 도달할 수 없다(`INV-HLT-005`).
- migration 이후 이전 generation의 늦은 결과가 상태를 바꾸지 못한다
  (`INV-REL-010` — `#106`/`#107`이 정의만 하고 호출자가 없던
  `FilterJob.advanceAttemptGeneration`/`STALE_ATTEMPT_GENERATION` fencing을
  이 이슈가 실제로 소비하는 첫 호출자가 된다).

## Scope

1. `ModerationFailureClassification`(신규 enum) — HTTP status와 OpenAI
   응답의 `error.code`로 `RATE_LIMITED`/`SERVER_ERROR`/`TIMEOUT_OR_NETWORK`/
   `NON_TARGET_CLIENT_ERROR`/`UNKNOWN`으로 분류한다.
   `OpenAiModerationProviderClient`/`OpenAiModerationResponseMapper`를
   확장하되 기존 429(`ModerationRateLimitedException`, `#108`)와
   `FLT-EXT-001`(`MODERATION_PROVIDER_UNAVAILABLE`) 계약은 그대로 유지한다.
2. `SnapshotHealth`(신규 도메인, `modelSnapshot` 문자열 keyed) —
   `HEALTHY`/`DEGRADED`/`PERMANENT_SUSPECTED`/`PERMANENT_CONFIRMED`/
   `RECOVERED` 상태를 갖는다. 개별 job 실패가 아니라 synthetic probe
   결과로만 전이한다.
3. Target/control synthetic probe 기록 서비스 메서드 — 실사용자 요청과
   분리된 별도 진입점. control probe도 실패하면 공급자 전역 장애로 간주해
   target-only 실패로 집계하지 않는다.
4. 분류별 반영 규칙 — `NON_TARGET_CLIENT_ERROR`·`UNKNOWN`은 어떤 반복
   횟수에서도 `PERMANENT_SUSPECTED` 후보 증거로 집계되지 않는다.
   target-only `SERVER_ERROR`/`TIMEOUT_OR_NETWORK`의 지속(횟수·기간, 정확한
   임계값은 주입 값)만 증거로 누적한다.
5. Evidence 축적 — 공식 공지(운영자 수기 플래그), target-only 실패 지속
   기간·횟수, recovery 신호(target probe 재성공 시 `HEALTHY` 복귀 + 증거
   초기화)를 `SnapshotHealth`에 보관한다.
6. `PERMANENT_CONFIRMED` 운영자 승인 — `SnapshotHealthService`가
   `FilterReleaseRegistryService.promote`/`rollback`과 동일한 패턴
   (`operatorUserId` 필수, append-only 감사 이력 테이블)으로
   `PERMANENT_SUSPECTED` → `PERMANENT_CONFIRMED` 전이를 운영자 호출로만
   허용한다. 이 전이를 노출하는 REST endpoint 1개를 추가한다.
7. Emergency migration — `PERMANENT_CONFIRMED` snapshot과 status가
   `CANARY` 또는 `ROLLED_BACK`인 대상 release가 모두 확인될 때만 실행
   가능하다. 신규 `FilterJob.migrateToRelease(newReleaseId, now)` 도메인
   메서드로 영향받는 `AUTOMATED` job들의 `filterReleaseId` 재배정과
   `attemptGeneration + 1`을 한 전이로 처리한다(기존
   `STALE_ATTEMPT_GENERATION` fencing이 그대로 상속되어 `INV-REL-010`
   보장, worker 코드 변경 없음). 대상 release는 기존 `promote()`/
   `rePromote()` 경로로 승격한다. 원본→대상 release·snapshot health
   id·operator·시각을 별도 감사 테이블에 기록해 lineage를 보존한다.
8. DB 마이그레이션(V15) — `snapshot_health`, `snapshot_health_probe_result`,
   `snapshot_emergency_migration_history` 테이블.
9. 단위·PostgreSQL 통합(동시성 포함) 테스트와 테스트 보고서.

## Explicit exclusions

- 상태별 window, threshold, probe cadence, alert 정책의 실제 운영 수치 —
  전부 미결정이며 생성자/설정 주입 값으로만 존재한다.
- 운영자 승인 endpoint의 세부 권한(role) 정책 — 이슈 본문이 미결정으로
  명시.
- synthetic probe를 실제로 주기적으로 실행하는 scheduler와 Spring bean
  배선 — `#105`~`#108`과 동일하게 `#113` production gate로 이연.
- 공식 공지 수집을 위한 외부 연동(RSS, 웹훅 등) — 운영자가 직접 기록하는
  수기 플래그만 다룬다.
- `PERMANENT_CONFIRMED`에서 되돌리는 운영자 번복(un-confirm) 경로와
  emergency migration의 역방향 — 이슈 범위 밖.
- Slack 알림(`#111`), `ManualReviewCase` 우선순위/band(`#110`).
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `ModerationFailureClassification`, `SnapshotHealth`, probe 기록, evidence, 운영자 승인, `FilterJob.migrateToRelease`, emergency migration 서비스, V15 마이그레이션, 단위·통합 테스트 | Feature executor | `INV-HLT-001`~`007`, `INV-REL-010` 검증, `#104`(release registry)·`#106`/`#107`(attempt generation fencing)·`#108`(release retry gate) 기존 계약과의 호환성 리뷰 |

## Existing user-owned changes

- `main`(#143 병합 직후)에서 새로 분기했다(`./harness start --issue 109
  --type feat --slug snapshot-health-migration`). 분기 시점
  `git status --short`는 비어 있었다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.filtering.*" --max-workers=1 --no-daemon
./gradlew integrationTest --tests "com.dnd.qello.SnapshotHealth*" --tests "com.dnd.qello.*EmergencyMigration*" --max-workers=1 --no-daemon --no-parallel --rerun-tasks
./harness test-run --id TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION
./harness check
./harness pr-ready --project-tests
git diff --check
```

## Completion criteria

- [x] 429, 5xx, timeout, network 또는 알 수 없는 오류가 자동 영구 장애를
      만들지 않는다(`INV-HLT-002`, `003`, `004`) —
      `SnapshotHealthTest#neverAccumulatesNonTargetClientError`,
      `#neverAccumulatesUnknownClassification`,
      `OpenAiModerationProviderClientTest`의 분류 테스트 5건으로 검증했다.
- [x] 인증·권한·결제·quota·invalid request가 영구 snapshot 폐기로
      오분류되지 않는다(`INV-HLT-003`) — `NON_TARGET_CLIENT_ERROR`
      분류가 `SnapshotHealth.recordProbe`에서 어떤 반복 횟수에도 증거로
      집계되지 않음을 확인했다.
- [x] 운영자 확인 없는 emergency migration이 불가능하다(`INV-HLT-005`) —
      `SnapshotEmergencyMigrationService`가 `SnapshotHealth`
      `PERMANENT_CONFIRMED`(운영자 전용 `confirmPermanent` 경로로만 도달)를
      전제조건으로 강제함을 UNIT-021과 INT-002·INT-003으로 검증했다.
- [x] 이전 generation의 늦은 결과가 상태를 바꾸지 못한다(`INV-REL-010`) —
      `FilterJobTest#rejectsStaleResultAfterEmergencyMigration`과
      `SnapshotHealthMigrationIntegrationTest#staleGenerationResultAfterMigrationIsRejected`로
      검증했다. `#106`/`#107`이 정의만 하고 호출자가 없던
      `advanceAttemptGeneration`/`STALE_ATTEMPT_GENERATION` fencing을 이
      이슈가 실제로 소비하는 첫 호출자가 됐다(`FilterJob.migrateToRelease`).
- [x] 승인된 P0 테스트와 저장소 필수 검증이 통과하고 테스트 보고서가
      남는다 — unit 23개(UNIT-001~023), integration 8개(INT-001~008,
      실제 PostgreSQL 동시성·트랜잭션·REST endpoint 인가 포함) 전부
      통과. 상세는
      `docs/reports/tests/gh-109-TEST-PLAN-GH-109-SNAPSHOT-HEALTH-MIGRATION.md`
      참고. `./harness check`, `./harness pr-ready --project-tests`
      (전체 unit·integration 스위트, `./gradlew check` 포함) 통과.
- [x] 실행하지 못한 검증과 남은 위험을 보고서에 기록한다 — 위 보고서
      6·7절에 synthetic probe scheduler 배선(`#113`), 실제 OpenAI
      계정 검증, 진짜 mid-transaction 부분 실패 재현(INT-003 범위 축소),
      운영자 승인 endpoint 세부 권한 정책 등을 명시했다.
