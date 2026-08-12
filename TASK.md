# GitHub Issue #119 Task Contract

> Generated at: `2026-08-13T01:33:56+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `Outbox 이벤트별 배치 점유와 재시도 기반`
- GitHub Issue: `#119`
- Branch: `feat/gh-119-outbox-retry-foundation`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-119-OUTBOX-RETRY-FOUNDATION`

## Objective

- 여러 종류의 Outbox 작업을 처리하는 worker가 자신이 담당하는 event type만
  원자적으로 점유하도록 한다.
- 실패 성격과 누적 시도 횟수를 공통 정책으로 판정해 재시도 가능한 작업은
  `FAILED`, 영구 실패 또는 최대 시도 횟수에 도달한 작업은 `DEAD`로 전이한다.
- #115에서 확정한 lease fencing을 유지해 만료된 작업은 회수하되 stale worker의
  늦은 완료·실패 갱신은 차단한다.

## Scope

- 비어 있지 않은 `OutboxEventType` 집합을 받는 batch claim repository 계약 추가
- production 사용처가 없는 기존 무필터 batch claim 계약은 제거해 worker가 event
  type 범위를 생략할 수 없게 함
- due `PENDING`/`FAILED`와 만료된 `PROCESSING` 중 요청한 event type만
  `FOR UPDATE SKIP LOCKED`로 점유
- 기존 `next_attempt_at`, `id` 정렬, batch limit, `lease_owner`,
  `lease_expires_at`, `lease_generation`, `attempt_count` 원자 갱신 유지
- `RETRYABLE`/`PERMANENT` 실패 종류와 주입 가능한 최대 시도 횟수·backoff 전략을
  입력으로 `FAILED`/`DEAD` 전이를 결정하는 순수 재시도 정책 추가
- repository 실패 전이가 재시도 정책의 결정을 사용하되 owner·generation·유효
  lease fencing 조건을 유지
- event type 격리, batch 경쟁, 만료 경계, retry/DEAD 전이, stale worker 차단,
  정확 좌표 비노출 회귀를 JUnit 5와 실제 PostgreSQL/PostGIS에서 검증

## Explicit exclusions

- #115에서 완료된 lease 컬럼, `lease_generation`, claim index와 V12 migration 재구현
- 새로운 Flyway migration과 성능 근거 없는 신규 index
- scheduler, worker polling loop, thread pool과 worker identity 생성
- #120의 방향 매칭·PostGIS 후보 재계산·수신자/슬롯 확정
- #123의 인앱 알림 fan-out과 외부 Push provider 호출
- 구체적인 업무 오류를 `RETRYABLE`/`PERMANENT`로 분류하는 handler 규칙
- 운영 환경의 lease duration, 최대 시도 횟수와 backoff 기본값 확정
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 재시도 정책과 단위 테스트 | Test/Backend executor A | 상태 전이·횟수 경계 리뷰 |
| event type batch claim과 PostgreSQL 통합 테스트 | Test/Backend executor B | SQL lock·순서·fencing 리뷰 |
| 테스트 계획·보고서 | Test orchestrator | Issue 범위·증거 리뷰 |
| 최종 변경과 검증 증거 | Independent reviewer | 구현 에이전트와 분리된 리뷰 |

## Existing user-owned changes

- 작업 시작 시 `git status --short` 결과는 clean이었다.
- 로컬 `main`과 `origin/main`은 같은 commit이었다.
- `./harness start` 후 현재 브랜치는
  `feat/gh-119-outbox-retry-foundation`이다.

## Validation

```bash
./gradlew test --tests com.dnd.qello.notification.OutboxRetryPolicyTest
./gradlew integrationTest --tests com.dnd.qello.OutboxLeaseIntegrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- worker는 요청한 event type만 점유하며 다른 종류의 due event를 변경하지 않는다.
- 두 worker의 동시 batch claim 결과에 같은 event가 중복되지 않는다.
- future due, 유효 lease, `PROCESSED`, `DEAD` 행은 claim 대상에서 제외된다.
- 만료된 lease는 같은 시각부터 회수되며 generation과 attempt가 각각 1 증가한다.
- `RETRYABLE` 실패는 최대 횟수 미만에서 `FAILED`와 다음 시각을 반환하고,
  최대 횟수 도달 시 `DEAD`를 반환한다.
- `PERMANENT` 실패는 남은 횟수와 무관하게 `DEAD`를 반환한다.
- `DEAD`는 재점유 대상이 아니며, NOT NULL인 `next_attempt_at`에는 terminal 전이
  시각을 기록한다.
- 완료·실패 전이는 owner, generation, `PROCESSING`, 유효 lease를 모두 검증한다.
- 정확 좌표 비노출과 기존 dedup/matching round 계약이 회귀하지 않는다.
- 승인된 P0 단위·통합 테스트와 저장소 필수 검증이 통과한다.
- 실행하지 못한 검증과 남은 위험을 테스트 보고서에 기록한다.
