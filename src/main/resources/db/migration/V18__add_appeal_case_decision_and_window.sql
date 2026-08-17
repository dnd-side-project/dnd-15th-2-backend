-- #112: 작성자 이의제기(appeal)의 접수 기간, 검토자 결정과 공개 복원 콜백 계약.
--
-- V10이 appeal_case의 정체성과 유일성(INV-APL-002)만 만들고 "6개월 만료,
-- UPHOLD/OVERTURN 결과는 #112가 컬럼을 추가한다"라고 남긴 자리를 채운다.
--
-- appeal_case는 어떤 프로덕션 경로에서도 아직 행을 만들지 않는다(저장 지점이
-- 테스트에만 존재한다). 그래서 신규 컬럼에 기존 행 보정 없이 NOT NULL을
-- 추가한다 — V16이 manual_review_case에 적용한 것과 같은 논리다.
ALTER TABLE appeal_case
    ADD COLUMN appellant_user_id            BIGINT NOT NULL,
    ADD COLUMN status                       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN window_started_at            TIMESTAMPTZ NOT NULL,
    ADD COLUMN expires_at                   TIMESTAMPTZ NOT NULL,
    ADD COLUMN acceptance_reason_code       VARCHAR(30) NOT NULL DEFAULT 'WITHIN_WINDOW',
    ADD COLUMN decision                     VARCHAR(20),
    ADD COLUMN decided_at                   TIMESTAMPTZ,
    ADD COLUMN decided_by_operator_user_id  BIGINT,
    ADD COLUMN restore_blocked_reason_code  VARCHAR(30);

ALTER TABLE appeal_case
    ADD CONSTRAINT ck_appeal_case_appellant_user_id
        CHECK (appellant_user_id > 0),
    ADD CONSTRAINT ck_appeal_case_status
        CHECK (status IN ('OPEN', 'RESOLVED')),
    ADD CONSTRAINT ck_appeal_case_acceptance_reason_code
        CHECK (acceptance_reason_code IN ('WITHIN_WINDOW', 'WINDOW_UNVERIFIABLE')),
    ADD CONSTRAINT ck_appeal_case_decision
        CHECK (decision IS NULL OR decision IN ('UPHOLD_HIDDEN', 'OVERTURN_HIDDEN')),
    ADD CONSTRAINT ck_appeal_case_decided_fields
        CHECK ((status = 'RESOLVED')
            = (decision IS NOT NULL AND decided_at IS NOT NULL
                AND decided_by_operator_user_id IS NOT NULL)),
    -- 복원 차단 사유는 복원을 시도한 결정에만 붙는다. UPHOLD_HIDDEN은 애초에
    -- 복원 콜백을 내지 않으므로 차단 사유가 존재할 수 없다.
    ADD CONSTRAINT ck_appeal_case_restore_blocked_reason
        CHECK (restore_blocked_reason_code IS NULL OR decision = 'OVERTURN_HIDDEN'),
    -- 접수 기간을 6개월보다 줄이는 경로를 막는 마지막 방어선(INV-APL-008,
    -- INV-APL-009). 애플리케이션의 AppealWindow 하한과 AppealCase.extendExpiry
    -- 가드를 통과하지 못한 UPDATE가 DB에 직접 들어와도 여기서 걸린다.
    ADD CONSTRAINT ck_appeal_case_expires_after_window_start
        CHECK (expires_at >= window_started_at + INTERVAL '184 days');

-- 작성자 본인의 appeal 목록 조회 경로.
CREATE INDEX appeal_case_appellant_idx
    ON appeal_case (appellant_user_id, created_at);

-- 검토자 큐 조회(FIFO). RESOLVED case는 큐에 나타나지 않으므로 OPEN만 좁힌다.
CREATE INDEX appeal_case_queue_idx
    ON appeal_case (created_at)
    WHERE status = 'OPEN';

COMMENT ON COLUMN appeal_case.appellant_user_id IS
    '접수한 작성자. 호출자 도메인의 식별자이며 이 시스템은 FK로 참조하지 않는다(target_id와 같은 취급).';
COMMENT ON COLUMN appeal_case.window_started_at IS
    '접수 기간의 기산점. 원칙은 filter_decision.decided_at이고, 그 값을 신뢰할 수 없으면 접수 시각으로 대체한다.';
COMMENT ON COLUMN appeal_case.acceptance_reason_code IS
    'WITHIN_WINDOW 또는 WINDOW_UNVERIFIABLE. 후자는 기산점을 확정할 수 없어 거절 대신 접수를 허용한 fallback 경로를 뜻한다.';
COMMENT ON COLUMN appeal_case.expires_at IS
    '접수 시점에 고정한다. 법률·정책상 연장만 가능하고 단축 경로는 없다(INV-APL-008, INV-APL-009).';
COMMENT ON COLUMN appeal_case.restore_blocked_reason_code IS
    'OVERTURN_HIDDEN 결정이 났지만 다른 공개 금지 사유가 남아 복원 콜백을 내지 않은 경우의 사유. 사유 목록이 미결정이라 열거형이 아닌 문자열이다.';

-- appeal 결과 콜백의 aggregate와 event type. 기존 CHECK를 이 값들을 포함하도록
-- 다시 만든다(V2·V13과 동일한 패턴).
--
-- MODERATION_VERDICT_READY를 재사용하지 않는 이유: appeal은 이미 확정된
-- filter_job 판정을 고쳐 쓰는 절차가 아니라 그 이후의 별도 구제 절차다.
-- 재사용하면 수동 종결된 job의 판정이 나중에 바뀌지 않는다는 INV-MAN-004와
-- filter_decision의 append-only 성격이 함께 무너진다.
ALTER TABLE outbox_event
    DROP CONSTRAINT ck_outbox_event_aggregate_type;

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_aggregate_type
    CHECK (aggregate_type IN (
        'DIRECTION_POST', 'POST_RECIPIENT', 'ANSWER', 'QUESTION_ASSIGNMENT',
        'QUESTION_PROPOSAL', 'REPORT', 'FILTER_JOB', 'APPEAL_CASE'
    ));

ALTER TABLE outbox_event
    DROP CONSTRAINT ck_outbox_event_event_type;

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_event_type
    CHECK (event_type IN (
        'RECIPIENT_MATCH_REQUESTED', 'RECIPIENTS_CONFIRMED', 'DIRECTION_POST_EXPIRED',
        'ANSWER_PUBLISHED', 'ANSWER_REACTED', 'SKIP_CONFIRMATION_DUE',
        'QUESTION_RECOMMENDED', 'QUESTION_PROPOSAL_REVIEWED', 'REPORT_RESOLVED',
        'MODERATION_EXECUTION_REQUESTED', 'MODERATION_VERDICT_READY', 'MODERATION_DEADLINE_ELAPSED',
        'MODERATION_APPEAL_RESOLVED'
    ));
