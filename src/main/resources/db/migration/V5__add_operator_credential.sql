-- 운영자 자격증명을 user_account에서 분리한다.
--
-- 계정 생명주기(차단·삭제)와 자격증명 생명주기(비밀번호 교체·잠금)는 변경 주기가
-- 다르다. user_account에 인증 컬럼을 계속 두면 일반 사용자 전체가 운영자 몇 명 때문에
-- 존재하는 NULL 컬럼을 들고 다니고, 비밀번호를 바꿀 때마다 계정 행의 version이 올라간다.
-- 결정 배경은 docs/adr/0006-split-operator-and-device-authentication.md에 있다.

-- PostgreSQL은 FK 대상 컬럼 조합에 unique 제약을 요구한다. id가 이미 PK지만
-- (id, role) 복합 FK를 걸려면 이 unique가 따로 있어야 한다.
ALTER TABLE user_account
    ADD CONSTRAINT uq_user_account_id_role UNIQUE (id, role);

CREATE TABLE operator_credential (
    user_id                 BIGINT PRIMARY KEY,
    role                    VARCHAR(20) NOT NULL DEFAULT 'OPERATOR',
    login_id                VARCHAR(50) NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    failed_attempt_count    SMALLINT NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    password_updated_at     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    last_login_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    -- role을 함께 참조해 USER 계정에 자격증명이 붙는 것을 DB가 거절한다.
    -- user_account.role을 USER로 강등하면 이 FK가 위반되어 실패하므로,
    -- 권한을 내린 계정에 자격증명이 남는 사고도 함께 막힌다.
    CONSTRAINT fk_operator_credential_user
        FOREIGN KEY (user_id, role) REFERENCES user_account (id, role)
        ON DELETE CASCADE,
    CONSTRAINT ck_operator_credential_role
        CHECK (role = 'OPERATOR'),
    CONSTRAINT uq_operator_credential_login_id
        UNIQUE (login_id),
    CONSTRAINT ck_operator_credential_login_id
        CHECK (btrim(login_id) <> '' AND login_id = lower(login_id)),
    CONSTRAINT ck_operator_credential_failed_attempt
        CHECK (failed_attempt_count >= 0),
    -- 잠금 해제 시각은 잠긴 동안에만 의미가 있다. 값이 남아 있어도 과거 시각이면
    -- 잠금이 풀린 상태로 읽히므로 별도 상태 컬럼을 두지 않는다.
    CONSTRAINT ck_operator_credential_locked_until
        CHECK (locked_until IS NULL OR locked_until > created_at)
);

COMMENT ON TABLE operator_credential IS
    '백오피스 운영자 자격증명. 일반(USER) 계정은 행을 갖지 않는다. 평문 비밀번호는 저장하지 않는다.';
COMMENT ON COLUMN operator_credential.role IS
    'user_account.role과 함께 복합 FK를 이루기 위한 사본. 항상 OPERATOR이며 애플리케이션이 갱신하지 않는다.';
COMMENT ON COLUMN operator_credential.failed_attempt_count IS
    '연속 로그인 실패 횟수. 성공하면 0으로 되돌린다. 임계치 도달 시 locked_until을 설정한다.';
COMMENT ON COLUMN operator_credential.password_updated_at IS
    '비밀번호 마지막 변경 시각. 시드로 발급한 초기 비밀번호를 최초 로그인에서 강제 변경시키는 판단에 쓴다.';

-- version 컬럼을 두지 않는다. 이 테이블의 동시 수정은 사실상 로그인 실패 카운터
-- 증가뿐이고, 경합으로 증가 하나를 잃는 것은 무해하다. 낙관적 잠금을 걸면 그 무해한
-- 경합이 로그인 요청의 409 응답으로 바뀐다. 정확한 집계가 필요해지면 잠금이 아니라
-- 원자적 UPDATE로 해결한다.

-- user_account에서 비밀번호를 제거한다. 운영자를 만들 수 있는 경로(가입 API·시드)가
-- 아직 없어 이 컬럼에 값이 있는 행이 존재하지 않으므로 백필 없이 제거한다.
ALTER TABLE user_account
    DROP CONSTRAINT ck_user_account_password_hash;

ALTER TABLE user_account
    DROP COLUMN password_hash;
