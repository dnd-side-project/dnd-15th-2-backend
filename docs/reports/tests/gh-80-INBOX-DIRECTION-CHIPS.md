# Test Report: INBOX-DIRECTION-CHIPS

> Created at: `2026-08-08T21:37:10+09:00`
> GitHub Issue: `#80`
> Branch: `feat/gh-80-inbox-direction-chips`
> Commit: `ae3e956` (구현·테스트는 커밋 전 작업 트리에서 실행 후 이 커밋 위에 반영)

## 1. Executive summary

- Result: `PASS`
- Tested scope: 방향 칩 집계(`DirectionChip`, `InboxListing`), `InboxQueryRepository`의
  `countByDirection` 신설과 `findInbox`의 방향 필터 인자 추가, 원형 구간 파생 SQL
  (`InboxQuerySql.SEGMENT_JOIN`)과 도메인 `DirectionSegment.contains`의 일치, 카테고리별
  칩 스코프 한정, 칩 count와 필터 목록 건수 일치, 만료·차단·`SKIPPED`의 목록/칩 동일
  제외, 미지 `segmentKey` 처리, ACTIVE 스킴 선택(`qello.direction.scheme-code`)과 복수
  ACTIVE 스킴에서의 중복 집계 방어, 기존 `InboxQueryIntegrationTest`·
  `InboxSentPostWriteIntegrationTest` 회귀
- Unverified scope: 없음. 계획된 모든 P0/P1/P2 시나리오를 구현하고 실행했다.
  `docs/product/data-model/*` 문서 동기화는 이번 이슈 범위에 없다(DB 변경 없음).
- Release recommendation: 단위·통합 테스트 전부 통과, `./harness check`·
  `./harness pr-ready --project-tests`·`git diff --check` 모두 통과. 완료 조건
  전 항목 충족. PR 진행 가능.

## 2. Environment

런타임과 도구 버전만 기록한다. `.env` 값, 토큰, 서버 주소, 계정/IAM 식별자는
기록하지 않는다.

| Item | Version / safe description |
| --- | --- |
| Java | 21 (Gradle toolchain, `JavaLanguageVersion.of(21)`) |
| Spring Boot | 3.5.16 |
| Database | Testcontainers `postgis/postgis` (통합 테스트 전용, 로컬 Docker) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| Unit (`./gradlew test`) | PASS | 160 tests, 0 failed (기존 156 + 신규 4) | 2.4s | `build/test-results/test/*.xml` |
| Integration (`./gradlew integrationTest`) | PASS | 153 tests, 0 failed (기존 138 + `#89` 회귀분 5 + 신규 10) | 5.3s(테스트 실행) / 약 1m 20s(컨테이너 기동 포함 전체) | `build/test-results/integrationTest/*.xml` |
| `./harness check` | PASS | Secret preflight 525 파일, JUnit 정책 54 파일 | — | 명령 출력 |
| `./harness pr-ready --project-tests` | PASS | 위 unit/integration 재확인 포함 | — | 명령 출력 |
| `git diff --check` | PASS | 공백 오류 없음 | — | exit code 0 |

첫 실행부터 신규 시나리오 14개(단위 4 + 통합 10) 전부 통과했다. 계획 대비 재현·수정
사이클은 없었다 — §5에 그 이유를 기록한다.

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `DirectionChipTest.rejectsBlankSegmentKey` | `IllegalArgumentException`(도메인 에러코드 아님 — 조회 계층 내부 값이라 사용자 입력 검증과 다른 경로) |
| UNIT-002 | PASS | `DirectionChipTest.rejectsBlankDisplayName`, `.rejectsNegativeCountAndSortOrder` | `count == 0`은 허용됨을 함께 확인 |
| UNIT-003 | PASS | `DirectionChipTest.inboxListingDefensivelyCopiesAndReturnsImmutableLists` | 원본 리스트 변경이 반영 안 됨 + 반환 리스트 `add` 시 `UnsupportedOperationException` |
| UNIT-004 | PASS | `FeedPersistenceBoundaryTest.viewsAndPortsRemainIndependent` | 별도 assertion 추가 없이 디렉터리 순회로 신규 view 2종이 자동 포함되어 통과 |
| INT-001 | PASS | `InboxDirectionChipIntegrationTest.northSegmentWrapsAroundZeroDegrees` | 350°·10°·0° 모두 `N` 칩 하나에 count 3 |
| INT-002 | PASS | `.boundaryAnglesBelongToExactlyOneSegment` | 8개 경계각 각각과 `경계각 - 0.001`을 삽입, 칩 count 합이 정확히 16(경계 중복·누락 없음) |
| INT-003 | PASS | `.sqlDerivedSegmentMatchesDomainContainsAcrossSweep` | 대표각 8 + 경계각 8 + 경계 직전각 8 + 원형 케이스 4 = 28개 방위각 전부에서 SQL 필터 결과가 도메인 `DirectionSegment.contains` 판정과 일치 |
| INT-004 | PASS | `.excludesZeroCountDirectionsFromChips` | 0건 방향(6개)이 칩 목록에 없음 |
| INT-005 | PASS | `.chipAggregationIsScopedToCategory` | `UNANSWERED`는 `N`만, `ANSWERED`는 `S`만 — 카테고리 간 섞임 없음 |
| INT-006 | PASS | `.chipCountMatchesFilteredListSizeForEveryChip` | 반환된 모든 칩을 순회해 count == 필터 목록 크기 확인, 칩 count 합 == 필터 없는 목록 크기 확인 |
| INT-007 | PASS | `.expiredBlockedAndSkippedAreExcludedFromBothListAndChips` | 정상 1건 + `SKIPPED`/만료/차단 3건 중, 목록과 `N` 칩 모두 1건만 반영 |
| INT-008 | PASS | `.unknownSegmentKeyYieldsEmptyListButChipsRemain` | 미지 키에서 예외 없이 빈 목록, 칩은 `N`·`E` 그대로 |
| INT-009 | PASS | `.unfilteredListIsUnaffectedByMissingActiveScheme` | 시드 스킴을 `INACTIVE`로 바꿔도 필터 없는 목록은 그대로, 칩만 빈 리스트. `finally`에서 스킴 상태 원상 복구 확인 |
| INT-010 | PASS | `.otherActiveSchemeDoesNotCauseDuplicateAggregation` | 다른 code의 ACTIVE 스킴을 추가해도 `N` 칩 count가 2로 중복되지 않고 1 유지 — `qello.direction.scheme-code` 설정이 실제로 스킴을 고정함을 확인. `finally`에서 추가 스킴·세그먼트 삭제 확인 |
| INT-011 | PASS | `InboxQueryIntegrationTest`(11개 재실행), `InboxSentPostWriteIntegrationTest`(28개 재실행) | 방향 필터를 `null`로 넘기는 `cards(...)` 헬퍼로 시그니처 교체를 흡수, 기존 assertion 전부 무수정 통과 |

## 5. Failures and diagnostics

계획 단계에서 예상했던 세 가지 실패 모드(§4 Risk inventory) 중 실제로 재현된 것은
없었다. 구현 순서를 다음과 같이 잡은 것이 원인으로 보인다.

1. `InboxQuerySql.SEGMENT_JOIN`을 `findInbox`(필터 있을 때)와
   `SELECT_CHIP_AGGREGATE`(칩 집계)가 **같은 상수를 그대로 재사용**하도록 먼저
   설계했다 — 원형 판정 SQL을 두 번 작성하지 않았으므로 INT-006류의 count 불일치가
   애초에 발생할 여지가 없었다.
2. `MOD(... + 720, 360) < width` 공식을 코드 작성 전에 손으로 8개 구간에 대해
   검산(§`TASK.md` Scope)한 뒤 구현했다 — INT-001·INT-002·INT-003이 첫 실행에
   통과한 것이 그 검산이 맞았음을 확인해준다.

재현·수정이 필요했던 오류는 없다. 컴파일 오류나 assertion 실패로 재시도한 이력이
없다(첫 `./gradlew compileJava compileTestJava compileIntegrationTestJava`부터
성공, 첫 테스트 실행부터 전부 PASS).

## 6. Potential issues

### Application code

- `SELECT_CHIP_AGGREGATE`의 세그먼트 조인은 인덱스를 타지 않는 `MOD` 표현식이다.
  다만 이미 `recipient_id`·`status`로 좁혀진 행(수신 상한 또는 `ANSWERED` 만료 창
  이내 건수)에만 평가되므로 8배 정도의 산술 비용이며, 같은 요청에서 함께 도는
  `SELECT_CARD`의 카드당 상관 서브쿼리 4개보다 가볍다. 별도 인덱스 없이도 현재
  규모에서는 문제가 없다고 판단했다 — 측정 근거는 코드 리뷰 시점의 정성 분석이며
  부하 테스트는 이번 범위 밖이다.
- `#80` 범위상 controller/DTO가 없어 `InboxQueryService.list(...)`의 새 인자
  (`directionSegmentKey`)를 실제로 채워 보내는 호출 경로는 아직 없다. API 계층이
  붙을 때 `recipientId`를 인증 토큰에서만 얻고 `at`을 서버 `Clock`에서만 얻어야
  한다는 제약(과거 논의에서 확인)을 그 작업의 완료 조건에 반드시 포함해야 한다.

### Infrastructure and resource limits

- 해당 없음. 인프라 변경 없음.

### Database and migrations

- 신규 마이그레이션 없음. `#78`의 V8 컬럼과 `#39` 시점 V1 시드(`OCTANT`)를 그대로
  썼다. INT-009·INT-010이 시드 `direction_scheme`을 일시적으로 변경하지만 각각
  `finally` 블록에서 원상 복구하며, 실행 후 확인 결과 잔여 변경이 남지 않았다
  (전체 스위트 재실행 시 다른 테스트에 영향 없음을 §4 INT-011 재실행으로 간접
  확인).

### Concurrency and idempotency

- `findInbox`와 `countByDirection`은 같은 `@Transactional(readOnly = true)`
  안에서 실행되지만 PostgreSQL 기본 격리 수준(READ COMMITTED)에서는 두 쿼리가
  서로 다른 스냅샷을 본다. 조회 도중 답변이 새로 공개되거나 만료 시각이 지나면
  칩 count와 목록 건수가 순간적으로 1 어긋날 수 있다. 테스트는 고정 `Instant`와
  단일 스레드라 이 경합을 재현하지 않으며, 계획 단계에서부터 "결함이 아니라
  알려진 동작"으로 분류했다(§7 Cross-cutting, 테스트 계획 문서). 막으려면 이
  메서드만 `REPEATABLE READ`로 올리거나 두 쿼리를 `GROUPING SETS`로 합쳐야 하는데,
  이득 대비 구현·유지비용이 커 이번 범위에서 보류한다.

### Transactions and event ordering

- 두 쿼리 모두 읽기 전용이며 이벤트를 발행하지 않는다. 트랜잭션 순서 문제 없음.

### External APIs

- 해당 없음. 외부 호출 없음.

### Failure recovery and reconciliation

- ACTIVE 스킴이 전혀 없거나(`INACTIVE`로 바뀜) 설정된 `schemeCode`에 해당하는
  스킴이 없으면 칩은 예외 없이 빈 리스트가 된다(INT-009로 확인). 목록은 스킴과
  무관하게 온전하다 — 칩 하나의 데이터 결손이 수신함 전체를 막지 않는다.
- 스킴이 360도를 완전히 덮지 못하도록 잘못 구성되면(구간 사이 간격), 그 간격에
  속한 `inbound_bearing_deg`를 가진 행은 방향 필터를 걸었을 때만 결과에서
  빠진다. 필터 없는 목록에는 영향이 없다(INT-009와 같은 원리). 이 상태 자체를
  막는 `validateCoverage` 검증은 스킴 저장 경로(`direction` 패키지)의 책임이며
  이번 이슈 범위 밖이다.

## 7. Regression and residual risk

- 기존 `InboxQueryIntegrationTest`(11개)와 `InboxSentPostWriteIntegrationTest`
  (28개)가 시그니처 변경(`findInbox`에 `directionSegmentKey` 추가,
  `InboxQueryService.list`가 `InboxListing` 반환) 이후에도 무수정 assertion으로
  전부 통과했다 — 회귀 없음.
- 잔여 위험: `ANSWERED` 카테고리는 수신 상한의 통제를 받지 않고 만료 창 안에서
  계속 누적된다(`#79` 이후의 기존 특성이며 이번 이슈가 만들지 않았다). 이 카테고리가
  커지면 `SELECT_CARD`의 카드당 서브쿼리 4개가 먼저 비용 문제를 일으킬 곳이고,
  `SELECT_CHIP_AGGREGATE`의 `MOD` 연산은 그보다 가볍다. 후속 이슈로 관찰이 필요하다.
- 잔여 위험: §6 동시성 항목의 스냅샷 불일치는 운영에서 배지 숫자가 순간적으로
  어긋나는 정도이며, 현재 설계에서는 의도적으로 남겨둔 트레이드오프다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-80-INBOX-DIRECTION-CHIPS.md`
- CI run: (해당 없음 — 로컬 실행. PR 생성 시 CI 링크로 대체)
- Related ADR: `docs/reports/private/direction/gh-80-inbox-direction-chip.local.md`
  (설계 결정 근거, gitignore 대상 로컬 문서)
- PR: (아직 생성되지 않음 — `/harness-pr`에서 연결)

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 — 없음(전 시나리오 실행)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 — 신규 후속 Issue 없음. `ANSWERED`
      누적에 따른 카드 서브쿼리 비용은 §7에 관찰 필요 항목으로만 기록(신규 Issue
      생성은 PR 리뷰 후 필요시 진행)
- [ ] 실행 결과와 PR 설명이 일치함 — PR 생성 시 확인
