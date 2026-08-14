-- #108: 답변 moderation job durable retry.
--
-- filter_job은 #107까지 실제 production writer가 없었으므로(닉네임 경로는
-- ephemeral 요청만 쓰고 job을 만들지 않는다) 기존 행 보정 없이 NOT NULL을
-- 추가할 수 있다. logical_attempt_count는 outbox_event.attempt_count(claim마다
-- 무조건 증가하는 인프라 카운터)와 다르다 — 실제로 pipeline을 호출했을 때만
-- 증가한다(INV-RTY-001). deadline_at과 달리 이 값의 origin은 created_at이라
-- deadline 경과가 이 값을 초기화하지 않는다(INV-RTY-006).
ALTER TABLE filter_job
    ADD COLUMN logical_attempt_count INT NOT NULL DEFAULT 0;

ALTER TABLE filter_job
    ALTER COLUMN logical_attempt_count DROP DEFAULT;

ALTER TABLE filter_job
    ADD CONSTRAINT ck_filter_job_logical_attempt_count
    CHECK (logical_attempt_count >= 0);

COMMENT ON COLUMN filter_job.logical_attempt_count IS
    '실제 pipeline 호출 횟수만 센다. snapshot 단위 retry gate로 미뤄진 재클레임은 늘리지 않는다(INV-RTY-001).';

-- release(snapshot) 단위 재시도 게이트. 연속 실패가 임계값에 도달하면 HEALTHY에서
-- DEGRADED로 저하되어 배치당 재시도 admitted 수를 current_limit으로 제한하고,
-- 복구 후 연속 성공에 따라 단계적으로 한도를 늘린다(INV-RTY-007). release당
-- 1행이며 여러 worker가 동시에 갱신할 수 있어 애플리케이션이 SELECT ... FOR
-- UPDATE로 행을 잠그고 읽기-수정-쓰기를 직렬화한다.
CREATE TABLE filter_release_retry_gate (
    filter_release_id      BIGINT PRIMARY KEY,
    state                   VARCHAR(20) NOT NULL,
    current_limit           INT,
    consecutive_failures    INT NOT NULL DEFAULT 0,
    consecutive_successes   INT NOT NULL DEFAULT 0,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_filter_release_retry_gate_release
        FOREIGN KEY (filter_release_id) REFERENCES filter_release (id),
    CONSTRAINT ck_filter_release_retry_gate_state
        CHECK (state IN ('HEALTHY', 'DEGRADED')),
    CONSTRAINT ck_filter_release_retry_gate_limit
        CHECK ((state = 'DEGRADED') = (current_limit IS NOT NULL)),
    CONSTRAINT ck_filter_release_retry_gate_limit_positive
        CHECK (current_limit IS NULL OR current_limit > 0),
    CONSTRAINT ck_filter_release_retry_gate_counts
        CHECK (consecutive_failures >= 0 AND consecutive_successes >= 0)
);

COMMENT ON TABLE filter_release_retry_gate IS
    'release 단위 재시도 폭주 완화 상태. 임계값·ramp step·한도의 실제 운영 수치는 미결정이며 애플리케이션이 주입한다(#108).';
