# Direction Communication Schema Manifest

> **현행 스냅샷:** 2026-09-29 · Issue #288 · `TASK-GH-288-ERD-DBML-REFRESH` · 기준 commit `b7118f7628813bed86a15f9eb5b46063b4400a1d` · Flyway V1~V28. 이 문서의 현행 인벤토리는 해당 migration을 빈 PostgreSQL 16/PostGIS 3.5에 적용한 로컬 카탈로그 기준이며 운영 DB와의 일치를 주장하지 않는다.

## 권위와 범위

[ADR-0001](../../adr/0001-database-schema-ownership.md)에 따라 실행 DB 변경의 권위는 [Flyway V1~V28 migration](../../../src/main/resources/db/migration/)이다. [DBML](direction_communication.dbml)은 현재 백엔드의 논리 설계이고 [ERD 설명](DIRECTION_COMMUNICATION_ERD.md)은 관계·동작의 해설이다. 과거 외부 vault/독립 DDL snapshot은 아래 접힌 이력에만 둔다. 이 manifest의 전체 집계는 `public`의 제품·백엔드 52개와 Spring Session 2개를 포함하고, PostGIS extension 소유 객체와 `flyway_schema_history`를 제외한다.

## 현재 파일 체크섬

| Artifact | SHA-256 | 검증 시점·의미 |
| --- | --- | --- |
| [DBML](direction_communication.dbml) | `8d11d7e861e574843af8311621f917438bda81eeb0b23f2143c169b8aa03ca31` | 2026-09-29, 최종 편집 후 계산 |
| [ERD](DIRECTION_COMMUNICATION_ERD.md) | `87a40bfa4726d3059a9fd055a8f6b0a3ad7ea2b5a116d20eeec6b8100f69d170` | 2026-09-29, 최종 편집 후 계산 |

과거 checksum은 아래 **이력** 표에 원문대로 보존한다. 이 두 현행 값은 저장소 파일의 해시이며 외부 vault 원본과의 byte-for-byte 일치 증거가 아니다.

## V1~V28 전체 카탈로그 요약

| 객체 | 전체 | 제품·백엔드 | Spring Session |
| --- | ---: | ---: | ---: |
| 테이블 | 54 | 52 | 2 |
| 컬럼 | 454 | 444 | 10 |
| 인덱스 | 166 | 161 | 5 |
| 사용자 함수 | 13 | 13 | 0 |
| 사용자 트리거 | 12 | 12 | 0 |
| PRIMARY KEY | 54 | 52 | 2 |
| FOREIGN KEY | 82 | 81 | 1 |
| UNIQUE 제약 | 23 | 23 | 0 |
| CHECK | 187 | 187 | 0 |
| 지연 constraint trigger | 8 | 8 | 0 |
| **일반 테이블 제약 (p/f/u/c)** | **346** | **343** | **3** |
| **`pg_constraint` 전체 (p/f/u/c/t)** | **354** | **351** | **3** |

제약 트리거 8개(`t`)는 사용자 트리거 12개 중 8개의 카탈로그 표현이기도 하다. 따라서 354행을 PK/FK/UNIQUE/CHECK 합계로 읽거나 사용자 트리거 12개를 다시 더하지 않는다. 인덱스 166개는 PK 지원 54, UNIQUE 제약 지원 23, 독립 UNIQUE 23, 독립 비 UNIQUE 66으로 나뉜다. 제품·백엔드 161개와 Spring Session 5개를 같은 기준으로 센 값이다.

## 테이블·컬럼 전체 인벤토리

열 이름은 카탈로그의 ordinal 순서다. 괄호의 숫자는 열 수이며 이름별 상세 타입·nullable·기본값은 DBML과 migration에 있다.

| 범위 | 테이블 | 컬럼 | 열 이름 |
| --- | --- | ---: | --- |
| 제품·백엔드 | `active_user_presence` | 8 | `user_id`, `position`, `coarse_cell_id`, `coarse_region_code`, `accuracy_m`, `receive_allowed`, `location_at`, `expires_at` |
| 제품·백엔드 | `answer` | 16 | `id`, `post_recipient_id`, `author_id`, `status`, `idempotency_key`, `body_text`, `coarse_region_code`, `bearing_from_sender_deg`, `distance_band`, `moderation_status`, `submitted_at`, `published_at`, `deleted_at`, `distance_m`, `edited_at`, `edit_count` |
| 제품·백엔드 | `answer_reaction` | 3 | `answer_id`, `reactor_id`, `created_at` |
| 제품·백엔드 | `appeal_case` | 14 | `id`, `target_type`, `target_id`, `filter_decision_id`, `created_at`, `appellant_user_id`, `status`, `window_started_at`, `expires_at`, `acceptance_reason_code`, `decision`, `decided_at`, `decided_by_operator_user_id`, `restore_blocked_reason_code` |
| 제품·백엔드 | `approved_question` | 11 | `id`, `source_proposal_id`, `source_type`, `status`, `question_text`, `answer_format`, `active_from`, `active_until`, `approved_at`, `approved_by`, `created_at` |
| 제품·백엔드 | `device_credential` | 9 | `id`, `user_id`, `installation_id`, `secret_hash`, `platform`, `credential_status`, `last_used_at`, `created_at`, `revoked_at` |
| 제품·백엔드 | `direction_post` | 14 | `id`, `sender_id`, `approved_question_id`, `status`, `idempotency_key`, `body_text`, `coarse_region_code`, `moderation_status`, `submitted_at`, `published_at`, `expires_at`, `deleted_at`, `answers_read_at`, `request_fingerprint` |
| 제품·백엔드 | `direction_scheme` | 7 | `id`, `code`, `version`, `type`, `segment_count`, `start_offset_deg`, `status` |
| 제품·백엔드 | `direction_segment` | 7 | `id`, `scheme_id`, `segment_key`, `display_name`, `center_bearing_deg`, `angular_width_deg`, `sort_order` |
| 제품·백엔드 | `filter_decision` | 8 | `id`, `filter_job_id`, `attempt_generation`, `verdict`, `requested_release_id`, `actual_model`, `decided_at`, `created_at` |
| 제품·백엔드 | `filter_job` | 14 | `id`, `target_type`, `target_id`, `target_version`, `filter_release_id`, `status`, `attempt_generation`, `manually_resolved`, `resolved_verdict`, `idempotency_key`, `created_at`, `updated_at`, `deadline_at`, `logical_attempt_count` |
| 제품·백엔드 | `filter_job_status_history` | 6 | `id`, `filter_job_id`, `from_status`, `to_status`, `reason`, `occurred_at` |
| 제품·백엔드 | `filter_release` | 8 | `id`, `created_at`, `normalization_ref`, `local_ruleset_ref`, `category_mapping_ref`, `model_snapshot`, `status`, `promoted_at` |
| 제품·백엔드 | `filter_release_retry_gate` | 6 | `filter_release_id`, `state`, `current_limit`, `consecutive_failures`, `consecutive_successes`, `updated_at` |
| 제품·백엔드 | `manual_review_case` | 15 | `id`, `target_type`, `target_id`, `target_version`, `filter_release_id`, `created_at`, `status`, `filter_job_id`, `band`, `validated_report_signal_count`, `priority_policy_version`, `priority_reason_code`, `resolved_at`, `resolved_by_operator_user_id`, `resolved_verdict` |
| 제품·백엔드 | `manual_review_priority_evaluation` | 6 | `id`, `manual_review_case_id`, `band`, `reason_code`, `policy_version`, `evaluated_at` |
| 제품·백엔드 | `media_asset` | 11 | `id`, `owner_id`, `status`, `storage_key`, `mime_type`, `byte_size`, `checksum`, `exif_stripped`, `moderation_status`, `created_at`, `deleted_at` |
| 제품·백엔드 | `media_attachment` | 5 | `media_id`, `owner_id`, `post_id`, `answer_id`, `display_order` |
| 제품·백엔드 | `moderation_review` | 7 | `id`, `report_id`, `reviewer_id`, `decision`, `action_type`, `internal_note`, `reviewed_at` |
| 제품·백엔드 | `notification` | 11 | `id`, `recipient_id`, `outbox_event_id`, `notification_type`, `dedup_key`, `direction_post_id`, `answer_id`, `status`, `created_at`, `read_at`, `report_id` |
| 제품·백엔드 | `notification_delivery` | 9 | `id`, `notification_id`, `push_device_id`, `status`, `attempt_count`, `next_attempt_at`, `created_at`, `sent_at`, `provider_message_id` |
| 제품·백엔드 | `notification_event` | 11 | `id`, `case_id`, `admin_link_path`, `status`, `attempt_count`, `next_attempt_at`, `created_at`, `processed_at`, `lease_owner`, `lease_expires_at`, `lease_generation` |
| 제품·백엔드 | `notification_preference` | 4 | `notification_type`, `user_id`, `enabled`, `updated_at` |
| 제품·백엔드 | `notification_seen_state` | 2 | `user_id`, `seen_at` |
| 제품·백엔드 | `notification_user_setting` | 6 | `user_id`, `push_enabled`, `quiet_start`, `quiet_end`, `quiet_zone_id`, `updated_at` |
| 제품·백엔드 | `operator_action_audit` | 9 | `id`, `operator_user_id`, `action_type`, `target_type`, `target_key`, `reason_code`, `reason_text`, `policy_version`, `occurred_at` |
| 제품·백엔드 | `operator_credential` | 10 | `user_id`, `role`, `login_id`, `password_hash`, `failed_attempt_count`, `locked_until`, `password_updated_at`, `last_login_at`, `created_at`, `updated_at` |
| 제품·백엔드 | `outbox_event` | 15 | `id`, `aggregate_type`, `aggregate_id`, `event_type`, `dedup_key`, `payload`, `status`, `attempt_count`, `next_attempt_at`, `created_at`, `processed_at`, `match_round`, `lease_owner`, `lease_expires_at`, `lease_generation` |
| 제품·백엔드 | `post_audience` | 10 | `post_id`, `direction_scheme_id`, `selected_segment_key`, `center_bearing_deg`, `angular_width_deg`, `min_distance_m`, `max_distance_m`, `origin_position`, `origin_cell_id`, `snapshotted_at` |
| 제품·백엔드 | `post_reaction` | 3 | `post_id`, `reactor_id`, `created_at` |
| 제품·백엔드 | `post_recipient` | 18 | `id`, `post_id`, `recipient_id`, `status`, `distance_band`, `matched_bearing_deg`, `matched_region_code`, `matched_at`, `discovered_at`, `opened_at`, `skipped_at`, `capacity_released_at`, `expired_at`, `blocked_at`, `skip_requested_at`, `inbound_bearing_deg`, `distance_m`, `answers_read_at` |
| 제품·백엔드 | `push_daily_budget` | 5 | `user_id`, `budget_date`, `consumed_total`, `consumed_general`, `updated_at` |
| 제품·백엔드 | `push_device` | 8 | `id`, `user_id`, `platform`, `token_ciphertext`, `token_fingerprint`, `device_status`, `last_seen_at`, `revoked_at` |
| 제품·백엔드 | `push_dispatch_group` | 15 | `id`, `recipient_id`, `notification_type`, `aggregation_key`, `status`, `window_started_at`, `collect_until`, `policy_expires_at`, `attempt_count`, `next_attempt_at`, `budget_local_date`, `budget_consumed_at`, `first_attempted_at`, `created_at`, `completed_at` |
| 제품·백엔드 | `push_dispatch_group_member` | 3 | `group_id`, `notification_id`, `created_at` |
| 제품·백엔드 | `question_assignment` | 7 | `id`, `cycle_id`, `approved_question_id`, `display_order`, `assigned_at`, `first_viewed_at`, `used_at` |
| 제품·백엔드 | `question_assignment_cycle` | 8 | `id`, `user_id`, `cycle_key`, `pool_version`, `status`, `starts_at`, `ends_at`, `created_at` |
| 제품·백엔드 | `question_proposal` | 8 | `id`, `proposer_id`, `status`, `proposed_text`, `decision_reason`, `submitted_at`, `created_at`, `updated_at` |
| 제품·백엔드 | `question_proposal_review` | 6 | `id`, `proposal_id`, `reviewer_id`, `decision`, `reason`, `reviewed_at` |
| 제품·백엔드 | `recipient_receive_state` | 6 | `user_id`, `active_unhandled_count`, `recent_received_count`, `recent_window_started_at`, `last_received_at`, `updated_at` |
| 제품·백엔드 | `region_code` | 5 | `code`, `parent_code`, `display_name`, `level`, `created_at` |
| 제품·백엔드 | `release_promotion_history` | 6 | `id`, `release_id`, `action`, `previous_active_release_id`, `operator_user_id`, `occurred_at` |
| 제품·백엔드 | `report` | 12 | `id`, `reporter_id`, `target_user_id`, `direction_post_id`, `answer_id`, `reason_code`, `detail`, `status`, `created_at`, `resolved_at`, `case_id`, `sub_reason_code` |
| 제품·백엔드 | `report_case` | 12 | `id`, `target_user_id`, `direction_post_id`, `answer_id`, `status`, `severity`, `queue`, `decision`, `created_at`, `resolved_at`, `sla_due_at`, `linked_manual_review_case_id` |
| 제품·백엔드 | `report_case_event` | 5 | `id`, `case_id`, `event_type`, `detail`, `occurred_at` |
| 제품·백엔드 | `report_content_snapshot` | 12 | `report_id`, `captured_at`, `target_type`, `target_id`, `author_id`, `body_text`, `media_object_keys`, `edit_count`, `content_published_at`, `content_hash`, `legal_hold`, `purge_after` |
| 제품·백엔드 | `snapshot_emergency_migration_history` | 7 | `id`, `model_snapshot`, `source_release_id`, `target_release_id`, `migrated_job_count`, `operator_user_id`, `occurred_at` |
| 제품·백엔드 | `snapshot_health` | 9 | `model_snapshot`, `status`, `target_only_failure_count`, `first_target_only_failure_at`, `last_target_only_failure_at`, `official_announcement`, `confirmed_at`, `confirmed_by_operator_user_id`, `updated_at` |
| 제품·백엔드 | `snapshot_health_probe_result` | 5 | `id`, `model_snapshot`, `probe_type`, `classification`, `probed_at` |
| 프레임워크 | `spring_session` | 7 | `primary_id`, `session_id`, `creation_time`, `last_access_time`, `max_inactive_interval`, `expiry_time`, `principal_name` |
| 프레임워크 | `spring_session_attributes` | 3 | `session_primary_id`, `attribute_name`, `attribute_bytes` |
| 제품·백엔드 | `user_account` | 14 | `id`, `role`, `status`, `coarse_region_code`, `locale`, `timezone`, `nickname`, `created_at`, `updated_at`, `deleted_at`, `version`, `country_code`, `country_level`, `profile_image_media_id` |
| 제품·백엔드 | `user_block` | 4 | `blocker_id`, `blocked_id`, `created_at`, `released_at` |
| 제품·백엔드 | `user_private_attribute` | 4 | `user_id`, `gender`, `age_band`, `updated_at` |

## 제약 전체 인벤토리

각 행의 이름은 해당 테이블의 최종 `pg_constraint` 명칭이다. `t` 항목은 아래 trigger 인벤토리와 중복 표시된다. CHECK의 SQL 식은 DBML Table Note와 원본 migration을 따른다.

| 테이블 | PK | FK | UNIQUE | CHECK | t (constraint trigger) |
| --- | --- | --- | --- | --- | --- |
| `active_user_presence` | `active_user_presence_pkey` | `fk_active_user_presence_region`, `fk_active_user_presence_user` | — | `ck_active_user_presence_accuracy`, `ck_active_user_presence_expiry`, `ck_active_user_presence_location` | — |
| `answer` | `answer_pkey` | `fk_answer_recipient_author`, `fk_answer_region` | `uq_answer_id_author`, `uq_answer_idempotency` | `ck_answer_bearing`, `ck_answer_body`, `ck_answer_deleted_at`, `ck_answer_distance_band`, `ck_answer_distance_m`, `ck_answer_edit_count`, `ck_answer_edit_count_edited_at`, `ck_answer_moderation`, `ck_answer_published_at`, `ck_answer_status` | `ct_answer_has_content` |
| `answer_reaction` | `pk_answer_reaction` | `fk_answer_reaction_answer`, `fk_answer_reaction_user` | — | — | `ct_answer_reaction_reactor_can_view` |
| `appeal_case` | `appeal_case_pkey` | `fk_appeal_case_decision` | — | `ck_appeal_case_acceptance_reason_code`, `ck_appeal_case_appellant_user_id`, `ck_appeal_case_decided_fields`, `ck_appeal_case_decision`, `ck_appeal_case_expires_after_window_start`, `ck_appeal_case_restore_blocked_reason`, `ck_appeal_case_status`, `ck_appeal_case_target_type` | — |
| `approved_question` | `approved_question_pkey` | `fk_approved_question_approver`, `fk_approved_question_source_proposal` | `uq_approved_question_source_proposal` | `ck_approved_question_active_range`, `ck_approved_question_answer_format`, `ck_approved_question_approval`, `ck_approved_question_source`, `ck_approved_question_source_type`, `ck_approved_question_status`, `ck_approved_question_text` | — |
| `device_credential` | `device_credential_pkey` | `fk_device_credential_user` | — | `ck_device_credential_installation_id`, `ck_device_credential_platform`, `ck_device_credential_revoked_at`, `ck_device_credential_status` | — |
| `direction_post` | `direction_post_pkey` | `fk_direction_post_question`, `fk_direction_post_region`, `fk_direction_post_sender` | `uq_direction_post_id_sender`, `uq_direction_post_idempotency` | `ck_direction_post_answers_read_at`, `ck_direction_post_body`, `ck_direction_post_deleted_at`, `ck_direction_post_expiry`, `ck_direction_post_moderation`, `ck_direction_post_published_at`, `ck_direction_post_status` | `ct_direction_post_has_content`, `ct_direction_post_question_active` |
| `direction_scheme` | `direction_scheme_pkey` | — | `uq_direction_scheme_code_version` | `ck_direction_scheme_segment_count`, `ck_direction_scheme_start_offset`, `ck_direction_scheme_status`, `ck_direction_scheme_type`, `ck_direction_scheme_version` | — |
| `direction_segment` | `direction_segment_pkey` | `fk_direction_segment_scheme` | `uq_direction_segment_key`, `uq_direction_segment_order` | `ck_direction_segment_center`, `ck_direction_segment_order`, `ck_direction_segment_width` | — |
| `filter_decision` | `filter_decision_pkey` | `fk_filter_decision_job`, `fk_filter_decision_requested_release` | — | `ck_filter_decision_verdict` | — |
| `filter_job` | `filter_job_pkey` | `fk_filter_job_release` | — | `ck_filter_job_attempt_generation`, `ck_filter_job_logical_attempt_count`, `ck_filter_job_manual_implies_resolved`, `ck_filter_job_resolved_has_verdict`, `ck_filter_job_resolved_verdict`, `ck_filter_job_status`, `ck_filter_job_target_type` | — |
| `filter_job_status_history` | `filter_job_status_history_pkey` | `fk_filter_job_status_history_job` | — | — | — |
| `filter_release` | `filter_release_pkey` | — | — | `ck_filter_release_promoted_at`, `ck_filter_release_status` | — |
| `filter_release_retry_gate` | `filter_release_retry_gate_pkey` | `fk_filter_release_retry_gate_release` | — | `ck_filter_release_retry_gate_counts`, `ck_filter_release_retry_gate_limit`, `ck_filter_release_retry_gate_limit_positive`, `ck_filter_release_retry_gate_state` | — |
| `manual_review_case` | `manual_review_case_pkey` | `fk_manual_review_case_job`, `fk_manual_review_case_release` | — | `ck_manual_review_case_band`, `ck_manual_review_case_report_signal_count`, `ck_manual_review_case_resolved_fields`, `ck_manual_review_case_resolved_verdict`, `ck_manual_review_case_status`, `ck_manual_review_case_target_type` | — |
| `manual_review_priority_evaluation` | `manual_review_priority_evaluation_pkey` | `fk_manual_review_priority_evaluation_case` | — | `ck_manual_review_priority_evaluation_band` | — |
| `media_asset` | `media_asset_pkey` | `fk_media_asset_owner` | `uq_media_asset_id_owner`, `uq_media_asset_storage_key` | `ck_media_asset_deleted_at`, `ck_media_asset_moderation`, `ck_media_asset_size`, `ck_media_asset_status` | `ct_media_status_preserves_content` |
| `media_attachment` | `media_attachment_pkey` | `fk_media_attachment_answer_owner`, `fk_media_attachment_asset_owner`, `fk_media_attachment_post_owner` | — | `ck_media_attachment_exactly_one_target`, `ck_media_attachment_order` | `ct_media_attachment_preserves_content` |
| `moderation_review` | `moderation_review_pkey` | `fk_moderation_review_report`, `fk_moderation_review_reviewer` | — | `ck_moderation_review_action_type`, `ck_moderation_review_decision` | — |
| `notification` | `notification_pkey` | `fk_notification_answer`, `fk_notification_outbox`, `fk_notification_post`, `fk_notification_recipient`, `notification_report_id_fkey` | `uq_notification_recipient_dedup` | `ck_notification_read_at`, `ck_notification_status`, `ck_notification_target`, `ck_notification_type` | — |
| `notification_delivery` | `notification_delivery_pkey` | `fk_notification_delivery_device`, `fk_notification_delivery_notification` | `uq_notification_delivery_device` | `ck_notification_delivery_attempt_count`, `ck_notification_delivery_sent_at`, `ck_notification_delivery_status` | — |
| `notification_event` | `notification_event_pkey` | `fk_notification_event_case` | `uq_notification_event_case_id` | `ck_notification_event_attempt_count`, `ck_notification_event_lease_generation`, `ck_notification_event_lease_state`, `ck_notification_event_processed_at`, `ck_notification_event_status` | — |
| `notification_preference` | `pk_notification_preference` | `fk_notification_preference_user` | — | `ck_notification_preference_type` | — |
| `notification_seen_state` | `notification_seen_state_pkey` | `fk_notification_seen_state_user` | — | — | — |
| `notification_user_setting` | `notification_user_setting_pkey` | `fk_notification_user_setting_user` | — | `ck_notification_user_setting_distinct_quiet_hours`, `ck_notification_user_setting_quiet_hours` | — |
| `operator_action_audit` | `operator_action_audit_pkey` | — | — | `ck_operator_action_audit_action_type`, `ck_operator_action_audit_operator`, `ck_operator_action_audit_policy_version`, `ck_operator_action_audit_reason_code`, `ck_operator_action_audit_reason_text`, `ck_operator_action_audit_target_key`, `ck_operator_action_audit_target_type` | — |
| `operator_credential` | `operator_credential_pkey` | `fk_operator_credential_user` | `uq_operator_credential_login_id` | `ck_operator_credential_failed_attempt`, `ck_operator_credential_locked_until`, `ck_operator_credential_login_id`, `ck_operator_credential_role` | — |
| `outbox_event` | `outbox_event_pkey` | — | `uq_outbox_event_dedup` | `ck_outbox_event_aggregate_type`, `ck_outbox_event_attempt_count`, `ck_outbox_event_event_type`, `ck_outbox_event_lease_generation`, `ck_outbox_event_lease_state`, `ck_outbox_event_match_round`, `ck_outbox_event_payload`, `ck_outbox_event_processed_at`, `ck_outbox_event_status` | — |
| `post_audience` | `post_audience_pkey` | `fk_post_audience_post`, `fk_post_audience_segment` | — | `ck_post_audience_center`, `ck_post_audience_distance`, `ck_post_audience_origin`, `ck_post_audience_width` | — |
| `post_reaction` | `pk_post_reaction` | `fk_post_reaction_recipient` | — | — | — |
| `post_recipient` | `post_recipient_pkey` | `fk_post_recipient_post`, `fk_post_recipient_region`, `fk_post_recipient_user` | `uq_post_recipient_id_user`, `uq_post_recipient_post_user` | `ck_post_recipient_answers_read_at`, `ck_post_recipient_bearing`, `ck_post_recipient_distance_band`, `ck_post_recipient_distance_m`, `ck_post_recipient_inbound_bearing`, `ck_post_recipient_skip_pending`, `ck_post_recipient_status`, `ck_post_recipient_status_timestamps`, `ck_post_recipient_timestamps` | `ct_post_recipient_capacity_release`, `ct_post_recipient_not_sender` |
| `push_daily_budget` | `pk_push_daily_budget` | `fk_push_daily_budget_user` | — | `ck_push_daily_budget_counts` | — |
| `push_device` | `push_device_pkey` | `fk_push_device_user` | — | `ck_push_device_platform`, `ck_push_device_revoked_at`, `ck_push_device_status` | — |
| `push_dispatch_group` | `push_dispatch_group_pkey` | `fk_push_dispatch_group_recipient` | `uq_push_dispatch_group_aggregation_key` | `ck_push_dispatch_group_attempt_count`, `ck_push_dispatch_group_budget`, `ck_push_dispatch_group_completed_at`, `ck_push_dispatch_group_first_attempt`, `ck_push_dispatch_group_status`, `ck_push_dispatch_group_type`, `ck_push_dispatch_group_window` | — |
| `push_dispatch_group_member` | `pk_push_dispatch_group_member` | `fk_push_dispatch_group_member_group`, `fk_push_dispatch_group_member_notification` | — | — | — |
| `question_assignment` | `question_assignment_pkey` | `fk_question_assignment_cycle`, `fk_question_assignment_question` | `uq_question_assignment_cycle_order`, `uq_question_assignment_cycle_question` | `ck_question_assignment_display_order`, `ck_question_assignment_used_at`, `ck_question_assignment_viewed_at` | — |
| `question_assignment_cycle` | `question_assignment_cycle_pkey` | `fk_question_assignment_cycle_user` | `uq_question_assignment_cycle_user_key` | `ck_question_assignment_cycle_range`, `ck_question_assignment_cycle_status` | — |
| `question_proposal` | `question_proposal_pkey` | `fk_question_proposal_proposer` | — | `ck_question_proposal_status`, `ck_question_proposal_submission`, `ck_question_proposal_text` | — |
| `question_proposal_review` | `question_proposal_review_pkey` | `fk_question_proposal_review_proposal`, `fk_question_proposal_review_reviewer` | — | `ck_question_proposal_review_decision`, `ck_question_proposal_review_reason` | — |
| `recipient_receive_state` | `recipient_receive_state_pkey` | `fk_recipient_receive_state_user` | — | `ck_recipient_receive_state_active_count`, `ck_recipient_receive_state_last_received`, `ck_recipient_receive_state_recent_count` | — |
| `region_code` | `region_code_pkey` | `fk_region_code_parent` | `uq_region_code_code_level` | `ck_region_code_display_name`, `ck_region_code_level`, `ck_region_code_not_self_parent`, `ck_region_code_root` | — |
| `release_promotion_history` | `release_promotion_history_pkey` | `fk_release_promotion_history_previous_release`, `fk_release_promotion_history_release` | — | `ck_release_promotion_history_action` | — |
| `report` | `report_pkey` | `fk_report_answer`, `fk_report_direction_post`, `fk_report_reporter`, `fk_report_target_user`, `report_case_id_fkey` | — | `ck_report_exactly_one_target`, `ck_report_reason`, `ck_report_resolution`, `ck_report_status`, `ck_report_sub_reason` | — |
| `report_case` | `report_case_pkey` | `fk_report_case_answer`, `fk_report_case_direction_post`, `fk_report_case_target_user` | — | `ck_report_case_decision`, `ck_report_case_exactly_one_target`, `ck_report_case_linked_manual_review_case_id`, `ck_report_case_queue`, `ck_report_case_resolution`, `ck_report_case_severity`, `ck_report_case_status` | — |
| `report_case_event` | `report_case_event_pkey` | `fk_report_case_event_case` | — | `ck_report_case_event_type` | — |
| `report_content_snapshot` | `report_content_snapshot_pkey` | `fk_report_content_snapshot_report` | — | `ck_report_content_snapshot_author_id`, `ck_report_content_snapshot_edit_count`, `ck_report_content_snapshot_target_id`, `ck_report_content_snapshot_target_type` | — |
| `snapshot_emergency_migration_history` | `snapshot_emergency_migration_history_pkey` | `fk_snapshot_emergency_migration_history_snapshot`, `fk_snapshot_emergency_migration_history_source_release`, `fk_snapshot_emergency_migration_history_target_release` | — | `ck_snapshot_emergency_migration_history_different_release`, `ck_snapshot_emergency_migration_history_job_count` | — |
| `snapshot_health` | `snapshot_health_pkey` | — | — | `ck_snapshot_health_confirmed`, `ck_snapshot_health_status`, `ck_snapshot_health_target_only_failure_count` | — |
| `snapshot_health_probe_result` | `snapshot_health_probe_result_pkey` | `fk_snapshot_health_probe_result_snapshot` | — | `ck_snapshot_health_probe_result_classification`, `ck_snapshot_health_probe_result_type` | — |
| `spring_session` | `spring_session_pk` | — | — | — | — |
| `spring_session_attributes` | `spring_session_attributes_pk` | `spring_session_attributes_fk` | — | — | — |
| `user_account` | `user_account_pkey` | `fk_user_account_country`, `fk_user_account_profile_image`, `fk_user_account_region` | `uq_user_account_id_role` | `ck_user_account_country_code`, `ck_user_account_deleted_at`, `ck_user_account_nickname`, `ck_user_account_role`, `ck_user_account_status`, `ck_user_account_user_country` | — |
| `user_block` | `pk_user_block` | `fk_user_block_blocked`, `fk_user_block_blocker` | — | `ck_user_block_not_self`, `ck_user_block_release` | — |
| `user_private_attribute` | `user_private_attribute_pkey` | `fk_user_private_attribute_user` | — | `ck_user_private_attribute_age_band`, `ck_user_private_attribute_gender` | — |

82개 FK 중 제품·백엔드 81개, `spring_session_attributes_fk` 1개다. 제품 DBML Ref와 카탈로그를 비교할 때는 프레임워크 FK를 빼고 센다.

## 인덱스 전체 인벤토리

`PK`/`UQ 제약`은 지원 인덱스, `독립 UQ`/`독립`은 별도 인덱스다. `부분`/`식`/`GiST` 표기는 카탈로그 정의의 성격을 나타낸다. 선택도나 planner의 실제 사용을 보장하지 않는다. 특히 공간 후보 인덱스의 사용은 조건과 통계에 따라 달라진다.

| 테이블 | 인덱스 (종류) |
| --- | --- |
| `active_user_presence` | `active_user_presence_expiry_idx` (독립, 부분), `active_user_presence_pkey` (PK), `active_user_presence_position_gix` (독립, 부분, GiST), `active_user_presence_region_idx` (독립) |
| `answer` | `answer_pkey` (PK), `answer_recipient_idx` (독립), `answer_region_idx` (독립), `uq_answer_id_author` (UQ 제약), `uq_answer_idempotency` (UQ 제약), `uq_answer_one_per_recipient` (독립 UQ, 부분) |
| `answer_reaction` | `answer_reaction_reactor_idx` (독립), `pk_answer_reaction` (PK) |
| `appeal_case` | `appeal_case_appellant_idx` (독립), `appeal_case_pkey` (PK), `appeal_case_queue_idx` (독립, 부분), `uq_appeal_case_target_decision` (독립 UQ) |
| `approved_question` | `approved_question_active_idx` (독립, 부분), `approved_question_approver_idx` (독립, 부분), `approved_question_pkey` (PK), `uq_approved_question_source_proposal` (UQ 제약) |
| `device_credential` | `device_credential_pkey` (PK), `device_credential_user_idx` (독립, 부분), `uq_active_device_installation` (독립 UQ, 부분), `uq_device_credential_secret` (독립 UQ) |
| `direction_post` | `direction_post_expiry_idx` (독립, 부분), `direction_post_pkey` (PK), `direction_post_question_idx` (독립), `direction_post_region_idx` (독립), `direction_post_sender_idx` (독립), `uq_direction_post_id_sender` (UQ 제약), `uq_direction_post_idempotency` (UQ 제약) |
| `direction_scheme` | `direction_scheme_pkey` (PK), `uq_direction_scheme_active` (독립 UQ, 부분), `uq_direction_scheme_code_version` (UQ 제약) |
| `direction_segment` | `direction_segment_pkey` (PK), `uq_direction_segment_key` (UQ 제약), `uq_direction_segment_order` (UQ 제약) |
| `filter_decision` | `filter_decision_pkey` (PK), `uq_filter_decision_job_attempt` (독립 UQ) |
| `filter_job` | `filter_job_deadline_scan_idx` (독립, 부분), `filter_job_pkey` (PK), `filter_job_target_idx` (독립), `uq_filter_job_idempotency_key` (독립 UQ) |
| `filter_job_status_history` | `filter_job_status_history_job_idx` (독립), `filter_job_status_history_pkey` (PK) |
| `filter_release` | `filter_release_pkey` (PK), `filter_release_status_idx` (독립), `uq_filter_release_single_promoted` (독립 UQ, 부분, 식) |
| `filter_release_retry_gate` | `filter_release_retry_gate_pkey` (PK) |
| `manual_review_case` | `manual_review_case_pkey` (PK), `manual_review_case_queue_idx` (독립, 부분), `uq_manual_review_case_target` (독립 UQ) |
| `manual_review_priority_evaluation` | `manual_review_priority_evaluation_case_idx` (독립), `manual_review_priority_evaluation_pkey` (PK) |
| `media_asset` | `media_asset_owner_idx` (독립), `media_asset_pkey` (PK), `uq_media_asset_id_owner` (UQ 제약), `uq_media_asset_storage_key` (UQ 제약) |
| `media_attachment` | `media_attachment_pkey` (PK), `uq_media_attachment_answer_order` (독립 UQ, 부분), `uq_media_attachment_post_order` (독립 UQ, 부분) |
| `moderation_review` | `moderation_review_pkey` (PK), `moderation_review_report_idx` (독립), `moderation_review_reviewer_idx` (독립) |
| `notification` | `notification_answer_idx` (독립, 부분), `notification_inbox_idx` (독립), `notification_outbox_idx` (독립), `notification_pkey` (PK), `notification_post_idx` (독립, 부분), `notification_recipient_feed_idx` (독립, 부분), `uq_notification_recipient_dedup` (UQ 제약) |
| `notification_delivery` | `notification_delivery_device_idx` (독립), `notification_delivery_dispatch_idx` (독립, 부분), `notification_delivery_pkey` (PK), `uq_notification_delivery_device` (UQ 제약) |
| `notification_event` | `notification_event_claim_idx` (독립, 부분), `notification_event_pkey` (PK), `uq_notification_event_case_id` (UQ 제약) |
| `notification_preference` | `notification_preference_user_idx` (독립), `pk_notification_preference` (PK) |
| `notification_seen_state` | `notification_seen_state_pkey` (PK) |
| `notification_user_setting` | `notification_user_setting_pkey` (PK) |
| `operator_action_audit` | `operator_action_audit_operator_idx` (독립), `operator_action_audit_pkey` (PK), `operator_action_audit_target_idx` (독립) |
| `operator_credential` | `operator_credential_pkey` (PK), `uq_operator_credential_login_id` (UQ 제약) |
| `outbox_event` | `outbox_event_claim_idx` (독립, 부분), `outbox_event_dispatch_idx` (독립, 부분), `outbox_event_pkey` (PK), `uq_outbox_event_dedup` (UQ 제약), `uq_outbox_event_direction_matching_round` (독립 UQ, 부분) |
| `post_audience` | `post_audience_pkey` (PK), `post_audience_segment_idx` (독립) |
| `post_reaction` | `pk_post_reaction` (PK), `post_reaction_reactor_idx` (독립) |
| `post_recipient` | `post_recipient_capacity_idx` (독립, 부분), `post_recipient_inbox_idx` (독립), `post_recipient_pkey` (PK), `post_recipient_region_idx` (독립), `uq_post_recipient_id_user` (UQ 제약), `uq_post_recipient_post_user` (UQ 제약) |
| `push_daily_budget` | `pk_push_daily_budget` (PK) |
| `push_device` | `push_device_pkey` (PK), `push_device_user_idx` (독립, 부분), `uq_active_push_token` (독립 UQ, 부분) |
| `push_dispatch_group` | `push_dispatch_group_due_idx` (독립, 부분), `push_dispatch_group_pkey` (PK), `push_dispatch_group_recommendation_history_idx` (독립, 부분), `uq_push_dispatch_group_aggregation_key` (UQ 제약), `uq_push_dispatch_group_collecting` (독립 UQ, 부분) |
| `push_dispatch_group_member` | `pk_push_dispatch_group_member` (PK), `uq_push_dispatch_group_member_notification` (독립 UQ) |
| `question_assignment` | `question_assignment_history_idx` (독립), `question_assignment_pkey` (PK), `uq_question_assignment_cycle_order` (UQ 제약), `uq_question_assignment_cycle_question` (UQ 제약) |
| `question_assignment_cycle` | `question_assignment_cycle_pkey` (PK), `uq_question_assignment_cycle_user_key` (UQ 제약) |
| `question_proposal` | `question_proposal_pkey` (PK), `question_proposal_proposer_idx` (독립), `question_proposal_review_queue_idx` (독립, 부분) |
| `question_proposal_review` | `question_proposal_review_history_idx` (독립), `question_proposal_review_pkey` (PK), `question_proposal_review_reviewer_idx` (독립) |
| `recipient_receive_state` | `recipient_receive_selection_idx` (독립), `recipient_receive_state_pkey` (PK) |
| `region_code` | `region_code_parent_idx` (독립, 부분), `region_code_pkey` (PK), `uq_region_code_code_level` (UQ 제약) |
| `release_promotion_history` | `release_promotion_history_pkey` (PK), `release_promotion_history_release_idx` (독립) |
| `report` | `idx_report_reporter_answer_suppression` (독립, 부분), `report_answer_idx` (독립, 부분), `report_direction_post_idx` (독립, 부분), `report_pkey` (PK), `report_queue_idx` (독립, 부분), `report_target_user_idx` (독립, 부분), `uq_open_report_answer` (독립 UQ, 부분), `uq_open_report_post` (독립 UQ, 부분), `uq_open_report_user` (독립 UQ, 부분) |
| `report_case` | `report_case_pkey` (PK), `uq_open_case_answer` (독립 UQ, 부분), `uq_open_case_post` (독립 UQ, 부분), `uq_open_case_user` (독립 UQ, 부분) |
| `report_case_event` | `report_case_event_case_idx` (독립), `report_case_event_pkey` (PK) |
| `report_content_snapshot` | `report_content_snapshot_pkey` (PK) |
| `snapshot_emergency_migration_history` | `snapshot_emergency_migration_history_pkey` (PK), `snapshot_emergency_migration_history_snapshot_idx` (독립) |
| `snapshot_health` | `snapshot_health_pkey` (PK) |
| `snapshot_health_probe_result` | `snapshot_health_probe_result_pkey` (PK), `snapshot_health_probe_result_snapshot_idx` (독립) |
| `spring_session` | `spring_session_ix1` (독립 UQ), `spring_session_ix2` (독립), `spring_session_ix3` (독립), `spring_session_pk` (PK) |
| `spring_session_attributes` | `spring_session_attributes_pk` (PK) |
| `user_account` | `uq_user_account_id_role` (UQ 제약), `uq_user_account_nickname_ci` (독립 UQ, 부분, 식), `user_account_country_idx` (독립), `user_account_pkey` (PK), `user_account_region_idx` (독립) |
| `user_block` | `pk_user_block` (PK), `user_block_reverse_idx` (독립, 부분) |
| `user_private_attribute` | `user_private_attribute_pkey` (PK) |

DBML parser `@dbml/core@10.2.0`은 `checks {}`와 TableGroup 속성을 받지 않는다. 정확한 CHECK SQL은 Note, 그룹 색상은 주석, generated column·GiST·partial/표현식 인덱스·지연 제약 트리거의 실행 의미는 Note와 migration에 남긴다. DBML 다이어그램만으로 SQL 집행을 추론하지 않는다.

## 사용자 함수·트리거 전체 인벤토리

| 함수 서명 |
| --- |
| `assert_answer_has_content(bigint)` |
| `assert_post_has_content(bigint)` |
| `enforce_answer_has_content()` |
| `enforce_answer_reaction_reactor_can_view()` |
| `enforce_direction_post_question_active()` |
| `enforce_media_attachment_preserves_content()` |
| `enforce_media_status_preserves_content()` |
| `enforce_post_has_content()` |
| `enforce_post_recipient_capacity_release()` |
| `enforce_post_recipient_not_sender()` |
| `enforce_question_text_immutability()` |
| `enforce_report_evidence_immutability()` |
| `enforce_report_snapshot_immutability_except_media_purge()` |

| 테이블 | 트리거 | 호출 함수 |
| --- | --- | --- |
| `answer` | `ct_answer_has_content` | `enforce_answer_has_content()` |
| `answer_reaction` | `ct_answer_reaction_reactor_can_view` | `enforce_answer_reaction_reactor_can_view()` |
| `approved_question` | `tr_approved_question_text_immutable` | `enforce_question_text_immutability()` |
| `direction_post` | `ct_direction_post_has_content` | `enforce_post_has_content()` |
| `direction_post` | `ct_direction_post_question_active` | `enforce_direction_post_question_active()` |
| `media_asset` | `ct_media_status_preserves_content` | `enforce_media_status_preserves_content()` |
| `media_attachment` | `ct_media_attachment_preserves_content` | `enforce_media_attachment_preserves_content()` |
| `post_recipient` | `ct_post_recipient_capacity_release` | `enforce_post_recipient_capacity_release()` |
| `post_recipient` | `ct_post_recipient_not_sender` | `enforce_post_recipient_not_sender()` |
| `question_proposal` | `tr_question_proposal_text_immutable_after_submit` | `enforce_question_text_immutability()` |
| `report_case_event` | `tr_report_case_event_immutable` | `enforce_report_evidence_immutability()` |
| `report_content_snapshot` | `tr_report_content_snapshot_immutable` | `enforce_report_snapshot_immutability_except_media_purge()` |

## 2026-09-29 검증 근거와 한계

- Java 21에서 기존 `FlywayMigrationIntegrationTest` 11개 통과. 별도 빈 PostgreSQL 16/PostGIS 3.5에 변경하지 않은 V1~V28 migration 28개를 적용하고 전체 `public` 카탈로그를 추출했다. 생성 전후 migration 파일의 정렬된 SHA-256 목록은 일치했고 임시 DB는 제거했다.
- DBML은 `@dbml/core@10.2.0`에서 파싱·export했다. 독립 비교에서 제품 52 테이블/444 컬럼, 81 FK, 187 CHECK SQL 정의, 161 제품 인덱스, 12 트리거 이름이 카탈로그와 일치했다. 복합 FK, 부분 인덱스 predicate, 생성 컬럼, 그룹 소속도 비교했다. 이 결과는 로컬 작업 검증이며 운영 DB 조회 결과가 아니다.
- 기존 `./harness test-run --id TEST-PLAN-GH-288-ERD-DBML-REFRESH`: 단위 1,064개·통합 742개, 실패·오류·skip 0. `./harness check`, `./harness pr-ready --project-tests`, `npm run hooks:validate`도 통과했다. 이 Task 3에서는 Gradle을 재실행하지 않고 문서의 링크·표·해시·공백 일관성을 확인한다.
- 재현 방법: 저장소의 [migration](../../../src/main/resources/db/migration/)을 새 PostgreSQL/PostGIS에 Flyway로 적용한 뒤 `pg_catalog`에서 `public`의 table/column/constraint/index/user trigger/function을 추출한다. `pg_constraint.contype`별로 p/f/u/c/t를 나누고, `pg_index`에서 primary·제약 연결·독립 index를 분리한다. DBML을 고정 parser로 export하여 Spring Session 2개를 제외한 52개 테이블과 대조한다. 보안상 연결 값은 문서에 기록하지 않는다.
- 검증 범위 밖: 외부 vault의 현행 파일, 운영 DB 스키마, 실제 푸시 provider/스케줄러 활성화. 과거 독립 DDL 실험과 이 snapshot의 결과를 혼합하지 않는다.

## 과거 source snapshot과 결정 기록 (현행 지침 아님)

아래 기록은 이전 날짜의 원문 체크섬과 변경·검증 이력을 보존한다. 당시의 "현재", V12 working branch 상태, 파일 경로, 카탈로그 개수, vault 동기화 문구는 해당 날짜의 진술이며 2026-09-29 V28 스냅샷의 사실로 재사용하지 않는다.

<details>
<summary>2026-08 source snapshot·V12 계약·인계 기록 펼치기</summary>

## 3. Source snapshot

| Artifact | Repository path or source | SHA-256 | Role |
| --- | --- | --- | --- |
| DBML (2026-08-08b, baseline) | `docs/product/data-model/direction_communication.dbml` | `ef5e9885f9308d1b86946094dfa49f084e2e3ace4094482d8b3e4b3c3dccd13c` | Issue #115 이전 logical schema baseline |
| ERD (2026-08-08b, baseline) | `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md` | `6d99297f5bf771a48db98f616c3bbeb7282e44311aadf47080214f276dd8c4ac` | Issue #115 이전 explanatory contract baseline |
| DBML (Issue #115, 2026-08-11) | `docs/product/data-model/direction_communication.dbml` | `3e5c9142eeb415ccfd503413ea73a3de74f3ba472ee4c3803e390af1f326213c` | `direction_post.request_fingerprint`, `outbox_event.match_round`·lease fencing, matching partial unique index와 claim index, exact-coordinate payload exclusion을 반영한 현재 working-tree 판 |
| ERD (Issue #115, 2026-08-11) | `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md` | `8ac5080dd0fd0fd8a5c9e3ac6fba75f58f3ab22d8103eb7d77f12c1543be9a77` | Issue #115의 fingerprint·outbox-as-matching-job·lease fencing·payload 보안 계약을 설명하는 현재 working-tree 판 |
| V12 migration (Issue #115, working branch) | `src/main/resources/db/migration/V12__add_direction_matching_outbox_contract.sql` | `f62f6ad5bfdc88601a3630b10bdc7a48b546d1913152e31ead2fd1bf5a1364be` | 이 문서 변경의 비교 기준. 실제 catalog 적용 검증은 통합 테스트가 소유한다 |
| target DDL (2026-08-08b) | source workspace `docs/sql/direction_communication_ddl.sql` | `98b611c9d5ca8a912a97fb0be77de4ae9c6d31b3982acac04cfb3a44ac47e5a9` | vault의 최신 상태 스크립트. `uq_operator_credential_login_id` UNIQUE INDEX 추가와 헤더 주석 갱신. 이 저장소는 이 판도 어떤 migration의 authoring reference로 쓰지 않았다 |
| DBML (2026-08-08a, 이력) | 위 파일의 2026-08-08a 판 | `2386e15ebcf6eb3f89b093fe3904b9402c41b50fcd4cfbc24ca83f09ff9ea4da` | `post_reaction` Note 정정만 반영하고 인증 부록 정합화(위 08-08b) 이전인 판. `#79`가 만든 판 |
| ERD (2026-08-08a, 이력) | 위 파일의 2026-08-08a 판 | `a8487e35d63d174eb73e3b18fcc26172881e29eefdd74a9dd36329f39070309c` | 위 DBML(08-08a)과 짝을 이루는 판 |
| target DDL (2026-08-08a, 이력) | source workspace `docs/sql/direction_communication_ddl.sql`의 2026-08-08a 판 | `ac57f3229a4bc439153c1c3e8b39d37877e064ce07adf01e54254bd7094892c7` | `post_reaction` Note 정정에 더해 `user_account.version`(V4)/`operator_credential`(V5)/`device_credential`(V7)을 처음 반영한 판 |
| DBML (2026-08-07, 이력) | 위 파일의 2026-08-07 판 | `3b443c4bea41a92d4f803e78d004fbe1ca6e1475f7ed6ae8f94fa8cc3121acc1` | `post_reaction` Note 정정 이전 판. 이 표는 이 값을 `현행`으로 잘못 기록한 채 `#78`에서 남아 있었다(§5~§12 갱신 시 SHA-256 재계산을 누락) — `#79`에서 바로잡았다 |
| target DDL (2026-08-07, 이력) | source workspace `docs/sql/direction_communication_ddl.sql`의 2026-08-07 판 | `d873908c802b4c3ff73637e3ef4c1ec84862146055f3ff7672e6908947fb2d31` | V8 authoring reference(원래 V7로 작성, `#81`과의 번호 충돌로 재번호). `V8`은 이 판을 기준으로 작성됐고 그 사실은 바뀌지 않는다 |
| DBML (2026-08-04, 이력) | 위 파일의 2026-08-04 판 | `4637f956f9703a8bdc38590957c2e48d60633e6d633beb3f193151b5c4c928f5` | 답변 격리 폐기(ADR-0002) 이전 판 |
| ERD (2026-08-04, 이력) | 위 파일의 2026-08-04 판 | `181604080ecffd58752e2b40bc3008fbdcdfb7736caff00820535ed6ba128886` | 답변 격리 폐기(ADR-0002) 이전 판 |
| target DDL (2026-08-04, 이력) | source workspace `docs/sql/direction_communication_ddl.sql`의 2026-08-04 판 | `be8aaee3b4671aa218c78c15bf33d6ade3ad1cfce902dc10d2cbd45b9fe5805f` | V2 authoring reference only |
| V1 원본 DDL (2026-08-03, 이력) | 위 파일의 2026-08-03 판 | `cc93ba87aa5999bdd48589b63fa4da4e383270626fb36ecb7adac482ed3d95a7` | `V1__…sql`이 파생된 원본 |

원본 DBML과 ERD는 byte-for-byte로 복사되어 위 checksum과 일치한다. target DDL은
전체 상태 스크립트이므로 migration 경로에 복사하지 않는다. `V8__…sql`은 V7과
target DDL의 차이만 담은 delta로 손으로 작성한다. 이력 행은 보존용이며,
`FlywayMigrationContractTest`가 `V1__…sql`의 sha256을 그 값으로 잠근다.

vault DBML과 target DDL 사이에는 알려진 불일치가 하나 있다. vault의 DBML은
`ck_direction_post_answers_read_at`을 선언하지 않지만, 같은 vault의 target DDL
(따라서 이를 손으로 옮긴 `V2__…sql`)은 이 제약을 선언한다. 이는 vault 원본 자체의
내부 일관성 결함이며 V2나 이 저장소가 보관한 DBML 사본의 오류가 아니다 — 이
저장소의 DBML은 불완전한 원본을 byte-for-byte로 정확히 복사한 것이다. 대조적으로
`ck_post_recipient_skip_pending`은 vault DBML에도 선언되어 있어 이런 불일치가 없다.
2026-08-07 판에서 이 불일치는 다시 확인하지 않았다 — `V8`이 이 제약을 건드리지
않으므로 범위 밖이다.

**2026-08-08b에 새로 발견한 두 번째 불일치**: vault DBML은 `answer` 테이블에
`ck_answer_edit_count_matches_edited_at`이라는 이름의 check를 선언하지만, `V8`이
실제로 만든 제약 이름은 `ck_answer_edit_count_edited_at`이다(§12 참고, 검증 근거는
`FlywayMigrationIntegrationTest`). 검사하는 조건(`(edit_count = 0) = (edited_at IS
NULL)`)은 동일하고 이름만 다르다. 앞의 `ck_direction_post_answers_read_at` 사례와
같은 종류의 결함 — vault DBML의 표현 오류이며 `V8`이나 이 저장소가 보관한 DBML
사본의 오류가 아니다. 임의로 고치지 않고 기록만 해둔다.

## 4. 폐기된 계보

다음 파일은 중간 설계 이력이며 새 Flyway migration의 입력이 아니다.

- `001_create_direction_communication_schema.sql`
- `002_add_topic_generation_schema.sql`
- `003_add_region_code_master.sql`
- `004_add_user_demographic.sql`

이 계보를 이어서 실행하거나 Flyway baseline으로 이름만 바꾸는 작업은 금지한다.

## 5. Baseline summary

V1~V9(2026-08-08) 전체를 반영한다. `V7`(#81, `device_credential`)과 `V8`(#78, 답변
열람 범위 확대)은 서로 다른 테이블을 다뤄 내용은 겹치지 않지만, `V8`이 원래 `V7`로
작성됐다가 `V7` 번호 충돌로 재번호된 이력이 있다 — Flyway 카탈로그 카운트에는 영향이
없다. 표는 `FlywayMigrationIntegrationTest`의
`EXPECTED_TABLES`/`EXPECTED_INDEXES`/`EXPECTED_FUNCTIONS`/`EXPECTED_TRIGGERS`와
`catalogMatchesApprovedManifest()`의 `countConstraints` assertion으로 검증된 값과
일치한다.

| Object | Count | Notes |
| --- | ---: | --- |
| DBML enums | 28 | SQL에서는 `VARCHAR + CHECK`로 표현. `V3`~`V7`(운영자 인증, Spring Session, 기기 자격증명)은 vault DBML이 다루는 범위 밖이라 이 수치에 영향이 없다 |
| Tables | 33 | 모든 테이블에 논리 PK 존재. `V5`가 `operator_credential`, `V6`이 `spring_session`/`spring_session_attributes`, `V7`이 `device_credential`, `V26`이 `notification_user_setting`을 추가 |
| Primary keys | 33 | 29개는 단일 컬럼 inline, 4개는 명시적으로 이름 붙인 복합 PK(`pk_user_block`, `pk_notification_preference`, `pk_post_reaction`, `pk_answer_reaction`) |
| Foreign keys | 53 | named `fk_*` constraints. `V5`가 `fk_operator_credential_user`, `V6`이 `spring_session_attributes_fk`, `V7`이 `fk_device_credential_user`, `V9`가 `fk_user_account_country`, `V26`이 `fk_notification_user_setting_user`를 추가 |
| Unique constraints | 21 | named `uq_*` constraints. `V5`가 `uq_user_account_id_role`, `uq_operator_credential_login_id`, `V9`가 `uq_region_code_code_level`을 추가. `V7`은 named unique 제약이 아니라 `CREATE UNIQUE INDEX` 2개를 추가해 이 수치에 영향이 없다 |
| Unique indexes | 12 | `CREATE UNIQUE INDEX`로 만든 것만 센다(named unique 테이블 제약이 만드는 인덱스는 위 "Unique constraints"에서 센다). `V6`이 `spring_session_ix1`, `V7`이 `uq_device_credential_secret`/`uq_active_device_installation`, `V12`가 `uq_outbox_event_direction_matching_round`를 추가 |
| Check constraints | 114 | named `ck_*` constraints. `V3`이 추가한 `ck_user_account_password_hash`는 `V5`가 제거해 순증감 없음. `V5`가 operator_credential 관련 4개, `V7`이 device_credential 관련 4개, `V8`이 6개(방향·거리·수정 이력 컬럼), `V9`가 USER 국가 필수·국가 코드 형식 2개를 추가했다. `V26`은 `notification_user_setting`에 quiet 3값·동일 시각 금지 CHECK 2개를 추가하고 `ck_notification_preference_quiet_hours`를 제거해 순증 1이다 |
| Non-unique indexes | 47 | GiST, partial, sort-order index 포함. `V6`이 `spring_session_ix2`, `spring_session_ix3`, `V7`이 `device_credential_user_idx`, `V9`가 `user_account_country_idx`, `V12`가 `outbox_event_claim_idx`를 추가 |
| Functions | 11 | trigger support functions. `V8`이 `enforce_answer_reaction_reactor_is_sender`를 `enforce_answer_reaction_reactor_can_view`로 교체(개수 불변) |
| Triggers | 10 | 2 regular + 8 constraint triggers. `V8`이 `ct_answer_reaction_reactor_is_sender`를 `ct_answer_reaction_reactor_can_view`로 교체(개수 불변) |
| Extensions | 1 | `postgis` |

"Unique indexes"(12) + "Non-unique indexes"(47) = 59이며, `uq_user_account_id_role`/
`uq_operator_credential_login_id`가 만드는 인덱스 2개는 "Unique constraints"에서만
센다. `FlywayMigrationIntegrationTest`의 `EXPECTED_INDEXES.hasSize(62)`는 이 3개를
포함해 세므로(pg_indexes catalog는 제약이 만든 인덱스와 `CREATE INDEX`로 만든 인덱스를
구분하지 않는다) 62 = 59 + 3다. 두 표가 다른 숫자를 보여주는 것은 오류가 아니라
분류 기준의 차이다.

## 5.1 Issue #115 비동기 매칭 delta (V12)

아래 항목은 V1~V9 baseline 이후 Issue #115에서 추가된 논리·물리 계약이다. 이
문서는 현재 작업 브랜치의 `V12__add_direction_matching_outbox_contract.sql`과
DBML/ERD를 대조해 기록하며, 실제 PostgreSQL catalog 적용 여부는 통합 테스트에서
확정한다. lease duration과 retry backoff의 숫자값은 application configuration의
책임이므로 manifest에 기록하지 않는다.

| 대상 | 계약 |
| --- | --- |
| `direction_post.request_fingerprint` | `VARCHAR(80)`, legacy 행의 `NULL`을 허용한다. 새 제출은 `v1:SHA-256` fingerprint를 저장하고, 기존 행은 첫 idempotency 재시도에서 저장된 의도를 복원할 수 있을 때 lazy backfill한다. 복원할 수 없는 legacy 행은 기존 결과를 반환하고 reconciliation 대상으로 남긴다. |
| `outbox_event.match_round` | `aggregate_type = 'DIRECTION_POST'`이고 `event_type = 'RECIPIENT_MATCH_REQUESTED'`인 행에만 필수다. 초기 매칭은 `1`이며 retry/reclaim은 round를 증가시키지 않는다. 별도 `matching_job` 테이블 없이 이 Outbox row 자체를 matching job으로 취급한다. |
| `uq_outbox_event_direction_matching_round` | `(aggregate_id, match_round, event_type)` partial unique index. 방향글의 같은 matching round/event 작업을 한 번만 생성한다. |
| `outbox_event.lease_owner` / `lease_expires_at` / `lease_generation` | `PROCESSING`일 때 owner와 expiry가 함께 존재하고, 그 외 상태에서는 둘 다 `NULL`이다. generation은 claim/reclaim마다 증가하는 monotonic fencing token이다. stale worker 갱신은 id·PROCESSING·owner·generation·유효 lease 조건이 모두 맞을 때만 허용한다. |
| `outbox_event_claim_idx` | `(status, next_attempt_at, lease_expires_at, id)` claim index. due PENDING/FAILED와 만료된 PROCESSING을 찾는 경로이며, due 판정과 row 점유는 한 transaction에서 처리한다. |
| matching payload | `postId`, `matchRound`, `eventType`, `requestFingerprint`와 coarse 식별자만 저장한다. 정확 좌표, `PostGIS point`, WKB/GeoJSON 등 좌표를 복원할 수 있는 값은 저장하지 않는다. |

### V12 constraint/index inventory

- Check: `ck_outbox_event_match_round`, `ck_outbox_event_lease_generation`, `ck_outbox_event_lease_state`
- Partial unique index: `uq_outbox_event_direction_matching_round`
- Claim index: `outbox_event_claim_idx`
- 기존 `outbox_event_dispatch_idx`와 `uq_outbox_event_dedup`는 유지한다.

## 13. 구현 전 결정과 명시적 제외

| Topic | Issue #35 contract | Enforcement timing |
| --- | --- | --- |
| 인증·신원 매핑 | 현재 baseline은 내부 `user_account.id`만 가진다. 외부 IdP subject 매핑은 인증 Issue의 새 migration으로 추가하며 이메일을 식별자로 추정하지 않는다. | 인증 구현 전 별도 승인 |
| P04 만료 | `direction_post.expires_at`은 필수지만 기간 기본값은 두지 않는다. 서버가 승인된 제품 정책으로 계산한 절대 시각을 저장한다. | 제품 기간 확정 후 application policy |
| P07 보관·삭제 | 보관 기간, 익명화, 물리 삭제 job을 baseline에 넣지 않는다. 상태/soft-delete 필드와 FK로 참조만 보존한다. | Privacy/Security 승인 후 별도 Issue |
| `updated_at` | JPA 쓰기는 auditing, JDBC 쓰기는 SQL에서 명시적으로 갱신한다. DB default는 insert fallback이며 범용 update trigger를 추가하지 않는다. | Issues #36-#40 |
| FK 삭제 | 기준 DDL의 명시적 CASCADE/RESTRICT/SET NULL을 유지한다. 제품 경로에서 hard delete API를 만들지 않고 상태 전이를 우선한다. | P07 확정 시 재검토 |
| `region_code` seed | table과 FK만 baseline에 포함한다. 출처·버전이 승인되기 전 seed data를 넣지 않는다. | 지역 데이터 출처 승인 후 migration |
| 방향 coverage | 8개 45° half-open sector `[start, end)`와 0°/360° 정규화를 기준으로 한다. Issue #36은 고정 scheme seed를, Issue #39는 모든 경계값 테스트를 소유한다. | Issues #36, #39 |
| PostGIS extension | local/test migration은 `CREATE EXTENSION IF NOT EXISTS postgis`를 검증한다. production은 플랫폼이 extension과 migration-role 권한을 사전 준비해야 한다. | Issue #36 local/test only |

## 14. Issue handoff

- Issue #36: Flyway baseline과 빈 PostgreSQL/PostGIS 재현
- Issue #37: JPA 공통 규칙과 Account 첫 수직 슬라이스
- Issue #38: Question persistence
- Issue #39: Direction/PostGIS persistence
- Issue #40: Answer/Safety/Notification persistence
- Issue #73 (PR #81): `V7` — `device_credential` 테이블, 앱 사용자 기기 자격증명과
  액세스 토큰 발급. `main`에 먼저 merge되어 `V7` 번호를 점유했다
- Issue #78: `V8` — 2026-08-07 스키마 개정(답변 격리 폐기, ADR-0002) 반영. `answer_reaction`
  복합 PK 전환과 `ct_answer_reaction_reactor_can_view` 자격 트리거, `post_recipient`/
  `answer`의 방향·거리·수정 이력 컬럼과 백필, `uq_answer_one_per_recipient` 조건 축소.
  원래 `V7`로 작성했으나 #81과의 번호 충돌로 `V8`로 재번호했다

</details>
