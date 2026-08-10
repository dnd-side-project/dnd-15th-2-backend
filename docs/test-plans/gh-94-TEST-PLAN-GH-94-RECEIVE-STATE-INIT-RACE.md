# Test Plan: TEST-PLAN-GH-94-RECEIVE-STATE-INIT-RACE

> Created at: `2026-08-10T15:15:11+09:00`
> GitHub Issue: `#94`
> Status: Approved

## 1. Objective

수신 상한(F04, D05)은 사용자를 과다 수신에서 보호하는 장치다. 그 상한은
`recipient_receive_state.active_unhandled_count`가 **실제 배달 건수를 정확히
센다**는 전제 위에서만 작동한다.

`DirectionPostService.reserve()`는 그 전제를 깬다.

```java
if (receiveStateRepository.findByUserId(userId).isEmpty()) {
    receiveStateRepository.save(RecipientReceiveState.restore(userId, 0, 0, at, null, at));
}
return receiveStateRepository.reserve(userId, at, receiveProperties.receiveCapacity());
```

SELECT와 INSERT 사이가 원자적이지 않고, `save()`의 UPSERT는 충돌 시
`active_unhandled_count`를 제안값 0으로 **덮어쓴다**. 두 발송 트랜잭션이 같은
신규 사용자를 동시에 잡으면 둘 다 `findByUserId()`에서 empty를 받고, 나중에
커밋하는 쪽의 `save()`가 먼저 예약된 슬롯을 지운다.

검증할 사용자 가치는 **"수신 상한이 실제로 상한으로 동작한다"** 이다.

실패 시 위험은 두 방향으로 갈린다.

1. **카운터가 실제보다 낮아지는 방향(현재 상태)** — 사용자가 상한을 초과해
   질문글을 받는다. `recent_received_count`와 `last_received_at`도 같이
   초기화되므로, 이 두 값을 근거로 하는 후속 기능(수신 빈도 제어, 최근 수신
   기준 정렬)도 함께 어긋난다. 화면에는 "글이 좀 많이 오네" 정도로만 보이고
   조용히 누적된다.
2. **수정이 도입할 새 위험** — 초기화를 원자적으로 만들면서 `reserve()`의
   조건부 성립(`active_unhandled_count < :activeLimit`)이 INSERT 경로와
   CONFLICT 경로에서 다르게 판정되면, 반대로 **상한 도달 사용자에게도 슬롯을
   내주거나**, 정상 사용자에게 `false`를 돌려 배달을 누락시킨다. `reserve()`의
   반환값은 `send()`에서 그대로 수신자 목록 필터로 쓰이므로(`.filter(candidate ->
   reserve(...))`), 잘못된 `true`는 `post_recipient` 행까지 만들어 카운터와
   실제 행 수가 어긋나는 2차 오염을 만든다.

이 계획은 두 방향을 모두 고정한다.

### 이 결함이 단위 테스트로 관측 불가능하다는 점

이 결함은 Java 로직이 아니라 **두 SQL 문 사이의 원자성 부재**에 있다.
`RecipientReceiveStateRepository`를 stub으로 대체한 `DirectionPostService`
단위 테스트는 현재의 버그 있는 코드에서도 통과한다 — stub은 "두 트랜잭션이
동시에 empty를 본다"를 재현할 수 없기 때문이다.

따라서 이 계획의 P0 증거는 전부 실제 PostgreSQL 컨테이너를 쓰는 통합
시나리오다. 단위 시나리오는 결함 자체가 아니라 **결함이 되돌아오는 것을 막는
구조적 가드**로만 둔다(§5).

### 구현되지 않은 동작에 대한 전제

이슈는 두 가지 수정 방향을 열어뒀다. `TASK.md`는 그중
**`reserve()` 자체를 단일 UPSERT로 합치는 쪽**을 기본안으로 고정했다.

이 계획의 시나리오는 **관측 가능한 행동**(호출 후 DB의 최종 상태와 반환값)으로
서술했으므로 Executor가 다른 형태를 고르더라도 그대로 성립한다. 다음 두 형태를
모두 허용한다.

- **형태 A (기본안)**: `RecipientReceiveStateRepository.reserve(userId, at, activeLimit)`가
  단일 `INSERT ... ON CONFLICT (user_id) DO UPDATE ... WHERE ...` 문이 되고,
  `DirectionPostService.reserve()`의 `findByUserId`/`save` 2단계가 사라진다.
- **형태 B**: 초기화 전용 메서드(가칭 `initializeIfAbsent(userId, at)`)가
  `ON CONFLICT DO NOTHING`으로 추가되고, `reserve()`는 지금의 조건부 UPDATE를
  유지한다.

형태 B를 고를 경우 §5의 UNIT-002와 §6의 INT-007이 "`save()`를 프로덕션
초기화에 쓰지 않는다"를 검증하는 지점이 된다.

> **결정(2026-08-10)**: 사람의 승인과 함께 **형태 A**로 확정됐다. 이 계획의
> 나머지 서술은 형태 A를 전제로 읽는다.

### red 재현의 결정성

수정 전 실패(red)의 결정성이 시나리오마다 다르다. 이 차이를 미리 고정한다.

- **INT-002는 결정적으로 실패한다.** 현재 리포지토리의 `reserve()`는 조건부
  UPDATE 하나뿐이라 행이 없으면 0행을 갱신하고 `false`를 반환한다 — 행 자체가
  생기지 않으므로 최종 카운터는 0이다. 수정 후에는 두 호출 모두 `true`,
  카운터 2가 된다. 이 시나리오가 "수정 전 red" 기록의 근거다.
- **INT-001은 확률적으로 실패한다.** 파괴적 덮어쓰기는 두 트랜잭션이 **모두**
  `findByUserId()`에서 empty를 본 경우에만 발현한다. 한쪽이 완전히 커밋한 뒤
  다른 쪽이 조회하면 `save()`를 건너뛰므로 결함이 드러나지 않는다. 따라서
  INT-001의 red는 관측되면 기록하되, **관측되지 않았다고 해서 결함이 없다고
  판단하지 않는다**. INT-001의 가치는 수정 후 두 인터리빙 모두에서
  결정적으로 green이 된다는 데 있다.
- 나머지 시나리오는 수정 전후 모두 통과하는 **가드**다. red를 기대하지 않는다.

## 2. Scope

### Included

- 기존 `recipient_receive_state` 행이 있는 사용자를 대상으로 예약할 때
  `active_unhandled_count` / `recent_received_count` / `last_received_at` /
  `recent_window_started_at`이 초기화되지 않고 증가·유지되는지.
- 행이 없는 신규 사용자를 대상으로 한 **동시 예약**에서 최종 카운터가 실제
  배달 건수와 일치하는지(이슈 재현 절차 그대로).
- 상한에 도달한 사용자에 대한 **동시 예약**에서 상한을 넘겨 예약되지 않는지.
- `reserve()` 반환값의 의미 보존 — "이 호출이 슬롯을 실제로 점유했는가".
  상한 도달 시 `false`, 신규 행 생성 시 `true`.
- 반환값과 실제 부수효과의 일치 — `false`를 받은 호출이 카운터를 건드리지
  않고, `true`를 받은 호출이 정확히 1만 올린다.
- `DirectionPostService.send()` end-to-end에서 카운터와 `post_recipient` 행
  수가 일치하는지.
- DB 제약과의 정합 — `ck_recipient_receive_state_active_count`(0~50),
  `ck_recipient_receive_state_last_received`(`last_received_at >=
  recent_window_started_at`), `fk_recipient_receive_state_user`.
- `release()`의 동작·시그니처 불변(회귀 가드, `#93` 보호).
- `save()`의 덮어쓰기 계약 불변(회귀 가드, 기존 통합 테스트 시더 보호).

### Excluded

- **슬롯 해제 경로 복구(만료·넘김확정·차단) — `#93`.** `#93`은
  `release()`만 호출한다. 이 계획은 `release()`를 **변경하지 않는다는 사실만**
  회귀 가드로 고정하고(INT-011), 해제 경로의 정확성은 검증하지 않는다.
  `#93`의 `ReceiveSlotReleaseIntegrationTest`와 이 계획의 테스트 파일은
  겹치지 않는다.
- 수신자 선정 규칙(차단·계정 상태 필터, 인원 상한, 분산 정렬) — `#97`.
- 수신 상한값(`receive-capacity`) 자체의 조정. 이 계획은 현재 설정값 5를
  전제로만 검증한다. DB 안전 상한과 애플리케이션 검증 범위는 이미 50으로
  일치하므로(§3 확인된 전제) 불일치를 다룰 필요가 없다.
- `recent_window_started_at`의 **rolling window 갱신 로직**. 현재 어떤 코드도
  이 값을 롤링하지 않는다(초기 생성 후 고정). 이 계획은 "예약이 이 값을
  건드리지 않는다"만 검증하고 롤링 정책은 다루지 않는다.
- 같은 `idempotencyKey`로 두 `send()`가 **동시에** 들어오는 경쟁. 이는
  `direction_post`의 idempotency 제약 문제이지 `recipient_receive_state`
  초기화 경쟁이 아니다. 순차 재시도(이미 `DirectionPostgisPersistenceIntegrationTest`가
  커버)만 회귀 가드로 유지한다.
- 이미 어긋난 카운터를 재계산·복구하는 기능.
- 스키마 변경(DDL). UPSERT 구문만 바꾼다.
- 알림, outbox, API 응답 형태.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue `#94` | 초기 행 생성이 기존 값을 덮어쓰지 않는다. `reserve()`의 조건부 원자성이 초기화 경로에서 깨지지 않는다. 동시 예약 통합 테스트 |
| `TASK.md` (`#94`) | Completion criteria 7개 항목이 이 계획의 시나리오 그룹과 대응한다. 기본안은 형태 A(단일 UPSERT) |
| `direction/service/DirectionPostService.java:147-152` | 결함 지점. `findByUserId().isEmpty()` 후 `save()`, 그 뒤 `reserve()` — 3개 문이 원자적이지 않다 |
| `direction/service/DirectionPostService.java:56,78-81` | `send()`는 `@Transactional`. `reserve()`가 candidate stream의 `filter`로 호출되어 반환값이 곧 수신자 선정 결과다. `false`면 `post_recipient` 행이 만들어지지 않는다 |
| `direction/repository/jdbc/JdbcRecipientReceiveStateRepository.java:24-41` | `save()`의 `ON CONFLICT DO UPDATE SET active_unhandled_count = EXCLUDED....` — 파괴적 UPSERT |
| `direction/repository/jdbc/JdbcRecipientReceiveStateRepository.java:49-61` | `reserve()`의 조건부 UPDATE. `updated == 1`로 성공을 판정하는 기존 계약 |
| `direction/repository/jdbc/JdbcRecipientReceiveStateRepository.java:63-79` | `release()`. 이 작업에서 변경 금지 — `#93`의 `ReceiveSlotReleaseService`가 3개 경로에서 호출 |
| `V1__create_direction_communication_schema.sql:148-164` | `recipient_receive_state` DDL. PK `user_id`(= `ON CONFLICT (user_id)`의 근거), `ck_..._last_received`, `fk_..._user` |
| `V2__add_reactions_and_skip_pending.sql:18-31` | V1의 `ck_..._active_count BETWEEN 0 AND 5`를 **`BETWEEN 0 AND 50`으로 교체**했다. 실효 상한은 DB가 아니라 애플리케이션 설정이 정하고 DB는 안전 상한만 강제한다는 결정 — `RecipientReceiveState.SAFETY_CEILING`(50)과 일치한다. **V1만 읽으면 상한을 5로 오독하게 된다** |
| `direction/config/DirectionReceiveProperties.java` | `receiveCapacity`는 1 이상으로 검증됨 — INSERT 경로가 `active_unhandled_count = 1`을 넣을 때 상한 위반이 구조적으로 불가능하다는 근거 |
| `application.properties:23` | `qello.direction.receive-capacity=5` |
| `InboxSentPostWriteIntegrationTest.java:201,215,398,414,428,460,472` | `save()`가 카운터를 **정확한 값으로 덮어쓰는** 테스트 시더로 7곳에서 쓰인다 — `save()`의 계약을 바꾸면 이 시나리오들이 조용히 무력화된다 |
| `InboxSentPostWriteIntegrationTest.java:426-450` | `ExecutorService(2)` + `CountDownLatch(ready/start)` 동시성 테스트의 기존 관례. 새 동시성 시나리오는 이 구조를 그대로 따른다 |
| `DirectionPostgisPersistenceIntegrationTest.java:121-148` | `send()` end-to-end 관례와 `recipient_receive_state` 직접 seed(`jdbc.update`) 패턴. 재시도 시 카운터가 1로 유지되는 기존 회귀 가드 |
| `integrationTest/resources/application-test.properties:2` | `hikari.maximum-pool-size=4` — 동시 스레드 2개까지가 안전한 상한. 3개 이상 동시 트랜잭션은 pool 고갈로 테스트가 거짓 실패할 수 있다 |
| 명세 F04 / D05 | 활성 미처리 상한 5. "상한은 수신자를 보호한다" |

### 확인된 전제

- **격리 수준은 READ COMMITTED**(PostgreSQL 기본). `application*.properties`와
  `application-test.properties` 어디에도 `isolation` 설정이 없다. 이 수준에서
  `INSERT ... ON CONFLICT DO UPDATE`는 충돌 시 선행 트랜잭션의 커밋을 기다린
  뒤 **갱신된 행 버전**에 대해 `DO UPDATE`를 적용한다 — 형태 A가 성립하는
  근거다. (REPEATABLE READ 이상이었다면 같은 상황이 serialization failure로
  터지므로 재시도 설계가 필요했다.)

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 신규 사용자 동시 예약에서 나중 트랜잭션이 먼저 예약된 슬롯을 0으로 덮어써 사용자가 상한을 초과해 받는다(이슈의 핵심 재현) | High | High | P0 | INT-001, INT-002 |
| 같은 경쟁에서 `recent_received_count`와 `last_received_at`이 초기화되어 수신 이력이 소실된다 | Medium | High | P0 | INT-002, INT-003 |
| 수정이 INSERT 경로에서 상한 검사를 빠뜨려, 상한에 도달한 사용자에게 슬롯을 내준다 | High | Medium | P0 | INT-004, INT-005 |
| 수정이 CONFLICT 경로에서 상한 검사를 지나치게 걸어, 여유 있는 사용자에게 `false`를 돌려 배달을 누락시킨다 | High | Medium | P0 | INT-003, INT-006 |
| `reserve()` 반환값과 실제 부수효과가 어긋나 `post_recipient` 행 수와 카운터가 불일치한다(`send()`의 filter가 반환값을 그대로 신뢰) | High | Medium | P0 | INT-006, INT-008 |
| 단일 UPSERT의 INSERT 경로가 `recent_window_started_at`을 잘못 채워 `ck_..._last_received`를 위반하고 발송 전체가 롤백된다 | High | Medium | P0 | INT-003, INT-007 |
| CONFLICT 경로가 `recent_window_started_at`을 `EXCLUDED` 값으로 덮어써 수신 윈도우가 매 예약마다 리셋된다 | Medium | Medium | P1 | INT-003 |
| 상한 도달로 `false`를 받은 호출이 그래도 `updated_at`이나 `last_received_at`을 건드려, 예약하지 않은 시각이 수신 시각으로 기록된다 | Medium | Medium | P1 | INT-005 |
| 수정이 `release()`의 시그니처나 동작을 함께 바꿔 `#93`의 세 해제 경로가 깨진다 | High | Low | P0 | INT-011 |
| 수정이 `save()`를 `DO NOTHING`으로 바꿔 기존 통합 테스트 7개 시더가 조용히 no-op이 되고, 통과하던 테스트가 무의미해진다 | High | Medium | P0 | INT-010, UNIT-002 |
| 두 트랜잭션이 같은 행을 UPSERT하며 락을 잡은 채 나머지 발송 작업을 진행해 교착 또는 pool 고갈로 발송이 지연·실패한다 | Medium | Low | P1 | INT-002, INT-009 |
| 존재하지 않는 사용자에 대한 예약이 FK 위반으로 발송 트랜잭션 전체를 롤백시킨다 | Low | Low | P2 | INT-012 |
| 향후 누군가 `findByUserId`-then-`save` 2단계 초기화를 다시 도입해 같은 결함이 재발한다 | Medium | Medium | P1 | UNIT-001 |

## 5. Unit scenarios

§1에서 밝힌 대로 이 결함 자체는 단위 테스트로 관측할 수 없다. 아래 두
시나리오는 **결함의 재발을 막는 구조적 가드**이며, `#93`이 수정 중인
`DirectionDomainTest`와 겹치지 않도록 기존
`src/test/java/com/dnd/qello/direction/DirectionPersistenceBoundaryTest.java`에
추가한다(이 파일은 이미 소스 텍스트를 읽어 경계를 검사하는 관례를 갖고 있다).

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-94-RECEIVE-STATE-INIT-RACE-UNIT-001 | `DirectionPostService.java` 소스 | 발송 경로의 수신 상태 초기화 구현을 검사 | `findByUserId(...)`의 결과를 조건으로 `save(...)`를 호출하는 2단계 초기화 패턴이 존재하지 않는다. 형태 A면 `receiveStateRepository`에 대한 호출이 `reserve` 하나로 끝나고, 형태 B면 초기화 호출이 조건문 없이 무조건 실행된다(`ON CONFLICT DO NOTHING`이 조건을 대신하므로) | P1 | Executor 1 |
| …-UNIT-002 | `JdbcRecipientReceiveStateRepository.java` 소스(또는 SQL이 `sql/RecipientReceiveStateSql.java`로 추출된 경우 그 파일) | 예약 경로 SQL과 `save()` SQL을 검사 | (a) 예약 경로 SQL은 `recipient_receive_state`에 대한 **단일 문**이다 — `INSERT`와 `UPDATE`가 별도 문으로 나뉘어 있지 않다. (b) `save()`의 `ON CONFLICT` 절은 여전히 `active_unhandled_count`를 `EXCLUDED` 값으로 덮어쓴다 — 테스트 시더의 덮어쓰기 계약이 보존됐음을 고정한다. (c) `release()`의 SQL 본문이 변경되지 않았다 | P1 | Executor 1 |

> **주의**: UNIT-002는 소스 텍스트 검사라 구현 형태에 민감하다. Executor가 SQL을
> `sql/RecipientReceiveStateSql.java`로 추출하기로 하면(다른 5개 리포지토리의
> 기존 관례와 일치) 검사 대상 경로를 그쪽으로 바꾼다. 추출 여부는 Executor의
> 재량이며 이 계획이 강제하지 않는다. **이 시나리오가 통과한다는 사실만으로
> 원자성이 검증되지는 않는다** — 원자성의 증거는 INT-001·INT-002다.

## 6. Integration scenarios

모두 `PostgisContainerIntegrationTestSupport`를 확장하고 `@SpringBootTest`
`@ActiveProfiles("test")`로 실행한다. 신규 파일
`src/integrationTest/java/com/dnd/qello/ReceiveStateReservationIntegrationTest.java`
하나에 모은다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| …-INT-001 | `DirectionPostService.send()`, `RecipientReceiveStateRepository`, PostGIS | `recipient_receive_state` 행이 **없는** 사용자 R을 두 발송의 공통 후보로 만든다. 서로 다른 sender 2명, 서로 다른 `idempotencyKey`, R은 두 sender 모두의 방향 세그먼트·거리 범위 안에 위치 | `ExecutorService(2)` + `CountDownLatch(ready=2, start=1)`로 두 `send()`를 동시에 실행 | 두 발송 모두 R을 수신자로 잡았다면 `active_unhandled_count == 2`. 한쪽이 상한 등으로 잡지 못했다면 카운터는 실제로 만들어진 `post_recipient` 행 수와 **정확히 같다**. 즉 `SELECT count(*) FROM post_recipient WHERE recipient_id = R` == `active_unhandled_count`. 어느 경우에도 1로 리셋되지 않는다 | `@BeforeEach`에서 관련 테이블 DELETE |
| …-INT-002 | `RecipientReceiveStateRepository` | INT-001과 같되 **저수준으로 좁힌 재현** — 행이 없는 사용자 R에 대해 `reserve()`만 두 트랜잭션에서 동시에 호출(발송 전체가 아님) | 두 스레드가 각자 트랜잭션에서 `reserve(R, at, 5)` 실행 | 두 호출 모두 `true`를 반환하고 `active_unhandled_count == 2`, `recent_received_count == 2`. **현재 구현에서는 결정적으로 실패한다** — 조건부 UPDATE만 있어 행이 없으면 두 호출 모두 `false`이고 행조차 생기지 않는다. 수정 전 red 기록의 근거 | 동일 |
| …-INT-003 | `RecipientReceiveStateRepository` | 기존 행: `active_unhandled_count = 2`, `recent_received_count = 7`, `recent_window_started_at = W`, `last_received_at = L` (`W < L < at`) | `reserve(R, at, 5)` 1회 | `true` 반환. `active_unhandled_count == 3`, `recent_received_count == 8`, `last_received_at == at`, **`recent_window_started_at`은 `W` 그대로**. 초기화 경로가 기존 값을 건드리지 않는다는 완료 조건의 직접 증거 | 동일 |
| …-INT-004 | `RecipientReceiveStateRepository` | 행이 **없는** 사용자 R | `reserve(R, at, 5)` 1회 | `true` 반환. 행이 생성되고 `active_unhandled_count == 1`, `recent_received_count == 1`, `last_received_at == at`, `recent_window_started_at <= at`(`ck_..._last_received` 충족). 커밋이 제약 위반 없이 성공한다 | 동일 |
| …-INT-005 | `RecipientReceiveStateRepository` | 기존 행: `active_unhandled_count = 5`(= 설정 상한), `recent_received_count = 5`, `last_received_at = L` | `reserve(R, at, 5)` 1회 | `false` 반환. 세 값(`active_unhandled_count`, `recent_received_count`, `last_received_at`) 모두 **변하지 않는다**. 특히 `last_received_at`이 `at`으로 갱신되지 않는다 — 예약하지 않은 시각이 수신 시각으로 기록되면 안 된다 | 동일 |
| …-INT-006 | `RecipientReceiveStateRepository` | 행이 없는 사용자 R, 상한 3 | `reserve(R, at1, 3)` → `reserve(R, at2, 3)` → `reserve(R, at3, 3)` → `reserve(R, at4, 3)` 순차 4회 | 반환값이 `true, true, true, false`. 최종 `active_unhandled_count == 3`. 반환값과 부수효과가 매 호출마다 일치한다 | 동일 |
| …-INT-007 | `RecipientReceiveStateRepository` | 행이 **없는** 사용자 R, 상한 5 | 같은 사용자에 대해 행이 없는 상태에서 `reserve()`를 두 트랜잭션이 동시에 실행하되 **한쪽만 커밋하고 다른 쪽은 롤백** | 커밋한 쪽의 효과만 남아 `active_unhandled_count == 1`. 롤백한 쪽이 만든 행이 유령으로 남지 않는다(행 자체가 롤백되거나, 행은 남되 카운터가 커밋된 예약만 반영) | 동일 |
| …-INT-008 | `DirectionPostService.send()` | 상한 5 설정, 후보 R 한 명, R의 행이 없음 | `send()`를 서로 다른 `idempotencyKey`로 6회 **순차** 실행 | 앞의 5회는 R을 수신자로 포함하고 6회차는 포함하지 않는다. `active_unhandled_count == 5`이고 `SELECT count(*) FROM post_recipient WHERE recipient_id = R` == 5. 카운터와 실제 배달 행 수가 일치한다. 이슈의 "상한 초과 예약 금지" 완료 조건 | 동일 |
| …-INT-009 | `DirectionPostService.send()` | 상한 5, R의 `active_unhandled_count = 4`(행 존재) | 서로 다른 sender 2명이 R을 공통 후보로 동시 `send()` | 정확히 한쪽만 R을 수신자로 잡는다. `active_unhandled_count == 5`(6이 아니다). `post_recipient` 행도 R에 대해 정확히 1건 추가. 두 트랜잭션 모두 예외 없이 커밋된다(교착·타임아웃 없음, 10초 내 완료) | 동일 |
| …-INT-010 | `RecipientReceiveStateRepository.save()` | 기존 행: `active_unhandled_count = 3`, `recent_received_count = 9` | `save(RecipientReceiveState.restore(R, 1, 1, W, L, U))` 호출 | 세 값이 **인자대로 덮어써진다**(`1, 1, L`). `save()`의 덮어쓰기 계약이 보존됐음을 고정 — 이게 깨지면 `InboxSentPostWriteIntegrationTest`의 시더 7곳이 조용히 무력화된다 | 동일 |
| …-INT-011 | `RecipientReceiveStateRepository.release()` | 기존 행: `active_unhandled_count = 2` | `release(R, at)` 1회, 이어서 카운터가 0인 사용자에 대해 `release()` 1회 | 첫 호출은 `true`, `active_unhandled_count == 1`, `updated_at == at`. 0인 사용자에 대한 호출은 `false`이고 카운터가 음수로 내려가지 않는다. **`#93`이 의존하는 계약이 이 작업에서 변경되지 않았다는 회귀 가드** | 동일 |
| …-INT-012 | `RecipientReceiveStateRepository` | `user_account`에 없는 `userId` | `reserve(존재하지 않는 userId, at, 5)` | `DataAccessException`(FK 위반). 조용히 `false`를 반환하며 삼키지 않는다 — 잘못된 후보가 배달 누락으로 위장되면 안 된다 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- `recipient_receive_state`의 PK는 `user_id` 단일 컬럼이므로 `ON CONFLICT
  (user_id)`가 유일한 충돌 대상이다. 부분 인덱스나 복합 제약이 없어 추론 절
  선택의 모호성이 없다(INT-004).
- 단일 UPSERT의 INSERT 경로는 `ck_recipient_receive_state_last_received`
  (`last_received_at IS NULL OR last_received_at >= recent_window_started_at`)를
  만족해야 한다. `recent_window_started_at`을 `at`보다 늦게 잡으면 첫 예약부터
  커밋이 거부된다(INT-004).
- `ck_recipient_receive_state_active_count`는 `BETWEEN 0 AND 50`인 **즉시 검사**
  제약이다(V2에서 교체). 실효 상한(설정값 5)은 SQL의 `WHERE`가 강제하고 DB는
  안전 상한만 본다. 상한 판정이 `WHERE`에서 빠지면 6번째 예약이 `false` 대신
  카운터를 6으로 올려버리고, DB 제약은 50까지 허용하므로 이를 잡아주지
  못한다 — 조용히 상한을 넘긴다(INT-008).
- `send()`는 `@Transactional`이고 `reserve()`는 후보 stream 안에서 호출된다.
  UPSERT가 잡은 행 락은 발송 트랜잭션이 끝날 때까지 유지된다 — 두 발송이 같은
  수신자를 노리면 뒤쪽이 앞쪽의 커밋까지 블로킹된다. INT-009는 이 블로킹이
  **교착이나 타임아웃이 아니라 정상 직렬화로 끝나는지** 확인한다.
- 롤백 시 부분 반영이 남지 않아야 한다(INT-007).

### Concurrency and idempotency

- 격리 수준은 READ COMMITTED(§3 확인된 전제). 이 수준에서
  `ON CONFLICT DO UPDATE`는 선행 커밋을 기다린 뒤 최신 행에 적용되므로 재시도
  없이 정확하다. **Executor가 어떤 이유로든 격리 수준을 올리면 이 전제가 깨지고
  serialization failure 재시도 설계가 필요해진다** — 격리 수준을 바꾸지 않는다.
- `reserve()`는 **멱등이 아니다**. 두 번 부르면 두 번 예약하는 것이 옳다(서로
  다른 두 질문글이 배달됐다는 뜻). 멱등성은 상위 `send()`의 `idempotencyKey`가
  담당한다. INT-006이 이 구분을 고정한다.
- 동시 스레드는 **2개까지만** 쓴다. `application-test.properties`의
  `hikari.maximum-pool-size=4`이고 각 스레드가 트랜잭션 하나씩 커넥션을 잡으므로
  3개 이상은 pool 고갈로 거짓 실패를 만든다.
- 동시성 시나리오는 `InboxSentPostWriteIntegrationTest:426-450`의
  `ready`/`start` 이중 래치 구조를 그대로 따른다. `ready.await(5s)`로 두 스레드가
  모두 준비된 것을 확인한 뒤 `start.countDown()`으로 동시에 푼다.

### External APIs

- 해당 없음. 이 변경은 외부 연동 경계를 건드리지 않는다. LocalStack이나 알림
  double이 필요 없다.

### Failure recovery and reconciliation

- 이미 어긋난 카운터의 복구는 범위 밖(§2). 이 계획은 **정상 경로에서 새로
  어긋나지 않는지**만 본다.
- 카운터와 실제 `post_recipient` 행 수의 일치는 이 결함군의 유일한 관측 가능한
  불변식이다. INT-001과 INT-008이 이 불변식을 직접 단언한다 — 카운터만 보는
  단언은 "둘 다 틀렸지만 서로 일치하는" 상태를 놓친다.
- FK 위반이 발송 트랜잭션 전체를 롤백시키는 것은 **의도된 동작**이다. 부분
  배달보다 전체 실패가 낫다(INT-012).

## 8. Test data and isolation

- **Fixtures**: `DirectionPostgisPersistenceIntegrationTest`의 헬퍼
  (`createUser`, `createActiveQuestion`, `createEightSegmentScheme`, presence
  저장)를 같은 형태로 신규 테스트 클래스에 둔다. 기존 클래스를 수정하지 않는다
  — 파일 소유권 분리를 위해 복제를 허용한다.
- **Region code**: 다른 통합 테스트와 충돌하지 않는 전용 코드
  (예: `TEST-DIRECTION-94`)를 쓰고 `@BeforeEach`에서 재생성한다.
- **Database isolation**: `PostgisContainerIntegrationTestSupport`의 공유
  컨테이너를 쓴다. `@BeforeEach`에서 `post_recipient` → `post_audience` →
  `direction_post` → `recipient_receive_state` → `active_user_presence` →
  `direction_segment` → `direction_scheme` → `approved_question` →
  `user_account`(전용 region) → `region_code`(전용) 순으로 FK 역순 DELETE.
  `DirectionPostgisPersistenceIntegrationTest.reset()`과 같은 순서다.
  `@DirtiesContext(AFTER_CLASS)`를 붙인다.
- **Clock/randomness**: 고정 `Instant`만 쓴다(`Instant.parse(...)`).
  `clock_timestamp()`에 의존하는 단언은 만들지 않는다 — `updated_at`은 값
  자체가 아니라 "변했다/안 변했다"로만 검증한다. 단, `release()`의 `updated_at`은
  인자로 받으므로 값 단언이 가능하다(INT-011).
- **동시성 제어**: `CountDownLatch` 2개(ready/start), `ExecutorService(2)`,
  `Future.get(10, SECONDS)`. `finally`에서 `executor.shutdownNow()`.
  타임아웃이 나면 실패로 처리하고 재시도하지 않는다.
- **External API doubles**: 없음.
- **Cleanup**: `@BeforeEach` DELETE로 충분하다. 다른 테스트 클래스가 만든
  데이터는 건드리지 않는다(전용 region code로 격리).

`#93` 작업이 `../dnd-15th-2-backend`에서 동시에 진행 중이다. **두 워크트리에서
`integrationTest`를 동시에 구동하지 않는다** — `PostgisContainerIntegrationTestSupport`가
공유 컨테이너를 쓰므로 테이블 DELETE가 서로의 데이터를 지운다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor 1 (production fix) | `src/main/java/com/dnd/qello/direction/repository/jdbc/JdbcRecipientReceiveStateRepository.java`, `src/main/java/com/dnd/qello/direction/repository/RecipientReceiveStateRepository.java`, `src/main/java/com/dnd/qello/direction/service/DirectionPostService.java`, (선택) `src/main/java/com/dnd/qello/direction/repository/jdbc/sql/RecipientReceiveStateSql.java` | — (구현) | `./gradlew compileJava` |
| 2 | Executor 1 (unit guard) | `src/test/java/com/dnd/qello/direction/DirectionPersistenceBoundaryTest.java` (append) | UNIT-001, UNIT-002 | `./gradlew test --tests "*DirectionPersistenceBoundaryTest"` |
| 3 | Executor 2 (integration) | 신규 `src/integrationTest/java/com/dnd/qello/ReceiveStateReservationIntegrationTest.java` | INT-001 ~ INT-012 | `./gradlew integrationTest --tests "*ReceiveStateReservationIntegrationTest"` |
| 4 | Executor 2 (regression) | — (기존 파일 수정 없음) | 기존 통합 테스트 회귀 확인 | `./gradlew integrationTest --tests "*InboxSentPostWriteIntegrationTest" --tests "*DirectionPostgisPersistenceIntegrationTest"` |
| 5 | 통합 | — | 전체 | `./harness check`, `./harness pr-ready --project-tests`, `git diff --check` |

### 소유권 분리

- **`#93`과 겹치는 파일 없음.** `#93`은 `PostRecipient*`, `SafetyService`,
  `SkipConfirmationProperties`, `ReceiveSlotReleaseService`,
  `DirectionDomainTest`, `application.properties`를 소유한다. 이 계획의 어느
  Executor도 그 파일들을 수정하지 않는다.
- `#93`의 통합 테스트는 신규 `ReceiveSlotReleaseIntegrationTest`,
  이 계획의 통합 테스트는 신규 `ReceiveStateReservationIntegrationTest`로
  파일명이 다르다.
- 단위 테스트도 분리된다 — `#93`은 `DirectionDomainTest`, 이 계획은
  `DirectionPersistenceBoundaryTest`.
- **`release()`는 이 작업에서 변경하지 않는다.** Executor 1의 소유 파일에
  `JdbcRecipientReceiveStateRepository.java`가 포함되지만, `release()` 메서드
  본문과 인터페이스 시그니처는 손대지 않는다. INT-011이 이를 강제한다.

### 실패 판단 기준

- INT-001, INT-002, INT-004, INT-005, INT-006, INT-008이 하나라도 실패하면
  수정이 완결되지 않은 것으로 본다(P0).
- INT-002는 **수정 전에 반드시 실패해야 한다**. 수정 전에도 통과한다면 재현
  조건이 잘못 구성된 것이므로 시나리오를 다시 설계한다.
- INT-011이 실패하면 `#93`을 깨뜨린 것이므로 즉시 되돌린다.
- 동시성 시나리오가 간헐적으로 실패하면 통과로 간주하지 않는다. 원인을
  규명하기 전까지 `FAIL`로 보고한다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 수정 전 INT-002의 red 확인 기록
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer:
- Decision:
- Approved at:
