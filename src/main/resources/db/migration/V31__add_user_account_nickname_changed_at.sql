-- #315: F01 닉네임 변경 주기 제한. 본인 요청으로 닉네임을 마지막으로 바꾼 시각을 둔다.
-- 가입할 때 정한 닉네임은 변경으로 보지 않으므로 기존 행과 새 계정 모두 NULL로 시작하고,
-- NULL이면 첫 변경을 바로 허용한다. 주기 길이는 운영 설정값이라 DB 제약으로 두지 않는다.
ALTER TABLE user_account
    ADD COLUMN nickname_changed_at TIMESTAMPTZ;

COMMENT ON COLUMN user_account.nickname_changed_at IS
    '본인 요청으로 닉네임을 마지막으로 바꾼 시각. 가입 시 지정한 닉네임은 NULL로 남는다';
