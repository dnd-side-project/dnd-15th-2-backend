-- #137: 방향 질문글 본문을 공통 moderation 비동기 파이프라인에 넣는다. 질문글 job과, 재시도를
-- 소진한 질문글 job의 수동 검토 case가 저장될 수 있게 target_type 제약에 DIRECTION_POST를 더한다.
-- 질문글 이의제기는 아직 없으므로 appeal_case 제약은 바꾸지 않는다.
ALTER TABLE filter_job
    DROP CONSTRAINT ck_filter_job_target_type;

ALTER TABLE filter_job
    ADD CONSTRAINT ck_filter_job_target_type
        CHECK (target_type IN ('ANSWER', 'NICKNAME', 'DIRECTION_POST'));

ALTER TABLE manual_review_case
    DROP CONSTRAINT ck_manual_review_case_target_type;

ALTER TABLE manual_review_case
    ADD CONSTRAINT ck_manual_review_case_target_type
        CHECK (target_type IN ('ANSWER', 'NICKNAME', 'DIRECTION_POST'));
