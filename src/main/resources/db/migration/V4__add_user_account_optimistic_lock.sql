ALTER TABLE user_account
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN user_account.version IS
    'JPA 낙관적 잠금용 행 버전. 수정할 때마다 증가하며 같은 계정을 동시에 수정한 요청 중 뒤늦은 쪽을 거절한다. 기본값은 JPA를 거치지 않고 삽입된 행을 위한 것이다.';
