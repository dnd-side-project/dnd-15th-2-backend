# Test Plan: TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION

> Created at: `2026-08-17T19:40:00+09:00`
> GitHub Issue: `#153`
> Status: Approved

## 1. Objective

신고 기능 전체(#153 Foundation, #154~#157 하위 이슈)가 의존하는 사건(case)·증거
스냅샷·감사 이력의 정합성 기반을 검증한다. 저장소에는 이미 `report`·
`moderation_review`·`user_block`과 `SafetyService`가 있지만, 신고(제보)와 사건
(처리 단위)이 분리돼 있지 않고 신고 시점의 증거가 남지 않는다. 이 이슈는 스키마와
도메인 모델만 추가하며 REST API·집계 제외·알림·운영자 판정은 다루지 않는다
(각각 #154~#157).

검증해야 하는 것은 네 가지다.

- 같은 대상(사용자/질문글/답변)에 동시에 열린 사건이 둘 이상 만들어지지 않는다
  (`INV-RPT-001`). 깨지면 운영자 대기열에 같은 콘텐츠가 중복으로 뜨고 판정도
  중복으로 내려간다.
- `MORE_INFO_REQUIRED`로 전이해도 같은 신고자·같은 대상의 중복 신고 차단이
  풀리지 않는다(`INV-RPT-002`). **이것은 현재 코드의 실제 결함이다** —
  `Report.resolve()`가 `MORE_INFO_REQUIRED`를 종결로 취급해 `resolvedAt`을
  채우는데, `uq_open_report_*` 세 인덱스의 열린 상태 목록에는 그 값이 없다.
  운영자가 추가 정보를 요청하는 순간 중복 신고 차단이 풀린다.
- 접수된 신고에는 그 시점의 증거 스냅샷이 정확히 하나 존재하고
  (`INV-RPT-003`), 그 스냅샷과 사건 이력은 생성 후 절대 바뀌지 않는다
  (`INV-RPT-004`). 깨지면 작성자가 신고 직후 글을 고치거나 지웠을 때 판정
  근거가 사라지거나, 사후에 조작될 수 있다.

설계 근거: `docs/product/ANSWER_REPORT_DESIGN.md`(§3, §5.4, §6.3, §7).

## 2. Scope

### Included

- `ReportReason`(8종), `ReportSubReason`(`CSAM`/`NCII`/`CREDIBLE_THREAT`),
  `ReportTargetType`(`USER`/`DIRECTION_POST`/`ANSWER`), `ReportCaseStatus`
  (`OPEN`/`UNDER_REVIEW`/`RESOLVED`), `ReportCaseSeverity`(`NORMAL`/`CRITICAL`),
  `ReportCaseQueue`(`STANDARD`/`URGENT`), `ReportCaseEventType`(`CASE_OPENED`/
  `REPORT_ATTACHED`/`DUPLICATE_SUPPRESSED`/`ESCALATED`/`MORE_INFO_REQUESTED`/
  `RESOLVED`) 신규 enum. `ReportCaseEventType`는 이 이슈가 값 전부를 정의하지만
  실제로 발행하는 것은 `CASE_OPENED`뿐이다 — 나머지는 #154/#156이 소비한다
  (`NotificationType`이 처음부터 미래 값을 포함해 정의된 기존 관례와 동일).
- `ReportCase`(신규 도메인) — 대상당 사건 하나. `open()`/`startReview()`/
  `resolve(ModerationDecision, Instant)` 전이. `severity`/`queue`는 이 이슈에서
  항상 `NORMAL`/`STANDARD`로 고정한다(실제 산출 로직은 #156 — **ASSUMED**,
  `AGENTS.md` §4.3 표기).
- `ReportContentSnapshot`(신규 도메인) — 신고 시점 본문·미디어 키·
  `content_hash`·`captured_at`. `author_id`에 FK를 걸지 않는다(계정 삭제와
  증거 보존 충돌 시 증거가 이긴다). 내용 해시는 media key를 정렬한 뒤
  계산해 순서에 무관하다.
- `ReportCaseEvent`(신규 도메인) — append-only 사건 이력.
- `Report` 확장 — `caseId`(nullable), `subReasonCode`(nullable) 필드,
  `attachToCase(long)` 전이, **`requestMoreInfo(Instant)` 신규 전이**(종결
  아님, `resolvedAt` 미설정), `resolve()`는 `ACTIONED`/`NO_VIOLATION`만
  받도록 축소(`MORE_INFO_REQUIRED`를 넘기면 거절).
- `uq_open_report_{user,post,answer}` 세 부분 유일 인덱스 술어에
  `'MORE_INFO_REQUIRED'` 추가.
- `report_case`(대상당 열린 사건 부분 유일 인덱스 포함), `report_content_snapshot`,
  `report_case_event` 테이블. `report.case_id`·`report.sub_reason_code` 컬럼과
  `ck_report_reason`(8종 CHECK)·사유-하위사유 페어링 CHECK.
- `notification.report_id` 컬럼과 `ck_notification_target` 확장(최대 1개
  대상 불변식 유지).
- 스냅샷·사건 이력의 `UPDATE`·`DELETE`를 막는 append-only 트리거 —
  `enforce_question_text_immutability()`처럼 특정 컬럼의 `UPDATE`만 막는
  기존 선례와 달리 **`UPDATE OR DELETE` 전체**를 막는다(design doc §7 요구).
- V16 마이그레이션.
- 설계 문서 `docs/product/ANSWER_REPORT_DESIGN.md` 커밋.
- 신규 오류 코드 `SAF-DOM-004`(`REPORT_ALREADY_LINKED_TO_CASE`),
  `SAF-DOM-005`(`REPORT_CASE_ALREADY_RESOLVED`), `SAF-VAL-008`
  (`INVALID_SNAPSHOT_EDIT_COUNT`) — `SAF-VAL-006`/`007`/`SAF-DOM-003`/
  `SAF-APP-002~004`는 #154(R01)가 이미 이슈 본문에 예약했으므로 이 이슈는
  건드리지 않는다(번호 충돌 방지).

### Excluded

- 신고 접수 서비스·REST API, 사건 병합의 `ON CONFLICT` 동시성 처리(#154).
  이 이슈는 부분 유일 인덱스가 실제로 두 번째 삽입을 막는지까지만 검증하고,
  그 위에서 우아하게 재조회·병합하는 서비스 로직은 검증하지 않는다.
- 집계 제외 2계층, 결과 알림 fan-out(#155).
- 심각도 산출 로직, 대기열 라우팅, 운영자 판정 API(#156).
- 보존 기간·`purge_after` 기본값, 국가별 분기, `CRITICAL` 즉시 숨김 여부
  같은 정책 결정(#157).
- `filtering.ManualReviewCase`/`AppealCase`와의 통합.
- 신고 사유 8종에 `OTHER` 설명 필수 같은 API 레벨 검증 — 도메인이 아니라
  #154가 요청 검증으로 다룬다.
- 기존 feed 조회 SQL(`PostAnswerQuerySql` 등) 자체의 수정 — 이 이슈는 그
  쿼리들이 읽는 테이블에 영향이 없음을 회귀 검증만 한다.

## 3. Source requirements

| Source | Requirement / acceptance criterion |
| --- | --- |
| GitHub Issue #153 | 같은 대상에 열린 사건이 둘 이상 만들어지지 않는다 (`INV-RPT-001`) |
| GitHub Issue #153 | 운영자가 추가 정보를 요청해도 같은 신고자의 중복 신고가 다시 열리지 않는다 (`INV-RPT-002`) |
| GitHub Issue #153 | 접수된 신고에는 같은 트랜잭션에서 기록된 증거 스냅샷이 정확히 하나 존재한다 (`INV-RPT-003`) |
| GitHub Issue #153 | 증거 스냅샷과 사건 이력에 대한 `UPDATE`·`DELETE`가 DB에서 거부된다 (`INV-RPT-004`) |
| GitHub Issue #153 | 작성자가 답변을 삭제한 뒤에도 스냅샷 조회가 가능하다 |
| GitHub Issue #153 | 사유 코드가 8종 밖의 값이면 DB CHECK가 거부한다 |
| GitHub Issue #153 | 기존 `SafetyService`·`SafetyNotificationBoundaryTest`·feed 쿼리에 회귀가 없다 |
| 설계 문서 §3.2 | 상위 사유와 하위 사유 조합은 DB CHECK로 강제한다(도메인 레벨 검증 아님) |
| 설계 문서 §5.4 | 전역 숨김 시 알림 회수(`REVOKED`)는 #155 범위 — 이 이슈는 관련 스키마 준비만 |
| 설계 문서 §6.3 | `MORE_INFO_REQUIRED`는 종결이 아니다. `resolve()`가 이 값을 받으면 안 된다는 결함 수정 |
| 기존 코드: `enforce_question_text_immutability()` | append-only/불변 컬럼을 트리거로 강제하는 기존 선례. 이 함수는 `UPDATE`만 막고 `DELETE`는 막지 않는다 — 이번 트리거는 의도적으로 더 넓게(`UPDATE OR DELETE`) 만든다 |
| 기존 코드: `Report.resolve()` (safety/domain/Report.java:57) | 현재 `MORE_INFO_REQUIRED`를 종결 상태로 받아들이는 실제 결함 위치 |
| 기존 코드: `uq_open_report_*` (V1 스키마, L1227-1240) | 열린 상태 집합이 `('RECEIVED','AUTO_HIDDEN','UNDER_REVIEW')`뿐이라 `MORE_INFO_REQUIRED`가 빠져 있는 실제 결함 위치 |

## 4. Risk inventory

| Risk | Impact | Likelihood | Priority | Evidence needed |
| --- | --- | --- | --- | --- |
| `MORE_INFO_REQUIRED` 수정이 Java 레벨에서만 이뤄지고 인덱스 술어를 빠뜨림 | High — 실제 결함이 재발 | Medium | P0 | INT-011 |
| `report_case` 부분 유일 인덱스가 대상 3종(user/post/answer) 중 일부만 커버 | High — 특정 대상 유형만 중복 사건 허용 | Medium | P0 | INT-001, INT-002 |
| append-only 트리거가 `UPDATE`만 막고 `DELETE`는 빠뜨림(기존 선례를 그대로 복붙) | Critical — 증거 인멸 경로 존재 | Medium | P0 | INT-005, INT-006, INT-007 |
| `report_content_snapshot`이 `answer`/`user_account`에 FK를 걸어 하드 삭제나 CASCADE로 증거가 사라짐 | High — 증거 보존 설계 위반 | Low(설계상 차단 목표) | P0 | INT-008 |
| `ck_notification_target` 확장이 "최대 1개 대상" 불변식을 깨고 2개 이상 허용 | Medium — 결과 알림이 잘못된 화면으로 딥링크 | Low | P1 | INT-013 |
| `ALTER TABLE ADD COLUMN`이 `JdbcSafetyRepository`의 `SELECT *` 매핑을 깨뜨림 | Medium — 기존 조회 회귀 | Low | P1 | INT-012 |
| 사유/하위사유 페어링 CHECK에 오타로 잘못된 조합이 통과되거나 정상 조합이 막힘 | Medium | Medium | P1 | INT-009 |
| media key 정렬 없이 해시를 계산해 같은 내용도 다른 해시가 나옴(향후 #154 중복 억제 로직이 무력화) | Medium — 재신고 억제가 항상 실패 | Medium | P1 | UNIT-017 |
| `report_case.severity`/`queue` 컬럼이 NOT NULL인데 Foundation이 기본값을 주지 않아 삽입 실패 | Low | Low | P2 | UNIT-011 |

## 5. Unit scenarios

| Scenario ID | Given | When | Then | Priority | Owner |
| --- | --- | --- | --- | --- | --- |
| UNIT-001 | 없음 | `ReportReason.values()` 조회 | 정확히 8종, 이름이 설계 문서 §3.1 표와 일치 | P0 | Feature executor |
| UNIT-002 | 없음 | `ReportSubReason.values()` 조회 | 정확히 `CSAM`/`NCII`/`CREDIBLE_THREAT` | P0 | Feature executor |
| UNIT-003 | `RECEIVED` 상태 `Report`, caseId 없음 | `attachToCase(caseId)` 호출 | `caseId` 설정, 나머지 필드 불변 | P0 | Feature executor |
| UNIT-004 | 이미 `caseId`가 설정된 `Report` | 다른 `caseId`로 `attachToCase` 재호출 | 거절(`SAF-DOM-004`) | P0 | Feature executor |
| UNIT-005 | 이미 `caseId`가 설정된 `Report` | **같은** `caseId`로 `attachToCase` 재호출 | 멱등하게 동일 인스턴스 반환(예외 없음) | P1 | Feature executor |
| UNIT-006 | `RECEIVED` 상태 `Report` | `requestMoreInfo(at)` 호출 | 상태 `MORE_INFO_REQUIRED`, `resolvedAt`은 **null 유지**(`INV-RPT-002` 도메인 절반) | P0 | Feature executor |
| UNIT-007 | `MORE_INFO_REQUIRED` 상태 `Report` | `resolve(ACTIONED, at)` 호출 | 정상 전이(막다른 상태 아님), `resolvedAt = at` | P0 | Feature executor |
| UNIT-008 | 임의 상태 `Report` | `resolve(MORE_INFO_REQUIRED, at)` 직접 호출 | 거절(`SAF-DOM-002 INVALID_REPORT_STATUS`) — 기존 결함 제거 회귀 방지 | P0 | Feature executor |
| UNIT-009 | `MORE_INFO_REQUIRED` 상태 `Report` | `requestMoreInfo(at)` 재호출 | 거절(`SAF-DOM-002`) — `RECEIVED`/`UNDER_REVIEW`에서만 허용 | P1 | Feature executor |
| UNIT-010 | `caseId = 0` 또는 음수 | `Report` 생성 | 거절(`SAF-VAL-001 INVALID_ID`, 기존 `requirePositiveOrNull` 패턴 재사용) | P1 | Feature executor |
| UNIT-011 | `answerId`만 채운 대상 | `ReportCase.open(null, null, answerId, now)` 호출 | 상태 `OPEN`, `severity=NORMAL`, `queue=STANDARD`, `decision=null` | P0 | Feature executor |
| UNIT-012 | 대상 0개 또는 2개 이상 채움 | `ReportCase.open(...)` 호출 | 거절(`SAF-VAL-003 INVALID_REPORT_TARGET`, `Report`와 동일 코드 재사용) | P0 | Feature executor |
| UNIT-013 | `OPEN` 상태 `ReportCase` | `startReview()` 호출 | 상태 `UNDER_REVIEW` | P0 | Feature executor |
| UNIT-014 | `UNDER_REVIEW` 상태 `ReportCase` | `resolve(ACTIONED, at)` 호출 | 상태 `RESOLVED`, `decision=ACTIONED`, `resolvedAt=at` | P0 | Feature executor |
| UNIT-015 | `RESOLVED` 상태 `ReportCase` | `resolve(...)` 재호출 | 거절(`SAF-DOM-005 REPORT_CASE_ALREADY_RESOLVED`, `INV-RPT-007` 도메인 절반) | P0 | Feature executor |
| UNIT-016 | `RESOLVED` 상태 `ReportCase` | `startReview()` 재호출 | 거절(`SAF-DOM-005`) — 종결 후 재개방 불가 | P0 | Feature executor |
| UNIT-017 | 동일 `bodyText`, media key 2개를 서로 다른 순서로 담은 두 `ReportContentSnapshot` | 각각 `contentHash` 계산 | 두 해시가 **동일**(정렬 후 계산이므로 순서 무관) | P0 | Feature executor |
| UNIT-018 | `bodyText`만 다른 두 `ReportContentSnapshot` | 각각 `contentHash` 계산 | 두 해시가 다름 | P1 | Feature executor |
| UNIT-019 | `editCount = -1` | `ReportContentSnapshot` 생성 | 거절(`SAF-VAL-008 INVALID_SNAPSHOT_EDIT_COUNT`) | P1 | Feature executor |
| UNIT-020 | 양수 `caseId`, `CASE_OPENED` | `ReportCaseEvent.of(caseId, CASE_OPENED, now)` 호출 | 정상 생성, `occurredAt` 설정 | P1 | Feature executor |
| UNIT-021 | `caseId = 0` | `ReportCaseEvent.of(...)` 호출 | 거절(`SAF-VAL-001`) | P1 | Feature executor |

## 6. Integration scenarios

| Scenario ID | Components | Setup | Action | Expected result | Cleanup |
| --- | --- | --- | --- | --- | --- |
| INT-001 | `report_case`, PostgreSQL | 동일 `answer_id` 대상의 `report_case` INSERT(status `OPEN`)를 두 트랜잭션에서 동시 실행 | 동시 커밋 시도 | 하나만 성공, 나머지는 unique violation(`23505`)으로 거절 — `ON CONFLICT` 없는 raw INSERT로 인덱스 자체의 직렬화만 검증(`ON CONFLICT` 병합 로직은 #154) | 테스트 데이터 정리 |
| INT-002 | `report_case`, PostgreSQL | 동일 `direction_post_id` 대상의 `OPEN` 사건 1건 존재 | 같은 대상으로 `OPEN` 사건 재삽입 | unique violation(`23505`) | 정리 |
| INT-003 | `report_case`, PostgreSQL | 동일 `answer_id` 대상의 `RESOLVED` 사건 1건 존재 | 같은 대상으로 새 `OPEN` 사건 삽입 | 성공(`INV-RPT-007` — 재발은 새 사건, 부분 인덱스가 종결 사건은 배제) | 정리 |
| INT-004 | `report`, `report_content_snapshot`, PostgreSQL | `report` 1건 존재 | 그 `report_id`로 스냅샷 1건 INSERT, 이어서 같은 `report_id`로 2번째 스냅샷 INSERT 시도 | 1번째 성공, 2번째는 PK violation(`23505`) — `INV-RPT-003` "정확히 하나" | 정리 |
| INT-005 | `report_content_snapshot`, PostgreSQL | 스냅샷 1건 커밋됨 | `body_text` `UPDATE` 시도 | 트리거가 거절(`ERRCODE 23514`), 원본 값 유지 | 정리 |
| INT-006 | `report_content_snapshot`, PostgreSQL | 스냅샷 1건 커밋됨 | `DELETE` 시도 | 트리거가 거절(`23514`) — 기존 `enforce_question_text_immutability` 선례는 `DELETE`를 막지 않으므로 반드시 별도 확인 | 정리 |
| INT-007 | `report_case_event`, PostgreSQL | 이벤트 1건 커밋됨 | `UPDATE`와 `DELETE` 각각 시도 | 둘 다 트리거가 거절(`23514`) | 정리 |
| INT-008 | `answer`, `report`, `report_content_snapshot`, PostgreSQL | 답변 작성 → 신고 접수(수동 fixture) → 스냅샷 캡처 → 답변 `soft delete`(`deleted_at` 설정) | 스냅샷 재조회 | `body_text`·`media_object_keys` 등 원본 값 그대로 조회됨(FK 부재로 CASCADE 영향 없음). 스키마 카탈로그 조회로 `report_content_snapshot`이 `answer`/`user_account`를 참조하는 FK가 **존재하지 않음**을 함께 확인 | 정리 |
| INT-009 | `report`, PostgreSQL | 없음 | `reason_code='SEXUAL_CONTENT', sub_reason_code='CSAM'`로 INSERT, 이어서 `reason_code='SPAM_OR_ADVERTISING', sub_reason_code='CSAM'`로 INSERT 시도 | 1번째 성공, 2번째는 CHECK violation(`23514`) | 정리 |
| INT-010 | `report`, PostgreSQL | 없음 | `reason_code='NOT_A_REAL_REASON'`으로 INSERT | CHECK violation(`23514`) | 정리 |
| INT-011 | `report`, PostgreSQL | 신고자 A가 답변 X에 대해 이미 `MORE_INFO_REQUIRED` 상태인 신고 1건 보유 | 같은 신고자 A가 같은 답변 X에 재신고 INSERT | unique violation(`23505`) — 수정 전에는 통과했을 케이스(`INV-RPT-002` 핵심 회귀 방지 시나리오) | 정리 |
| INT-012 | `SafetyRepository`(JDBC), PostgreSQL | V16 적용 후 `report` 1건 저장 | `findOpenReport`/`findReportById` 호출 | 새 컬럼(`case_id`, `sub_reason_code`)이 있어도 기존 필드 매핑이 정상 동작 | 정리 |
| INT-013 | `notification`, PostgreSQL | 없음 | `report_id`만 채운 알림 INSERT(성공 기대), 이어서 `report_id`+`answer_id` 둘 다 채운 알림 INSERT 시도 | 1번째 성공, 2번째는 CHECK violation(`23514`) — "최대 1개 대상" 불변식이 넓힌 뒤에도 유지 | 정리 |

## 7. Cross-cutting scenarios

### Database and transactions

- append-only 트리거는 두 테이블(`report_content_snapshot`, `report_case_event`)에
  같은 함수(`enforce_report_evidence_immutability()`, `TG_TABLE_NAME` 분기)로
  구현하고 `BEFORE UPDATE OR DELETE`로 건다 — `enforce_question_text_immutability()`
  선례를 그대로 복사하면 `DELETE`가 빠지므로 INT-006/INT-007이 이 차이를 명시적으로
  가둔다.
- V16은 다른 DDL 트랜잭션과 마찬가지로 Flyway가 단일 트랜잭션으로 감싸는 것에
  의존한다. `CREATE INDEX CONCURRENTLY`처럼 트랜잭션 밖에서 실행되는 구문은
  쓰지 않는다(기존 V1~V15 관례와 동일).
- 신고 INSERT와 스냅샷 INSERT를 **같은 트랜잭션**에서 묶는 책임은 #154의
  `SafetyReportService`에 있다. 이 이슈는 스키마가 그 원자성을 허용하는지
  (FK, PK 제약)만 증명하고, 실제로 그렇게 호출하는 서비스는 만들지 않는다.

### Concurrency and idempotency

- INT-001은 사건 생성 경쟁의 스키마 레벨 방어선을 검증한다. 두 번째 트랜잭션이
  단순히 실패하는 것까지만 이 이슈의 책임이고, 실패를 잡아 기존 사건에 재조회·
  병합하는 것은 #154의 `ON CONFLICT ... DO NOTHING` 로직이다.
- `attachToCase`의 멱등성(UNIT-005)은 같은 사건으로 재시도되는 흔한 경로
  (outbox 재시도, 네트워크 재전송)에서 예외가 나지 않도록 하기 위함이다.

### External APIs

- 해당 없음. 이 이슈는 외부 연동을 갖지 않는다.

### Failure recovery and reconciliation

- 마이그레이션 실패 시 부분 반영이 없어야 한다 — Flyway 트랜잭션 경계로
  보장되며 별도 테스트로 재현하지 않는다(V1~V15와 동일 신뢰 경계).
- 트리거 거절(INT-005~007)은 애플리케이션에는 일반 `DataIntegrityViolationException`
  으로 전달된다. 이 예외를 도메인 오류 코드로 번역하는 것은 이 스키마를 실제로
  호출하는 이슈(#154 이후)의 책임이며, 이 이슈는 DB가 거절한다는 사실만 증명한다.

## 8. Test data and isolation

- Fixtures: `Report`(각 상태·대상 조합), `ReportCase`(`OPEN`/`UNDER_REVIEW`/
  `RESOLVED`), `ReportContentSnapshot`(다양한 media key 순서), `ReportCaseEvent`
  빌더. 기존 `SafetyNotificationBoundaryTest`의 `Report.forAnswer(...)` 호출부는
  새 필드 추가로 인해 함께 갱신한다(회귀 방지 대상, 새 시나리오는 아님).
- Database isolation: 기존 `#109` 통합 테스트와 동일하게
  `PostgisContainerIntegrationTestSupport`(Testcontainers PostgreSQL),
  `@SpringBootTest` + `@ActiveProfiles("test")`.
- Clock/randomness: 고정 `Instant` 리터럴만 사용, wall-clock 의존 없음.
- External API doubles: 불필요.
- Cleanup: 각 통합 테스트가 `@BeforeEach`/`@AfterEach`에서 FK 의존 순서대로
  `report_case_event` → `report_content_snapshot` → `notification` →
  `report` → `report_case` 순으로 삭제한다.

실제 자격 증명이나 `.env` 값을 기록하지 않는다.

## 9. Execution contracts

| Order | Executor | Owned files | Scenario IDs | Verification |
| --- | --- | --- | --- | --- |
| 1 | Feature executor | `src/main/resources/db/migration/V16__*.sql`; `safety/domain/{ReportReason,ReportSubReason,ReportTargetType,ReportCaseStatus,ReportCaseSeverity,ReportCaseQueue,ReportCaseEventType,ReportCase,ReportCaseEvent,ReportContentSnapshot,Report}.java`; `safety/error/SafetyErrorCode.java`; `safety/repository/**`(ReportCase/ReportContentSnapshot/ReportCaseEvent용 신규 포트·JDBC 구현); `docs/error-codes.md`; `docs/product/ANSWER_REPORT_DESIGN.md`; `src/test/java/com/dnd/qello/safety/**`(신규 단위 테스트 + `SafetyNotificationBoundaryTest.java` 갱신); `src/integrationTest/java/com/dnd/qello/**`(신규 통합 테스트 1개 클래스) | UNIT-001~021, INT-001~013 | `INV-RPT-001`~`004` 검증, 기존 `SafetyService`·`SafetyNotificationBoundaryTest`·`uq_open_report_*` 계약과의 호환성 리뷰. `#104`(release registry)·`#106`/`#107`(append-only 이력 선례)와의 패턴 일관성 리뷰 |

## 10. Completion criteria

- [ ] 모든 P0 시나리오 구현
- [ ] 모든 테스트 메서드에 `@DisplayName`
- [ ] 테스트 클래스 헤더의 timestamp와 source scenario 검증
- [ ] 단위 테스트 통과
- [ ] 통합 테스트 통과
- [ ] 잠재 문제 분석
- [ ] 테스트 보고서 생성 —
      `docs/reports/tests/gh-153-TEST-PLAN-GH-153-REPORT-CASE-FOUNDATION.md`

## 11. Human approval

- Reviewer: tkv00
- Decision: Approved
- Approved at: `2026-08-17T00:00:00+09:00`
