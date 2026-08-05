ALTER TABLE user_account
    ADD COLUMN password_hash VARCHAR(255);

ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_password_hash
        CHECK (
            (role = 'OPERATOR' AND password_hash IS NOT NULL)
            OR (role = 'USER' AND password_hash IS NULL)
        );

COMMENT ON COLUMN user_account.password_hash IS
    '관리자(OPERATOR) 계정의 검증된 비밀번호 해시. 평문은 저장하지 않는다. 일반(USER) 계정은 비밀번호를 사용하지 않으므로 항상 NULL이다.';
