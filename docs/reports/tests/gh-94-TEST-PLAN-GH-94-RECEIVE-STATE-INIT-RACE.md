# Test Report: TEST-PLAN-GH-94-RECEIVE-STATE-INIT-RACE

> Created at: `2026-08-10T15:29:00+09:00`
> GitHub Issue: `#94`
> Branch: `fix/gh-94-receive-state-init-race`
> Commit: `19e2a85` (base; 이 브랜치의 변경은 아직 미커밋)

## 1. Executive summary

- Result: `PASS`
- Tested scope: `recipient_receive_state` 초기 행 생성과 슬롯 예약의 원자성.
  단일 예약 계약(생성·증가·상한·롤백), 신규 수신자에 대한 동시 예약,
  발송 end-to-end의 카운터·배달 행 수 일치, 인접 계약(`save()` 덮어쓰기,
  `release()` 감소) 회귀 가드.
- Unverified scope:
  - 설정값 5가 아닌 `receive-capacity`에서의 동작. 이번 실행은 기본 설정값
    5만 전제로 검증했다(리포지토리 단위 시나리오는 상한 3도 함께 검증).
  - 같은 `idempotencyKey`로 두 발송이 **동시에** 들어오는 경쟁. 순차 재시도만
    기존 테스트로 커버된다.
  - 이미 어긋난 카운터의 재계산·복구.
  - 3개 이상 동시 발송. 테스트 커넥션 풀이 4라 2스레드까지만 검증했다.
- Release recommendation: 병합 가능. 스키마 변경이 없고 롤백은 커밋 되돌리기로
  충분하다. 별도 데이터 보정 작업이 필요하지 않다 — 애플리케이션 배포
  파이프라인이 아직 없어 결함 있는 코드가 실사용 데이터에 적용된 적이
  없다(§7).

## 2. Environment

| Item | Version / safe description |
| --- | --- |
| Java | OpenJDK 25.0.3 (Temurin 25.0.3+9, LTS) |
| Spring Boot | 3.5.16 |
| Database | PostGIS 16-3.5-alpine test container (Testcontainers, 클래스 간 공유) |
| Test runner | JUnit 5 |

## 3. Execution results

| Command / suite | Result | Tests | Duration | Evidence |
| --- | --- | --- | --- | --- |
| `./gradlew integrationTest --tests "*ReceiveStateReservationIntegrationTest"` (수정 **전**) | FAIL (의도된 red) | 12 | 8s | 5 failed — INT-002, INT-004, INT-006, INT-007, INT-012 |
| `./gradlew integrationTest --tests "*ReceiveStateReservationIntegrationTest"` (수정 후) | PASS | 12 | 0.31s | failures 0 |
| `./gradlew test --tests "*DirectionPersistenceBoundaryTest"` | PASS | 4 | 0.01s | failures 0 |
| `./gradlew integrationTest --tests "*InboxSentPostWriteIntegrationTest" --tests "*DirectionPostgisPersistenceIntegrationTest"` | PASS | — | 11s | 회귀 없음 |
| `./harness check` | PASS | — | — | secret preflight 538 files, JUnit policy 56 files, convention/workflow/label/husky 통과 |
| `./harness pr-ready --project-tests` | PASS | — | 1m 29s | `:test` + `:integrationTest` + `:check` 전체 통과 |
| `git diff --check` | PASS | — | — | 공백 오류 없음 |
| `./gradlew test integrationTest --tests "*DirectionPersistenceBoundaryTest" --tests "*ReceiveStateReservationIntegrationTest" --tests "*InboxSentPostWriteIntegrationTest" --tests "*DirectionPostgisPersistenceIntegrationTest"` (SQL 추출 후 재검증) | PASS | — | 24s | `RecipientReceiveStateSql` 추출 이후 전체 재실행, 회귀 없음 |

## 4. Scenario results

| Scenario ID | Result | Test class / method | Notes |
| --- | --- | --- | --- |
| UNIT-001 | PASS | `DirectionPersistenceBoundaryTest.sendPathDoesNotInitializeReceiveStateInTwoSteps` | `DirectionPostService`가 `receiveStateRepository`의 `findByUserId`/`save`를 더 이상 호출하지 않음을 고정 |
| UNIT-002 | PASS | `DirectionPersistenceBoundaryTest.receiveStateSqlKeepsSeparateContractsPerOperation` | 예약은 단일 UPSERT이고 `EXCLUDED`를 쓰지 않음. `save()`의 덮어쓰기와 `release()`의 감소는 그대로. SQL을 `sql/RecipientReceiveStateSql.java`로 추출한 뒤(다른 5개 리포지토리와 같은 관례) 검사 대상을 그 파일로 옮겼다 — 계획 §5가 이 경우를 이미 허용해뒀다 |
| INT-001 | PASS | `ReceiveStateReservationIntegrationTest.concurrentSendsToNewRecipientKeepCounterEqualToDeliveredRows` | 수정 전에도 통과했다 — 계획 §1이 예고한 확률적 red. 아래 §5 참고 |
| INT-002 | PASS (수정 전 FAIL) | `…concurrentReserveOnMissingRowReservesTwoSlots` | **결정적 red 근거.** 수정 전에는 두 호출 모두 `false`, 행 미생성 |
| INT-003 | PASS | `…reserveOnExistingRowPreservesAccumulatedValues` | 누적 카운터와 `recent_window_started_at` 보존 확인 |
| INT-004 | PASS (수정 전 FAIL) | `…reserveOnMissingRowCreatesRowWithOneSlot` | 수정 전 `Expecting value to be true but was false` |
| INT-005 | PASS | `…reserveAtCapacityFailsAndLeavesStateUntouched` | 상한 도달 시 `last_received_at` 미갱신 확인 |
| INT-006 | PASS (수정 전 FAIL) | `…repeatedReserveSucceedsOnlyUpToLimit` | 반환값 `true,true,true,false`와 카운터 일치 |
| INT-007 | PASS (수정 전 FAIL) | `…rolledBackReserveLeavesNoTrace` | 수정 전 `NoSuchElementException` — 행 자체가 없었다 |
| INT-008 | PASS | `…sendStopsSelectingRecipientAtCapacity` | 배달 `1,1,1,1,1,0`, 카운터 5 == `post_recipient` 5건 |
| INT-009 | PASS | `…concurrentSendsWithSingleRemainingSlotDeliverOnce` | 마지막 슬롯 경쟁에서 정확히 1건만 배달, 교착·타임아웃 없음 |
| INT-010 | PASS | `…saveOverwritesExistingState` | 시더 계약 보존 |
| INT-011 | PASS | `…releaseDecrementsOnceAndNeverGoesNegative` | `#93`이 의존하는 계약 불변 |
| INT-012 | PASS (수정 전 FAIL) | `…reserveForUnknownUserFailsLoudly` | 수정 전 `Expecting code to raise a throwable` — 조용히 `false`였다 |

## 5. Failures and diagnostics

수정 후 실패한 시나리오는 없다. 아래는 **수정 전 red 기록**이다(계획의 완료
조건 "수정 전 INT-002의 red 확인 기록").

| Scenario | 오류 유형 | 정리한 메시지 | 원인 |
| --- | --- | --- | --- |
| INT-002 | `AssertionFailedError` | `Expecting value to be true but was false` | 기존 `reserve()`는 조건부 UPDATE 하나뿐이라 행이 없으면 0행을 갱신하고 `false`를 반환한다 |
| INT-004 | `AssertionFailedError` | `Expecting value to be true but was false` | 동일 |
| INT-006 | `AssertionFailedError` | `Expecting value to be true but was false` | 동일 |
| INT-007 | `NoSuchElementException` | `No value present` | 행이 생성되지 않아 `findByUserId()`가 empty |
| INT-012 | `AssertionError` | `Expecting code to raise a throwable` | UPDATE가 0행에 적용되어 FK 위반이 발생하지 않았다 |

### 보고서 초안의 오독 정정 (2026-08-10)

초안은 "DB 제약이 0~5인데 애플리케이션은 1~50을 허용하므로 불일치"라고
기록하고 후속 Issue를 요구했다. **이는 틀렸다.**
`V1__create_direction_communication_schema.sql:159`의 `BETWEEN 0 AND 5`만 읽고,
`V2__add_reactions_and_skip_pending.sql:24-29`가 그 제약을 DROP하고
`BETWEEN 0 AND 50`으로 교체한 것을 보지 못한 결과다. 실제 제약은 50이고
`SAFETY_CEILING`과 일치한다. 불일치도, 후속 Issue도 없다.

이 오독은 테스트 결과에 영향을 주지 않는다 — 모든 시나리오는 설정값 5로
실행됐고 제약 상한을 건드리지 않는다.

### INT-001이 수정 전에도 통과한 것에 대해

계획 §1 "red 재현의 결정성"이 예고한 대로다. 파괴적 덮어쓰기는 두 트랜잭션이
**모두** `findByUserId()`에서 empty를 본 인터리빙에서만 발현한다. 한쪽이 커밋을
마친 뒤 다른 쪽이 조회하면 `save()`를 건너뛰므로 결함이 드러나지 않는다. 이번
실행에서는 그 창에 들어가지 않았다.

**이를 "결함이 없었다"는 근거로 읽지 않는다.** INT-001의 가치는 수정 후 두
인터리빙 모두에서 결정적으로 green이 된다는 데 있고, 결함의 존재 증거는
코드 경로 자체(`findByUserId` → `save`(`ON CONFLICT DO UPDATE SET
active_unhandled_count = EXCLUDED.active_unhandled_count`) → `reserve`)와
INT-002의 결정적 red다.

## 6. Potential issues

### Application code

- `RecipientReceiveStateRepository.reserve()`의 INSERT 경로는 `activeLimit`을
  검사하지 않는다. `activeLimit >= 1`이 호출 전에 두 곳에서 보장되므로
  (`DirectionReceiveProperties` 생성자, `RecipientReceiveState.canReserve`)
  현재는 안전하지만, 리포지토리만 보고는 알 수 없는 전제다. 메서드 javadoc에
  명시했다. 향후 이 리포지토리를 다른 호출자가 쓰게 되면 전제를 다시 확인해야
  한다.
- `save()`는 프로덕션 호출자가 사라지고 통합 테스트 시더로만 남았다. 지금은
  의도된 상태지만(§4 INT-010), 프로덕션에서 쓰이지 않는 공개 메서드라는 점을
  다음 정리 작업에서 재검토할 여지가 있다.

### Infrastructure and resource limits

- 동시 발송이 같은 수신자를 노리면 뒤쪽 트랜잭션이 앞쪽의 커밋까지
  `recipient_receive_state` 행 락에서 대기한다. 락은 `send()` 트랜잭션이 끝날
  때까지 유지되므로, 인기 수신자에게 발송이 몰리면 대기 시간이 트랜잭션 길이에
  비례해 늘어난다. INT-009에서 2건은 교착·타임아웃 없이 정상 직렬화됐지만
  높은 동시성은 검증하지 않았다.
- 테스트 환경의 `hikari.maximum-pool-size=4` 때문에 동시 스레드를 2개로
  제한했다. 운영 풀 크기에서의 동작은 이 테스트가 말하지 않는다.

### Database and migrations

- 스키마 변경 없음. Flyway 마이그레이션을 추가하지 않았다.
- DB 안전 상한과 애플리케이션 검증 범위는 **일치한다**.
  `V2__add_reactions_and_skip_pending.sql:24-29`가 V1의
  `ck_recipient_receive_state_active_count BETWEEN 0 AND 5`를 DROP하고
  `BETWEEN 0 AND 50`으로 다시 만들었으며, 이는
  `RecipientReceiveState.SAFETY_CEILING`(50)과 같은 값이다. 실효 상한은 DB가
  아니라 설정값이 정하고 `reserve()`의 `WHERE`가 강제한다.
  **주의: V1만 읽으면 상한을 5로 오독하게 된다.** 이 보고서의 초안이 실제로
  그 오독을 담고 있었고 검토 중 정정했다(§5).
- 실효 상한이 SQL의 `WHERE`에만 있고 DB 제약은 50까지 허용하므로, `WHERE`가
  빠지면 상한 초과가 예외 없이 조용히 통과한다. INT-008이 이 경로를 지킨다.
- 신규 행의 `recent_window_started_at`이 이전에는 "첫 후보 선정 시각",
  이제는 "첫 실제 수신 시각"이 된다. 예약에 실패하면 행 자체가 만들어지지
  않기 때문이다. 이 값을 쓰는 코드가 아직 없어 관측 가능한 차이는 없지만,
  rolling window 로직을 추가할 때 전제가 달라졌음을 알아야 한다.

### Concurrency and idempotency

- 격리 수준은 READ COMMITTED(PostgreSQL 기본, `application*.properties`에
  override 없음). 이 수준에서 `ON CONFLICT DO UPDATE`는 선행 커밋을 기다린 뒤
  갱신된 행에 적용되므로 재시도가 필요 없다. **격리 수준을 REPEATABLE READ
  이상으로 올리면 이 전제가 깨지고 serialization failure 재시도 설계가
  필요해진다.**
- `reserve()`는 의도적으로 멱등이 아니다. 두 번 부르면 두 번 예약한다 — 서로
  다른 두 질문글이 배달됐다는 뜻이기 때문이다. 발송 단위 멱등성은 상위
  `send()`의 `idempotencyKey`가 담당한다(기존 테스트로 커버).

### Transactions and event ordering

- `reserve()`는 `send()`의 `@Transactional` 안에서 후보 stream의 `filter`로
  호출된다. 반환값이 곧 수신자 선정 결과이므로 반환값과 부수효과의 일치가
  중요하다 — INT-006과 INT-008이 이를 고정한다.
- FK 위반이 발송 트랜잭션 전체를 롤백시키는 것은 의도된 동작이다(INT-012).
  부분 배달보다 전체 실패가 낫다.

### External APIs

- 해당 없음. 외부 연동 경계를 건드리지 않았다.

### Failure recovery and reconciliation

- 이 변경은 **앞으로** 카운터가 어긋나지 않게 만들 뿐, 이미 어긋난 데이터를
  교정하지 않는다(§7).

## 7. Regression and residual risk

- **회귀 없음.** `InboxSentPostWriteIntegrationTest`(`save()` 시더 7개 지점),
  `DirectionPostgisPersistenceIntegrationTest`(발송 후 카운터 1 유지) 모두
  통과했다. 전체 `:test` + `:integrationTest`도 통과했다.
- **`#93`과의 간섭 없음.** `release()`의 시그니처와 SQL 본문을 변경하지 않았고
  INT-011과 UNIT-002가 이를 강제한다. 파일 소유권도 겹치지 않는다 — `#93`은
  `PostRecipient*`·`SafetyService`·`AnswerNotificationService`·
  `ReceiveSlotReleaseService`를, 이 브랜치는
  `JdbcRecipientReceiveStateRepository`·`DirectionPostService`를 수정했다.
  `TASK.md`만 양쪽이 재작성하므로 병합 시 충돌한다(각 브랜치 버전 유지로 해결).
- **잔여 위험 1 — 기존 오염 데이터: 현재 해당 없음.** 이 수정은 앞으로의
  어긋남만 막고 이미 리셋된 `active_unhandled_count`를 교정하지 않는다. 다만
  저장소에 애플리케이션 배포 워크플로가 없고(`.github/workflows`는 harness
  정책과 Terraform만) `infra/environments`에도 `dev`만 있어, 결함 있는 코드가
  실사용 데이터에 적용된 적이 없다. **따라서 교정할 데이터가 존재하지 않는다.**
  참고로 오염이 발생하더라도 영구적이지는 않다 — 카운터가 실제보다 낮게
  어긋나고, 해제 경로가 항목마다 1씩 감소시키되 `release()`가 0에서 멈추므로
  사용자가 밀린 항목을 모두 처리하면 카운터와 실제가 0에서 다시 만난다.
  단 이 자기 교정은 해제 경로가 동작해야 성립하며, 그 복구가 `#93`이다.
- **잔여 위험 2 — 높은 동시성.** 2스레드까지만 검증했다. 인기 수신자에게 발송이
  몰릴 때의 락 대기 특성은 측정하지 않았다.

## 8. Artifacts

- Test plan: `docs/test-plans/gh-94-TEST-PLAN-GH-94-RECEIVE-STATE-INIT-RACE.md`
- CI run: 미실행 (로컬 검증만 수행. PR 생성 시 GitHub Actions에서 최종 강제)
- Related ADR: 없음
- PR: 미생성

## 9. Reviewer checklist

- [x] 보고서에 `.env` 값이나 비밀정보가 없음
- [x] 미실행 테스트가 명시됨 (§1 Unverified scope, §7)
- [x] 잠재 문제에 후속 GitHub Issue가 연결됨 — 후속 Issue가 필요한 항목 없음.
      초안이 지목했던 "설정 상한과 DB 제약 불일치"는 오독이었고 정정했다(§5, §6)
- [ ] 실행 결과와 PR 설명이 일치함 — PR 미생성
