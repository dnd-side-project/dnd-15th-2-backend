-- 신고자 한정 숨김(#155)의 NOT EXISTS 조회를 지원한다. uq_open_report_answer는
-- 열린 상태에만 걸리는 부분 유일 인덱스라 종결된 신고까지 포함하는 이 조회에는
-- 쓸 수 없다 — status 조건 없는 별도 인덱스가 필요하다.
CREATE INDEX idx_report_reporter_answer_suppression
    ON report (reporter_id, answer_id)
    WHERE answer_id IS NOT NULL;
