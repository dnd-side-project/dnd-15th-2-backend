-- #115: 기존 방향글은 fingerprint를 나중에 보정할 수 있도록 nullable로 둔다.
ALTER TABLE direction_post
    ADD COLUMN request_fingerprint VARCHAR(80);

ALTER TABLE outbox_event
    ADD COLUMN match_round INTEGER,
    ADD COLUMN lease_owner VARCHAR(100),
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN lease_generation BIGINT NOT NULL DEFAULT 0;

-- 같은 방향글의 legacy 매칭 이벤트가 둘 이상이면 match_round=1 보정 후
-- unique index 실패 원인을 특정하기 어렵다. 수동 정리 후 migration을 재실행한다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM outbox_event
        WHERE aggregate_type = 'DIRECTION_POST'
          AND event_type = 'RECIPIENT_MATCH_REQUESTED'
        GROUP BY aggregate_id, event_type
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate legacy direction matching outbox events must be reconciled before V12';
    END IF;
END $$;

-- 기존 매칭 이벤트는 최초 라운드로 보정하고, 진행 중이던 작업은 즉시 회수 가능한
-- lease로 표시한다. 새 worker는 generation 조건을 사용하므로 이전 worker의 완료를
-- 덮어쓸 수 없다.
UPDATE outbox_event
SET match_round = 1
WHERE aggregate_type = 'DIRECTION_POST'
  AND event_type = 'RECIPIENT_MATCH_REQUESTED';

UPDATE outbox_event
SET lease_owner = 'migration-v12',
    lease_expires_at = clock_timestamp(),
    lease_generation = lease_generation + 1
WHERE status = 'PROCESSING';

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_match_round
    CHECK (
        (
            aggregate_type = 'DIRECTION_POST'
            AND event_type = 'RECIPIENT_MATCH_REQUESTED'
        ) = (match_round IS NOT NULL)
        AND (match_round IS NULL OR match_round > 0)
    ),
    ADD CONSTRAINT ck_outbox_event_lease_generation
    CHECK (lease_generation >= 0),
    ADD CONSTRAINT ck_outbox_event_lease_state
    CHECK (
        (status = 'PROCESSING') = (lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        AND (lease_owner IS NULL) = (lease_expires_at IS NULL)
    );

CREATE UNIQUE INDEX uq_outbox_event_direction_matching_round
    ON outbox_event (aggregate_id, match_round, event_type)
    WHERE aggregate_type = 'DIRECTION_POST'
      AND event_type = 'RECIPIENT_MATCH_REQUESTED';

CREATE INDEX outbox_event_claim_idx
    ON outbox_event (status, next_attempt_at, lease_expires_at, id)
    WHERE status IN ('PENDING', 'FAILED', 'PROCESSING');
