# 답변글 신고 설계

> 작성일: 2026-08-17
>
> 상태: 초안 (GitHub Issue 미생성 — 구현 착수 전 게이트 미충족)
>
> 관련 자산: `safety` 패키지, `report`/`moderation_review`/`user_block` 테이블(V1),
> `notification`·`outbox_event`(V1, V2), `filtering` 패키지(`ManualReviewCase`,
> `AppealCase`), `feed` 조회 SQL

## 0. 작업 게이트

`AGENTS.md` §1에 따라 이 문서는 **설계와 작업 분해까지만** 다룬다. 구현은 다음이
갖춰진 뒤에 시작한다.

1. GitHub Issue 생성(`type: feat`, area: safety)
2. `./harness start --issue <N> --type feat --slug answer-report`
3. `./harness task-init`으로 `TASK.md` 계약 생성
4. 이 문서의 §12 미결 항목 중 `BLOCKED` 표시 항목에 대한 사람의 결정

현재 브랜치(`feat/gh-109-snapshot-health-migration`)는 `#109` 계약에 묶여 있으므로
이 문서는 커밋하지 않고, 신고 기능 Issue 브랜치로 옮겨서 커밋한다.

## 1. 요구사항 → 설계 매핑

| # | 요구사항 | 설계 위치 |
| --- | --- | --- |
| 1 | 신고글은 집계에서도 제외 | §5 2계층 숨김 |
| 2 | 처리 결과를 신고자에게 비동기 알림 | §8 Outbox → fan-out |
| 3 | 신고 사유 8종 | §3.1 `ReportReason` |
| 4 | 더보기 → 신고 → 사유 → 설명 → 접수 → 안내 | §9 API 계약 |
| 5 | 반복 신고가 사건을 무한 생성하지 않음 / 내부 판단·상대 정보 비공개 | §6 사건 병합, §10 노출 금지 |
| 6 | 신고 시점의 내용과 시간 기록 | §7 증거 스냅샷 |
| 7 | 즉시 대응 항목의 대기열 분리, 법률 검토 | §4 심각도, §12 법률 게이트 |
| 8 | 질문글·답변에서 같은 위치로 신고·차단 진입 | §9.1 진입점 |

## 2. 현재 상태

이미 존재하는 것과 없는 것을 먼저 고정한다. 신고 기능은 대부분 **기존 자산의
배선**이며 새로 만드는 개념은 "사건"과 "증거 스냅샷" 두 개다.

| 자산 | 현재 | 필요 |
| --- | --- | --- |
| `report` 테이블 | 대상 3종(user/post/answer), `reason_code VARCHAR(50)` 자유 문자열, 상태 6종 | 사유 코드 고정, 사건 FK, 하위 사유 |
| `uq_open_report_{user,post,answer}` | `(reporter_id, target)` 부분 유일 인덱스 — 같은 신고자의 미종결 중복 차단 | 그대로 사용. 단 `MORE_INFO_REQUIRED`를 열린 상태로 재분류(§6.3) |
| `SafetyService.report()` | `findOpenReport` → 없으면 저장(멱등) | 사건 병합·증거 스냅샷·감사 이벤트 추가 |
| `moderation_review` | 운영자 판정 1건 append (`internal_note` 포함) | 그대로. 신고자에게 절대 노출 금지 |
| `AnswerStatus.HIDDEN` | enum에 존재하나 **전이 메서드도 사용처도 없음** | 전역 숨김의 유일한 표현으로 채택(§5.2) |
| `NotificationType.REPORT_RESOLVED` | enum·DB CHECK에 존재, 생산자·소비자 없음 | fan-out worker 신규(§8.2) |
| `OutboxEventType.REPORT_RESOLVED`, `OutboxAggregateType.REPORT` | 존재, 사용처 없음 | 종결 트랜잭션에서 발행 |
| `notification` 테이블 | 대상 컬럼이 `direction_post_id`/`answer_id`뿐 | `report_id` 추가(§8.3) |
| `safety/web` | **없음** | 컨트롤러·ApiSpec 신규 |
| `filtering.ManualReviewCase` / `AppealCase` | 자동 필터링 파이프라인의 검토·이의제기 | 신고 사건과 상관관계만 연결(§11.2) |

## 3. 도메인 모델

### 3.1 `ReportReason` (신규 enum, `safety.domain`)

`reason_code`는 현재 자유 문자열이다. 클라이언트가 임의 값을 넣으면 대기열 라우팅과
통계가 무너지므로 enum + DB CHECK로 고정한다.

| 코드 | 표시 문구 | 설명 필수 | 하위 사유 |
| --- | --- | --- | --- |
| `SEXUAL_CONTENT` | 성적 또는 노골적인 컨텐츠 | 선택 | `CSAM`, `NCII` |
| `VIOLENCE_OR_THREAT` | 폭력, 위협 | 선택 | `CREDIBLE_THREAT` |
| `HATE_OR_HARASSMENT` | 혐오, 괴롭힘 | 선택 | — |
| `PRIVACY_VIOLATION` | 개인정보 유출 | 선택 | — |
| `SPAM_OR_ADVERTISING` | 스팸, 광고 | 선택 | — |
| `IMPERSONATION` | 사칭 | 선택 | — |
| `ILLEGAL_OR_DANGEROUS` | 불법 거래 또는 위험 행동 | 선택 | — |
| `OTHER` | 기타 | **필수** | — |

`OTHER`만 설명을 필수로 둔다. 사유 없는 `기타`는 운영자가 판단할 근거가 없어
사건 대기열만 늘린다. 설명 길이 상한은 주입값(초기 제안 500자)으로 두고 서버가
`btrim` 후 검증한다.

사유 목록은 서버가 `GET /api/v1/report-reasons`로 내려준다. 정책이 바뀔 때마다 앱
배포를 기다리지 않기 위해서다.

### 3.2 `ReportSubReason` (신규 enum)

요구사항 7의 "즉시 대응" 항목은 8개 사유의 형제가 아니라 특정 사유의 **하위 선택**
이다. 사유 목록을 10개로 늘리지 않고 2단계로 묻는다.

| 하위 코드 | 상위 사유 | 심각도 |
| --- | --- | --- |
| `CSAM` (아동 성착취물) | `SEXUAL_CONTENT` | `CRITICAL` |
| `NCII` (불법 촬영물·동의 없는 성적 이미지) | `SEXUAL_CONTENT` | `CRITICAL` |
| `CREDIBLE_THREAT` (구체적 폭력 위협) | `VIOLENCE_OR_THREAT` | `CRITICAL` |

상위 사유와 하위 사유의 조합은 DB CHECK로 강제한다. 자해·자살 위험은 사용자가 준
목록에 없어 임의로 추가하지 않는다(§12 미결).

### 3.3 `ReportCase` (신규 도메인) — "사건"

지금 모델에는 신고(제보)와 사건(처리 단위)이 분리돼 있지 않다. 서로 다른 신고자
10명이 같은 답변을 신고하면 운영자 대기열에 10건이 뜨고, 판정도 10번 내려야 한다.
사건을 도입해 **대상 콘텐츠당 열린 사건 1개**로 수렴시킨다.

```text
report_case 1 ──< report  (여러 신고자의 제보)
            1 ──< report_case_event (append-only 이력)
report      1 ──1 report_content_snapshot (신고 시점 증거)
```

사건 상태: `OPEN` → `UNDER_REVIEW` → `RESOLVED`.
사건 판정(`decision`): `ACTIONED` | `NO_VIOLATION` | `MORE_INFO_REQUIRED`.

`report.status`는 소속 사건의 상태를 반영한다. 기존 부분 유일 인덱스가
`report.status`를 읽으므로 컬럼을 유지하되, 권위 있는 상태는 사건이 갖는다.

### 3.4 불변식

| ID | 불변식 | 강제 수단 |
| --- | --- | --- |
| `INV-RPT-001` | 한 대상에 열린 사건은 최대 하나 | 부분 유일 인덱스 |
| `INV-RPT-002` | 같은 신고자·같은 대상에 열린 신고는 최대 하나 | 기존 `uq_open_report_*`(술어 수정) |
| `INV-RPT-003` | 접수된 신고에는 그 시점의 증거 스냅샷이 정확히 하나 존재한다 | PK + 같은 트랜잭션 삽입 |
| `INV-RPT-004` | 증거 스냅샷과 사건 이력은 UPDATE·DELETE 불가 | BEFORE 트리거 |
| `INV-RPT-005` | 신고자에게 반환·전송되는 어떤 응답도 상대 식별자·`internal_note`를 포함하지 않는다 | DTO 필드 집합 테스트 |
| `INV-RPT-006` | 전역 숨김된 답변은 목록과 모든 카운트에서 동시에 사라진다 | `status <> 'PUBLISHED'` 단일 술어(§5.2) |
| `INV-RPT-007` | 사건은 재개방되지 않는다. 재발은 새 사건이다 | 상태 전이 + outbox dedup key |
| `INV-RPT-008` | 결과 알림은 신고 1건당 정확히 한 번 도달한다 | outbox dedup + `uq_notification_recipient_dedup` |

## 4. 심각도와 대기열 분리 (요구사항 7)

```text
report(reason, subReason)
   └─ severity = CRITICAL if subReason ∈ {CSAM, NCII, CREDIBLE_THREAT} else NORMAL
        └─ queue  = URGENT if severity = CRITICAL else STANDARD
             └─ sla_due_at = receivedAt + slaOf(queue)   // 주입값
```

사건은 소속 신고 중 **가장 높은 심각도**를 갖는다. `NORMAL`로 열린 사건에
`CRITICAL` 신고가 붙으면 사건이 `URGENT`로 승격되고 `ESCALATED` 이벤트가 남는다.
강등은 운영자만 할 수 있다.

### 4.1 `CRITICAL` 1건으로 즉시 전역 숨김을 할 것인가

이건 제품·법무 결정이 필요한 지점이라 양쪽을 적어 둔다.

| 안 | 장점 | 위험 |
| --- | --- | --- |
| A. `CRITICAL` 1건 → 즉시 전역 숨김 | 피해 확산 시간 최소화. 방치 비용이 오검 비용보다 압도적으로 큼 | 아무나 하위 사유를 골라 남의 글을 즉시 내릴 수 있음 |
| B. `URGENT` 대기열 라우팅만, 숨김은 운영자·임계 도달·필터링 판정에서만 | 남용 불가 | 운영자 부재 시간대에 최악 콘텐츠가 노출 유지 |

**권고: A + 남용 통제.** 통제 장치는 (a) 계정당 `CRITICAL` 신고 일일 쿼터,
(b) 허위 `CRITICAL` 신고를 `report_case_event`에 기록해 제재 근거로 축적,
(c) SLA 내 미검토 사건 에스컬레이션 알림, (d) 작성자 이의제기 경로(§11.2).
법무·안전 담당 검토 전에는 프로덕션에 켜지 않는다(§12).

`NORMAL` 사건의 자동 전역 숨김은 다음 셋 중 하나일 때만 한다.

1. 서로 다른 신고자 수 ≥ 임계값(주입값)
2. 같은 대상에 대한 `filter_job` 판정이 이미 숨김·수동검토 대상
3. 운영자의 명시적 조치

## 5. 집계 제외 (요구사항 1)

### 5.1 두 계층으로 나누는 이유

"신고글은 집계에서도 제외"를 무조건 전역으로 해석하면 **검증되지 않은 신고 1건으로
누구나 남의 글을 내릴 수 있다.** 반대로 전역 숨김만 두면 신고한 사람이 자기가 신고한
글을 계속 봐야 한다. 두 계층으로 나눈다.

| 계층 | 시점 | 범위 | 대상 |
| --- | --- | --- | --- |
| 신고자 한정 숨김 | 접수 즉시 | 신고자 본인의 화면·카운트 | 모든 신고 |
| 전역 숨김 | 사건 판정 또는 §4 자동 조건 | 전체 사용자 | 일부 사건 |

신고자 한정 숨김은 **종결 결과와 무관하게 유지**한다. `NO_VIOLATION`으로 끝났다고
신고자 화면에 다시 띄우면 "왜 다시 보이냐"는 문의만 만든다. 차단과 같은 성격으로
다룬다.

### 5.2 전역 숨김: `AnswerStatus.HIDDEN`을 쓴다

집계가 노출과 어긋나는 사고("답변 3개인데 2개만 보임")는 목록 쿼리와 카운트 쿼리가
각자 필터를 들 때 생긴다. 다행히 현재 코드베이스는 **다섯 군데 모두 같은 술어 하나**
를 쓴다.

| 파일 | 위치 | 술어 |
| --- | --- | --- |
| `PostAnswerQuerySql.SELECT_ANSWERS` | 답변 목록 | `a.status = 'PUBLISHED'` |
| `InboxQuerySql.SELECT_CARD` | `answer_count` | 동일 |
| `InboxQuerySql.SELECT_CARD` | `unread_answer_count` | 동일 |
| `SentPostQuerySql` | `answer_count` | 동일 |
| `SentPostQuerySql` | `unread_answer_count` | 동일 |

따라서 전역 숨김은 `answer.status`를 `PUBLISHED` → `HIDDEN`으로 바꾸는 것만으로
목록·카드 카운트·미읽음 카운트·칩 카운트에서 **동시에** 빠진다. 새 컬럼도, 새 SQL
조각도 필요 없다. `HIDDEN`은 이미 enum과 DB CHECK에 있으나 전이 메서드가 없으므로
도메인에 추가한다.

```java
// Answer
public Answer hide(Instant at)     // PUBLISHED -> HIDDEN, publishedAt 보존
public Answer restore(Instant at)  // HIDDEN -> PUBLISHED
```

부수 효과 두 가지를 명시한다.

* `assert_answer_has_content` 트리거는 `status = 'PUBLISHED'`일 때만 본문·미디어
  존재를 요구한다. 숨김은 이 검사를 통과시키고, **복원은 다시 검사한다** — 숨김
  기간에 미디어가 정리됐다면 복원이 실패한다(§11.1).
* `post_recipient.status = 'ANSWERED'`와 슬롯 회계는 건드리지 않는다. 답변은 실제로
  제출됐다. 발신자에게 보이는 답변 수만 줄어든다.

### 5.3 신고자 한정 숨김: 공유 SQL 조각

목록·카운트 다섯 곳에 같은 조각을 넣는다. `FeedScopeSql`이 이미 같은 이유로 조각을
공유하고 있으므로 그 관례를 따른다.

```java
// feed/repository/jdbc/sql/ContentSuppressionSql.java
/**
 * 신고자 본인에게만 적용되는 숨김. 종결 결과와 무관하게 유지한다 —
 * NO_VIOLATION으로 끝난 글을 신고자 화면에 되살리지 않는다.
 * 목록과 카운트가 각자 술어를 들면 "답변 수와 실제 목록 길이"가 어긋나므로
 * 반드시 이 상수를 공유한다.
 */
public static final String NOT_REPORTED_BY_VIEWER = """
      AND NOT EXISTS (SELECT 1 FROM report r
                      WHERE r.answer_id = a.id
                        AND r.reporter_id = :viewerId)
    """;
```

`unread_answer_count`와 `answer_count`는 바인딩 이름이 `:recipientId`이므로 조각을
두 벌 두지 말고 파라미터 이름을 통일한다.

필요한 인덱스(기존 부분 유일 인덱스는 열린 신고만 덮으므로 별도로 둔다):

```sql
CREATE INDEX report_reporter_answer_idx
    ON report (reporter_id, answer_id) WHERE answer_id IS NOT NULL;
```

### 5.4 이미 발송된 알림 회수

전역 숨김된 답변을 가리키는 `ANSWER_RECEIVED` 알림이 이미 있으면 목록엔 없는 글로
연결된다. 숨김 트랜잭션에서 해당 알림을 `NotificationStatus.REVOKED`로 전이한다.
`REVOKED`는 이미 enum·DB CHECK에 있고 `markRead`가 거부하도록 돼 있다.

## 6. 반복 신고 억제 (요구사항 5)

### 6.1 세 가지 중복

| 상황 | 처리 | 결과 |
| --- | --- | --- |
| 같은 신고자, 같은 대상, 열린 신고 존재 | 새 행 만들지 않고 기존 접수증 반환(멱등) | `200`, `alreadyReceived: true` |
| 다른 신고자, 같은 대상 | 신고 행은 만들고 **열린 사건에 붙인다** | `201`, 사건은 1개 유지 |
| 같은 신고자, 종결된 사건, 내용 미변경 | 신고 행도 사건도 만들지 않고 `DUPLICATE_SUPPRESSED` 이벤트만 기록 | `200`, "이미 검토 완료" 안내 |

세 번째가 무한 사건 생성을 막는 핵심이다. "내용 미변경" 판정은 증거 스냅샷의
`content_hash`를 재계산해 비교한다. 내용이 바뀌었거나 **사유 범주가 다르면** 새
사건을 연다 — 다른 위반은 다시 판단해야 한다.

### 6.2 사건 병합의 동시성

두 신고자가 동시에 같은 답변을 신고하면 둘 다 사건을 만들려 한다. 부분 유일 인덱스에
기대어 낙관적으로 처리한다.

```sql
INSERT INTO report_case (...)
VALUES (...)
ON CONFLICT (target_type, target_id) WHERE status IN ('OPEN', 'UNDER_REVIEW')
DO NOTHING
RETURNING id;
-- RETURNING이 비면 열린 사건을 다시 SELECT ... FOR UPDATE
```

PostgreSQL은 부분 유일 인덱스에 대해 같은 술어를 명시한 `ON CONFLICT`를 추론한다.
재조회 후에도 없으면(그 사이 종결됨) 한 번만 재시도하고, 그래도 실패하면
`SAF-INFRA-001`로 반환한다.

### 6.3 `MORE_INFO_REQUIRED`는 종결이 아니다 (현재 모델의 결함)

`Report.resolve()`는 `MORE_INFO_REQUIRED`를 종결 상태로 취급해 `resolvedAt`을
설정한다. 그리고 `uq_open_report_*`의 열린 상태 집합은
`('RECEIVED','AUTO_HIDDEN','UNDER_REVIEW')`이므로 **추가 정보를 요청한 순간 중복
신고 차단이 풀린다.** 수정한다.

* 도메인: `requestMoreInfo(Instant at)` 전이 신설 — `resolvedAt`을 설정하지 않는다.
  `resolve()`는 `ACTIONED`/`NO_VIOLATION`만 받는다.
* 스키마: 세 부분 유일 인덱스의 술어에 `'MORE_INFO_REQUIRED'` 추가.
* `AUTO_HIDDEN`은 신고가 아니라 대상 콘텐츠의 성질이므로 신규 코드에서 쓰지 않는다.
  인덱스 술어와 CHECK에는 하위 호환으로 남긴다.

## 7. 증거 스냅샷 (요구사항 6)

신고 시점의 내용과 시간을 기록한다. 작성자가 신고 직후 글을 지우거나 고쳐도 판정
근거가 남아야 한다.

```sql
CREATE TABLE report_content_snapshot (
    report_id               BIGINT PRIMARY KEY,
    captured_at             TIMESTAMPTZ NOT NULL,
    target_type             VARCHAR(20) NOT NULL,
    target_id               BIGINT NOT NULL,
    author_id               BIGINT NOT NULL,     -- FK 없음: 계정 삭제 후에도 증거 유지
    body_text               TEXT,
    media_object_keys       TEXT[] NOT NULL DEFAULT '{}',
    edit_count              INTEGER NOT NULL,
    content_published_at    TIMESTAMPTZ,
    content_hash            CHAR(64) NOT NULL,   -- sha256(정규화 본문 || 정렬된 media key)
    legal_hold              BOOLEAN NOT NULL DEFAULT false,
    purge_after             TIMESTAMPTZ,

    CONSTRAINT fk_report_content_snapshot_report
        FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE RESTRICT
);
```

설계 근거:

* **비정규화 사본이다.** `answer`를 조인해 읽으면 편집·삭제 후 증거가 사라진다.
* `author_id`에 FK를 걸지 않는다. 계정 삭제와 증거 보존이 충돌하면 증거가 이긴다.
  대신 `report.target_user_id`/`answer_id`의 기존 `ON DELETE RESTRICT`가 하드 삭제를
  막고 있고, 답변은 실제로 soft delete(`deleted_at`)로 운영된다.
* `content_hash`는 §6.1의 "내용 미변경" 판정과 중복 신고 억제에 쓴다.
* **append-only.** `BEFORE UPDATE OR DELETE` 트리거로 예외를 던진다. 저장소에 이미
  `enforce_question_text_immutability()` 선례가 있으므로 같은 형태로 만든다.
* 미디어는 S3 객체 키만 기록한다. 원본 객체가 수명주기 정책으로 지워지면 증거가
  깨지므로, 스냅샷이 참조하는 `media_asset`에 보존 플래그가 필요하다(§11.1).
* `purge_after`는 보존 기간 만료 시각이다. `legal_hold = true`면 무시한다. 실제 기간
  값은 법무 검토 대상(§12).

## 8. 비동기 결과 알림 (요구사항 2)

### 8.1 접수 알림은 보내지 않는다

요구사항 4의 "접수 완료와 이후 처리 안내"는 **동기 응답**으로 충분하다. 사용자가
방금 버튼을 누른 화면에 푸시를 또 보내면 소음이다. 접수증(`reportId`, 접수 시각,
안내 문구)을 `POST` 응답에 실어 보낸다. 비동기 알림은 **처리 결과 한 번**이다.

### 8.2 흐름

```text
[운영자 판정 트랜잭션]
  moderation_review INSERT
  report_case      → RESOLVED
  report(N건)      → 판정 상태 반영
  answer           → HIDDEN (ACTIONED이고 숨김 조치일 때)
  notification     → 대상 답변을 가리키는 기존 알림 REVOKED
  outbox_event     → 신고 1건당 1개 INSERT        ← 같은 트랜잭션 (transactional outbox)
        ↓
[ReportResolutionFanOutWorker]  (RecipientNotificationFanOutWorker와 같은 골격)
  claimDue(REPORT_RESOLVED, lease) → notification INSERT → notification_delivery INSERT
```

Outbox 이벤트:

| 항목 | 값 |
| --- | --- |
| `aggregate_type` | `REPORT` (기존 enum) |
| `aggregate_id` | `report.id` — 사건이 아니라 신고. 신고자별로 하나씩 보내야 한다 |
| `event_type` | `REPORT_RESOLVED` (기존 enum) |
| `dedup_key` | `report-resolved:{reportId}` |
| `payload` | `{"reportId":…, "decision":"ACTIONED", "resolvedAt":"…"}` — 대상·작성자 식별자 없음 |

`uq_outbox_event_dedup(dedup_key)`가 재발행을 막고, fan-out 쪽은
`uq_notification_recipient_dedup(recipient_id, dedup_key)` + `saveIfAbsent`로 at-least-once
전달을 effectively-once 알림으로 접는다(`INV-RPT-008`). 사건이 재개방되지 않는다는
`INV-RPT-007`이 dedup key 충돌을 막는 전제다.

### 8.3 알림 선호 설정의 예외

`RecipientNotificationFanOutWorker`는 선호가 꺼져 있으면 **알림 자체를 만들지 않는다.**
결과 통지는 다르게 다룬다.

* 인앱 `notification` 행은 **항상 생성한다.** 신고 처리 결과는 기록이고, 사용자가
  "내 신고 내역"에서 확인할 수 있어야 한다.
* `notification_delivery`(푸시)만 `isPreferenceEnabled(reporterId, REPORT_RESOLVED)`로
  게이트한다.

### 8.4 `notification` 스키마 확장

현재 알림 대상 컬럼은 `direction_post_id`/`answer_id`뿐이고
`ck_notification_target`이 `num_nonnulls(...) <= 1`을 강제한다. 결과 알림에
`answer_id`를 채우면 **신고자에게 숨긴 답변으로 딥링크**가 걸린다. `report_id`를
추가하고 딥링크는 "내 신고 내역"으로 보낸다.

```sql
ALTER TABLE notification
    ADD COLUMN report_id BIGINT REFERENCES report (id) ON DELETE SET NULL,
    DROP CONSTRAINT ck_notification_target,
    ADD CONSTRAINT ck_notification_target
        CHECK (num_nonnulls(direction_post_id, answer_id, report_id) <= 1);
```

`Notification` record의 검증도 함께 넓힌다.

## 9. API 계약

### 9.1 진입점 (요구사항 4, 8)

"내게 온 질문글"과 "내가 받은 답변" 두 화면의 더보기 메뉴가 같은 항목 집합을 갖는다.

```text
더보기
 ├─ 신고    → GET /api/v1/report-reasons  → 사유 선택 → (설명) → POST .../reports
 └─ 차단    → POST /api/v1/users/{userId}/blocks
```

신고 완료 시트에서 "이 사용자 차단"을 함께 제안한다. 왕복을 줄이고 원자성을 얻기
위해 신고 요청 본문에 `blockAuthor: true`를 허용하고, 한 트랜잭션에서
`SafetyService.block()`까지 수행한다(기존 구현이 미종결 수신 슬롯 해제까지 처리한다).

### 9.2 엔드포인트

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `GET` | `/api/v1/report-reasons` | 사유·하위 사유·설명 필수 여부 |
| `POST` | `/api/v1/answers/{answerId}/reports` | 답변 신고 |
| `POST` | `/api/v1/direction-posts/{postId}/reports` | 질문글 신고 |
| `POST` | `/api/v1/users/{userId}/reports` | 사용자 신고 |
| `GET` | `/api/v1/reports/me` | 내 신고 내역(커서 페이지네이션) |
| `GET` | `/api/v1/reports/{reportId}` | 접수증·처리 결과. 신고자 본인만 |
| `POST` | `/api/v1/users/{userId}/blocks` | 차단 |
| `DELETE` | `/api/v1/users/{userId}/blocks` | 차단 해제 |

운영자 대기열 API(`/api/v1/operator/report-cases…`)는 권한 정책이 미결이므로 별도
Issue로 분리한다(§13).

응답 본문은 ADR-0005의 `ApiResponse`/`ApiErrorResponse`를 따르고, 문서화는 기존
관례대로 `SafetyApiSpec` 인터페이스의 애노테이션으로 작성한 뒤
`/harness-api-docs`로 `docs/api/openapi.json`을 재생성한다.

### 9.3 요청·응답

```jsonc
// POST /api/v1/answers/{answerId}/reports
{
  "reasonCode": "SEXUAL_CONTENT",
  "subReasonCode": "NCII",      // 선택
  "detail": "…",                // OTHER면 필수, 그 외 선택
  "blockAuthor": true           // 선택, 기본 false
}

// 201 Created (신규) / 200 OK (이미 접수됨)
{
  "reportId": 1234,
  "status": "RECEIVED",
  "receivedAt": "2026-08-17T04:12:33Z",
  "alreadyReceived": false,
  "guidance": "접수되었습니다. 검토 후 결과를 알림으로 보내드립니다."
}
```

### 9.4 인가

* 인증된 `USER`만 신고할 수 있다.
* **신고자가 볼 수 없는 콘텐츠는 신고할 수 없다.** 그렇지 않으면 id를 훑어 존재
  여부를 캐낼 수 있다. `PostAnswerQuerySql.CAN_VIEW_ANSWERS_SQL`과 같은 열람 자격
  술어를 인가 조건으로 재사용한다.
* 열람 자격이 없거나 대상이 없으면 **둘 다 `404`** 로 응답한다(존재 노출 방지).
* 자기 글 신고는 `400`.
* 신고자당 생성 rate limit(주입값)을 둔다. 초과 시 `429`.

### 9.5 신규 오류 코드

| 코드 | 이름 | HTTP | 분류 |
| --- | --- | --- | --- |
| `SAF-VAL-006` | `INVALID_REPORT_DETAIL` | 400 | VAL |
| `SAF-VAL-007` | `INVALID_REPORT_SUB_REASON` | 400 | VAL |
| `SAF-DOM-003` | `SELF_REPORT_NOT_ALLOWED` | 400 | DOM |
| `SAF-APP-002` | `REPORT_TARGET_NOT_FOUND` | 404 | APP |
| `SAF-APP-003` | `REPORT_NOT_FOUND` | 404 | APP |
| `SAF-APP-004` | `REPORT_RATE_LIMIT_EXCEEDED` | 429 | APP |

`SafetyErrorCode`의 주석 규칙대로 메시지에 신고 내용·당사자 정보를 넣지 않는다.
`docs/error-codes.md` 표도 함께 갱신한다.

## 10. 신고자에게 노출하지 않는 정보 (요구사항 5)

| 필드 | 노출 |
| --- | --- |
| `report.id`, `status`, `createdAt`, `resolvedAt`, 판정 | ✅ |
| 내가 쓴 `reasonCode`, `detail` | ✅ |
| `moderation_review.internal_note` | ❌ |
| `moderation_review.reviewer_id`, `action_type` | ❌ |
| 피신고자 식별자·닉네임·프로필 | ❌ |
| 피신고자에게 적용된 제재 종류·기간 | ❌ |
| 같은 대상의 다른 신고 수, 사건 id | ❌ |
| 증거 스냅샷 본문 | ❌ (자기 신고 내용 회신에도 원문을 되돌려주지 않는다) |

판정 노출 수준도 결정이 필요하다. `조치 완료 / 위반 아님 / 추가 정보 필요` 3단계는
신뢰 형성에 필요하지만 상대에 대한 정보를 아주 약하게 흘린다. 더 보수적인 대안은
`검토 완료`로 단일화하는 것이다. **권고는 3단계 노출**이며 §12에 결정 항목으로
남긴다.

응답 DTO는 record로 만들고, 필드 집합을 고정하는 테스트를 둔다(`INV-RPT-005`).
`moderation_review`는 신고자 응답 경로의 어떤 쿼리에서도 조인하지 않는다.

## 11. 실패 모드와 통합 지점

### 11.1 실패 모드

| 상황 | 결과 | 대응 |
| --- | --- | --- |
| 신고 저장 성공 후 스냅샷 저장 실패 | 증거 없는 신고 | 같은 트랜잭션에 묶는다(`INV-RPT-003`) |
| 작성자가 신고 직후 글 삭제 | soft delete, 스냅샷 보존 | 설계대로 |
| 계정 삭제 요청과 증거 보존 충돌 | FK `RESTRICT`로 하드 삭제 차단 | 법무 결정 필요(§12) |
| 숨김 기간 중 미디어 정리 → 복원 시 `assert_answer_has_content` 실패 | 복원 불가 | 스냅샷이 참조하는 `media_asset`에 보존 플래그. 정리 배치가 이를 건너뛴다 |
| 판정 트랜잭션은 커밋, fan-out worker 장애 | 알림 지연 | outbox 재시도·`DEAD` 처리. 기존 `OutboxRetryPolicy` 재사용 |
| 두 운영자가 같은 사건을 동시 판정 | 이중 판정 | 사건 행 `SELECT … FOR UPDATE` + 상태 전이 검사 |
| 대량 조직적 신고 | 무고한 글 자동 숨김 | §4 임계값은 **서로 다른 신고자 수** 기준. 신고자 rate limit 병행 |

### 11.2 `filtering` 파이프라인과의 관계

자동 필터링과 사용자 신고는 같은 운영자 책상으로 들어오는 두 개의 문이다. 지금은
`filtering.ManualReviewCase`(대상+release당 1건)와 `report_case`(대상당 열린 1건)가
서로를 모른다. 같은 답변에 대해 항목이 둘 뜬다.

* 단기: `report_case.linked_manual_review_case_id`(nullable)로 상관관계만 기록한다.
  테이블 통합은 하지 않는다 — 출처와 불변식이 다르다.
* 장기: 통합 운영자 대기열 뷰(별도 Issue).

**이의제기 경로의 공백:** `AppealCase`는 `filter_decision_id`를 필수로 요구한다.
신고로 인한 숨김에는 `filter_decision`이 없어 작성자가 이의제기를 걸 수 없다.
`AppealCase`를 "숨김 사유 참조"로 넓히거나 신고 전용 이의제기 경로를 두어야 한다.
§12 미결.

## 12. 미결 항목

`AGENTS.md` §4.3의 표기를 따른다.

| 항목 | 상태 | 비고 |
| --- | --- | --- |
| 신고 사유 8종·표시 문구 | `CONFIRMED` | 요구사항 3 |
| 신고 흐름 단계 | `CONFIRMED` | 요구사항 4 |
| 질문글·답변 동일 진입점 | `CONFIRMED` | 요구사항 8 |
| 자동 전역 숨김 임계값(서로 다른 신고자 수) | `UNKNOWN` | 주입값으로 구현, 기본값은 운영 결정 |
| `URGENT`/`STANDARD` SLA 시간 | `UNKNOWN` | 주입값 |
| 신고 rate limit, `CRITICAL` 일일 쿼터 | `UNKNOWN` | 주입값 |
| 설명 길이 상한 | `ASSUMED` | 500자 제안 |
| `CRITICAL` 1건 즉시 전역 숨김 여부 | `BLOCKED` | §4.1. 법무·안전 결정 |
| 신고자에게 노출할 판정 상세 수준 | `BLOCKED` | §10. 제품·법무 결정 |
| 증거 보존 기간·`purge_after` 기본값 | `BLOCKED` | 요구사항 7. 법무 결정 |
| 국가별 신고 의무(`user_account.country_code` 기반 분기) | `BLOCKED` | 요구사항 7. 법무 결정 |
| 계정 삭제 요청 시 증거 보존 우선순위 | `BLOCKED` | 법무 결정 |
| 자해·자살 위험 사유 추가 여부 | `BLOCKED` | 사용자 제시 목록에 없음. 제품 결정 |
| 운영자 대기열 API의 role 정책 | `BLOCKED` | 별도 Issue |
| 신고 기반 숨김의 작성자 이의제기 경로 | `BLOCKED` | §11.2 |
| 허위 신고자 제재 정책 | `UNKNOWN` | 범위 밖. 이벤트 기록만 남긴다 |

`BLOCKED` 항목은 스키마·코드에 하드코딩하지 않고 설정 주입 또는 기능 플래그로 두고,
프로덕션 활성화는 법률·안전 담당 검토 뒤에 한다.

## 13. 작업 분해 (Issue 후보)

각 항목은 독립 리뷰가 가능하고 뒤 항목이 앞 항목에만 의존한다.

| # | 제목 | 산출물 |
| --- | --- | --- |
| 1 | 신고 사유·하위 사유 도메인과 스키마 고정 | `ReportReason`, `ReportSubReason`, V16 CHECK, `GET /report-reasons` |
| 2 | 신고 사건과 증거 스냅샷 | `report_case`, `report_content_snapshot`, `report_case_event`, append-only 트리거, 사건 병합·중복 억제 |
| 3 | `MORE_INFO_REQUIRED` 열린 상태 교정 | `Report.requestMoreInfo`, 부분 유일 인덱스 술어 수정 |
| 4 | 신고·차단 REST API | `safety/web`, 인가 술어, rate limit, 오류 코드, OpenAPI 재생성 |
| 5 | 집계 제외 2계층 | `Answer.hide/restore`, `ContentSuppressionSql`, 5개 쿼리 적용, 알림 `REVOKED` |
| 6 | 결과 알림 비동기 전송 | `notification.report_id`, `ReportResolutionFanOutWorker`, 선호 설정 예외 |
| 7 | 심각도·대기열 분리 | `severity`/`queue`/`sla_due_at`, 승격 규칙, 에스컬레이션 |
| 8 | 운영자 대기열·판정 API | 별도 설계 (권한 정책 미결) |
| 9 | 법률 검토 반영 | 보존 기간, 국가별 분기, 삭제 기준 |

## 14. 테스트 관점

`AGENTS.md` §3에 따라 단위와 통합을 분리하고 `@DisplayName`과 ISO 8601 생성 시각,
테스트 계획 식별자를 붙인다.

단위:

* 사유·하위 사유 조합 검증, `OTHER` 설명 필수
* `Report.requestMoreInfo`가 `resolvedAt`을 설정하지 않음
* `Answer.hide/restore` 전이와 `publishedAt` 보존
* 결과 응답 DTO 필드 집합 고정(`INV-RPT-005`)

통합(PostgreSQL 필수):

* **목록 길이 == `answer_count`** — 신고자 한정 숨김·전역 숨김 각각에서
* `unread_answer_count`와 칩 카운트도 동일하게 감소
* 서로 다른 신고자 2명 동시 신고 → 사건 1개(`INV-RPT-001`, 동시성)
* 같은 신고자 재신고 멱등, 종결 후 내용 미변경 재신고가 사건을 만들지 않음
* 작성자 삭제 후에도 스냅샷 조회 가능, 스냅샷 UPDATE·DELETE 거부
* 판정 → outbox 1건/신고 → fan-out → 알림 1건, worker 재실행 시 중복 없음
* 열람 자격 없는 사용자의 신고 시도가 `404`
