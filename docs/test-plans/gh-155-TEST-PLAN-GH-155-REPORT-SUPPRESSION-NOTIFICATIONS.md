# Test Plan: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS

> Created at: `2026-08-19T00:00:00+09:00`
> GitHub Issue: `#155`
> Status: Draft

## 1. Objective

신고된 콘텐츠가 목록·카운트에서 실제로 빠지는지(집계 제외 2계층), 사건 종결이
신고자당 정확히 1건의 알림으로 이어지는지, 그 과정에서 콘텐츠나 알림 데이터가
새는(leak) 경로가 없는지 검증한다. 실패 시 위험은 두 갈래다 — (1) 신고된
콘텐츠가 목록/카운트 중 일부 경로에서만 빠져 "신고했는데 그대로 보인다"는
신뢰 문제, (2) outbox/알림 중복 또는 유실로 신고자가 결과를 못 받거나
여러 번 받는 문제.

## 2. Scope

### Included

- 신고자 한정 숨김: `ContentSuppressionSql`(신규) 적용 5곳
  (`PostAnswerQuerySql.SELECT_ANSWERS`, `InboxQuerySql.SELECT_CARD`의
  `answer_count`/`unread_answer_count`, `SentPostQuerySql.SELECT_CARD`의
  `answer_count`/`unread_answer_count`) + `report(reporter_id, answer_id)`
  부분 인덱스.
- 전역 숨김: `Answer.hide(at)`/`restore(at)` 도메인 전이, 위 5곳의 상태
  기반 자동 제외, 답변을 가리키던 `notification`의 `REVOKED` 전이,
  `assert_answer_has_content` 트리거로 인한 복원 실패 경로.
- 결과 알림: 내부 전용 사건 종결 서비스 메서드(REST 없음, TASK.md "Scope
  decision" 참고) → 신고자 수만큼 outbox event 발행 → `ReportResolutionFanOutWorker`
  fan-out → 신고자당 `notification` 1건, push 선호가 켜진 기기에만
  `notification_delivery`.

### Excluded

- 신고 접수·사유·병합·evidence 스냅샷 경로 자체(#154, 이미 구현·테스트됨) —
  회귀만 필요하면 기존 통합 테스트가 커버한다.
- 심각도 산출·대기열 라우팅·운영자 판정 REST API(#156).
- 자동 전역 숨김 임계값 트리거(R04).
- Slack 보조 알림(#111).
- `OutboxRetryPolicy`의 재시도/backoff 산식 자체 재검증 — `RecipientNotificationFanOutWorker`
  경로에서 이미 검증됐으므로, `ReportResolutionFanOutWorker`는 그 정책을
  "재사용"하는지만 확인하고 산식 세부는 재테스트하지 않는다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #155 | 답변 목록 길이 == `answer_count` (신고자 한정 숨김 + 전역 숨김 각각에서) — `INV-RPT-006` |
| GitHub Issue #155 | `unread_answer_count`·발신함 방향 칩 카운트도 동일 규칙으로 감소 |
| GitHub Issue #155 | 전역 숨김된 답변을 가리키던 기존 알림이 `REVOKED` |
| GitHub Issue #155 | 사건 종결 시 신고자 수만큼 outbox event, fan-out 후 신고자당 알림 1건 — `INV-RPT-008` |
| GitHub Issue #155 | worker 재실행해도 알림 중복 없음 |
| GitHub Issue #155 | 푸시 선호 꺼져도 인앱 알림 행은 생성 |
| GitHub Issue #155 | 미디어 정리된 답변의 복원이 `assert_answer_has_content`로 실패하는 경로가 테스트로 문서화 |
| TASK.md Scope decision | 사건 종결은 내부 서비스 메서드로만 노출, REST 없음 |
| `V1__create_direction_communication_schema.sql:962-985` | `assert_answer_has_content`: `status='PUBLISHED'` AND `body_text` 공백 AND READY 미디어 0건이면 예외(`23514`) |
| `V1__create_direction_communication_schema.sql:745-775` | `notification` CHECK: `notification_type`에 `REPORT_RESOLVED` 포함, `status`에 `REVOKED` 포함, `UNIQUE(recipient_id, dedup_key)` |
| `RecipientNotificationFanOutWorker.java` | claim→트랜잭션→lease-fencing→재시도→stale 골격을 `ReportResolutionFanOutWorker`가 재현해야 함 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| 5곳 중 한 곳만 신고자 한정 숨김을 반영하고 나머지는 놓침 | 신고자가 여전히 자기가 신고한 콘텐츠를 일부 화면에서 봄 | Medium | P0 | INT-001~004 각 지점 개별 검증 |
| 전역 숨김이 목록에서는 빠지는데 카운트에서만 남거나 그 반대 | 목록 길이 != count, 프론트 뱃지 불일치 | Medium | P0 | INT-006, INT-007 |
| 신고자 한정 숨김이 사건 종결(RESOLVED)과 잘못 결합돼 종결 후 다시 보임 | "종결 결과와 무관하게 유지" 요구 위반 | Medium | P0 | INT-005 |
| outbox event가 사건 1개당 1개만 발행돼 다중 신고자 중 일부가 알림을 못 받음 | 신뢰 문제, `INV-RPT-008` 직접 위반 | High | P0 | INT-011 |
| fan-out worker 재실행 시 `notification`/`notification_delivery` 중복 삽입 | 사용자가 같은 알림을 여러 번 받음 | Medium | P0 | INT-014 |
| 선호 게이트가 `RecipientNotificationFanOutWorker`처럼 알림 생성 자체를 막아버림 | "인앱은 항상 생성" 요구 위반 | High | P0 | INT-015 |
| 사건 종결 트랜잭션 중 outbox 삽입 실패 시 case 상태만 RESOLVED로 남고 event가 없음 | 신고자가 영영 알림을 못 받는데 사건은 종결된 것처럼 보임 | Medium | P0 | INT-016 |
| 동시에 같은 사건을 두 번 종결 시도해 outbox event가 신고자당 2개씩 생김 | 알림 중복, DB 정합성 붕괴 | Low | P1 | INT-017 (concurrency) |
| `restore()`가 트리거 예외를 삼키거나 무시해 빈 콘텐츠 답변이 조용히 재공개됨 | 빈 답변이 PUBLISHED로 노출 | Low | P1 | INT-010 |
| `report(reporter_id, answer_id)` 인덱스 누락으로 신고자 한정 숨김 조회가 seq scan | 운영 성능 저하(기능 실패 아님) | Low | P2 | INT-018 (스키마 확인, 실행계획까지는 아님) |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | `AnswerStatus.PUBLISHED` 상태의 `Answer` | `hide(at)` 호출 | `status == HIDDEN`, 나머지 필드 보존 | P0 | Executor A |
| UNIT-002 | `AnswerStatus.SUBMITTED`/`SAFETY_CHECKING`/`REJECTED`/`DELETED` 상태의 `Answer` | `hide(at)` 호출 | `AnswerException(INVALID_ANSWER_STATUS)` | P0 | Executor A |
| UNIT-003 | `AnswerStatus.HIDDEN` 상태의 `Answer` | `restore(at)` 호출 | `status == PUBLISHED`, `publishedAt` 갱신 여부는 구현 결정에 맞춰 검증(최초 `publishedAt` 보존 또는 갱신 중 택1 — 구현 시 결정 근거를 클래스 주석 또는 커밋 메시지에 남긴다) | P0 | Executor A |
| UNIT-004 | `AnswerStatus.PUBLISHED`/`SUBMITTED` 등 HIDDEN이 아닌 `Answer` | `restore(at)` 호출 | `AnswerException(INVALID_ANSWER_STATUS)` | P0 | Executor A |
| UNIT-005 | `hide(at)`에 `at == null` | 호출 | `REQUIRED_VALUE_MISSING` | P1 | Executor A |
| UNIT-006 | `UNREAD`/`READ`/`DISMISSED` 상태의 `Notification` | (신규 도메인 메서드, 예: `revoke(at)`) 호출 | `status == REVOKED` | P0 | Executor A |
| UNIT-007 | 이미 `REVOKED` 상태의 `Notification` | `revoke(at)` 재호출 | 예외 없이 같은 `REVOKED` 상태 유지(멱등) — 전역 숨김이 반복 호출돼도 안전해야 함 | P0 | Executor A |
| UNIT-008 | `NotificationStatus.REVOKED` 상태의 `Notification` | `markRead(at)` 호출 | 기존 가드 그대로 예외(회귀 확인, 코드 변경 없음) | P2 | Executor A |
| UNIT-009 | `ReportCase.RESOLVED` 상태가 아닌 `ReportCase`(OPEN/UNDER_REVIEW) | 내부 종결 서비스 메서드가 내부적으로 `ReportCase.resolve(...)` 호출 | `RESOLVED` 전이(기존 #153 도메인 가드 재확인, 신규 로직 아님 — 회귀 성격) | P2 | Executor A |

## 6. Integration scenarios

DB 필요(PostgreSQL). Spring 컨텍스트 기동, 실제 트랜잭션 사용.

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | `PostAnswerQuerySql`, `JdbcPostAnswerQueryRepository`(또는 해당 구현체) | 질문글 + PUBLISHED 답변 1개, reporterA가 그 답변을 신고(RECEIVED, 사건 무관) | reporterA가 답변 목록 조회 / 다른 열람자가 같은 목록 조회 | reporterA 결과에서 그 답변 제외, 다른 열람자 결과에는 포함 | 트랜잭션 롤백 |
| INT-002 | `InboxQuerySql.SELECT_CARD` `answer_count` | 수신자(reporterA)가 받은 질문글에 PUBLISHED 답변 1개, reporterA가 그 답변 신고 | reporterA의 수신함 카드 조회 | `answer_count`가 신고 전 대비 1 감소 | 트랜잭션 롤백 |
| INT-003 | `InboxQuerySql.SELECT_CARD` `unread_answer_count` | 위와 동일, 답변이 미열람 상태 | reporterA의 수신함 카드 조회 | `unread_answer_count`가 1 감소 | 트랜잭션 롤백 |
| INT-004 | `SentPostQuerySql.SELECT_CARD` `answer_count`/`unread_answer_count` | 발신자(reporterA)가 보낸 질문글에 PUBLISHED 답변 1개, reporterA(발신자 본인)가 그 답변 신고 | reporterA의 발신함 카드 조회 | 두 카운트 모두 1 감소 | 트랜잭션 롤백 |
| INT-005 | §1 전체 + 내부 종결 서비스 | INT-001 상태에서 사건을 `NO_VIOLATION`으로 종결 | reporterA가 다시 답변 목록/카운트 조회 | 여전히 제외됨(종결 결과와 무관하게 유지) | 트랜잭션 롤백 |
| INT-006 | `Answer.hide`, 5개 조회 지점 | PUBLISHED 답변 1개, 신고와 무관한 제3자 뷰어 | 서비스 계층에서 `Answer.hide(at)` 후 저장 → 제3자가 목록/`answer_count` 조회 | 목록에서 빠지고 `answer_count`도 함께 감소(둘 다 같은 스냅샷에서) | 트랜잭션 롤백 |
| INT-007 | `Answer.hide`, `unread_answer_count`, 발신함 카운트 | 위와 동일 | 전역 숨김 후 재조회 | `unread_answer_count`·발신함 카운트도 함께 감소 | 트랜잭션 롤백 |
| INT-008 | `Answer.hide`, `NotificationRepository` | 그 답변을 가리키는 기존 `notification`(예: `ANSWER_RECEIVED`) 1건 존재 | 전역 숨김 실행 | 해당 `notification.status == REVOKED` | 트랜잭션 롤백 |
| INT-009 | `Answer.restore` | HIDDEN 상태 답변(본문 텍스트 있음) | `restore(at)` 후 저장 | 목록/카운트에 다시 나타남 | 트랜잭션 롤백 |
| INT-010 | `assert_answer_has_content` 트리거 | HIDDEN 상태 답변, `body_text IS NULL`, 첨부 미디어를 테스트 SQL로 `READY` 아닌 상태로 직접 갱신(정리 시뮬레이션) | `restore(at)` 후 저장 시도 | DB 예외(`23514`, deferred constraint) 발생 — 애플리케이션이 이를 삼키지 않고 전파하는지 확인 | 트랜잭션 롤백 |
| INT-011 | 내부 종결 서비스, `SafetyRepository.findReportsByCaseId`(신규), `OutboxEventRepository` | 같은 대상에 reporterA·reporterB 2명이 신고해 병합된 사건 1개(#154 패턴 재사용) | 사건 종결 | outbox event가 정확히 2개, `dedup_key`가 각각 `report-resolved:{reportIdA}`/`report-resolved:{reportIdB}`로 서로 다름 | 트랜잭션 롤백 |
| INT-012 | outbox event payload | INT-011 상태 | payload JSON 파싱 | 대상 식별자(targetUserId/directionPostId/answerId)·작성자 식별자가 payload에 없음(reportId만 또는 최소 정보) | 트랜잭션 롤백 |
| INT-013 | `ReportResolutionFanOutWorker` | INT-011 상태의 outbox event 2개 | `processBatch(...)` 실행 | `notification` 2건(신고자별 1건), `notification_delivery`는 각 신고자의 active push device 수만큼 | 트랜잭션 롤백 |
| INT-014 | `ReportResolutionFanOutWorker` | INT-013 처리 완료 후 event가 `PROCESSED`인 상태에서 같은 batch를 다시 실행(claim 대상 없음을 확인) 또는 `saveIfAbsent` 재호출을 직접 시뮬레이션 | worker 재실행 | `notification`/`notification_delivery` 행 수 불변(중복 없음) | 트랜잭션 롤백 |
| INT-015 | `notification_preference`, `ReportResolutionFanOutWorker` | reporterA가 `REPORT_RESOLVED` 타입 push 선호를 `enabled=false`로 설정, active push device 보유 | 사건 종결 → fan-out 실행 | reporterA의 `notification` 행은 생성, `notification_delivery` 행은 생성되지 않음 | 트랜잭션 롤백 |
| INT-016 | 내부 종결 서비스, `OutboxEventRepository`를 `@MockitoSpyBean`으로 감싸 저장 실패 주입(#154 `blockAuthorFailureRollsBackReportSnapshotAndCase` 패턴 재사용) | 사건 종결 호출 중 outbox 저장이 예외를 던지도록 스텁 | 종결 서비스 메서드 호출 | 트랜잭션 전체 롤백 — `report_case.status`가 여전히 OPEN/UNDER_REVIEW, outbox event 없음 | 스파이 리셋 |
| INT-017 | 내부 종결 서비스, 2-way 동시성(#154 concurrency 테스트 스타일 재사용) | OPEN 상태 사건 1개, 신고자 2명이 이미 연결됨 | 같은 사건 id로 종결 메서드를 동시에 2번 호출 | 정확히 1번만 성공(`RESOLVED` 전이), 다른 1번은 `REPORT_CASE_ALREADY_RESOLVED` 예외, outbox event는 신고자당 정확히 1개(총 2개, 4개 아님) | 트랜잭션 롤백 |
| INT-018 | `report` 테이블 인덱스 | 없음 | `information_schema.indexes`(또는 `pg_indexes`)에서 `report(reporter_id, answer_id)` 부분 인덱스 존재 확인 | 인덱스 존재, `WHERE answer_id IS NOT NULL` 조건 포함 | 없음(읽기 전용 조회) |

## 7. Cross-cutting scenarios

### Database and transactions

- INT-016으로 "사건 종결 + outbox 발행 + (필요시 전역 숨김 부수효과)"가 단일
  트랜잭션임을 직접 검증한다. 부분 커밋(사건만 RESOLVED로 남고 outbox 없음)은
  치명적 결함으로 취급한다.
- `assert_answer_has_content`는 `DEFERRABLE INITIALLY DEFERRED` 제약이라
  트랜잭션 커밋 시점에 평가된다 — INT-010은 `save()` 호출 자체가 아니라
  트랜잭션 커밋(또는 flush + commit)에서 예외가 나는지 확인해야 한다. JPA
  `saveAndFlush`를 쓰는 `JpaAnswerRepository.save`가 flush 시점에 예외를
  던지는지, 아니면 커밋까지 지연되는지 실행 에이전트가 실제로 확인하고
  테스트를 그에 맞게 작성한다(계획 단계에서 단정하지 않는다).

### Concurrency and idempotency

- INT-017: 동시 종결 시도 — 기존 `ReportCase.resolve()`의 `requireStatus`
  가드가 두 번째 호출에서 예외를 던지는 경로를 실제 DB 트랜잭션 경합으로
  재현한다(단위 테스트의 순차 호출과 다르다 — 두 스레드/커넥션이 실제로
  겹치게 만든다).
- INT-014: fan-out worker의 `saveIfAbsent`/`saveDeliveryIfAbsent` 기반
  멱등성을 실제 재실행으로 검증한다.
- UNIT-007: `Notification.revoke`가 이미 REVOKED인 상태에서도 예외 없이
  멱등하게 동작하는지 도메인 레벨에서 먼저 보장한다.

### External APIs

- 해당 없음 — 이 이슈는 외부 API 연동이 없다(push provider 실제 호출은
  `notification_delivery` 행 생성까지만 다루고 provider 전송 자체는 기존
  delivery 워커 책임이라 이 계획 범위 밖이다).

### Failure recovery and reconciliation

- INT-016이 실패 복구의 핵심 시나리오다 — outbox 발행 실패 시 사건 상태와
  outbox가 같이 롤백되는지 확인한다(둘 중 하나만 반영되는 반쪽 상태 방지).
- fan-out worker의 재시도/dead-letter 분류(`OutboxRetryPolicy`)는 이미
  `RecipientNotificationFanOutWorker`에서 검증됐다는 전제 하에, 이 계획은
  `ReportResolutionFanOutWorker`가 실패 시 같은 `recordFailure` 경로를
  "호출"하는지까지만 확인하고 재시도 횟수·backoff 산식 자체는 재검증하지
  않는다(§2 Excluded와 일치).

## 8. Test data and isolation

- Fixtures: #154 통합 테스트가 이미 쓰는 질문글/답변/신고 생성 헬퍼가 있다면
  재사용한다(신규 유틸리티를 중복 작성하지 않는다). 없으면 이 계획의 실행
  에이전트가 최소 헬퍼를 만들되 safety 패키지 밖(answer/feed)까지 새
  테스트 전용 인프라를 넓히지 않는다.
- Database isolation: 각 통합 테스트는 트랜잭션 롤백 또는 기존 컨벤션(예:
  `@Transactional` 테스트 롤백, 또는 명시적 cleanup)을 그대로 따른다 —
  이 저장소의 기존 통합 테스트 컨벤션을 실행 에이전트가 확인하고 맞춘다.
- Clock/randomness: 기존 `Clock` 주입 패턴(`SafetyReportService`,
  `RecipientNotificationFanOutWorker` 모두 `Clock` 또는 `Instant at`
  파라미터를 받는다)을 그대로 따라 고정 시각을 사용한다.
- External API doubles: 불필요(§7 참고).
- Cleanup: INT-016의 `@MockitoSpyBean`은 테스트 후 자동 컨텍스트 정리 또는
  명시적 리셋으로 다른 테스트에 스텁이 새지 않게 한다(#154의 기존 패턴
  재사용).

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Executor A (domain) | `src/main/java/com/dnd/qello/answer/domain/Answer.java`, `src/main/java/com/dnd/qello/notification/domain/Notification.java`, 대응 단위 테스트 | UNIT-001~009 | `./gradlew test --tests "com.dnd.qello.answer.*" --tests "com.dnd.qello.notification.*"` |
| 2 | Executor A (SQL/suppression) | 신규 `ContentSuppressionSql`, `PostAnswerQuerySql`, `InboxQuerySql`, `SentPostQuerySql`, 신규 DB 마이그레이션(인덱스) | INT-001~005, INT-018 | `./gradlew integrationTest --tests "com.dnd.qello.*Suppress*" --tests "com.dnd.qello.*Feed*"` |
| 3 | Executor A (전역 숨김) | `AnswerRepository` 호출부(서비스 계층), 5개 조회 지점 회귀 확인 | INT-006~010 | `./gradlew integrationTest --tests "com.dnd.qello.*Answer*Hide*"` (실제 클래스명은 실행 시 확정) |
| 4 | Executor A (결과 알림) | 신규 내부 종결 서비스 메서드, `SafetyRepository.findReportsByCaseId`, `NotificationRepository` REVOKE 확장, 신규 `ReportResolutionFanOutWorker` | INT-011~017 | `./gradlew integrationTest --tests "com.dnd.qello.*ReportResolution*"` |

단일 실행 에이전트(Executor A)로 순서만 나눈 이유: 전역 숨김·알림 REVOKE·
outbox fan-out이 같은 트랜잭션 경계를 공유해 파일을 여러 에이전트로 쪼개면
서로의 계약(예: `Answer.hide`가 반환하는 상태를 알림 REVOKE가 바로 다음
단계에서 가정)이 어긋나기 쉽다. 병렬화가 필요하면 1(도메인)과 2(SQL)는
독립적이라 동시 진행 가능하다.

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario ID 기록
      (`Source scenario: TEST-PLAN-GH-155-REPORT-SUPPRESSION-NOTIFICATIONS`)
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석(애플리케이션·DB·동시성·트랜잭션·장애 복구 관점)
- [ ] 테스트 보고서 생성(`templates/test-report.md`)
- [ ] INT-010의 트리거 실패 경로가 "고침"이 아니라 "문서화"임을 보고서에
      명시(§2 Excluded, 자동 전역 숨김/미디어 보존 정책은 이 이슈 밖)

## 11. Human approval

- Reviewer:
- Decision:
- Approved at:
