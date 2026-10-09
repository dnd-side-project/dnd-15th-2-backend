-- #337: 앱 탈퇴를 30일 유예로 받는다. 요청하면 WITHDRAWAL_PENDING이 되고 유예가 끝나면 sweep이
-- DELETED로 바꾼다. 유예 중에는 deleted_at이 NULL이라 uq_user_account_nickname_ci가 닉네임을 계속
-- 묶어 두므로 철회해도 닉네임이 충돌하지 않는다. 유예 길이는 운영 설정값이라 DB 제약으로 두지 않는다.
ALTER TABLE user_account
    DROP CONSTRAINT ck_user_account_status;

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_status
        CHECK (status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'BLOCKED', 'DELETED'));

ALTER TABLE user_account
    ADD COLUMN withdrawal_requested_at TIMESTAMPTZ;

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_withdrawal_requested_at
        CHECK ((status = 'WITHDRAWAL_PENDING') = (withdrawal_requested_at IS NOT NULL));

-- 유예가 끝난 계정을 요청 시각 순으로 찾는 sweep 전용 경로다. 유예 중인 행만 담는다.
CREATE INDEX user_account_withdrawal_due_idx
    ON user_account (withdrawal_requested_at, id)
    WHERE status = 'WITHDRAWAL_PENDING';

COMMENT ON COLUMN user_account.withdrawal_requested_at IS
    '탈퇴를 요청한 시각. WITHDRAWAL_PENDING일 때만 값이 있고 철회하거나 탈퇴가 끝나면 NULL로 돌아간다';
