# Test Plan: TEST-PLAN-GH-78-SCHEMA-REVISION-V7

> Created at: `2026-08-07T18:23:58+09:00`
> GitHub Issue: `#78`
> Status: Approved
>
> **2026-08-08 참고**: 이 계획 수립 당시 마이그레이션은 `V7`로 설계됐으나, PR #81의
> `device_credential`이 먼저 `main`에 merge되며 실제 파일은
> `V8__widen_answer_visibility_to_recipients.sql`로 재번호됐다. 본문의 `V7` 서술은
> 계획 수립 시점 기준이며, 실행 가능한 파일·버전은 `V8`이다.

## 1. Objective

2026-08-07 제품 개정(답변 격리 폐기)으로 `answer_reaction`의 `answer_id` 단독 PK가
두 번째 사용자의 공감을 PK 충돌로 거부하는 상태다. `V7` 마이그레이션과 매핑 갱신이
실패하면 두 가지 방식으로 조용히 틀릴 수 있다.

1. **DB가 틀린 것을 허용한다** — 복합 PK와 신규 트리거를 잘못 작성하면 자격 없는
   사용자의 공감이나 자기 답변 공감을 막지 못한다.
2. **기존 기능이 조용히 깨진다** — `post_recipient`/`answer`에 `NOT NULL` 컬럼을
   추가하면서 백필을 빼먹으면 기존 행이 있는 DB에서 마이그레이션 자체가 실패하고,
   6개 통합 테스트 파일의 SQL 리터럴 INSERT가 컬럼 목록을 나열하고 있어 그대로
   컴파일은 통과하고 런타임에만 실패한다.

이 계획은 "DB가 새 자격 규칙을 정확히 강제하는가"와 "기존 쓰기 경로가 마이그레이션
직후에도 깨지지 않는가"를 분리해서 검증한다. `V7`은 제품 규칙(누가 답변을 볼 수
있는가)을 구현하지 않는다 — 그 자격 판정은 조회 계층의 몫이며 #79 범위다. 이
계획이 검증하는 것은 DB가 **집합 소속 여부**(질문자인가, 그 질문글의 수신자
집합에 속하는가, 자기 답변인가)까지만 정확히 강제하는가이다.

## 2. Scope

### Included

- `answer_reaction` PK를 `answer_id` 단독에서 `(answer_id, reactor_id)` 복합으로
  전환하는 `V7` 마이그레이션과, 이를 반영한 `AnswerReactionJpaEntity`/
  `AnswerReactionRepository` 시그니처 변경
- `ct_answer_reaction_reactor_is_sender` → `ct_answer_reaction_reactor_can_view`
  트리거 교체(질문자 또는 수신자 집합 소속, 자기 답변 금지)
- `post_recipient`(+`inbound_bearing_deg`, +`distance_m`, +`answers_read_at`)와
  `answer`(+`distance_m`, +`edited_at`, +`edit_count`)의 `NOT NULL`/`CHECK` 컬럼
  추가와 이에 따른 `PostRecipient`/`Answer` 도메인, `JdbcPostRecipientRepository`,
  `AnswerJpaEntity`/`AnswerJpaMapper` 매핑 갱신
- `uq_answer_one_per_recipient` 조건 축소(`status <> 'REJECTED'`)
- 기존 데이터가 있는 DB에서 `V7`이 실패하지 않도록 하는 백필 경로
- `FlywayMigrationContractTest`(SHA-256, migration 목록)와
  `FlywayMigrationIntegrationTest`(catalog 이름·개수) 갱신
- `V7` 적용 이후에도 기존 SQL 리터럴 기반 통합 테스트가 깨지지 않는지 확인하는
  회귀 검증

### Excluded

- 답변 공감·열람 자격 판정 **서비스 로직** 변경(`AnswerReactionService.toggle`의
  "누가 볼 수 있는가" 규칙 자체) — #79
- `feed` 조회 계층(`InboxCard`/`InboxDetail`/`AnswerCard`), 수신함 2카테고리 — #79
- 방향 칩 집계 — #80
- 답변 수정 **쓰기 경로**(만료 전 제출 제한, 운영 설정값 상한) — 이번 회차 제외.
  `edit_count`/`edited_at` 컬럼과 DB `CHECK`만 검증한다
- `docs/product/data-model/*` 문서 동기화와 `schema-manifest.md` SHA-256 재계산 —
  자동화된 JUnit 검증 대상이 아니라 사람이 확인하는 항목이다. §10에 수동 확인
  항목으로 남긴다
- controller, DTO, API 문서

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #78 | `answer_reaction` 복합 PK, 트리거 교체, `post_recipient`/`answer` 컬럼 추가, `uq_answer_one_per_recipient` 조건 축소, 매핑 갱신 |
| TASK.md 완료 조건 | 두 사용자 공감 가능 / 중복 공감 거절 / 자기 답변 공감 거절 / 삭제 후 재작성 불가 / edit_count·edited_at 동치 위반과 상한 초과 거절 / 신규 NOT NULL 컬럼 백필 경로 존재 / manifest SHA-256 일치 / 기존 통합 테스트 통과 |
| `docs/adr/0001-database-schema-ownership.md` | Flyway가 실행 스키마를 소유. 이미 적용된 migration은 수정하지 않고 새 versioned migration만 추가 |
| `docs/adr/0002-jpa-jdbc-boundary.md` | `post_recipient`는 JDBC, `answer`/`answer_reaction`은 JPA로 유지(D6 선례) |
| vault DBML(2026-08-07) | `inbound_bearing_deg`는 매칭 시점 스냅샷, 구간 키는 저장하지 않음. `distance_m`은 근거리 하한(10km) 미만이면 `distance_band`로 대체 표시. `edit_count` 실효 상한은 운영 설정값(초기 3), DB 안전 상한은 10 |
| `docs/product/data-model/schema-manifest.md` §2 | 이미 적용된 migration은 수정하지 않고 새 versioned migration을 추가한다 — `V7`은 append-only여야 한다 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 레거시 `post_recipient`/`answer` 행에는 `inbound_bearing_deg`/`distance_m`을 계산할 근거(발송 시점 좌표)가 남아 있지 않다. `active_user_presence`는 TTL로 사라지므로 "올바른" 백필 값이 존재하지 않는다 | High — 백필 정책 없이는 기존 행이 있는 DB에서 `V7`이 그대로 실패하거나, 의미 없는 placeholder가 조회 결과를 오염시킬 수 있다 | Medium — 로컬/테스트 DB는 대개 비어 있지만 개발 중 누적 데이터가 있을 수 있다 | P0 | 백필 정책(예: placeholder 값과 그 의미)에 대한 설계 결정. 이 계획은 정확한 값이 아니라 "제약을 만족하는 값으로 백필되어 마이그레이션이 실패하지 않는다"만 검증한다(§6 INT-002) |
| `answer`/`post_recipient`에 SQL 리터럴로 INSERT하며 컬럼을 전부 나열하는 통합 테스트가 6개 파일에 있다(`InboxSentPostWriteIntegrationTest`, `SentPostQueryIntegrationTest`, `InboxQueryIntegrationTest`, `AnswerSafetyNotificationPersistenceIntegrationTest`, `ReactionPersistenceIntegrationTest`, `SchemaRevisionMigrationIntegrationTest`) | High — `NOT NULL` 컬럼 추가 시 컴파일은 통과하고 런타임에만 실패해 원인 추적이 늦어진다 | High — 확실히 발생한다 | P0 | 6개 파일 전수 갱신 확인. `SchemaRevisionMigrationIntegrationTest`는 버전 고정 스키마를 의도적으로 쓰므로 기존 시나리오는 그대로 두고 새 V7 시나리오만 추가한다(§6 INT-002) |
| `PostRecipient` 생성자는 15개 positional 파라미터를 가지며 `DirectionDomainTest`에서 9곳, `JdbcPostRecipientRepository`에서 2곳, `DirectionPostService`에서 1곳이 이를 호출한다. 3개 필드 추가는 전 호출부의 인자 순서를 흔든다 | Medium — 컴파일 실패는 즉시 드러나지만, 순서를 착각하면 조용히 잘못된 값이 들어갈 수 있다 | High | P1 | `DirectionDomainTest`의 기존 9개 시나리오가 새 필드 추가 후에도 전부 통과 |
| `FlywayMigrationIntegrationTest`의 `EXPECTED_FUNCTIONS`/`EXPECTED_TRIGGERS`가 `..._reactor_is_sender`를 하드코딩하고 있고, CHECK 제약 개수(101)가 `V7`이 추가하는 개수만큼 어긋난다 | Medium — 놓치면 카탈로그 회귀 테스트가 실패해 원인이 불명확해 보인다 | High | P0 | 개명된 함수·트리거 이름과 갱신된 CHECK 개수가 실제 catalog와 일치 (§6 INT-001) |
| `answer_reaction`이 JPA `saveAndFlush()`로 upsert되던 기존 방식(같은 PK로 재호출 시 UPDATE)이 복합 키에서는 "같은 (answer, reactor) 조합 재확인"으로 의미가 좁아진다. `AnswerReactionService.toggle()`의 호출부는 새 시그니처에 맞춰 기계적으로 고쳐야 컴파일되지만, 자격 판정 로직(질문자 전용 여부) 자체는 #79까지 바꾸지 않아야 한다 | Medium — 범위를 넘으면 #79와 작업이 겹친다 | Medium | P1 | 리뷰에서 `AnswerReactionService`의 조건문(`senderOf(answerId) != reactorId`)이 그대로인지 diff로 확인 |
| 로컬 개발 DB에 이미 V1~V6가 적용된 채로 남아 있으면 애플리케이션 재기동 시 `V7`이 그 위에 자동 적용된다. 첫 번째 리스크(백필 불가)와 결합하면 로컬 기동이 막힐 수 있다 | Medium | Low — Testcontainers 기반 테스트는 매번 새 컨테이너라 영향 없음. 개발자 로컬 상시 DB만 해당 | P2 | 로컬 실행 전 DB 초기화 필요 여부를 PR에 안내 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-001 | `src/main/resources/db/migration/`에 `V7__*.sql`이 추가된 상태 | `FlywayMigrationContractTest`가 migration 파일 목록과 SHA-256을 검사 | `sqlMigrationNames()`가 V1~V7을 이 순서로 포함하고, `V7`의 SHA-256이 잠금 값과 일치한다 | P0 | Executor 1 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-002 | `PostRecipient.available(...)` 또는 `restore(...)` 호출 | `inboundBearingDeg`에 음수, `360`, `359.999`(경계값)를 각각 전달 | 음수와 `360`은 `DirectionException`(`INVALID_BEARING`), `359.999`는 정상 생성 — `matchedBearingDegrees`와 동일한 `[0, 360)` 규칙 | P0 | Executor 2 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-003 | `PostRecipient` 생성 | `distanceM`에 음수 전달 | `DirectionException`으로 거절 | P1 | Executor 2 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-004 | `PostRecipient.restore(...)`에 `matchedAt`과 `answersReadAt` 전달 | `answersReadAt`이 `matchedAt`보다 이름 | 기존 `validateTimestamp` 패턴과 동일하게 `DirectionException`(`INVALID_TIME_ORDER`) | P1 | Executor 2 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-005 | `Answer.restore(...)` 호출 | (a) `editCount=0`이면서 `editedAt`이 not null, (b) `editCount>0`이면서 `editedAt`이 null인 두 조합을 각각 전달 | 둘 다 `AnswerException`(`INVALID_ANSWER_STATE`)으로 거절 — TASK.md 완료 조건 "`(edit_count = 0) = (edited_at IS NULL)` 위반 거절"의 도메인 레벨 대응 | P0 | Executor 2 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-006 | `Answer.submit(...)` 호출 | `distanceM`에 음수 전달 | `AnswerException`으로 거절 | P1 | Executor 2 |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-001 | `FlywayMigrationIntegrationTest`, 빈 PostGIS 컨테이너 | V1~V7 적용 | catalog에서 함수/트리거/제약 이름과 개수를 조회 | `EXPECTED_FUNCTIONS`/`EXPECTED_TRIGGERS`가 `enforce_answer_reaction_reactor_can_view`/`ct_answer_reaction_reactor_can_view`로 개명되어 있고(옛 `_is_sender` 이름은 존재하지 않음), FK(50)·UNIQUE(20) 개수는 불변, CHECK 개수는 `V7`이 추가한 제약 수만큼 정확히 증가한다 | Testcontainers 자동 정리 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-002 | `SchemaRevisionMigrationIntegrationTest`(신규 시나리오, `v7_backfill` 전용 schema), Flyway `target()` API | 별도 schema에 V1~V6만 적용한 뒤 `post_recipient`/`answer`에 V6 형태(신규 컬럼 없는) 레거시 행을 삽입 | `V7`을 적용 | 마이그레이션이 실패하지 않고, 백필된 `inbound_bearing_deg`/`distance_m`이 각 컬럼의 `NOT NULL`·범위 제약을 만족하는 값을 가진다(정확한 값이 아니라 제약 충족 여부만 검증 — §4 리스크 참고) | 격리된 schema이므로 별도 정리 불필요 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-003 | `ReactionPersistenceIntegrationTest`(재작성), `AnswerReactionRepository` | 질문자(sender)와 수신 자격자(recipient)가 모두 `post_recipient`로 존재하는 질문글에 답변 1건 | sender와 recipient가 각각 그 답변에 공감(직접 repository 호출) | 둘 다 성공하고 서로 다른 행(복합 PK)으로 저장된다 — 이전엔 PK 충돌로 두 번째가 실패했던 지점 | `@BeforeEach`에서 관련 테이블 delete |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-004 | 위와 동일 fixture + outsider(질문글과 무관한 계정) | outsider가 그 답변에 공감 시도(직접 repository 호출, 서비스 우회) | `ct_answer_reaction_reactor_can_view`가 거부 | `DataIntegrityViolationException` | 동일 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-005 | 위와 동일 fixture | 답변 작성자 본인(수신 자격자 중 한 명)이 자기 답변에 공감 시도 | 트리거가 자기 답변 공감을 거부 | `DataIntegrityViolationException` — 수신 자격이 있어도 자기 답변에는 안 된다는 규칙을 DB가 직접 강제하는지 확인 | 동일 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-006 | 위와 동일 fixture | 답변 1건, 아직 공감 없음 | 같은 사용자가 같은 답변에 공감 → 취소 → 재공감 | 매 단계 성공, PK 충돌 없음(멱등 재삽입) | 동일 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-007 | 신규 통합 테스트 또는 `AnswerJdbcBoundaryTest` 인접 클래스, raw JDBC | `status='DELETED'`인 기존 답변이 있는 `post_recipient`에 새 답변 INSERT 시도 | `uq_answer_one_per_recipient` 위반 확인 | `DataIntegrityViolationException` — 이전엔 `DELETED`가 예외 목록에 있어 통과하던 경로 | 트랜잭션 롤백 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-008 | 동일 | `status='REJECTED'`인 기존 답변이 있는 `post_recipient`에 새 답변 INSERT | 정상 삽입(회귀 없음 확인) | 성공 | 동일 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-009 | `answer` 테이블, raw JDBC | (a) `edit_count=0, edited_at=NOW()` 삽입, (b) `edit_count=11` 삽입을 각각 시도 | 신규 CHECK 위반 | 둘 다 `DataIntegrityViolationException`. `edit_count=0, edited_at=NULL`(기본값)은 정상 삽입되는 대조군도 함께 확인 | 동일 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-010 | `InboxSentPostWriteIntegrationTest`, `SentPostQueryIntegrationTest`, `InboxQueryIntegrationTest`, `AnswerSafetyNotificationPersistenceIntegrationTest` (기존 파일 4개) | 각 파일의 `post_recipient`/`answer` INSERT에 신규 `NOT NULL` 컬럼 값 추가 | 기존 스위트 재실행 | 새 assertion 없이 기존 assertion이 모두 그대로 통과한다(순수 회귀) | 기존 `@BeforeEach` 정리 로직 유지 |
| TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-011 | `InboxSentPostWriteIntegrationTest`의 `togglesAnswerReaction`/`nonSenderCannotReactToAnswer`/`answerAuthorCannotReactToOwnAnswer` | `answerReactionRepository.findByAnswerId(answerId)` 호출부 3곳을 신 시그니처(`findByAnswerIdAndReactorId` 등)로 교체 | 동일 시나리오 재실행 | 세 테스트 모두 기존과 동일한 결과(성공/`INELIGIBLE_REACTOR`)로 통과 — 서비스 계층의 "질문자만" 자격 판정은 #79까지 바뀌지 않으므로 결과가 달라지면 안 된다 | 동일 |

## 7. Cross-cutting scenarios

### Database and transactions

- `ct_answer_reaction_reactor_can_view`는 `DEFERRABLE INITIALLY DEFERRED` constraint
  trigger로 유지한다 — 위반은 INT-004/005에서 개별 `INSERT` 직후가 아니라 트랜잭션
  commit 시점에 드러나야 한다. 테스트가 `TransactionTemplate.executeWithoutResult`로
  감싸 실제 commit을 트리거하는지 확인한다(`ReactionPersistenceIntegrationTest`의
  기존 패턴을 유지).
- `V7`은 `V2`와 마찬가지로 확장을 만들지 않으므로 기본 트랜잭션 실행이다. 백필
  UPDATE는 제약 추가보다 먼저 실행되어야 한다(V2 선례와 동일한 순서:
  DROP/backfill → ADD).
- `answer_reaction` 복합 PK 전환이 기존 행의 `answer_id` 값을 보존하는지(데이터
  손실 없음) INT-002에서 함께 확인한다.

### Concurrency and idempotency

- INT-006(취소 후 재공감)이 동시성 시나리오는 아니지만, 복합 PK에서 같은
  `(answer_id, reactor_id)`로 반복 `react()`가 예외 없이 멱등하게 동작하는지는
  검증한다. 진짜 동시 요청(같은 사용자가 두 트랜잭션에서 동시에 `react()`)은
  #79의 `AnswerReactionService.toggle()` 재설계 시점에 다룬다 — 이번 계획은
  repository 계층 단위 동작만 본다.

### External APIs

- 해당 없음. 이 이슈는 외부 API 연동이 없다.

### Failure recovery and reconciliation

- `V7`이 레거시 데이터가 있는 스키마에서 실패하면(백필 정책 미비 등)
  `flyway_schema_history`에 `success=false`로 기록되고 이후 재시도가 막혀야 한다
  — `SchemaRevisionMigrationIntegrationTest`의 기존
  `v2FailsLoudlyWhenAPostRecipientAlreadyHasTwoLiveAnswers` 패턴을 참고해, INT-002가
  실패하는 경우(백필 정책이 실제로 없다고 밝혀지면) 조용히 데이터를 지우지 않고
  시끄럽게 실패하는지도 함께 확인한다.

## 8. Test data and isolation

- Fixtures: `PostgisContainerIntegrationTestSupport`(공유 Testcontainers PostGIS
  컨테이너)를 그대로 사용한다. `region_code`는 파일별로 고유한 테스트 지역 코드를
  써서(`TEST-REACT` 등 기존 관행) 파일 간 오염을 막는다.
- Database isolation: INT-002는 `SchemaRevisionMigrationIntegrationTest`의 기존
  관행대로 별도 Postgres schema(`v7_backfill`)를 만들어 애플리케이션 schema(V1~V7
  전체 적용)와 분리한다. 나머지 통합 시나리오는 `@SpringBootTest`가 관리하는
  기본(public) schema를 쓰고 `@BeforeEach`에서 관련 테이블을 delete한다.
- Clock/randomness: 고정 `Instant` 상수(`NOW` 등 기존 관행)를 사용하고
  `Clock.systemUTC()`를 테스트에서 직접 호출하지 않는다.
- External API doubles: 해당 없음.
- Cleanup: JPA 경로(`answer_reaction`)는 `saveAndFlush`/`deleteById`로 명시적 정리,
  JDBC 경로는 `@BeforeEach`의 `DELETE FROM` 순서를 FK 의존 순서(자식→부모)로 유지한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor 1 (migration & catalog) | `src/main/resources/db/migration/V7__*.sql`(신규), `src/test/java/com/dnd/qello/FlywayMigrationContractTest.java`, `src/integrationTest/java/com/dnd/qello/FlywayMigrationIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/SchemaRevisionMigrationIntegrationTest.java`(기존 시나리오 보존, 신규 메서드만 추가), `src/integrationTest/java/com/dnd/qello/ReactionPersistenceIntegrationTest.java` | UNIT-001, INT-001, INT-002, INT-003, INT-004, INT-005, INT-006 | `./gradlew test --tests "com.dnd.qello.FlywayMigrationContractTest"`, `./gradlew integrationTest --tests "com.dnd.qello.FlywayMigrationIntegrationTest" --tests "com.dnd.qello.SchemaRevisionMigrationIntegrationTest" --tests "com.dnd.qello.ReactionPersistenceIntegrationTest"` |
| 2 | Executor 2 (도메인·매핑·회귀, Executor 1의 `V7` 존재를 전제) | `src/main/java/com/dnd/qello/direction/domain/PostRecipient.java`, `.../direction/repository/jdbc/JdbcPostRecipientRepository.java`, `.../direction/service/DirectionPostService.java`(호출부만), `.../answer/domain/Answer.java`, `.../answer/repository/jpa/AnswerJpaEntity.java`, `.../answer/repository/jpa/AnswerJpaMapper.java`, `.../answer/repository/jpa/AnswerReactionJpaEntity.java`, `.../answer/repository/AnswerReactionRepository.java`, `.../answer/repository/jpa/JpaAnswerReactionRepository.java`, `.../answer/repository/jpa/SpringDataAnswerReactionRepository.java`, `.../answer/service/AnswerReactionService.java`(호출부만, 자격 로직 불변), `src/test/java/com/dnd/qello/direction/domain/DirectionDomainTest.java`, `src/test/java/com/dnd/qello/answer/AnswerPersistenceBoundaryTest.java`, `src/integrationTest/java/com/dnd/qello/InboxSentPostWriteIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/SentPostQueryIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/InboxQueryIntegrationTest.java`, `src/integrationTest/java/com/dnd/qello/AnswerSafetyNotificationPersistenceIntegrationTest.java` | UNIT-002, UNIT-003, UNIT-004, UNIT-005, UNIT-006, INT-007, INT-008, INT-009, INT-010, INT-011 | `./gradlew test`, `./gradlew integrationTest`(전체 스위트 — 이 단계의 목적 자체가 "기존 코드 전부가 여전히 통과하는가") |

두 executor는 파일 소유가 겹치지 않는다. Executor 2는 Executor 1이 만든 `V7`
파일 위에서 작업하므로 순서상 뒤에 온다(진짜 병렬 실행 불가 — 같은 마이그레이션에
의존).

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과 (`./gradlew test`)
- [ ] 통합 테스트 통과 (`./gradlew integrationTest`)
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성 (`templates/test-report.md`)
- [ ] (수동, JUnit 대상 아님) `docs/product/data-model/direction_communication.dbml`·
      `DIRECTION_COMMUNICATION_ERD.md`가 vault 최신본과 byte-for-byte 일치하고,
      `schema-manifest.md`의 SHA-256 3곳이 재계산한 값과 일치한다
- [ ] (수동) `./harness check`, `./harness pr-ready --project-tests`,
      `git diff --check` 통과

## 11. Human approval

- Reviewer: Byuntil
- Decision: Approved
- Approved at: 2026-08-07
