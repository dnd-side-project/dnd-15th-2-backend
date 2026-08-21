-- #156: 사건 SLA와 filtering.manual_review_case 상관관계.
-- 스키마 소유 경계상 filtering 스키마로의 FK는 걸지 않는다 — opaque id로만
-- 기록한다(상관관계만, 테이블 통합 없음).
ALTER TABLE report_case
    ADD COLUMN sla_due_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    ADD COLUMN linked_manual_review_case_id BIGINT;

ALTER TABLE report_case
    ALTER COLUMN sla_due_at DROP DEFAULT;

ALTER TABLE report_case
    ADD CONSTRAINT ck_report_case_linked_manual_review_case_id
        CHECK (linked_manual_review_case_id IS NULL OR linked_manual_review_case_id > 0);
