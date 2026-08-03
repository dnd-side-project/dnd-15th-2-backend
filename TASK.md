# GitHub Issue #39 Task Contract

> Generated at: `2026-08-03T19:06:00+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `[E] Direction/PostGIS persistence`
- GitHub Issue: `#39`
- Branch: `feat/gh-39-direction-postgis`
- Status: Implementation blocked until human approval of the test plan

## Objective

- 승인된 V1 schema 위에 방향 정책·위치 presence·방향 글·수신자 snapshot을
  PostGIS/JDBC 경계로 영속화하고, 전송 시 서버가 재계산한 방향/거리와 동일
  transaction의 수신 자격 확정을 보장한다.

## Scope

- `direction_scheme`, `direction_segment`의 8×45° half-open sector 정책과
  coverage/경계값 검증을 domain model과 repository port로 구현한다.
- `active_user_presence`의 PostGIS `geography(Point,4326)`, coarse region,
  정확도·측정 시각·server-authoritative `expiresAt`을 JDBC로 저장·조회한다.
- `recipient_receive_state`의 활성 미처리 용량과 최근 수신 투영값을 row lock 또는
  조건부 갱신으로 안전하게 예약·해제한다.
- `direction_post`, `post_audience`, `post_recipient`를 별도 aggregate/port로 두고
  feature 간 Entity/Repository 직접 참조 없이 scalar ID를 사용한다.
- 발송 시점에 sender의 최신 위치와 선택 방향을 재계산하고, `post_audience`와
  `post_recipient` snapshot을 같은 transaction으로 저장한다.
- PostGIS 거리·방위 후보 query, 공간 인덱스 사용 여부와 실행 계획을 실제
  PostgreSQL/PostGIS Testcontainers에서 검증한다.
- idempotency key, active question deferred trigger, sender 자기수신 방지,
  recipient status/timestamp/capacity release 제약을 DB와 application에서 검증한다.
- Issue #38의 승인 질문은 scalar `approvedQuestionId`로만 참조하며 Answer,
  Safety, Notification persistence는 포함하지 않는다.

## Explicit exclusions

- 적용된 V1 migration, DBML/ERD/schema manifest 및 region seed를 수정하지 않는다.
- 모바일 위치 수집 UI, API controller, 인증·권한 정책, 외부 메시지 전송을 구현하지
  않는다.
- 추천 알고리즘, 인접 sector fallback, 질문 선정 정책을 만들지 않는다. 후보 query는
  caller가 지정한 scheme/sector/range와 현재성 조건만 적용한다.
- 정확 좌표를 API 응답·로그·분석 이벤트·outbox payload에 노출하지 않는다.
- Answer, Report/Block/Safety, Notification persistence와 운영 DB/deploy/apply를
  구현하지 않는다.
- `active_user_presence`와 `post_audience`의 정확 위치 보존 정책을 변경하지 않는다.
- #39에서 정하지 않은 expiresAt 기간, 수신 상한, 거리/방향 임계값을 임의 상수로
  추가하지 않는다. V1에 명시된 active unhandled 상한 5와 caller가 전달한 절대 시각만
  사용한다.
- 기존 Account/Question JPA Entity 또는 Spring Data Repository에 직접 의존하지
  않고 `Long` ID와 JDBC row mapping으로 경계를 유지한다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| Direction scheme/segment domain and half-open sector rules | Issue #39 domain executor | Sector boundary/coverage review |
| Presence, PostGIS SQL, distance/bearing candidate adapter | Issue #39 JDBC executor | Spatial query/index/plan review |
| DirectionPost/Audience/Recipient persistence and state transitions | Issue #39 direction executor | Aggregate/constraint review |
| Capacity reservation, send-time snapshot and transaction orchestration | Issue #39 transaction executor | Lock/conditional-update review |
| Unit/integration tests and report | TEST-PLAN-GH-39-DIRECTION-POSTGIS | Test-plan approval |

## Existing user-owned changes

- Issue #38 승인 commit `9260faa`이 origin에 push된 clean 상태에서 분기했다.
- Issue #35~#38의 Flyway V1, JPA/JDBC ADR, Account/Question scalar-ID 경계를
  선행 계약으로 보존한다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

PostGIS 기능 검증은 H2가 아닌 PostgreSQL/PostGIS Testcontainers에서 실행한다. 실제
컨테이너가 필요한 명령이 실패하면 원인과 미검증 범위를 보고하며 성공으로 표현하지
않는다.

## Completion criteria

- 8개 sector가 0°/360° 정규화와 half-open 경계에서 정확히 하나로 매핑되고 scheme
  coverage/overlap 오류가 거절된다.
- 위치 `geography(Point,4326)`가 round-trip되며 `expiresAt > locationAt`, region FK,
  coarse fallback 규칙이 V1과 일치한다.
- PostGIS 후보 query가 만료 presence, receive 허용, region/range/sector 조건을
  적용하고 exact coordinate를 반환·로그하지 않는다.
- 발송 transaction이 최신 위치/방향을 재계산하고 audience와 recipient snapshot을
  함께 저장하며, 중간 실패 시 모두 rollback한다.
- `approved_question`이 ACTIVE가 아니면 direction post가 commit되지 않고, sender는
  자기 post recipient가 될 수 없다.
- idempotency key와 `(post_id, recipient_id)` 중복, recipient 상태/시각/capacity
  release invariant가 DB 및 application에서 검증된다.
- active unhandled count 5 상한이 row lock 또는 조건부 update로 동시 요청에서도
  초과 예약되지 않고, 해제 시 정확히 감소한다.
- feature 간 JPA 직접 참조가 없고 모든 테스트 클래스/메서드가 저장소 테스트 규칙을
  만족한다.
- 단위/통합 테스트, 실행 계획 확인, 테스트 보고서, Harness/Gradle/Hook 검증을
  완료한다.
- 구현과 테스트 보고서를 로컬 commit까지만 만들고 origin에는 push하지 않은 채
  사용자 검토를 기다린다.
