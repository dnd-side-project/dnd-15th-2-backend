# Test Plan: TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE

> Created at: `2026-08-17T18:50:27+09:00`
> GitHub Issue: `#112`
> Status: Approved

## 1. Objective

`BLOCK` 판정으로 비공개(`HIDDEN`) 처리된 답변에 대해 작성자가 이의를 제기하고,
검토자가 `UPHOLD_HIDDEN`/`OVERTURN_HIDDEN`을 수동으로 결정하며,
`OVERTURN_HIDDEN`일 때만 — 그리고 다른 공개 금지 사유가 없을 때만 — 공개 복원
콜백이 나가는 경로를 검증한다.

- **접수 기간이 6개월보다 짧아지는 경로가 없는지 검증한다**(`INV-APL-008`,
  `INV-APL-009`). 깨지면 작성자가 법적·정책적으로 보장된 이의제기 기회를
  잃는다. 되돌릴 수 없는 종류의 손실이라 우선순위가 가장 높다.
- **appeal 접수가 콘텐츠 공개 상태를 바꾸지 않는지 검증한다**(`INV-APL-003`).
  깨지면 이의제기 접수만으로 비공개 처리된 콘텐츠가 다시 노출되어, 검토 전에
  유해 콘텐츠가 공개되는 상태가 된다.
- **동일 대상·decision에 활성 appeal이 하나만 생기는지 검증한다**
  (`INV-APL-002`). 깨지면 같은 판정에 대해 서로 다른 결론이 병렬로 나올 수
  있고, 어느 쪽이 최종인지 결정할 근거가 사라진다.
- **`OVERTURN_HIDDEN` 결정이 다른 공개 금지 사유를 무시하고 복원 콜백을
  보내지 않는지 검증한다.** 깨지면 계정이 차단된 사용자의 콘텐츠나 법적
  명령으로 내려진 콘텐츠가 appeal 한 건으로 되살아난다.
- **만료 뒤에도 작성자가 새 콘텐츠를 제출할 수 있는지 확인한다**
  (`INV-APL-011`). 깨지면 만료된 appeal이 계정 전체를 사실상 정지시킨다.

## 2. Scope

### Included

- `appeal_case` 확장(V18): `appellant_user_id`, `status`(`OPEN`/`RESOLVED`),
  `window_started_at`, `expires_at`, `acceptance_reason_code`,
  `decision`(`UPHOLD_HIDDEN`/`OVERTURN_HIDDEN`), `decided_at`,
  `decided_by_operator_user_id`, `restore_blocked_reason_code`와 관련 CHECK
  제약·인덱스.
- `outbox_event`의 `ck_outbox_event_aggregate_type`에 `APPEAL_CASE`,
  `ck_outbox_event_event_type`에 `MODERATION_APPEAL_RESOLVED` 추가.
- `AppealWindow`(신규 값 객체): `GLOBAL_ACCEPTANCE_WINDOW`(184일) 미만 거절,
  `evaluate(windowStartedAt, now)`의 순수 판정, `expiresAt(windowStartedAt)`.
- `AppealAcceptance`·`AppealAcceptanceReasonCode`(`WITHIN_WINDOW`,
  `WINDOW_ELAPSED`, `WINDOW_UNVERIFIABLE`), `AppealCaseStatus`,
  `AppealDecision`.
- `AppealCase` 확장: `file(...)`, `restore(...)`, `decide(...)`,
  `extendExpiry(...)`.
- `AppealCaseRepository` 확장과 JPA 매핑: 접수자별 조회, OPEN 큐 조회,
  행 잠금 조회(`findByIdForUpdate`).
- `AppealCaseService`(`filtering.moderation`): 접수, 검토자 결정, 만료 연장.
- 포트 `AppealTargetOwnershipChecker`, `PublicationBlockChecker`와 답변 도메인
  어댑터 2개.
- REST endpoint 5개(작성자 2, 검토자 3)와 `FilteringErrorCode` 신규 코드.
- 단위 테스트, PostgreSQL 통합 테스트(동시성 포함), 테스트 보고서.

### Excluded

- `MODERATION_APPEAL_RESOLVED` 콜백을 소비해 답변의 `moderationStatus`와 공개
  상태를 실제로 되돌리는 구현. 이슈 본문이 답변 담당 코드의 몫으로 명시했다.
- 통지 성공 증명과 통지 시각 기반 기산. `notified_at` 컬럼을 만들지 않는다.
- calendar-month·timezone 계산. 184일 상수로 대체하며 그 근거는 `TASK.md`의
  Design decisions 3번에 있다.
- 처리 SLA, 악용 제한(rate limit), 외부 분쟁조정, 상세 reason 텍스트·UI,
  기록 보관 기간 — 이슈 본문에서 미결정.
- appeal 만료를 배치로 감지하는 스케줄러. 만료는 접수 시점에만 평가한다.
- `NICKNAME` 대상 appeal. `UNSUPPORTED_APPEAL_TARGET`으로 거절하는 것만
  검증하고 그 이상 다루지 않는다.
- `safety` 패키지(`Report`/`ModerationReview`)와의 통합.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #112 | 특정 moderation 대상·`HIDDEN` decision에 귀속된 단일 활성 appeal case (`INV-APL-002`) |
| GitHub Issue #112 | 이의제기 중 `HIDDEN` 유지, 실제 비공개 반영은 답변 담당 콜백 (`INV-APL-003`) |
| GitHub Issue #112 | reviewer의 `UPHOLD_HIDDEN`/`OVERTURN_HIDDEN` 수동 결과와 자동 결과 대비 우선권 |
| GitHub Issue #112 | overturn 시 복원 콜백 전 다른 공개 금지 사유 재검증 |
| GitHub Issue #112 | 전 세계 공통 6개월 접수 기간과 고정 `appeal_expires_at` (`INV-APL-008`, `INV-APL-009`) |
| GitHub Issue #112 | 통지·만료 정합성이 불명확하면 접수를 허용하는 fallback |
| GitHub Issue #112 | 만료 뒤에도 작성자는 새 콘텐츠를 제출할 수 있다 (`INV-APL-011`) |
| `V10__create_filtering_schema.sql` 134-151행 | `appeal_case`의 유일성 인덱스와 "`#112`가 컬럼을 추가한다"는 선언 |
| `filtering/package-info.java` | answer·user_account 도메인은 콜백/이벤트 계약으로만 연결한다 |
| `V13__...outbox_contract.sql` 33-42행 | `outbox_event`의 aggregate/event type CHECK를 drop 후 재생성하는 방식 |
| `V16__add_manual_review_priority_and_authority.sql` | 프로덕션 행이 없는 필터링 테이블에 기존 행 보정 없이 NOT NULL을 추가하는 선례 |
| `TASK.md` Design decisions | 구현 전 확정한 8개 판단과 근거 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 접수 기간을 6개월보다 짧게 만드는 경로가 존재 | 작성자가 구제 기회를 영구히 상실 | Low | P0 | `AppealWindow` 생성자 거절, `extendExpiry` 단축 거절, DB CHECK 세 겹 모두 검증 |
| appeal 접수가 공개 상태나 job 판정을 변경 | 검토 전 유해 콘텐츠 노출 | Medium | P0 | 접수 후 `filter_job`·`answer` 행과 `outbox_event` 개수 불변 확인 |
| 동시 접수로 같은 대상에 appeal 2건 생성 | 상충하는 결론, 최종 판단 불가 | Medium | P0 | 실제 PostgreSQL에서 두 스레드 동시 접수, 정확히 1건 성공 |
| 동시 결정으로 같은 case가 두 번 종결되고 콜백 2건 발행 | 중복 복원 콜백, 감사 이력 모순 | Medium | P0 | 행 잠금 하에 동시 `decide`, 1건 성공 + 1건 거절, outbox 1건 |
| overturn 시 공개 금지 사유 재검증 누락 | 차단 계정 콘텐츠가 되살아남 | Medium | P0 | 계정 `BLOCKED` 상태에서 overturn 시 콜백 미발행 + 사유 기록 |
| 타인이 남의 콘텐츠에 appeal 접수 | 권한 우회, 타인 콘텐츠 상태 조작 | Medium | P0 | 소유자가 아닌 사용자 접수 시 403과 행 미생성 |
| 기산점 불명확 시 접수를 거절 | 데이터 결함이 곧 구제 거부로 이어짐 | Medium | P0 | `decided_at` 없음·미래 값에서 접수 성공 + `WINDOW_UNVERIFIABLE` 기록 |
| V18이 기존 `outbox_event` CHECK를 깨뜨림 | 기존 이벤트 발행 전면 실패 | Low | P0 | 마이그레이션 후 기존 12개 타입 + 신규 타입 모두 삽입 가능 확인 |
| `AppealCase` 시그니처 변경이 기존 테스트를 깨뜨림 | 회귀 미검출 | High | P1 | 기존 `FilteringValueObjectsTest`·`FilteringPersistenceIntegrationTest` 갱신 후 통과 |
| 만료된 appeal이 새 콘텐츠 제출을 막음 | 계정 사실상 정지 | Low | P1 | 만료 appeal 존재 상태에서 답변 제출 성공 확인 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| ...-UNIT-001 | 184일 미만 접수 기간 | `new AppealWindow(Duration.ofDays(183))` | `FilteringException`(`INVALID_VALUE_RANGE`)으로 거절한다 | P0 | Feature executor |
| ...-UNIT-002 | `GLOBAL` 창(184일), 기산점 이후 183일 시점 | `evaluate(startedAt, now)` | `accepted=true`, `WITHIN_WINDOW` | P0 | Feature executor |
| ...-UNIT-003 | `GLOBAL` 창, 기산점 이후 185일 시점 | `evaluate(startedAt, now)` | `accepted=false`, `WINDOW_ELAPSED` | P0 | Feature executor |
| ...-UNIT-004 | 기산점이 `null`(판정 시각 미상) | `evaluate(null, now)` | `accepted=true`, `WINDOW_UNVERIFIABLE` — 거절하지 않는다 | P0 | Feature executor |
| ...-UNIT-005 | 기산점이 현재보다 미래(정합성 파손) | `evaluate(future, now)` | `accepted=true`, `WINDOW_UNVERIFIABLE` | P0 | Feature executor |
| ...-UNIT-006 | 기산점과 `GLOBAL` 창 | `AppealCase.file(...)` | `expiresAt == startedAt + 184일`로 고정된다 | P0 | Feature executor |
| ...-UNIT-007 | `WINDOW_UNVERIFIABLE`로 접수한 case | `AppealCase.file(...)` | 기산점을 접수 시각으로 두고 만료를 그 기준으로 고정한다 | P1 | Feature executor |
| ...-UNIT-008 | 이미 `RESOLVED`인 case | `decide(...)` | `INVALID_APPEAL_CASE_STATUS`로 거절한다 | P0 | Feature executor |
| ...-UNIT-009 | `OPEN` case | `decide(UPHOLD_HIDDEN, operatorId, now)` | `RESOLVED`, `decision`/`decidedAt`/`decidedByOperatorUserId` 동시 설정 | P0 | Feature executor |
| ...-UNIT-010 | `OPEN` case | `decide(UPHOLD_HIDDEN, ..., restoreBlockedReasonCode="X")` | `UPHOLD_HIDDEN`에는 복원 차단 사유를 붙일 수 없다 | P1 | Feature executor |
| ...-UNIT-011 | 만료 `T`인 case | `extendExpiry(T - 1일)` | 단축을 거절한다 (`INV-APL-008`) | P0 | Feature executor |
| ...-UNIT-012 | 만료 `T`인 case | `extendExpiry(T + 30일)` | 연장을 허용한다 | P0 | Feature executor |
| ...-UNIT-013 | 만료 `T`인 case | `extendExpiry(T)` | 동일 시각은 변화가 없으므로 거절한다 | P2 | Feature executor |
| ...-UNIT-014 | 잘못된 필수값(`appellantUserId <= 0` 등) | 생성자 호출 | 각각 `REQUIRED_VALUE_MISSING`/`INVALID_VALUE_RANGE` | P2 | Feature executor |
| ...-UNIT-015 | `RESOLVED`가 아닌데 `decidedAt`만 설정 | `restore(...)` | 상태와 결정 필드의 동반 조건을 강제한다 | P1 | Feature executor |
| ...-UNIT-016 | `NICKNAME` 대상 | `AppealCaseService.file(...)` | `UNSUPPORTED_APPEAL_TARGET`으로 거절한다 | P1 | Feature executor |
| ...-UNIT-017 | 작성자가 아닌 사용자 | `AppealCaseService.file(...)` | `APPEAL_NOT_OWNED`로 거절하고 decision 조회조차 하지 않는다 | P0 | Feature executor |
| ...-UNIT-018 | 공개 금지 사유가 있는 대상 | `decide(OVERTURN_HIDDEN, ...)` | case는 종결하되 복원 콜백을 발행하지 않고 사유를 기록한다 | P0 | Feature executor |
| ...-UNIT-019 | 공개 금지 사유가 없는 대상 | `decide(OVERTURN_HIDDEN, ...)` | `APPEAL_CASE` aggregate로 `MODERATION_APPEAL_RESOLVED`를 발행한다 | P0 | Feature executor |
| ...-UNIT-020 | `OPEN` case | `decide(UPHOLD_HIDDEN, ...)` | 공개 금지 사유를 조회하지도, 콜백을 내지도 않는다 | P1 | Feature executor |

> UNIT-017 ~ UNIT-020은 구현 중에 추가했다. 서비스 계층의 검사 순서와 콜백
> 발행 조건은 통합 시나리오(INT-005, INT-008 ~ INT-010)로도 덮이지만, 포트를
> 스텁으로 갈아끼우면 컨테이너 없이 수 초 만에 회귀를 잡을 수 있어 단위로도
> 남겼다.

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| ...-INT-001 | Flyway, PostgreSQL | V18까지 마이그레이션 적용 | `appeal_case` 컬럼·CHECK와 `outbox_event` 두 CHECK를 조회 | 신규 컬럼·제약이 존재하고, 기존 12개 event type과 `MODERATION_APPEAL_RESOLVED`가 모두 삽입 가능하다 | 트랜잭션 롤백 |
| ...-INT-002 | `AppealCaseService`, `filter_job`, `outbox_event` | `BLOCK` 판정과 `HIDDEN` 답변 | 작성자가 접수 | `appeal_case` 1건 생성, `filter_job` 행 불변, `outbox_event` 개수 불변 (`INV-APL-003`) | 트랜잭션 롤백 |
| ...-INT-003 | `AppealCaseService`, unique index | 이미 접수된 appeal | 같은 대상·decision으로 재접수 | `DUPLICATE_CASE`로 거절하고 행이 늘지 않는다 (`INV-APL-002`) | 트랜잭션 롤백 |
| ...-INT-004 | `AppealCaseService`, PostgreSQL 동시성 | 접수 이력 없음 | 두 스레드가 동시에 같은 대상 접수 | 정확히 1건 성공, 나머지는 `DUPLICATE_CASE`, 최종 행 1건 | 명시적 삭제 |
| ...-INT-005 | `AppealTargetOwnershipChecker` 어댑터 | 다른 사용자 소유의 답변 | 비소유자가 접수 | `APPEAL_NOT_OWNED`(403), `appeal_case` 행 미생성 | 트랜잭션 롤백 |
| ...-INT-006 | `AppealWindow`, `filter_decision` | `decided_at`이 185일 전 | 접수 | `APPEAL_WINDOW_ELAPSED`로 거절 | 트랜잭션 롤백 |
| ...-INT-007 | `AppealWindow`, `filter_decision` | `decided_at`이 현재보다 미래 | 접수 | 접수 성공, `acceptance_reason_code = WINDOW_UNVERIFIABLE` | 트랜잭션 롤백 |
| ...-INT-008 | `AppealCaseService`, `PublicationBlockChecker`, `outbox_event` | `OPEN` appeal, 계정 `ACTIVE` | 검토자가 `OVERTURN_HIDDEN` 결정 | case `RESOLVED`, `MODERATION_APPEAL_RESOLVED` 이벤트 1건(aggregate `APPEAL_CASE`), `restore_blocked_reason_code` 없음 | 트랜잭션 롤백 |
| ...-INT-009 | 같은 구성, 계정 `BLOCKED` | `OPEN` appeal, 계정 차단 | 검토자가 `OVERTURN_HIDDEN` 결정 | case `RESOLVED`, `restore_blocked_reason_code` 기록, 복원 이벤트 **미발행** | 트랜잭션 롤백 |
| ...-INT-010 | `AppealCaseService`, `outbox_event` | `OPEN` appeal | 검토자가 `UPHOLD_HIDDEN` 결정 | case `RESOLVED`, 복원 이벤트 미발행 | 트랜잭션 롤백 |
| ...-INT-011 | `AppealCaseService`, 행 잠금 | `OPEN` appeal | 두 스레드가 동시에 `decide` | 1건만 성공, 나머지는 `INVALID_APPEAL_CASE_STATUS`, `outbox_event` 정확히 1건 | 명시적 삭제 |
| ...-INT-012 | `AppealCaseService`, DB CHECK | 만료 `T`인 case | `extendExpiry(T - 1일)`과 직접 UPDATE 시도 | 서비스는 도메인 예외로, DB는 CHECK 위반으로 각각 거절 | 트랜잭션 롤백 |
| ...-INT-013 | `AnswerRepository`, `appeal_case` | 만료된 appeal이 있는 작성자 | 새 답변 제출 | 제출이 성공한다 (`INV-APL-011`) | 트랜잭션 롤백 |
| ...-INT-014 | 기존 `FilteringPersistenceIntegrationTest` | 확장된 `appeal_case` | 기존 유일성·조회 계약 재실행 | 회귀 없이 통과한다 | 트랜잭션 롤백 |
| ...-INT-015 | Spring Security 두 체인 | 인증 없음 / 사용자 토큰 | 검토자 endpoint 호출 | 401 또는 403으로 차단된다 | 없음 |
| ...-INT-016 | `AppealCaseRepository` 조회 경로 | 서로 다른 접수자의 appeal 2건 | `findMine` / `findQueue` | 작성자는 본인 것만, 검토자 큐는 OPEN 전체를 본다 | 명시적 삭제 |

> INT-016은 구현 중에 추가했다. `findMine`이 접수자별로 좁혀지지 않으면 남의
> 이의제기 이력이 그대로 노출되는데, 원래 계획에는 그 경로를 검증하는
> 시나리오가 없었다. INT-014(기존 유일성 회귀)는 신규 파일이 아니라 기존
> `FilteringPersistenceIntegrationTest`를 갱신해 담당한다.

## 7. Cross-cutting scenarios

### Database and transactions

- 접수와 결정은 각각 단일 트랜잭션이다. 결정 트랜잭션은 case 갱신과
  `outbox_event` 삽입을 함께 커밋해, 콜백만 나가고 case가 열린 채 남거나 그
  반대가 되는 상태를 만들지 않는다(INT-008, INT-009).
- V18은 신규 컬럼 추가와 CHECK 재생성만 하고 기존 행을 변경하지 않는다.
  `appeal_case`에 프로덕션 행이 없다는 전제는 V16이 `manual_review_case`에
  적용한 것과 같은 근거이며, 코드베이스 전체에서 `AppealCase`를 저장하는
  프로덕션 경로가 없음을 확인해 성립한다(INT-001).
- `outbox_event`의 두 CHECK는 drop 후 재생성이므로, 재생성 목록에서 기존
  값이 빠지면 기존 기능이 전면 중단된다. INT-001이 기존 12개 event type과
  7개 aggregate type을 모두 다시 삽입해 이를 막는다.

### Concurrency and idempotency

- 접수 경합은 `uq_appeal_case_target_decision` 유일 인덱스가 직렬화한다.
  애플리케이션은 위반을 잡아 `DUPLICATE_CASE`로 변환한다(INT-004).
- 결정 경합은 `findByIdForUpdate` 행 잠금이 직렬화한다. `#110`의
  `ManualReviewDecisionService`가 `filter_job`에 쓴 것과 같은 방식이며,
  잠금 없이는 두 트랜잭션이 같은 `OPEN` case를 읽어 복원 콜백을 두 번
  발행할 수 있다(INT-011).
- 두 동시성 시나리오는 스레드 2개와 `CountDownLatch`로 결정적으로 재현한다.
  `@Transactional` 롤백으로는 다른 커넥션이 행을 볼 수 없으므로 이 두
  시나리오만 명시적 정리를 쓴다.

### External APIs

- 외부 API 호출이 없다. `AppealTargetOwnershipChecker`와
  `PublicationBlockChecker`는 같은 데이터베이스를 읽는 인프로세스 포트다.
- 단위 테스트는 두 포트를 람다 스텁으로 대체하고, 통합 테스트는 실제 어댑터와
  실제 `answer`·`account` 행을 쓴다.

### Failure recovery and reconciliation

- 공개 금지 사유 재검증에서 예외가 나면 결정 트랜잭션 전체를 롤백한다.
  "차단 여부를 확인하지 못했다"를 "차단이 없다"로 해석해 복원 콜백을 내보내지
  않는다(fail-closed). INT-009의 변형으로 포트가 예외를 던지는 경우를 포함한다.
- 복원 콜백의 실제 소비(답변 공개 상태 반영)는 이 이슈 범위 밖이므로,
  발행된 이벤트가 `PENDING` 상태로 남아 있는 것까지만 확인한다.

## 8. Test data and isolation

- Fixtures: 기존 `FilteringPersistenceIntegrationTest`의 `filter_release` →
  `filter_job` → `filter_decision` 생성 흐름을 재사용한다. 답변·계정은
  기존 통합 테스트의 헬퍼로 만든다.
- Database isolation: Testcontainers PostgreSQL. 기본은 `@Transactional`
  롤백이며, 동시성 시나리오(INT-004, INT-011)만 커밋 후 명시적으로 삭제한다.
- Clock/randomness: 모든 시각은 고정 `Clock`(`Clock.fixed`)으로 주입한다.
  184일 경계는 상대 오프셋으로 계산하고 실제 현재 시각에 의존하지 않는다.
- External API doubles: 없음. 포트는 단위 테스트에서 람다 스텁을 쓴다.
- Cleanup: 동시성 시나리오는 생성한 `appeal_case`·`outbox_event` 행을
  테스트 종료 시 삭제한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `src/main/resources/db/migration/V18__add_appeal_case_decision_and_window.sql` | INT-001 | `./gradlew integrationTest --tests '*FlywayMigration*'` |
| 2 | Feature executor | `filtering/domain/{AppealCase,AppealCaseStatus,AppealDecision,AppealWindow,AppealAcceptance,AppealAcceptanceReasonCode}.java`, `filtering/error/FilteringErrorCode.java` | UNIT-001~015 | `./gradlew test --tests '*Appeal*'` |
| 3 | Feature executor | `filtering/repository/AppealCaseRepository.java`, `filtering/repository/jpa/{AppealCaseJpaEntity,AppealCaseJpaMapper,JpaAppealCaseRepository,SpringDataAppealCaseRepository}.java` | INT-014 | `./gradlew integrationTest --tests '*FilteringPersistence*'` |
| 4 | Feature executor | `filtering/moderation/{AppealCaseService,AppealTargetOwnershipChecker,PublicationBlockChecker}.java`, `filtering/moderation/AnswerModerationEventPayloads.java` | UNIT-016, INT-002~012 | `./gradlew test integrationTest --tests '*Appeal*'` |
| 5 | Feature executor | `answer/service/{AnswerAppealOwnershipChecker,AnswerPublicationBlockChecker}.java` | INT-005, INT-009 | 4번과 동일 |
| 6 | Feature executor | `filtering/web/{AppealApiSpec,AppealController,AppealCaseApiSpec,AppealCaseController,AppealCaseResponse,FileAppealRequest,AppealDecisionRequest,ExtendAppealExpiryRequest}.java`, `docs/api/openapi.json` | INT-013, INT-015 | `./harness pr-ready --project-tests` |
| 7 | Feature executor | 기존 테스트 갱신: `src/test/java/com/dnd/qello/filtering/FilteringValueObjectsTest.java`, `src/integrationTest/java/com/dnd/qello/FilteringPersistenceIntegrationTest.java` | INT-014 | 전체 스위트 |

시나리오 소유가 겹치지 않도록 파일 단위로 분리했다. 단일 실행자가 순서대로
진행하므로 파일 충돌은 발생하지 않는다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성

## 11. Human approval

- Reviewer: `@tkv00`
- Decision: Approved — 계획 전체와 `TASK.md`의 Design decisions 8개 판단을 함께 승인했다.
- Approved at: `2026-08-17`
