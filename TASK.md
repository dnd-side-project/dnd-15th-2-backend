# GitHub Issue #120 Task Contract

> Generated at: `2026-08-13T17:14:04+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `방향 매칭 워커와 원자적 수신자 확정`
- GitHub Issue: `#120`
- Branch: `feat/gh-120-direction-matching-worker`
- Base branch: `main`
- Test plan: `TEST-PLAN-GH-120-DIRECTION-MATCHING-WORKER`
- Test plan approval: User approved on 2026-08-13; implementation and verification completed locally.

## Objective

- #118이 남긴 `RECIPIENT_MATCH_REQUESTED` Outbox를 #119의 event-type claim과
  lease fencing 계약으로 점유한다.
- 질문글과 `PostAudience` 스냅샷을 서버 권위로 다시 읽고, 실행 시점의 위치·계정·
  차단·수신 슬롯 조건을 통과한 제한된 수신자만 확정한다.
- `post_recipient` 삽입, 성공한 사용자 슬롯·최근 수신량 증가, 수신자별
  `RECIPIENTS_CONFIRMED` Outbox, 질문글 `ACTIVE` 전이와 원본 매칭 Outbox 완료를
  한 트랜잭션에 묶는다.
- 만료와 moderation gate는 fail-closed로 적용한다. #137 전에는 실제 주기 실행을
  활성화하지 않는다.

## Implementation plan

1. **매칭 입력과 상태 전이 계약**
   - `DirectionPost`에 `MATCHING -> ACTIVE`와 deadline 선택 A
     (`expires_at <= now`이면 매칭 금지) 전이를 추가한다.
   - 실행 가능한 상태는 `status = MATCHING`, `moderation_status = PASSED`,
     `expires_at > now`로 제한한다.
   - 이미 만료됐으면 질문글을 `EXPIRED`로 전이하고 수신자 없이 원본 이벤트를
     완료한다. `REJECTED` 등 복구 불가능한 terminal 상태도 수신자 없이 종료한다.
   - `PENDING`·`REVIEW_HELD`는 성공 처리하지 않고 retryable/not-ready로 분류해
     #137이 `PASSED`로 전이하기 전 수신자 생성과 이벤트 소비를 모두 막는다.

2. **원자적 수신자 확정 JDBC 경계**
   - 기존 `DirectionPostRepository`, `PostAudienceRepository`, `ActiveUserPresenceRepository`,
     `PostRecipientRepository`, `RecipientReceiveStateRepository`를 조합하고 필요한
     row-lock/state-lock JDBC 연산만 확장한다.
   - 질문글은 `FOR UPDATE`로 잠그고 후보 계산에는 최초 `PostAudience`의 원점,
     방향 중심·폭, 거리 범위만 사용한다. Outbox payload나 preview 결과를 후보
     원천으로 사용하지 않는다.
   - 실행 시점 `active_user_presence`, ACTIVE 계정, 양방향 활성 차단, 지역·거리·
     방향을 다시 검사한다.
   - 누락된 `recipient_receive_state`는 같은 transaction에서 멱등 초기화한 뒤,
     공정성 순서
     `recent_received_count, last_received_at NULLS FIRST, distance, user_id`로
     `FOR UPDATE OF recipient_receive_state SKIP LOCKED`한다.
   - `max-recipients-per-post`까지만 선택하고 `ON CONFLICT (post_id, recipient_id)
     DO NOTHING`의 실제 삽입 성공자만 슬롯과 최근 수신량을 증가시킨다.
   - 이번 Issue는 단계적 추가 라운드를 만들지 않는다. 동시 잠금 때문에 이번
     실행에서 목표 인원보다 적게 확정되는 것은 허용하고, 상한 초과보다 안전성을
     우선한다.

3. **후속 Outbox와 질문글 활성화**
   - 실제 삽입된 `post_recipient`마다 `aggregate_type = POST_RECIPIENT`,
     `event_type = RECIPIENTS_CONFIRMED` Outbox를 하나 생성한다.
   - dedup key는 recipient row ID 기반의 안정 키를 사용하고 payload에는
     `postId`, `postRecipientId`, `recipientId`만 넣어 정확 좌표·거리·방위를
     전달하지 않는다.
   - 후보가 0명이어도 정상적으로 매칭을 완료해 질문글을 `ACTIVE`로 전이한다.
     실제 `Notification` fan-out은 #123의 책임이다.

4. **lease가 있는 worker 실행 경계**
   - 스케줄러가 아닌 호출 가능한 batch worker를 추가한다. worker는 #119의
     `claimDue(Set.of(RECIPIENT_MATCH_REQUESTED), ...)`만 사용한다.
   - claim transaction과 각 event 처리 transaction을 분리해 한 event 실패가 같은
     batch의 다른 event를 rollback하지 않게 한다.
   - event 처리 transaction 마지막에 owner·generation·유효 lease로 원본 Outbox를
     완료한다. fencing update가 0행이면 recipient/state/후속 Outbox/ACTIVE 전이를
     모두 rollback한다.
   - transient DB 오류와 moderation not-ready는 retryable, 손상된 event 계약·복구
     불가능한 참조 부재는 permanent로 분류해 #119의 `OutboxRetryPolicy`로
     `FAILED/DEAD`를 결정한다. stale lease는 이전 worker가 상태를 덮어쓰지 않고
     회수 경로에 맡긴다.

5. **검증과 활성화 게이트**
   - 도메인·worker 분류 단위 테스트와 실제 PostgreSQL/PostGIS 트랜잭션·동시성·
     rollback 통합 테스트를 구현한다.
   - 정확 좌표 비노출, #118 제출 비동기 경계, #119 claim/retry/fencing 회귀를 함께
     실행한다.
   - 테스트 보고서를 작성하되 scheduler/production activation은 #137 완료 후 별도
     연결로 남긴다.

## Scope

- `RECIPIENT_MATCH_REQUESTED` 전용 batch claim 호출과 per-event transaction handler
- 질문글 row lock, 상태·만료·`moderation_status = PASSED` 재검증
- 최초 `PostAudience` 스냅샷 기반 선택 구간 PostGIS 후보 재계산
- ACTIVE 계정, current/receive-allowed presence, 양방향 차단, 지역·거리·방향 필터
- 최근 수신량·마지막 수신 시각·거리·user ID 기반 결정론적 공정성 순서
- `recipient_receive_state` 멱등 초기화와 `FOR UPDATE SKIP LOCKED`
- recipient 삽입 성공자와 슬롯·최근 수신량 증가의 일치
- 발송별 최대 수신자 상한 적용
- 수신자별 `RECIPIENTS_CONFIRMED` Outbox 생성
- 질문글 `ACTIVE`/`EXPIRED` 전이와 원본 Outbox lease fencing 완료
- 중복 실행, 공통 슬롯 경합, 만료·moderation gate, rollback·stale lease 테스트

## Explicit exclusions

- #115/#119의 V12 lease schema, generic claim SQL, retry policy 재구현
- scheduler, polling interval, worker thread pool, 운영 worker identity와 자동 기동
- #137의 moderation job 생성·callback·미디어 안전 검사와 production activation
- #123의 `Notification`/`notification_delivery` fan-out과 외부 Push provider 호출
- 단계적 추가 매칭 라운드와 목표 수신자 수를 반드시 채우는 보충 스캔
- 새로운 Flyway migration과 성능 근거 없는 index 추가
- preview API, 질문글 제출 API와 수신함 API 변경
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| 질문글 상태 전이와 worker 분류 단위 테스트 | Test/backend executor A | deadline·moderation·fencing 분류 리뷰 |
| 원자적 matching JDBC와 PostgreSQL/PostGIS 통합 테스트 | Test/backend executor B | 공간 필터·lock·insert/count/outbox SQL 리뷰 |
| batch worker와 Outbox retry 연결 | Backend executor C | transaction propagation·lease owner/generation 리뷰 |
| 테스트 계획·보고서 | Test orchestrator | Issue/#137/#123 경계와 증거 리뷰 |
| 최종 변경과 검증 증거 | Independent reviewer | 구현 에이전트와 분리된 리뷰 |

각 executor는 계획에서 지정한 파일만 소유하고, 다른 executor나 사용자의 변경을
되돌리지 않는다.

## Existing user-owned changes

- 작업 시작 시 `git status --short`는 clean이었다.
- 로컬 `main`과 최신 `origin/main`은 commit
  `01fc92c113b297d0dab3036ba9219c3cf006ec77`로 일치했다.
- 구현 산출물과 검증 보고서는 현재 브랜치에 있지만 아직 commit/push/PR하지 않았다.

## Validation

```bash
./gradlew test --tests "com.dnd.qello.direction.matching.*"
./gradlew test --tests com.dnd.qello.direction.domain.DirectionPostMatchingTest
./gradlew integrationTest --tests com.dnd.qello.DirectionMatchingWorkerIntegrationTest
./gradlew integrationTest --tests com.dnd.qello.DirectionMatchingWorkerConcurrencyIntegrationTest
./gradlew integrationTest --tests com.dnd.qello.DirectionMatchingContractIntegrationTest
./gradlew integrationTest --tests com.dnd.qello.OutboxLeaseIntegrationTest
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

## Completion criteria

- worker는 `RECIPIENT_MATCH_REQUESTED`만 claim하고 다른 event type을 변경하지 않는다.
- preview/Outbox payload가 아니라 최초 audience와 실행 시점 DB 상태로 후보를 다시
  계산한다.
- 한 질문글·수신자 조합은 최대 한 행이며 실제 신규 recipient 수와 슬롯·최근 수신량
  증가량, 후속 Outbox 수가 일치한다.
- 한 질문글의 신규 수신자 수가 `max-recipients-per-post`를 넘지 않고 사용자별
  `receive-capacity`를 동시성 아래에서도 넘지 않는다.
- `expires_at <= now` 또는 `moderation_status != PASSED`에서는 수신자와 슬롯이
  생성되지 않는다.
- 정상 처리 후 질문글과 원본 Outbox가 각각 `ACTIVE`와 `PROCESSED`가 되며,
  후보가 0명인 경우도 완료된다.
- rollback과 stale lease에서 recipient/state/후속 Outbox/질문글 상태가 부분
  반영되지 않는다.
- 후속 Outbox payload에 정확 좌표·거리·방위가 없고 #123 fan-out과 구분된다.
- 승인된 P0 단위·통합 테스트와 저장소 필수 검증이 통과한다.
- 실행하지 못한 검증, 동시 `SKIP LOCKED`로 인한 under-fill, #137 활성화 의존성을
  테스트 보고서에 기록한다.
