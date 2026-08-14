-- #107: 답변 필터링 연동과 deadline 공개 전환.
--
-- filter_job은 지금까지 실제 production writer가 없었다(닉네임 경로는 #106에서
-- ephemeral 요청만 쓰고 job을 만들지 않는다) — 그래서 deadline_at을 기존 행
-- 보정 없이 NOT NULL로 추가할 수 있다. deadline_at은 job 생성 시 한 번만 고정되고
-- 이후 어떤 경로도 갱신하지 않는다(INV-ANS-002).
ALTER TABLE filter_job
    ADD COLUMN deadline_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();

ALTER TABLE filter_job
    ALTER COLUMN deadline_at DROP DEFAULT;

-- deadline scheduler가 "아직 RESOLVED되지 않았고 deadline이 지난 job"만 훑는
-- 조회 경로. RESOLVED된 job은 이 인덱스 대상이 아니다.
CREATE INDEX filter_job_deadline_scan_idx
    ON filter_job (deadline_at)
    WHERE status <> 'RESOLVED';

-- 답변 경로의 outbox 계약 추가: FILTER_JOB aggregate와 job 실행 요청/판정
-- 완료/deadline 경과 이벤트 타입. 기존 CHECK 제약을 이 값들을 포함하도록
-- 다시 만든다(V2와 동일한 패턴).
ALTER TABLE outbox_event
    DROP CONSTRAINT ck_outbox_event_aggregate_type;

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_aggregate_type
    CHECK (aggregate_type IN (
        'DIRECTION_POST', 'POST_RECIPIENT', 'ANSWER', 'QUESTION_ASSIGNMENT',
        'QUESTION_PROPOSAL', 'REPORT', 'FILTER_JOB'
    ));

ALTER TABLE outbox_event
    DROP CONSTRAINT ck_outbox_event_event_type;

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_event_type
    CHECK (event_type IN (
        'RECIPIENT_MATCH_REQUESTED', 'RECIPIENTS_CONFIRMED', 'DIRECTION_POST_EXPIRED',
        'ANSWER_PUBLISHED', 'ANSWER_REACTED', 'SKIP_CONFIRMATION_DUE',
        'QUESTION_RECOMMENDED', 'QUESTION_PROPOSAL_REVIEWED', 'REPORT_RESOLVED',
        'MODERATION_EXECUTION_REQUESTED', 'MODERATION_VERDICT_READY', 'MODERATION_DEADLINE_ELAPSED'
    ));

COMMENT ON COLUMN filter_job.deadline_at IS
    'job 생성 시 원자적으로 고정. 이후 어떤 재시도나 부하 변화로도 연장되지 않는다(INV-ANS-002).';
