-- =============================================================================
-- Qello 방향 소통 schema V2 — 2026-08-04 기능 명세서 개정 반영
--
-- 기준 문서
--   docs/product/data-model/direction_communication.dbml
--   <vault>/docs/sql/direction_communication_ddl.sql (목표 상태, 전체 스크립트)
--
-- V1은 수정하지 않는다. 이 파일은 V1과 목표 상태의 차이만 담은 delta다.
-- extension을 만들지 않으므로 Flyway 기본 트랜잭션 안에서 실행한다.
--
-- 백필이 필요한 이유
--   신규 CHECK 두 개가 기존 행 형태를 거부한다. 제약을 걸기 전에 데이터를
--   새 형태로 옮긴다. 빈 DB에서는 no-op이지만 데이터가 있는 DB에서도 맞아야 한다.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. recipient_receive_state — 수신 상한 하드코딩 제거
--
-- 수신 상한은 고정 상수가 아니라 운영 설정값이다. DB에는 어떤 설정에서도 넘을 수
-- 없는 안전 상한만 두고, 실효 상한은 애플리케이션 설정에서 읽어 예약 시 비교한다.
-- 상한을 CHECK로 박으면 값을 바꿀 때마다 마이그레이션이 필요해진다.
-- -----------------------------------------------------------------------------

ALTER TABLE recipient_receive_state
    DROP CONSTRAINT ck_recipient_receive_state_active_count;

ALTER TABLE recipient_receive_state
    ADD CONSTRAINT ck_recipient_receive_state_active_count
    CHECK (active_unhandled_count BETWEEN 0 AND 50);

COMMENT ON TABLE recipient_receive_state IS
    '활성 미처리 질문글이 설정된 수신 상한을 넘지 않도록 동시성 아래에서 예약하기 위한 사용자별 투영값. 상한은 운영 설정값이며 DB는 안전 상한 50만 강제한다.';

-- -----------------------------------------------------------------------------
-- 2. direction_post — 답변 읽음 기준선
--
-- '내가 쓴 질문' 카드의 '새로운 답변 n개' 배지는 이 시각 이후 공개된 답변 수로
-- 계산한다.
-- -----------------------------------------------------------------------------

ALTER TABLE direction_post
    ADD COLUMN answers_read_at TIMESTAMPTZ;

ALTER TABLE direction_post
    ADD CONSTRAINT ck_direction_post_answers_read_at
    CHECK (answers_read_at IS NULL OR answers_read_at >= submitted_at);

COMMENT ON COLUMN direction_post.answers_read_at IS
    '질문자가 이 질문글의 답변 목록을 마지막으로 읽은 시각. 새로운 답변 n개 배지는 이 시각 이후 공개된 답변 수로 계산한다.';

-- -----------------------------------------------------------------------------
-- 3. post_recipient — 넘김 되돌리기
--
-- 스와이프는 곧바로 SKIPPED가 되지 않고 SKIP_PENDING을 거친다. 되돌릴 수 있는
-- 동안에는 수신 용량을 해제하지 않아야 하므로 확정 상태와 분리한다.
--
-- ct_post_recipient_capacity_release는 고치지 않는다. 해제 대상 목록이
-- ANSWERED·SKIPPED·EXPIRED·BLOCKED이고 SKIP_PENDING이 거기 없으므로, 유예 중
-- 용량을 붙잡는 동작이 저절로 나온다.
-- -----------------------------------------------------------------------------

ALTER TABLE post_recipient
    ADD COLUMN skip_requested_at TIMESTAMPTZ;

-- 백필. 기존 확정 넘김 행은 skip_requested_at이 비어 있다. 아래에서 추가하는
-- ck_post_recipient_skip_pending의 둘째 절
-- (skip_requested_at IS NOT NULL OR skipped_at IS NULL)이 그런 행을 거부하므로,
-- 제약을 걸기 전에 확정 시각을 요청 시각으로도 채운다. 확정된 넘김은 두 시각을
-- 모두 갖는다는 정의와 일치하며 skipped_at >= skip_requested_at도 등호로 만족한다.
UPDATE post_recipient
SET skip_requested_at = skipped_at
WHERE skipped_at IS NOT NULL
  AND skip_requested_at IS NULL;

ALTER TABLE post_recipient
    DROP CONSTRAINT ck_post_recipient_status;

ALTER TABLE post_recipient
    ADD CONSTRAINT ck_post_recipient_status
    CHECK (status IN (
        'AVAILABLE', 'DISCOVERED', 'OPENED', 'ANSWERED',
        'SKIP_PENDING', 'SKIPPED', 'EXPIRED', 'BLOCKED'
    ));

ALTER TABLE post_recipient
    DROP CONSTRAINT ck_post_recipient_timestamps;

ALTER TABLE post_recipient
    ADD CONSTRAINT ck_post_recipient_timestamps
    CHECK (
        (discovered_at IS NULL OR discovered_at >= matched_at)
        AND (opened_at IS NULL OR opened_at >= matched_at)
        AND (skip_requested_at IS NULL OR skip_requested_at >= matched_at)
        -- 넘김 확정은 넘김 요청보다 앞설 수 없다.
        AND (skipped_at IS NULL OR skipped_at >= skip_requested_at)
        AND (capacity_released_at IS NULL OR capacity_released_at >= matched_at)
        AND (expired_at IS NULL OR expired_at >= matched_at)
        AND (blocked_at IS NULL OR blocked_at >= matched_at)
    );

-- SKIP_PENDING은 "넘김을 요청했지만 아직 확정되지 않은" 상태와 동치다.
-- 확정된 넘김은 두 시각을 모두 갖고, 되돌린 넘김은 둘 다 비운다.
ALTER TABLE post_recipient
    ADD CONSTRAINT ck_post_recipient_skip_pending
    CHECK (
        (status = 'SKIP_PENDING') = (skip_requested_at IS NOT NULL AND skipped_at IS NULL)
        AND (skip_requested_at IS NOT NULL OR skipped_at IS NULL)
    );

COMMENT ON COLUMN post_recipient.skip_requested_at IS
    '스와이프로 넘김을 요청한 시각. 되돌리기 시간 동안은 SKIP_PENDING이며 수신 용량을 해제하지 않는다. 되돌리면 NULL로 되돌아가고, 이전 상태는 opened_at이 있으면 OPENED, discovered_at만 있으면 DISCOVERED, 둘 다 없으면 AVAILABLE로 유도한다.';

-- -----------------------------------------------------------------------------
-- 4. answer — 한 질문글에 답변 1회
--
-- 거절되거나 삭제된 답변은 자리를 비켜 다시 쓸 수 있게 partial unique로 둔다.
--
-- 실패 시 대응: 이 인덱스 생성이 실패하면 같은 post_recipient_id에
-- status NOT IN ('REJECTED', 'DELETED')인 답변이 둘 이상 있다는 뜻이다.
-- 마이그레이션이 데이터를 임의로 지우지 않는다. 아래 쿼리로 중복을 찾아
-- 제품 판단으로 정리한 뒤 다시 실행한다.
--   SELECT post_recipient_id, count(*)
--   FROM answer WHERE status NOT IN ('REJECTED', 'DELETED')
--   GROUP BY post_recipient_id HAVING count(*) > 1;
-- -----------------------------------------------------------------------------

CREATE UNIQUE INDEX uq_answer_one_per_recipient
    ON answer (post_recipient_id) WHERE status NOT IN ('REJECTED', 'DELETED');

-- -----------------------------------------------------------------------------
-- 5. 공감 (화면 문구 '좋아요')
--
-- 누를 수 있는 사람이 서로 다르고 그 차이를 키로 강제할 수 있어 두 테이블로 나눈다.
-- 질문글 공감은 수신자만, 답변 공감은 질문자만 남긴다.
-- 취소는 행 삭제로 처리한다. 취소 이력을 보관할 제품 요구가 없다.
-- -----------------------------------------------------------------------------

-- (post_id, reactor_id) 복합 FK가 post_recipient를 참조하므로 "수신 자격이 있는
-- 사용자만 공감할 수 있다"가 트리거 없이 키로 성립한다. 질문글 작성자는 자기 글의
-- 수신자가 될 수 없으므로(ct_post_recipient_not_sender) 자기 글 공감도 함께 막힌다.
CREATE TABLE post_reaction (
    post_id                 BIGINT NOT NULL,
    reactor_id              BIGINT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT pk_post_reaction
        PRIMARY KEY (post_id, reactor_id),
    CONSTRAINT fk_post_reaction_recipient
        FOREIGN KEY (post_id, reactor_id)
        REFERENCES post_recipient (post_id, recipient_id) ON DELETE CASCADE
);

CREATE INDEX post_reaction_reactor_idx ON post_reaction (reactor_id);

COMMENT ON TABLE post_reaction IS
    '질문글에 남긴 공감. 답변은 부담스럽지만 반응은 하고 싶은 수신자의 유일한 저비용 표현 수단이다. 공감 수는 질문글 작성자에게만 노출하며 받은 사람 화면에는 총합을 표시하지 않는다. 공감은 수신 용량을 해제하지 않는다.';

-- 공감할 수 있는 사람이 질문자 한 명뿐이므로 answer_id를 PK로 두면 "한 답변에 공감은
-- 하나"가 키로 성립한다. reactor_id가 실제 질문자인지는 answer가 post_id를 직접 갖지
-- 않고 post_recipient를 거쳐 도달하므로 복합 FK로 표현할 수 없어 아래 constraint
-- trigger가 강제한다.
CREATE TABLE answer_reaction (
    answer_id               BIGINT PRIMARY KEY,
    reactor_id              BIGINT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_answer_reaction_answer
        FOREIGN KEY (answer_id) REFERENCES answer (id) ON DELETE CASCADE,
    CONSTRAINT fk_answer_reaction_user
        FOREIGN KEY (reactor_id) REFERENCES user_account (id) ON DELETE CASCADE
);

CREATE INDEX answer_reaction_reactor_idx ON answer_reaction (reactor_id);

COMMENT ON TABLE answer_reaction IS
    '질문자가 받은 답변에 남긴 공감. 답변자가 받는 유일한 반응 신호이며 이것이 없으면 답변자는 자기 답변이 읽혔는지도 알 수 없다. 만료된 질문글의 답변에도 공감할 수 있다 — 만료는 새 답변만 차단한다. 질문자가 답변자에게 줄 수 있는 것은 이 공감 하나이며 텍스트를 되보내는 통로는 만들지 않는다.';

COMMENT ON COLUMN answer_reaction.created_at IS
    '공감을 남긴 시각. 질문자가 만료 뒤에 처음 열어볼 수 있어 답변 시각보다 한참 늦을 수 있다.';

-- -----------------------------------------------------------------------------
-- 6. 답변 공감 작성자 검증 트리거
--
-- 답변 공감을 남길 수 있는 사람은 그 답변이 달린 질문글의 작성자 한 명뿐이다.
-- answer_id가 PK이므로 "답변당 공감 1건"은 키가 이미 보장하고, 이 트리거는
-- "그 하나를 누른 사람이 질문자인가"만 판정한다.
-- -----------------------------------------------------------------------------

CREATE FUNCTION enforce_answer_reaction_reactor_is_sender()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    post_sender_id BIGINT;
BEGIN
    SELECT p.sender_id
    INTO post_sender_id
    FROM answer a
    JOIN post_recipient pr ON pr.id = a.post_recipient_id
    JOIN direction_post p ON p.id = pr.post_id
    WHERE a.id = NEW.answer_id;

    IF NOT FOUND THEN
        RETURN NEW;
    END IF;

    IF post_sender_id <> NEW.reactor_id THEN
        RAISE EXCEPTION
            'answer_reaction on answer % must be created by the question author %, not %',
            NEW.answer_id, post_sender_id, NEW.reactor_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_answer_reaction_reactor_is_sender
AFTER INSERT OR UPDATE OF answer_id, reactor_id ON answer_reaction
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION enforce_answer_reaction_reactor_is_sender();

-- -----------------------------------------------------------------------------
-- 7. 알림·Outbox 종류 갱신
--
-- DAILY_QUESTION_ASSIGNED -> QUESTION_RECOMMENDED. 배정이 아니라 추천이므로
-- 사용자는 고르지 않아도 된다. ANSWER_REACTED와 SKIP_CONFIRMATION_DUE를 추가한다.
--
-- 백필. 기존 행에 옛 값이 남아 있으면 새 CHECK가 거부한다. CHECK를 갈기 전에
-- 값을 옮긴다. QUESTION_RECOMMENDED는 이번에 새로 생기는 값이라 기존 행과 겹칠 수
-- 없고, notification_preference의 PK가 (notification_type, user_id)여도 충돌하지
-- 않는다.
-- -----------------------------------------------------------------------------

UPDATE notification_preference
SET notification_type = 'QUESTION_RECOMMENDED'
WHERE notification_type = 'DAILY_QUESTION_ASSIGNED';

UPDATE notification
SET notification_type = 'QUESTION_RECOMMENDED'
WHERE notification_type = 'DAILY_QUESTION_ASSIGNED';

UPDATE outbox_event
SET event_type = 'QUESTION_RECOMMENDED'
WHERE event_type = 'DAILY_QUESTION_ASSIGNED';

ALTER TABLE notification_preference
    DROP CONSTRAINT ck_notification_preference_type;

ALTER TABLE notification_preference
    ADD CONSTRAINT ck_notification_preference_type
    CHECK (notification_type IN (
        'ANSWER_RECEIVED', 'ANSWER_REACTED', 'DIRECTION_POST_RECEIVED',
        'REPORT_RESOLVED', 'QUESTION_PROPOSAL_REVIEWED', 'QUESTION_RECOMMENDED'
    ));

ALTER TABLE outbox_event
    DROP CONSTRAINT ck_outbox_event_event_type;

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_event_type
    CHECK (event_type IN (
        'RECIPIENT_MATCH_REQUESTED', 'RECIPIENTS_CONFIRMED', 'DIRECTION_POST_EXPIRED',
        'ANSWER_PUBLISHED', 'ANSWER_REACTED', 'SKIP_CONFIRMATION_DUE',
        'QUESTION_RECOMMENDED', 'QUESTION_PROPOSAL_REVIEWED', 'REPORT_RESOLVED'
    ));

ALTER TABLE notification
    DROP CONSTRAINT ck_notification_type;

ALTER TABLE notification
    ADD CONSTRAINT ck_notification_type
    CHECK (notification_type IN (
        'ANSWER_RECEIVED', 'ANSWER_REACTED', 'DIRECTION_POST_RECEIVED',
        'REPORT_RESOLVED', 'QUESTION_PROPOSAL_REVIEWED', 'QUESTION_RECOMMENDED'
    ));
