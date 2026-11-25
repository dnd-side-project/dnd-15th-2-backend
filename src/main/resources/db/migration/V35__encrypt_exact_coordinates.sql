-- ADR-0009: 보존하지 않기로 승인한 기존 정확 좌표는 재암호화하지 않는다.
DELETE FROM active_user_presence WHERE position IS NOT NULL;

UPDATE direction_post p
SET status = 'MATCH_FAILED'
FROM post_audience a
WHERE a.post_id = p.id
  AND a.origin_position IS NOT NULL
  AND p.status IN ('SUBMITTED', 'SAFETY_CHECKING', 'MATCHING', 'REVIEW_HELD');

DROP INDEX active_user_presence_position_gix;

ALTER TABLE active_user_presence
    DROP CONSTRAINT ck_active_user_presence_location,
    DROP COLUMN position,
    ADD COLUMN position_ciphertext BYTEA,
    ADD CONSTRAINT ck_active_user_presence_location
        CHECK (num_nonnulls(position_ciphertext, coarse_cell_id) >= 1);

ALTER TABLE post_audience
    DROP CONSTRAINT ck_post_audience_origin,
    DROP COLUMN origin_position,
    ADD COLUMN origin_position_ciphertext BYTEA;

COMMENT ON COLUMN active_user_presence.position_ciphertext IS
    '좌표 전용 AES-256-GCM envelope. user_id와 table/column 용도를 AAD로 인증하며 앱에서만 복호화한다.';
COMMENT ON COLUMN post_audience.origin_position_ciphertext IS
    '발송 원점 AES-256-GCM envelope. post_id와 table/column 용도를 AAD로 인증한다. 기존 폐기 원점은 NULL이다.';
