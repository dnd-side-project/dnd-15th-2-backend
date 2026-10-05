-- 사용자가 "제안한 질문" 화면에서 제안을 지우거나 그 제안의 검토 결과 push만 끌 수 있게 한다.
-- question_proposal_review와 approved_question이 ON DELETE RESTRICT로 이 행을 참조하므로
-- 행을 물리 삭제하지 않고 deleted_at으로 사용자 목록에서만 제외한다.
ALTER TABLE question_proposal
    ADD COLUMN deleted_at          TIMESTAMPTZ,
    ADD COLUMN notification_muted  BOOLEAN NOT NULL DEFAULT FALSE;

-- 내 제안 목록은 삭제되지 않은 행만 최신순으로 읽는다.
CREATE INDEX question_proposal_proposer_active_idx
    ON question_proposal (proposer_id, created_at DESC)
    WHERE deleted_at IS NULL;
