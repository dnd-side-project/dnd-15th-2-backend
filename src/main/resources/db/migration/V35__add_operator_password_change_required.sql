-- #376: 초기 비밀번호를 쓰는 운영자를 판별한다.
--
-- 시드로 만든 자격증명은 TRUE로 시작하고, 운영자가 비밀번호를 직접 바꾸면 FALSE가 된다. 로그인할 때
-- 이 값이 TRUE면 비밀번호를 바꾸기 전까지 운영자 API를 막는다.
--
-- 기존 행은 DEFAULT로 TRUE가 된다. 지금까지 비밀번호 변경 API가 없었으므로 기존 운영자는 모두 시드
-- 비밀번호를 쓰고 있다. 적용 뒤 첫 로그인부터 변경을 요구받는 것은 의도한 동작이다.
--
-- 변경 주기 만료는 password_updated_at으로 판정하므로 컬럼을 더하지 않는다. created_at과 비교해
-- 초기 비밀번호를 판별하지 않는 이유는 앱 시계(Clock)와 DB 시계가 달라 판별이 틀릴 수 있어서다.
ALTER TABLE operator_credential
    ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN operator_credential.password_change_required IS
    '초기 비밀번호 여부(#376). 시드 발급은 TRUE, 운영자가 직접 바꾸면 FALSE. TRUE면 변경 전까지 운영자 API를 막는다';

-- V5의 설명은 이 컬럼이 초기 비밀번호 판별에 쓰인다고 적었으나, 초기 비밀번호는 위 컬럼이 판별한다.
COMMENT ON COLUMN operator_credential.password_updated_at IS
    '비밀번호 마지막 변경 시각. qello.auth.operator-password.max-age가 지나면 다음 로그인부터 변경을 요구한다(#376)';
