# GitHub Issue #126 Task Contract

> Generated at: `2026-08-17T20:37:57+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `만료·넘김 확정 sweep 실행기`
- GitHub Issue: `#126`
- Branch: `feat/gh-126-expiration-skip-sweep`
- Base branch: `main`

## Objective

- 이미 구현된 `PostRecipient.expire()`·`confirmSkip()` 전이와
  `ReceiveSlotReleaseService`의 행 단위 트랜잭션을, 처리량이 제한되고 실패가
  행 단위로 격리되며 재실행해도 수신 슬롯 카운터를 중복 감소시키지 않는
  batch 실행기에 연결해 수신 슬롯이 영구 점유되지 않게 한다.

## Scope

1. 만료 sweep 실행기: 소속 질문글이 만료된 `AVAILABLE`·`DISCOVERED`·`OPENED`
   수신 항목을 batch 조회하고 행마다 `ReceiveSlotReleaseService.expire()`를
   호출한다.
2. 넘김 확정 sweep 실행기: 되돌리기 유예(`qello.direction.skip-confirmation-grace-seconds`)가
   지난 `SKIP_PENDING`을 batch 조회하고 행마다
   `ReceiveSlotReleaseService.confirmSkip()`을 호출한다.
3. 처리량 제한: `PostRecipientRepository.findExpirableAsOf`·`findConfirmableSkips`에
   `limit`을 추가하고, 기아를 막는 결정적 정렬을 SQL에 명시한다
   (만료는 `dp.expires_at, pr.id`, 넘김은 `skip_requested_at, id`).
4. 행 단위 트랜잭션 경계 유지: 실행기는 자체 트랜잭션을 열지 않고
   `ReceiveSlotReleaseService`의 행별 `@Transactional` 경계를 그대로 쓴다.
   한 행의 실패가 같은 batch의 나머지 행 처리와 이미 커밋된 결과를 되돌리지
   않는다.
5. 재실행 안전성: 조건부 전이(`transitionToExpired`/`transitionToSkipped`의
   `previousStatus` 조건)가 빈 결과를 돌려주면 슬롯 해제를 건너뛰는 기존 계약을
   실행기 수준에서 보존하고 검증한다.
6. 실패 로그와 기본 카운터: batch당 `scanned`·`released`·`ineligible`·`failed`
   집계를 결과 값으로 반환하고 요약 로그를 남긴다. 좌표, 답변 본문, 내부 사용자
   식별자는 기록하지 않는다.
7. 답변 제출·만료·넘김 확정 3자 경합과 재실행 멱등성에 대한 JUnit 5 단위·
   PostgreSQL/PostGIS 통합·동시성 테스트와 테스트 보고서.

## Approved design decisions

- 이번 이슈는 **트리거 없는 worker 빈만** 추가한다(사용자 승인, `2026-08-17`,
  현재 Claude Code 대화). `@Scheduled`·`@EnableScheduling`은 도입하지 않으며
  `DirectionMatchingWorker`·`AnswerModerationVerdictWorker`와 같이
  `processBatch(command)` 호출 계약만 제공한다. 운영 주기 실행 활성화는 후속
  운영 이슈로 남긴다.
- 메트릭은 `spring-boot-starter-actuator`/Micrometer 의존성을 추가하지 않고
  batch 결과 카운터와 구조화 로그로 제공한다(사용자 승인, `2026-08-17`,
  현재 Claude Code 대화). 카운터 이름과 태그는 이후 Micrometer `Counter`로
  그대로 승격할 수 있는 형태로 정의한다. 대시보드 연결은 별도 운영 이슈다.
- 만료 후보 조회는 지금처럼 잠금 없는 스냅샷을 유지한다. 검사 중 답변 보호의
  정합성 근거는 `ReceiveSlotReleaseService.expire()`가 `findByIdForUpdate`로
  행을 잠근 뒤 다시 확인하는 경로이며, 후보 SQL의 `NOT EXISTS`는 대상 축소
  최적화로만 취급한다.
- `SKIP_PENDING`은 만료 sweep 후보에서 제외한다. 되돌리기 유예 동안 수신 용량을
  계속 붙잡는 기존 설계(`confirmSkip`/`revertSkip` 전용 레인)를 유지한다.

## Explicit exclusions

- `@Scheduled`, `@EnableScheduling`, 외부 스케줄러 연동과 운영 주기 실행 활성화.
- HTTP endpoint 추가. 이슈 명시대로 내부 실행기이며 외부 API를 포함하지 않는다.
- actuator·Micrometer 의존성 추가와 모니터링 대시보드 구축.
- 다중 인스턴스 중복 sweep을 막기 위한 분산 lease·advisory lock 도입. 현재
  구조는 행 잠금과 조건부 전이로 정합성이 보장되므로 중복 실행은 낭비일 뿐
  오염이 아니다. 필요해지면 별도 이슈에서 다룬다.
- 보존·삭제 정책의 최종 결정과 만료 항목의 물리 삭제.
- `DirectionPost` 자체의 상태 전이 변경. 이 이슈는 `post_recipient`와
  `recipient_receive_state`만 다룬다.
- Flyway migration. 기존 스키마와 제약으로 구현할 수 없다고 확인되면 범위를
  넓히지 않고 별도 승인을 받는다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| sweep 실행기 계약과 batch 결과·로그 | Direction executor | 행 단위 실패 격리, 트랜잭션 경계 미확장, 로그 민감정보 리뷰 |
| 후보 조회 limit·정렬과 JDBC 구현 | Persistence executor | 기아 방지 정렬, 인덱스 사용, 잠금 없는 스냅샷 전제 유지 리뷰 |
| 답변·만료·넘김 경합과 재실행 멱등성 | Test executor | 카운터 중복 감소 부재, 유예 경계, 실제 DB 제약 기반 판정 리뷰 |
| 전체 변경 및 검증 증거 | Independent verifier | 구현 설명이 아닌 diff·실행 결과 기반 독립 검증 |

## Existing user-owned changes

- 작업 시작 시 `main`의 `git status --short`는 비어 있었다.
- `./harness start`가 최신 `origin/main`을 fetch했고 `Already up to date`로
  fast-forward 대상이 없음을 확인한 뒤 `feat/gh-126-expiration-skip-sweep`을
  생성했다.
- 브랜치 생성 전에 보존해야 할 기존 사용자 변경은 없었다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- Test plan: `TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP`
  (`docs/test-plans/gh-126-TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP.md`, Status: Approved)
- Design approval evidence: 위 "Approved design decisions"의 사용자 승인
  (`2026-08-17`, 현재 Claude Code 대화).
- Test-plan approval: 사용자 승인 (`2026-08-17`, 현재 Claude Code 대화).

## Completion criteria

- [x] 미답변 만료 항목이 `EXPIRED`로 전이되고 수신 슬롯이 정확히 한 번 해제된다.
      (UNIT-002/003, INT-001)
- [x] 유예 중인 `SKIP_PENDING`은 확정되지 않고 슬롯도 해제되지 않는다.
      (INT-007)
- [x] 유예가 지난 항목은 `SKIPPED`로 전이되고 수신 슬롯이 정확히 한 번 해제된다.
      (INT-008/009)
- [x] 같은 sweep을 재실행해도 `recipient_receive_state` 카운터가 중복 감소하지
      않는다. (INT-002/010)
- [x] batch가 설정된 처리량 상한을 넘지 않고, 반복 실행이 남은 대상을 결정적
      순서로 이어서 처리한다. (INT-011)
- [x] 한 행의 실패가 같은 batch의 나머지 행 처리와 이미 커밋된 전이를 되돌리지
      않는다. (UNIT-004/010, INT-012)
- [x] 만료 sweep이 만료 전에 제출된 검사 중 답변의 수신 항목을 선점하지 않는다.
      (INT-005/006/013)
- [x] 배치 결과 카운터와 로그에 좌표, 답변 본문, 내부 사용자 식별자가 노출되지
      않는다. (UNIT-012)
- [x] 승인된 테스트 계획의 P0 시나리오가 통과하고 테스트 보고서가 남는다.
      `docs/reports/tests/gh-126-TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP.md`
      (Result: PASS, unit 581건·integration 440건, 실패 0건).
