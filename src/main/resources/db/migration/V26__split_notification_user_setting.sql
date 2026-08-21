-- 기존 quiet 값은 아직 배포되지 않은 계약이므로 사용자 공통 설정으로 이관하지 않는다.
CREATE TABLE notification_user_setting (
    user_id         BIGINT PRIMARY KEY,
    push_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_start     TIME,
    quiet_end       TIME,
    quiet_zone_id   VARCHAR(50),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

    CONSTRAINT fk_notification_user_setting_user
        FOREIGN KEY (user_id) REFERENCES user_account (id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_user_setting_quiet_hours
        CHECK (num_nonnulls(quiet_start, quiet_end, quiet_zone_id) IN (0, 3)),
    CONSTRAINT ck_notification_user_setting_distinct_quiet_hours
        CHECK (quiet_start IS NULL OR quiet_start <> quiet_end)
);

ALTER TABLE notification_preference
    DROP CONSTRAINT ck_notification_preference_quiet_hours,
    DROP COLUMN quiet_start,
    DROP COLUMN quiet_end;
