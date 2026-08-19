-- 프로필 이미지는 기존 media_asset을 그대로 참조한다. media_asset은 스키마상
-- answer 전용이 아니라 owner_id 기반 범용 자산이므로 presigned 발급·confirm·
-- 시그니처 검증을 복제하지 않고 재사용한다.
ALTER TABLE user_account
    ADD COLUMN profile_image_media_id BIGINT;

-- (media_id, 소유자)를 함께 참조해, 남의 자산을 자기 프로필로 지정하는 것을 DB에서 막는다.
-- media_asset의 uq_media_asset_id_owner가 이 복합 참조의 전제다.
-- profile_image_media_id가 NULL이면 MATCH SIMPLE 규칙에 따라 제약이 적용되지 않으므로
-- 프로필 미설정 상태를 그대로 표현할 수 있다.
ALTER TABLE user_account
    ADD CONSTRAINT fk_user_account_profile_image
        FOREIGN KEY (profile_image_media_id, id) REFERENCES media_asset (id, owner_id)
        ON DELETE RESTRICT;

COMMENT ON COLUMN user_account.profile_image_media_id IS
    '프로필 이미지로 사용하는 media_asset.id. NULL은 기본 이미지 사용을 뜻하며 별도 sentinel 값을 두지 않는다. 참조 자산이 이후 DELETED가 되어도 이 값은 유지하고, 조회 시점에 기본 이미지로 폴백한다.';
