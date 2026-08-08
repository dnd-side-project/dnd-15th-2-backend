# GitHub Issue #80 Task Contract

> Generated at: `2026-08-08T21:07:35+09:00`
>
> 이 파일은 현재 작업 브랜치의 계약이다. 저장소 전역 정책은 `AGENTS.md`를
> 따른다.

## Work gate

- Title: `수신함 방향 칩 집계와 방향 필터`
- GitHub Issue: `#80`
- Branch: `feat/gh-80-inbox-direction-chips`
- Base branch: `main` (`ae3e956` — `#89` JDBC SQL 분리 머지 직후에서 분기)
- 선행 Issue:
  - `#78` — `post_recipient.inbound_bearing_deg`를 V8로 추가. 이번 이슈는 그 컬럼을
    읽기만 한다.
  - `#79` — `InboxCategory`(`UNANSWERED`/`ANSWERED`)를 신설. 칩 집계는 이 카테고리를
    그대로 공유한다.
  - `#89` — `JdbcInboxQueryRepository`의 `SELECT_CARD`가
    `feed.repository.jdbc.sql.InboxQuerySql`로 이관됐다. 이번에 추가하는 정적 SQL도
    같은 규약(정적 뼈대는 `Sql` 클래스, 동적 조합은 리포지토리)을 따른다.
- 설계 근거:
  - `V8__widen_answer_visibility_to_recipients.sql:118,158` — "구간 키는 저장하지 않고
    조회 시점의 ACTIVE `direction_scheme`으로 파생시킨다"
  - `V1__create_direction_communication_schema.sql:1259~1282` — `OCTANT` v1 시드
    (8구간 × 45°, `start_offset_deg = 337.5`)
  - `direction_communication.dbml:85` — "목록 API는 방향별 집계를 함께 내려준다"
  - `docs/reports/private/direction/gh-80-inbox-direction-chip.local.md` — 이 계약의
    결정 근거를 정리한 로컬 설계 기록(gitignore 대상)

## Objective

`내게 온 질문` 목록에 방향 칩 필터가 추가됐다(2026-08-07 개정). 칩을 클라이언트가
받은 목록으로 만들면 두 가지가 깨진다.

1. `답변한` 카테고리는 수신 상한의 통제를 받지 않는다. 답변하면 슬롯이 즉시 해제되지만
   (`ct_post_recipient_capacity_release`) 행은 만료까지 목록에 남으므로, 그 크기는
   만료 창 안의 답변 횟수로 정해진다 — 코드에 상한이 없다(`expires_at`은 호출자 지정).
2. 구간 정의가 `direction_segment` 행이다. 클라이언트가 스킴을 복제하면 8방향에서
   16방향으로 바꿔도 클라 배포 전까지 칩이 재분류되지 않는다.

그래서 서버가 카테고리 스코프 전체에서 집계해 목록과 함께 내려준다.

`#69`·`#79`와 마찬가지로 API 계층은 만들지 않고 service·repository 메서드까지만
제공한다.

## Scope

### `DirectionChip` / `InboxListing` view 신설

`feed.view`에 두 record를 추가한다. 둘 다 Spring·JPA를 참조하지 않는다
(`FeedPersistenceBoundaryTest.viewsAndPortsRemainIndependent`).

- `DirectionChip(String segmentKey, String displayName, int sortOrder, long count)` —
  compact constructor로 공백 키·공백 표시명·음수 count를 차단한다.
- `InboxListing(List<InboxCard> cards, List<DirectionChip> chips)` — `InboxCard`와 같이
  `List.copyOf`로 방어 복사한다.

### `InboxQueryRepository` 포트 확장

```java
List<InboxCard> findInbox(long recipientId, InboxCategory category,
    String directionSegmentKey, Instant at);
List<DirectionChip> countByDirection(long recipientId, InboxCategory category, Instant at);
```

- `findInbox`의 `directionSegmentKey`가 null이면 필터 없음이다.
- `countByDirection`은 **방향 필터를 받지 않는다.** 칩 집계에 자기 필터를 걸면
  선택한 칩만 남아 다른 방향으로 갈아탈 수 없다. 포트 시그니처에 인자를 두지 않아
  실수로 넘길 수조차 없게 한다.
- 기존 3인자 `findInbox`는 오버로드로 남기지 않고 교체한다. `#79`의 "이관이지
  병존이 아니다"와 같은 이유로, 스코프 규칙이 두 경로로 갈라지는 것을 막는다.

### 구간 파생과 원형 각도 비교

구간 키는 저장하지 않고 조회 시점에 파생한다(`V8` 기결정). `direction_segment`를
`inbound_bearing_deg`에 대조하는 조인 조건은 다음 형태다.

```sql
JOIN direction_scheme ds  ON ds.status = 'ACTIVE' AND ds.code = :schemeCode
JOIN direction_segment seg ON seg.scheme_id = ds.id
 AND MOD(pr.inbound_bearing_deg - (seg.center_bearing_deg - seg.angular_width_deg / 2) + 720, 360)
     < seg.angular_width_deg
```

- 구간 시작각을 원점으로 옮긴 뒤 오프셋이 폭 미만인지만 본다. 북 구간(시작 337.5°)에서
  350°는 오프셋 12.5, 10°는 32.5로 둘 다 45 미만이라 같은 칩에 들어간다.
- `+720`은 `center - width/2`가 음수일 때(북 구간의 −22.5°) `MOD` 피연산자를 양수로
  올리기 위한 보정이다. PostgreSQL의 `MOD`는 피연산자 부호를 따라가므로 이 보정이
  없으면 음수 오프셋이 나와 비교가 깨진다.
- 비교는 반열림(`<`)이라 경계각이 두 칩에 중복 계상되지 않는다. 도메인
  `DirectionSegment.contains`의 `bearing >= start && bearing < end`와 같은 규칙이다.
- `NUMERIC`을 `double precision`으로 캐스팅하지 않는다. 부동소수 반올림이 22.5°·67.5°
  같은 경계각을 인접 칩으로 밀어낼 수 있고, 세그먼트 8행 × 대상 행 수 규모에서는
  속도 차이가 무의미하다.

### 목록 쿼리에는 필터가 있을 때만 조인한다

`directionSegmentKey`가 null이면 세그먼트 조인을 걸지 않는다. 항상 조인하면 스킴이
360°를 완전히 덮지 못하게 바뀌는 날 **방향 필터를 쓰지도 않은 사용자의 목록에서
항목이 조용히 사라진다.** 필터 없는 목록은 스킴 데이터에 의존하지 않아야 한다.

### 공유 scope 조건 추출

"칩 count == 그 칩으로 필터한 목록 건수"(`#80` 완료 조건)를 테스트가 아니라 구조로
보장한다. 두 쿼리가 공유하는 조건을 `InboxQuerySql`의 상수 하나로 뺀다.

```text
pr.recipient_id = :recipientId
dp.status = 'ACTIVE'
dp.deleted_at IS NULL
dp.expires_at > :at
NOT EXISTS (user_block: blocker_id = :recipientId AND blocked_id = dp.sender_id)
```

카테고리별 상태 필터는 `switch`로 만드는 동적 조합이므로 `#89`의 이동 원칙에 따라
리포지토리에 남긴다.

### `InboxQueryService`가 목록과 칩을 함께 반환

```java
InboxListing list(long recipientId, InboxCategory category,
    String directionSegmentKey, Instant at);
```

두 쿼리를 같은 `at`, 같은 `@Transactional(readOnly = true)` 안에서 실행한다.

### ACTIVE 스킴 선택

`qello.direction.scheme-code`(기본 `OCTANT`) 설정을 추가하고 `ds.code = :schemeCode AND
ds.status = 'ACTIVE'`로 조인한다. `uq_direction_scheme_active`가 `(code) WHERE status =
'ACTIVE'`라 code별로만 유일하므로, code를 고정하지 않으면 다른 code의 스킴이 동시에
ACTIVE가 될 때 한 수신 항목이 두 스킴의 구간에 각각 잡혀 count가 중복 집계된다.
`DirectionReceiveProperties`·`FeedDistanceProperties`가 이미 쓰는 "운영 설정값을 코드
상수로 박지 않는다" 패턴을 따른다. 아래 `Assumptions` 참고 — 리뷰 대상이다.

## Explicit exclusions

- controller, DTO, API 문서, endpoint — 이번 회차도 service 계층까지다.
- `내가 쓴 질문` 탭의 방향 필터 — 방향 칩을 쓰지 않는다(이슈 명시).
- 지도 마커 방향 집계.
- `ANSWERED` 목록 페이징 — 상한이 없어 언젠가 필요하지만 `#80`이 요구하지 않았다.
  이번 칩 계약은 페이지가 아니라 카테고리 스코프를 집계하므로 페이징이 나중에 붙어도
  바뀌지 않는다.
- 수신 상한(`qello.direction.receive-capacity`) 상향 — 별도 판단 사항이다.
- `SELECT_CARD`의 카드별 상관 서브쿼리 최적화 — `#79`에서 들어온 기존 비용이며 이번
  변경이 늘리지 않는다. `ANSWERED`가 커지면 먼저 아플 곳이지만 이 이슈 범위가 아니다.
- 신규 마이그레이션 — `#78`의 V8과 `#39` 시점의 V1 시드를 그대로 쓴다. DB 변경 없음.
- 만료 전이 배치, `SKIP_PENDING` 확정 워커 — `#79`와 같이 제외이며, 그래서 상태만
  보지 않고 `expires_at`을 함께 본다.
- 인프라 apply, 배포, 프로덕션 변경은 별도 승인 없이는 실행하지 않는다.
- Secret, 계정 식별자, 토큰, `.env` 값은 기록하지 않는다.

## Assumptions

구현을 위해 확정한 값이며 리뷰에서 뒤집힐 수 있다.

- `ASSUMED` — ACTIVE 스킴을 `qello.direction.scheme-code` 설정값으로 고정한다.
  대안은 (a) `status='ACTIVE'`만 조인 — 중복 집계 위험을 그대로 안는다, (b) ACTIVE가
  1개가 아니면 예외 — 스킴 전환 중 수신함 조회 전체가 막힌다. 설정값 방식을 골랐다.
- `ASSUMED` — 알 수 없는 `segmentKey`는 예외가 아니라 빈 목록이다. `#79`에서 자격
  없는 뷰어에게 예외 대신 빈 결과를 준 것과 같은 방향이며, 클라이언트가 옛 스킴의
  키를 들고 있는 전환기에 목록 전체가 400으로 죽는 것을 막는다. 이때도 칩은 카테고리
  전체를 그대로 반환하므로 사용자가 다른 방향으로 갈아타 빠져나올 수 있다.
- `CONFIRMED` — 구간 키를 저장하지 않고 조회 시점에 파생하는 것은 V8이 이미 확정했다
  (`V8:118`).
- `CONFIRMED` — 칩 집계가 서버에 있어야 하는 이유는 페이징 유무가 아니라 `ANSWERED`의
  무상한 누적과 구간 정의 소유권이다. 이슈 본문의 "페이지네이션" 근거는 `ANSWERED`에
  대해서는 지금도 실질적으로 성립한다.

## Ownership

| Area | Owner | Required review |
| --- | --- | --- |
| `DirectionChip`·`InboxListing` view | Feed executor | 불변식(공백 키·음수 count·방어 복사) 리뷰 |
| 구간 파생 SQL과 원형 각도 비교 | Feed executor | `+720` 보정·반열림 경계·`NUMERIC` 유지 근거 리뷰 |
| 공유 scope 상수와 두 쿼리 일치 | Feed executor | 칩 count와 목록 건수가 같은 조건에서 나오는지 리뷰 |
| 목록 쿼리의 조건부 조인 | Feed executor | 필터 없는 목록이 스킴에 의존하지 않는지 리뷰 |
| `qello.direction.scheme-code` 설정 | Direction executor | 중복 ACTIVE 스킴 방어와 설정 기본값 리뷰 |
| 단위/통합 테스트 | Test orchestrator | 원형 경계·카테고리 한정·count 일치·0건 방향 제외 리뷰 |

## Existing user-owned changes

- `./harness start` 직전 `git status --short`는 비어 있었다. 보존할 다른 사람의
  미커밋 변경이 없다. `TASK.md`는 `h task-init --replace`로 `#80` 계약을 새로 썼다.
- `docs/reports/private/direction/gh-80-inbox-direction-chip.local.md`는 이 브랜치에서
  만든 설계 기록이며 `.gitignore:51`(`docs/reports/**/*.local.md`)로 커밋되지 않는다.

## Validation

```bash
./harness check
./harness pr-ready --project-tests
npm run hooks:validate
git diff --check
```

- Docker가 사용 가능하므로 Testcontainers 기반 통합 테스트를 로컬에서 실행한다.
  `./gradlew test`와 `./gradlew integrationTest`를 모두 통과시킨다.

## Completion criteria

- [x] 구간 키를 저장하지 않고 조회 시점에 파생한다 — `direction_scheme`을 8방향에서
      16방향으로 바꾸면 데이터 마이그레이션 없이 칩이 재분류된다
      (`InboxQuerySql.SEGMENT_JOIN`은 저장된 구간 키를 참조하지 않는다)
- [x] 북 구간이 350°와 10°를 모두 포함한다
      (`InboxDirectionChipIntegrationTest.northSegmentWrapsAroundZeroDegrees`)
- [x] 경계각(22.5°·67.5°…)이 정확히 한 칩에만 잡힌다
      (`.boundaryAnglesBelongToExactlyOneSegment`)
- [x] 0~359° 대표각 스윕에서 SQL 파생 구간과 `DirectionSegment.contains` 결과가 모두
      일치한다 (`.sqlDerivedSegmentMatchesDomainContainsAcrossSweep`, 28개 방위각)
- [x] 0건인 방향은 칩 목록에 나오지 않는다 (`.excludesZeroCountDirectionsFromChips`)
- [x] 카테고리를 바꾸면 칩 집계 범위도 그 카테고리로 한정된다
      (`.chipAggregationIsScopedToCategory`)
- [x] 모든 칩에 대해 count가 그 `segmentKey`로 필터한 목록 건수와 일치한다
      (`.chipCountMatchesFilteredListSizeForEveryChip`)
- [x] 만료·차단·`SKIPPED` 항목이 목록과 칩에서 동일하게 빠진다
      (`.expiredBlockedAndSkippedAreExcludedFromBothListAndChips`)
- [x] 알 수 없는 `segmentKey`는 예외 없이 빈 목록이고, 칩은 그대로 반환된다
      (`.unknownSegmentKeyYieldsEmptyListButChipsRemain`)
- [x] controller와 endpoint를 추가하지 않는다
- [x] `./gradlew test`와 `./gradlew integrationTest`가 통과한다 (단위 160개, 통합
      154개, 전부 통과 — 154는 코드 리뷰 반영분 1건 포함, `docs/reports/tests/gh-80-INBOX-DIRECTION-CHIPS.md` §7a)
- [x] `./harness pr-ready --project-tests`가 통과한다
- [x] `npm run hooks:validate`가 통과한다 (`Husky validation passed` — `./harness
      check`가 내부적으로 실행하는 `scripts/validate-husky.py`와 동일 스크립트를
      호출한다)
- [x] `findInbox`와 `countByDirection`이 같은 트랜잭션 스냅샷에서 읽는다
      (`InboxQueryService.list`에 `isolation = Isolation.REPEATABLE_READ` 지정,
      2차 코드 리뷰 반영 — `docs/reports/tests/gh-80-INBOX-DIRECTION-CHIPS.md` §7b)
