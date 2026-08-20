-- 알림함 열람 기준선(점)은 알림 줄 상태(notification.status)와 다른 생명주기를
-- 갖는다. 하나의 테이블에 합치면 알림함을 한 번 여는 순간 모든 줄이 READ가 되어
-- 어떤 줄이 새 것이었는지 잃는다. user_id 하나가 곧 한 사용자의 유일한 기준선이다.
CREATE TABLE notification_seen_state (
    user_id  BIGINT PRIMARY KEY,
    seen_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_notification_seen_state_user
        FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE
);

-- 알림함 목록은 (recipient_id, created_at DESC, id DESC)로만 정렬한다.
-- uq_notification_recipient_dedup은 (recipient_id, dedup_key)라 이 정렬을 커버하지 못한다.
-- REVOKED·DISMISSED는 목록에서 제외되므로 부분 인덱스로 좁힌다.
CREATE INDEX notification_recipient_feed_idx
    ON notification (recipient_id, created_at DESC, id DESC)
    WHERE status IN ('UNREAD', 'READ');
