-- #168: 닉네임 대소문자 무시 유일성. 삭제된 계정은 닉네임을 반환하지 않으므로
-- 재사용을 막지 않는다. NULL 닉네임은 여러 계정이 동시에 가질 수 있어야 하므로
-- 부분 인덱스로 제외한다.
CREATE UNIQUE INDEX uq_user_account_nickname_ci
    ON user_account (lower(nickname))
    WHERE nickname IS NOT NULL AND deleted_at IS NULL;
