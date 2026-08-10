# Test Plan: TEST-PLAN-GH-93-RELEASE-RECEIVE-SLOTS

> Created at: `2026-08-10T14:45:23+09:00`
> GitHub Issue: `#93`
> Status: Approved

## 1. Objective

명세 F04는 수신 슬롯(`recipient_receive_state.active_unhandled_count`)이
`답변 / 넘김 확정 / 만료` 세 경로에서 정확히 한 번 해제된다고 규정한다.
현재 `RecipientReceiveStateRepository.release()`를 호출하는 곳은
`AnswerNotificationService.releaseSlot()` 한 곳뿐이다. `PostRecipient.confirmSkip()`은
도메인에 존재하지만 프로덕션 코드에 호출자가 없고, 만료 전이 메서드와 차단 전이
메서드는 아예 없다.

검증할 사용자 가치는 "답을 하지 않은 사용자가 영구히 신규 수신에서 제외되지
않는다"이다. 실패 시 위험은 두 방향으로 갈린다.

1. **해제가 안 되는 방향(현재 상태)**: 슬롯이 영원히 반환되지 않아 수신 상한이
   보호 장치가 아니라 영구 차단 장치로 오작동한다. 이번 이슈의 핵심 결함이다.
2. **해제가 잘못되는 방향(구현이 도입할 새 위험)**: 세 경로가 같은 행을 두 번
   해제하거나, 서로 다른 두 경로(만료 sweep와 넘김 확정 sweep)가 같은 행을
   동시에 건드리거나, 이미 `ANSWERED`인 행을 실수로 다시 전이시키면
   `active_unhandled_count`가 실제 미처리 건수와 어긋난다. 이 어긋남은
   화면에서 바로 드러나지 않고 조용히 누적된다.

이 계획은 새로 추가될 `PostRecipient` 전이 메서드(만료·차단)와 기존
`confirmSkip()`을 실제로 호출하는 경로가 이 두 실패 방향을 모두 피하는지
검증하는 시나리오를 정의한다.

### 구현되지 않은 동작에 대한 전제

다음은 아직 코드에 없고 이번 이슈의 구현 대상이다. 이 계획의 시나리오는 이
전제 위에서 설계됐으며, Executor가 실제 메서드/설정 이름을 다르게 정하면
시나리오의 의도(주어진 이름이 아니라 검증하려는 행동)를 보존한 채 시그니처만
맞추면 된다.

- `PostRecipient`에 만료 전이 메서드(가칭 `expire(Instant at)`)가 추가된다.
  기존 `answered()`/`open()`과 같은 멱등·상태 검증 패턴을 따른다고 가정한다.
- `PostRecipient`에 차단 전이 메서드(가칭 `block(Instant at)`)가 추가된다.
- 넘김 되돌리기 유예 시간이 설정값으로 노출된다(가칭
  `qello.direction.skip-confirmation-grace-seconds`). `DirectionReceiveProperties`가
  수신 상한을 다루는 방식과 동일하게 코드 상수가 아니라 `@ConfigurationProperties`로
  주입된다고 가정한다.
- 세 경로를 실제로 구동하는 진입점(스케줄러 또는 `SKIP_CONFIRMATION_DUE` outbox
  소비자)의 구체적 형태는 이슈 본문이 "구현 시 결정"으로 열어뒀다. 이 계획은
  진입점 형태에 의존하지 않고, **그 진입점이 호출할 서비스/리포지토리 계층의
  net effect**(어떤 행이 전이됐고 카운터가 얼마나 바뀌었는가)를 검증한다.

## 2. Scope

### Included

- `PostRecipient` 신규 전이 메서드(만료, 차단)의 도메인 불변식 — 상태 검증,
  타임스탬프 동치, 멱등성.
- 기존 `PostRecipient.confirmSkip()`을 실제로 호출하는 서비스/리포지토리 경로.
- 세 전이(만료/넘김확정/차단) 각각이 `recipient_receive_state.active_unhandled_count`를
  정확히 1 감소시키는지.
- 이미 terminal 상태(`ANSWERED`/`SKIPPED`/`EXPIRED`/`BLOCKED`)인 행에 같은 전이를
  재실행해도 카운터가 다시 감소하지 않는지(멱등성).
- 넘김 유예 시간이 지나기 전에는 확정되지 않고, 유예 중 되돌리면 확정 대상에서
  빠지는지.
- 차단 전이의 방향성 — `ub.blocker_id = :recipientId` 필터 관례(§3 근거 참고)와
  일치하게, **차단한 사람 자신의** 수신 항목만 대상이 되고 차단당한 사람의
  수신 항목은 건드리지 않는지.
- 차단 전이가 이미 `ANSWERED`인 행을 건드리지 않는지.
- 만료 전이가 `ANSWERED`인 행을 건드리지 않는지(만료는 새 답변만 막는다는
  F04 원칙과 별개로, 슬롯 해제 관점에서도 이미 해제된 행은 재처리 대상이
  아니다).
- 만료 sweep과 넘김 확정 sweep이 같은 행을 동시에 대상으로 잡는 경쟁 상황.
- `ct_post_recipient_capacity_release` 지연 트리거가 세 경로 모두에서 통과하는지.
- 이슈 재현 절차(수신 상한만큼 받고 전부 방치 → 만료 → 재수신 가능) 그대로의
  end-to-end 통합 시나리오.

### Excluded

- 수신자 선정 규칙(차단·계정 상태 필터, 인원 상한, 분산 정렬) — `#97`.
- `recipient_receive_state` 초기 행 생성 경쟁으로 인한 카운터 리셋 — `#94`.
  이 계획의 시나리오는 모두 **이미 존재하는** `recipient_receive_state` 행을
  전제로 하므로 `#94`의 결함과 독립적으로 성립한다. `#93`은 `#94` 완료를
  선행 조건으로 두지 않는다.
- `distanceBand` 파생 로직 — `#95`.
- `findDetail` 조회 스코프 — `#96`.
- 넘김 조작 방식(스와이프/버튼)과 클라이언트 스낵바 UI. 이 계획은 서버가
  "5초가 지났다"를 판정하는 로직만 다룬다.
- `direction_post.status`를 `EXPIRED`로 전이시키는 것. enum에 값은 존재하지만
  현재 어떤 코드도 이 전이를 수행하지 않고, 목록 가시성은
  `dp.status = 'ACTIVE' AND dp.expires_at > :at` 조합으로만 판정된다. 이번
  이슈는 `post_recipient` 단위 슬롯 해제만 다루며, `direction_post.status` 전이
  여부는 별도 결정 사항이다.
- `DIRECTION_POST_EXPIRED`/`SKIP_CONFIRMATION_DUE` outbox 이벤트의 소비·알림
  발송. 이슈 본문이 실행 방식을 구현 시 결정 사항으로 열어뒀고, 알림 발송은
  F07 영역이다. outbox 이벤트가 "생성되는지" 여부도 이번 계획에서는 검증하지
  않는다 — 진입점 형태가 정해지지 않았기 때문이다.
- `recipient_receive_state` 카운터 drift 복구/재계산 로직. 이 계획은 정상
  경로에서 카운터가 어긋나지 않는지만 검증하며, 이미 어긋난 데이터를 복구하는
  기능은 범위 밖이다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue `#93` | 만료·넘김확정·차단 세 경로 각각의 전이와 슬롯 해제, 유예 중 미확정, 재실행 시 중복 해제 금지, end-to-end 재수신 가능성 |
| `TASK.md` (`#93`) | Completion criteria 7개 항목이 그대로 이 계획의 상위 시나리오 그룹과 대응한다 |
| `direction/domain/PostRecipient.java:159-192` | `requestSkip`/`revertSkip`/`confirmSkip`의 기존 상태 검증. `confirmSkip`은 유예 시간 자체를 모른다 — `skipRequestedAt`만 갖고 있고 duration 비교는 하지 않는다 |
| `direction/domain/PostRecipient.java:33-110` | 생성자 불변식: terminal 상태(`ANSWERED`/`SKIPPED`/`EXPIRED`/`BLOCKED`)는 각자의 타임스탬프와 `capacityReleasedAt`이 반드시 함께 있어야 한다 |
| `V1__create_direction_communication_schema.sql:932-935` | `ct_post_recipient_capacity_release` — terminal 상태와 `capacity_released_at IS NOT NULL`의 동치를 커밋 시점(지연 트리거)에 검사 |
| `V2__add_reactions_and_skip_pending.sql` | `SKIP_PENDING`은 해제 대상 목록에 없어 유예 중 슬롯을 계속 점유 — 이미 DB 제약으로 보장됨 |
| `answer/service/AnswerNotificationService.java:63-79` | 유일하게 동작하는 해제 경로. `transitionToAnswered`의 조건부 성공 여부로 중복 release를 막는 기존 패턴 — 새 경로도 이 패턴을 따라야 한다 |
| `direction/repository/PostRecipientRepository.java` | `transitionToAnswered(answered, previousStatus)`가 조건부 전이의 기존 인터페이스 관례 |
| `feed/repository/jdbc/sql/InboxQuerySql.java:37-39,86-89` | 차단 필터가 `ub.blocker_id = :recipientId AND ub.blocked_id = (콘텐츠 작성자)` 방향으로만 걸림 — 차단 전이의 방향성 근거 |
| `safety/domain/UserBlock.java` | `(blockerId, blockedId)` 단방향 관계. 자동 역방향 행 생성 없음 |
| 명세 F04 (`기능-명세서.md` §수신 상한과 슬롯) | "슬롯은 답변/넘김/만료로 정확히 한 번 해제" · "넘김과 방치는 질문자 입장에서 구별되지 않는다" |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 만료된 미처리 슬롯이 영원히 해제되지 않아 사용자가 신규 수신에서 영구 제외됨(이슈의 핵심 재현 시나리오) | High | High | P0 | INT-001, INT-012 |
| `confirmSkip()`을 호출하는 프로덕션 경로가 없어 넘김 확정이 슬롯을 해제하지 않음 | High | High | P0 | INT-003 |
| 유예 시간이 지나기 전에 조기 확정되어 사용자가 되돌리기 기회를 잃음 | Medium | Medium | P0 | INT-004, INT-005 |
| 차단 전이가 방향을 반대로 잡아 차단당한 사람의 정상 슬롯을 함부로 해제하거나, 무관한 콘텐츠까지 건드림 | High | Medium | P0 | INT-008, INT-009 |
| 만료 sweep이 이미 `ANSWERED`인 행을 잘못 재전이시켜 이미 해제된 슬롯을 데이터상 훼손함 | High | Low | P1 | INT-013 |
| 같은 sweep이 재시도·중복 스케줄링으로 동시에 두 번 실행돼 `ct_post_recipient_capacity_release`가 커밋 시점에 거부하거나 카운터가 두 번 감소함 | High | Medium | P0 | INT-006 |
| 스케줄러/워커 재시도로 같은 sweep이 중복 실행돼 이미 terminal인 행이 다시 처리되어 카운터가 실제보다 낮아짐(음수 방지 가드는 있으나 잘못된 최종값은 못 막음) | High | Medium | P0 | INT-011, INT-014 |
| 세 경로 중 하나가 `PostRecipientRepository.transitionToAnswered`와 다른 방식(무조건 UPDATE)으로 구현돼 동시성 가드가 빠짐 | Medium | Medium | P1 | INT-006, INT-007 |
| 도메인 신규 메서드가 `capacityReleasedAt`을 상태와 별개로 잘못 설정해 생성자 불변식을 우회함 | Medium | Low | P1 | UNIT-001, UNIT-002, UNIT-006 |
| end-to-end 재현 시나리오에서 해제 후 재조회한 `RecipientReceiveState`가 `reserve()` 성공 조건(`active_unhandled_count < limit`)을 여전히 충족하지 못함 | High | Medium | P0 | INT-012 |

## 5. Unit scenarios

모두 `direction/domain` 순수 로직이며 Spring 컨텍스트가 필요 없다. 기존
`DirectionDomainTest`에 추가한다.

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-93-RELEASE-RECEIVE-SLOTS-UNIT-001 | `AVAILABLE`/`DISCOVERED`/`OPENED` 상태의 `PostRecipient` 각각 | `expire(at)` 호출 | 반환된 인스턴스는 `EXPIRED`, `expiredAt == at`, `capacityReleasedAt == at`. `discoveredAt`/`openedAt`은 있었으면 그대로 유지되고 없었으면 여전히 null(만료는 열람 이력을 소급 생성하지 않는다) | P0 | Executor 1 |
| …-UNIT-002 | `ANSWERED`/`SKIPPED`/`EXPIRED`/`BLOCKED`/`SKIP_PENDING` 상태의 `PostRecipient` 각각 | `expire(at)` 호출 | `DirectionException(INVALID_RECIPIENT_STATE)` — **설계 확정**: `expire()`는 `confirmSkip()`·`block()`과 같은 일회성 전이 계약을 따르며 재전이·교차전이를 허용하지 않는다. `SKIP_PENDING`도 거절 대상이다 — DB 주석("`SKIP_PENDING`은 되돌리기 시간 동안 수신 용량을 계속 붙잡는다")이 이미 이 상태를 `confirmSkip()`/`revertSkip()` 전용 레인으로 규정하므로, 만료 sweep의 대상 조회(`WHERE status IN ('AVAILABLE','DISCOVERED','OPENED')`)가 애초에 `SKIP_PENDING` 행을 후보에서 제외한다. 이 테스트는 그 경계가 도메인 계층에서도 강제됨을 고정한다 | P0 | Executor 1 |
| …-UNIT-003 | 이미 `EXPIRED`인 `PostRecipient` | `expire(at)` 호출 | `DirectionException(INVALID_RECIPIENT_STATE)` — UNIT-002와 같은 예외지만, "이미 이 메서드로 전이된 행"이라는 재실행 케이스를 별도로 고정한다. 카운터 재감소를 막는 책임은 도메인이 예외를 던지는 것이 아니라 **호출자(서비스)가 대상 조회 시점에 이미 `EXPIRED`인 행을 후보에서 제외**하는 데 있다 — `AnswerNotificationService.publish()`가 `PUBLISHED` 여부를 먼저 확인하고 조기 반환하는 것과 같은 책임 분리다 | P1 | Executor 1 |
| …-UNIT-004 | `discoveredAt`은 있고 `openedAt`은 없는 `DISCOVERED` 상태 | `expire(at)`을 `discoveredAt`보다 이른 시각으로 호출 | `DirectionException(INVALID_TIME_ORDER)` — 기존 `open()`/`answered()`의 시간 역전 방어와 동일 패턴 | P1 | Executor 1 |
| …-UNIT-005 | `SKIP_PENDING` 상태(`skipRequestedAt` 설정됨) | `confirmSkip(at)`을 `skipRequestedAt` 직후(유예 시간 미고려) 호출 | 예외 없이 `SKIPPED`로 전이된다 — **도메인 객체 자체는 유예 시간을 모른다**는 기존 사실을 재확인한다. 유예 시간 강제는 이 도메인 메서드의 책임이 아니라 호출자(서비스)의 책임임을 이 테스트로 명시한다 | P0 | Executor 1 |
| …-UNIT-006 | `AVAILABLE`/`DISCOVERED`/`OPENED`/`SKIP_PENDING` 상태의 `PostRecipient` 각각 | `block(at)` 호출 | `BLOCKED`, `blockedAt == at`, `capacityReleasedAt == at` | P0 | Executor 1 |
| …-UNIT-007 | `ANSWERED` 상태의 `PostRecipient` | `block(at)` 호출 | `DirectionException(INVALID_RECIPIENT_STATE)` — 이미 슬롯이 해제된 행은 차단으로 재전이하지 않는다(§2 Included의 방향성 결정과 일치) | P0 | Executor 1 |
| …-UNIT-008 | `SKIPPED`/`EXPIRED`/`BLOCKED` 상태 각각 | `block(at)` 호출 | `DirectionException(INVALID_RECIPIENT_STATE)` — 이미 terminal인 행은 중복 전이되지 않는다 | P1 | Executor 1 |

## 6. Integration scenarios

모두 `PostgisContainerIntegrationTestSupport`를 확장하고 `@SpringBootTest
@ActiveProfiles("test")`를 쓴다. `qello.direction.receive-capacity`는
`application.properties` 기본값 5를 그대로 쓴다.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| …-INT-001 | 만료 전이 서비스/리포지토리, `RecipientReceiveStateRepository` | 사용자 R의 `recipient_receive_state.active_unhandled_count = 1`. `direction_post.expires_at`이 과거인 `AVAILABLE` 수신 항목 1건 | 만료 전이 실행(`at` = 현재) | 해당 행이 `EXPIRED`로 전이되고 `capacity_released_at`이 채워진다. `active_unhandled_count`가 0으로 감소한다 | `@BeforeEach` delete |
| …-INT-002 | 위와 동일 | 서로 다른 사용자 3명에게 각 1~2건씩, 만료된 행과 아직 안 만료된 행을 섞어서 배치 | 만료 전이 실행 | 만료된 행만 전이되고 카운터가 감소한다. 안 만료된 행은 상태·카운터 모두 그대로다 | 동일 |
| …-INT-003 | `confirmSkip` 호출 경로, `RecipientReceiveStateRepository` | 사용자 R이 `SKIP_PENDING`(`skip_requested_at` = 유예 시간 이전) 행 1건, `active_unhandled_count = 1` | 유예 시간이 지난 시각으로 확정 전이 실행 | `SKIPPED`, `capacity_released_at` 설정, 카운터 0으로 감소 | 동일 |
| …-INT-004 | 위와 동일 | `SKIP_PENDING`(`skip_requested_at` = 방금) 행 1건 | 유예 시간이 **지나지 않은** 시각으로 확정 전이 실행 | 상태는 `SKIP_PENDING` 그대로, `capacity_released_at`은 null, 카운터 불변 — 조기 확정 방지 확인 | 동일 |
| …-INT-005 | `revertSkip`, 확정 전이 | `SKIP_PENDING` 행을 유예 시간 안에 `revertSkip()`으로 되돌림(→ 이전 상태로 복귀) | 되돌린 뒤 유예 시간이 지난 시각으로 확정 전이 실행 | 이미 `SKIP_PENDING`이 아니므로 확정 대상에서 제외되고 아무 전이도 일어나지 않는다. 카운터도 불변(애초에 해제된 적 없음) | 동일 |
| …-INT-006 | 같은 전이 경로의 동시 중복 실행(경쟁 대상 재정의 — 아래 근거 참고) | 사용자 R이 `AVAILABLE` 행 1건(`direction_post.expires_at` 과거), `active_unhandled_count = 1` | `ExecutorService(2)` + `CountDownLatch`로 **만료 전이를 같은 `postRecipientId`에 대해 동시에 두 번** 실행(기존 `AnswerSafetyNotificationPersistenceIntegrationTest`의 동시 claim 패턴과 동일 구조) | 정확히 한쪽만 성공하고 다른 쪽은 조건부 실패(예외 없이 no-op이거나 낙관적 실패)로 끝난다. 최종 상태는 `EXPIRED` 하나로만 확정되고 `capacity_released_at`은 한 번만 설정된다. 카운터는 정확히 1만 감소한다(2가 아니다). `ct_post_recipient_capacity_release`가 커밋을 거부하지 않는다. **근거**: `expire()`가 `SKIP_PENDING`을 유효 소스 상태로 받지 않기로 확정(UNIT-002)했으므로, 만료 sweep과 넘김확정 sweep은 대상 상태 집합이 겹치지 않아 같은 행을 동시에 노릴 수 없다. 실제 경쟁 지점은 "같은 sweep이 재시도/중복 실행되는 것"이다 | 동일 |
| …-INT-007 | 차단 전이, `SafetyService.block` | 사용자 A(차단자)가 사용자 B(차단대상)로부터 온 질문글에 대해 `OPENED` 수신 항목 1건과 `AVAILABLE` 수신 항목 1건(서로 다른 질문글, 둘 다 B가 발신). `active_unhandled_count = 2` | `SafetyService.block(A, B, at)` 실행 후 차단 전이 실행 | B가 보낸 두 행 모두 `BLOCKED`로 전이되고 `capacity_released_at` 설정. 카운터가 0으로 감소 | 동일 |
| …-INT-008 | 위와 동일 | 같은 A·B 관계에서, B가 보낸 질문글 중 하나는 A가 이미 `ANSWERED`한 상태 | 차단 실행 후 전이 | `ANSWERED` 행은 그대로 `ANSWERED`— 재전이되지 않는다. 카운터는 이미 답변 시점에 해제됐으므로 이 단계에서 추가로 줄지 않는다 | 동일 |
| …-INT-009 | 위와 동일 | 반대로 A가 보낸 질문글을 B가 받은 `AVAILABLE` 행(즉 `recipient_id = B`) | A가 B를 차단 실행 후 전이 | 이 행은 **건드리지 않는다** — `recipient_id`가 차단자(A)가 아니라 차단대상(B)이므로 방향 밖이다. 상태·카운터 모두 그대로 | 동일 |
| …-INT-010 | 위와 동일 | A가 관련 없는 제3자 C로부터 받은 `AVAILABLE` 행(발신자가 B가 아님) | A가 B를 차단 실행 후 전이 | 이 행도 건드리지 않는다 — 발신자가 차단 대상이 아니다 | 동일 |
| …-INT-011 | 만료 전이(또는 확정/차단 전이) 반복 실행 | INT-001 상태에서 만료 전이를 1차 실행해 이미 `EXPIRED`로 만든 뒤 | 같은 전이를 동일 `at`(또는 더 늦은 `at`)으로 재실행 | 두 번째 실행은 아무 것도 바꾸지 않는다. `capacity_released_at`은 최초 값 그대로, 카운터는 추가로 줄지 않는다(0 아래로 내려가지 않는 것과는 별개로, **애초에 다시 줄면 안 된다**는 것을 확인) | 동일 |
| …-INT-012 | `DirectionPostService.send`, `RecipientReceiveStateRepository`, 만료 전이 | 이슈 재현 절차 그대로: 사용자 R이 이미 `recipient_receive_state` 행을 가진 상태에서(사전 발송 1건으로 생성) 수신 상한(5)만큼 질문글을 받아 `active_unhandled_count = 5`. 전부 미답변 상태로 두고 각 `direction_post.expires_at`을 과거로 설정 | 만료 전이 실행 후, R을 후보로 포함하는 신규 발송을 `DirectionPostService.send()`로 한 번 더 실행 | 만료 전이로 카운터가 0으로 내려간 뒤, 신규 발송에서 R이 다시 수신자로 확정된다(`recipients`에 R 포함, 새 `post_recipient` 행 생성). 이슈 본문의 "기대 결과"를 그대로 재현한다 | 동일 |
| …-INT-013 | 만료 전이 | 사용자 R이 `ANSWERED`(`capacity_released_at` 이미 설정) 행 1건. 연결된 `direction_post.expires_at`도 과거 | 만료 전이 실행(만료 대상 조회 조건에 `ANSWERED`도 우연히 걸리는지 검증) | `ANSWERED` 행은 전이 대상에서 아예 제외된다 — 상태·`capacity_released_at`·카운터 모두 불변 | 동일 |
| …-INT-014 | 세 전이 전체 | 사용자 R이 `SKIPPED`/`EXPIRED`/`BLOCKED` 각 1건(이미 terminal, `capacity_released_at` 설정됨), `active_unhandled_count = 0` | 세 전이(만료/확정/차단)를 순서 무관하게 모두 한 번씩 실행 | 어떤 전이도 이 세 행을 다시 건드리지 않는다. 카운터는 0에서 변하지 않는다(음수로 내려가지 않는 것의 재확인이 아니라, 애초에 재처리 대상에서 제외됨을 확인) | 동일 |
| …-INT-015 | 세 전이 전체 + 기존 답변 경로 | 사용자 R이 `AVAILABLE` 행 1건, `active_unhandled_count = 1`. 이 행에 대해 만료 전이와 `AnswerNotificationService.publish()`(답변 경로)를 동시에 경쟁시킴(INT-006과 같은 latch 패턴) | 동시 실행 | 한쪽만 성공(`EXPIRED` 또는 `ANSWERED` 중 하나)하고 카운터는 정확히 1만 감소한다. 기존 답변 경로의 `transitionToAnswered` 조건부 성공 패턴과 새 만료 전이가 서로를 무시하지 않고 올바르게 배타적으로 동작하는지 확인 — 두 경로가 이번 이슈로 처음 공존하게 되므로 별도로 검증한다 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- 세 전이 모두 상태 변경과 `capacity_released_at` 설정이 **같은 UPDATE 문 또는 같은
  트랜잭션 내 연속 문장**으로 이뤄져야 `ct_post_recipient_capacity_release`(지연
  트리거, 커밋 시점 검사)를 통과한다. INT-001·INT-003·INT-007이 이를 각 경로별로
  확인하고, INT-006·INT-015가 두 경로가 동시에 커밋 직전까지 갔을 때도 정합성이
  유지되는지 확인한다.
- 카운터 감소(`RecipientReceiveStateRepository.release()`)와 `post_recipient` 상태
  전이가 **같은 트랜잭션**에서 함께 커밋돼야 한다. 한쪽만 성공하고 다른 쪽이
  롤백되면(예: 전이는 됐는데 release 호출 전에 예외) 다음 재실행에서 이미
  terminal인 행을 다시 release 시도하게 된다. INT-011이 재실행 멱등성을 검증하지만,
  "전이는 성공했는데 release가 아직 안 된 중간 상태"를 인위적으로 만드는 것은
  트랜잭션 경계를 강제로 깨야 해 이 계획에서는 다루지 않는다 — 구현이 기존
  `AnswerNotificationService.releaseSlot()`과 같은 단일 `@Transactional` 메서드
  경계를 따르면 발생하지 않는 상황이다.
- `direction_post.expires_at` 만료 판정 기준 시각(`at`)은 sweep을 구동하는
  진입점이 무엇이든 **서버 시각 하나**로 고정돼야 한다. 같은 sweep 실행 안에서
  일부 행은 만료로, 일부는 아직 안 만료로 판정이 갈리면 안 되므로, 통합 테스트는
  고정된 `Instant`를 명시적으로 넘겨 판정한다(기존 `InboxQueryIntegrationTest`의
  `NOW` 상수 관례와 동일).

### Concurrency and idempotency

- INT-006과 INT-015가 이 계획의 핵심 동시성 시나리오다. 두 개의 서로 다른 해제
  경로가 같은 `post_recipient` 행을 동시에 겨냥할 수 있다는 것이 이번 이슈가
  기존에 없던 새로운 위험이다(기존에는 답변 경로 하나뿐이라 경쟁이 없었다).
- 멱등성은 "같은 전이를 두 번 실행"(INT-011)과 "이미 terminal인 행에 다른 전이를
  실행"(INT-013, INT-014, UNIT-002, UNIT-007, UNIT-008) 두 축으로 나눠 검증한다.
  전자는 재시도 안전성, 후자는 전이 경계의 정확성이다.
- `RecipientReceiveStateRepository.release()` 자체(`WHERE active_unhandled_count > 0`
  조건부 UPDATE)는 이미 구현돼 있고 음수 하한은 보장한다. 이 계획은 그 하한
  보장을 재검증하지 않고, **그 위에서 호출 빈도가 정확한지**(정확히 필요한
  횟수만 호출되는지)를 검증한다 — 하한 가드가 있다고 해서 잘못된 호출 횟수가
  정당화되지는 않는다.

### External APIs

- 없음. 세 전이 모두 내부 DB 트랜잭션이다.

### Failure recovery and reconciliation

- sweep 진입점(스케줄러 등)이 배치 중간에 실패해도 안전해야 한다는 요구를
  INT-002(부분 대상만 조건에 맞는 배치)와 INT-011(재실행)이 함께 커버한다 —
  한 행 처리가 실패해도 이미 처리된 다른 행은 되돌리지 않고, 재실행 시 이미
  처리된 행을 건드리지 않는다.
- `active_unhandled_count`가 이미 실제 미처리 건수와 어긋난 상태(drift)에서
  시작하는 경우의 복구는 다루지 않는다(§2 Excluded). 이 계획의 모든 시나리오는
  일관된 상태에서 출발한다.

## 8. Test data and isolation

- Fixtures: `ReactionPersistenceIntegrationTest`/`InboxQueryIntegrationTest`
  관례를 따른다 — `@BeforeEach`에서 관련 테이블을 `DELETE`하고 전용
  `coarse_region_code`(`TEST-RELSLOT` 등)로 격리한다. `user_account`,
  `approved_question`, `direction_post`, `post_recipient`,
  `recipient_receive_state`는 raw JDBC로 직접 삽입한다.
- Database isolation: `PostgisContainerIntegrationTestSupport`의 공유 컨테이너를
  그대로 쓴다. 새 컨테이너를 추가로 띄우지 않는다.
- Clock/randomness: 모든 시나리오는 고정 `Instant` 상수(`NOW`)와 그로부터 파생된
  오프셋만 쓴다. `clock_timestamp()` DB 기본값에 의존하는 컬럼(`submitted_at` 등)은
  존재해도 assertion 대상에서 제외한다.
- External API doubles: 불필요.
- Cleanup: `@BeforeEach` delete 방식(기존 관례와 동일). 전용 리전 코드를 쓰므로
  다른 테스트 클래스의 fixture와 충돌하지 않는다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor 1 (domain) | `src/test/java/com/dnd/qello/direction/domain/DirectionDomainTest.java` (append) | UNIT-001 ~ UNIT-008 | `./harness check` 중 `./gradlew test --tests "*DirectionDomainTest"` |
| 2 | Executor 2 (integration) | 신규 `src/integrationTest/java/com/dnd/qello/ReceiveSlotReleaseIntegrationTest.java` | INT-001 ~ INT-015 | `./harness check` 중 `./gradlew integrationTest --tests "*ReceiveSlotReleaseIntegrationTest"` |

Executor 2는 Executor 1이 추가한 도메인 메서드에 의존하므로 순서대로
진행한다. 두 Executor 모두 프로덕션 코드(`PostRecipient`, 새 서비스/리포지토리
메서드)는 이 계획이 아니라 구현 단계에서 작성한다 — `AGENTS.md` 2.3절에 따라
구현과 검증은 분리된 역할이므로, 실제로는 구현 에이전트가 도메인 메서드와
서비스 로직을 먼저 작성하고 이 테스트들이 그 구현을 고정한다(TDD 순서를
강제하지는 않되, 두 Executor의 파일 소유권 분리는 유지한다).

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석(특히 §7의 트랜잭션 경계·동시성 항목)
- [ ] 테스트 보고서 생성
- [ ] `TASK.md`(`#93`)의 7개 Completion criteria가 시나리오 ID와 1:1 이상으로 대응됨을 보고서에서 확인

## 11. Human approval

- Reviewer: Byuntil
- Decision: Approved
- Approved at: 2026-08-10T15:00:00+09:00
