# Direction Communication Schema Manifest

> GitHub Issue: #35
>
> Snapshot date: 2026-08-03
>
> Status: review candidate — 사용자 검토 전에는 구현 기준으로 승인된 것으로 간주하지 않는다.

## 1. 목적

이 문서는 방향 소통 DBML, 설명 ERD, 독립 실행형 기준 DDL의 출처와
동기화 지점을 고정한다. Issue #36부터는 이 manifest와 ADR을 검토 기준으로
사용한다.

## 2. 권위와 변경 순서

1. 제품·논리 스키마 변경은 `direction_communication.dbml`에 먼저 반영한다.
2. Issue #36에서 생성할 Flyway migration만 실행 DB schema를 변경한다.
3. `DIRECTION_COMMUNICATION_ERD.md`는 DBML과 migration의 설명 문서다.
4. 이미 적용된 Flyway migration은 수정하지 않고 새 versioned migration을 추가한다.
5. DBML, migration, ERD가 충돌하면 임의로 DDL만 고치지 않는다. DBML 변경과
   정책 근거를 먼저 리뷰한 뒤 새 migration으로 반영한다.

## 3. Source snapshot

| Artifact | Repository path or source | SHA-256 | Role |
| --- | --- | --- | --- |
| DBML | `docs/product/data-model/direction_communication.dbml` | `75aa29875e094db8b72c1346eb30e3b88f93e5b4e5ec67b212b8ccffeec0823b` | logical schema source |
| ERD | `docs/product/data-model/DIRECTION_COMMUNICATION_ERD.md` | `5f5f532ec6de361fb2060ca4365664877823aeff0b7c36bab98256f3cadbf447` | explanatory contract |
| standalone DDL | source workspace `docs/sql/direction_communication_ddl.sql` | `cc93ba87aa5999bdd48589b63fa4da4e383270626fb36ecb7adac482ed3d95a7` | migration authoring reference only |

원본 DBML과 ERD는 byte-for-byte로 복사되어 위 checksum과 일치한다. 독립 DDL은
이 Issue에서 실행하거나 migration 경로에 복사하지 않는다. 원본 문서에는
PostgreSQL 16.4 + PostGIS 3.4에서 실행 검증했다고 기록되어 있지만, 이는
Issue #35의 실행 증거가 아니다. 빈 DB 재현은 Issue #36에서 다시 검증한다.

## 4. 폐기된 계보

다음 파일은 중간 설계 이력이며 새 Flyway migration의 입력이 아니다.

- `001_create_direction_communication_schema.sql`
- `002_add_topic_generation_schema.sql`
- `003_add_region_code_master.sql`
- `004_add_user_demographic.sql`

이 계보를 이어서 실행하거나 Flyway baseline으로 이름만 바꾸는 작업은 금지한다.

## 5. Baseline summary

| Object | Count | Notes |
| --- | ---: | --- |
| DBML enums | 28 | SQL에서는 `VARCHAR + CHECK`로 표현 |
| Tables | 26 | 모든 테이블에 논리 PK 존재 |
| Primary keys | 26 | 24개는 inline, 2개는 named composite PK |
| Foreign keys | 45 | named `fk_*` constraints |
| Unique constraints | 18 | named `uq_*` constraints |
| Unique indexes | 7 | partial/conditional uniqueness 포함 |
| Check constraints | 95 | named `ck_*` constraints |
| Non-unique indexes | 40 | GiST, partial, sort-order index 포함 |
| Functions | 10 | trigger support functions |
| Triggers | 9 | 2 regular + 7 constraint triggers |
| Extensions | 1 | `postgis` |

## 6. Table inventory

- `region_code`
- `user_account`
- `user_private_attribute`
- `active_user_presence`
- `recipient_receive_state`
- `question_proposal`
- `question_proposal_review`
- `approved_question`
- `question_assignment_cycle`
- `question_assignment`
- `direction_scheme`
- `direction_segment`
- `media_asset`
- `direction_post`
- `post_audience`
- `post_recipient`
- `answer`
- `media_attachment`
- `user_block`
- `report`
- `moderation_review`
- `push_device`
- `notification_preference`
- `outbox_event`
- `notification`
- `notification_delivery`

## 7. Function inventory

- `enforce_question_text_immutability`
- `enforce_direction_post_question_active`
- `enforce_post_recipient_not_sender`
- `enforce_post_recipient_capacity_release`
- `assert_post_has_content`
- `assert_answer_has_content`
- `enforce_post_has_content`
- `enforce_answer_has_content`
- `enforce_media_attachment_preserves_content`
- `enforce_media_status_preserves_content`

## 8. Trigger inventory

- `tr_approved_question_text_immutable`
- `tr_question_proposal_text_immutable_after_submit`
- `ct_direction_post_question_active`
- `ct_post_recipient_not_sender`
- `ct_post_recipient_capacity_release`
- `ct_direction_post_has_content`
- `ct_answer_has_content`
- `ct_media_attachment_preserves_content`
- `ct_media_status_preserves_content`

## 9. Index inventory

- `uq_media_attachment_post_order`
- `uq_media_attachment_answer_order`
- `region_code_parent_idx`
- `active_user_presence_position_gix`
- `active_user_presence_expiry_idx`
- `approved_question_active_idx`
- `uq_direction_scheme_active`
- `user_account_region_idx`
- `active_user_presence_region_idx`
- `question_proposal_proposer_idx`
- `question_proposal_review_reviewer_idx`
- `approved_question_approver_idx`
- `media_asset_owner_idx`
- `direction_post_question_idx`
- `direction_post_region_idx`
- `post_audience_segment_idx`
- `post_recipient_region_idx`
- `answer_region_idx`
- `report_target_user_idx`
- `report_direction_post_idx`
- `report_answer_idx`
- `report_queue_idx`
- `moderation_review_report_idx`
- `moderation_review_reviewer_idx`
- `push_device_user_idx`
- `notification_preference_user_idx`
- `notification_outbox_idx`
- `notification_post_idx`
- `notification_answer_idx`
- `notification_delivery_device_idx`
- `question_proposal_review_queue_idx`
- `question_proposal_review_history_idx`
- `question_assignment_history_idx`
- `direction_post_sender_idx`
- `direction_post_expiry_idx`
- `post_recipient_inbox_idx`
- `post_recipient_capacity_idx`
- `recipient_receive_selection_idx`
- `answer_recipient_idx`
- `user_block_reverse_idx`
- `uq_open_report_user`
- `uq_open_report_post`
- `uq_open_report_answer`
- `uq_active_push_token`
- `outbox_event_dispatch_idx`
- `notification_inbox_idx`
- `notification_delivery_dispatch_idx`

## 10. Foreign-key constraint inventory

- `fk_region_code_parent`
- `fk_user_account_region`
- `fk_user_private_attribute_user`
- `fk_active_user_presence_user`
- `fk_active_user_presence_region`
- `fk_recipient_receive_state_user`
- `fk_question_proposal_proposer`
- `fk_question_proposal_review_proposal`
- `fk_question_proposal_review_reviewer`
- `fk_approved_question_source_proposal`
- `fk_approved_question_approver`
- `fk_question_assignment_cycle_user`
- `fk_question_assignment_cycle`
- `fk_question_assignment_question`
- `fk_direction_segment_scheme`
- `fk_media_asset_owner`
- `fk_direction_post_sender`
- `fk_direction_post_question`
- `fk_direction_post_region`
- `fk_post_audience_post`
- `fk_post_audience_segment`
- `fk_post_recipient_post`
- `fk_post_recipient_user`
- `fk_post_recipient_region`
- `fk_answer_recipient_author`
- `fk_answer_region`
- `fk_media_attachment_asset_owner`
- `fk_media_attachment_post_owner`
- `fk_media_attachment_answer_owner`
- `fk_user_block_blocker`
- `fk_user_block_blocked`
- `fk_report_reporter`
- `fk_report_target_user`
- `fk_report_direction_post`
- `fk_report_answer`
- `fk_moderation_review_report`
- `fk_moderation_review_reviewer`
- `fk_push_device_user`
- `fk_notification_preference_user`
- `fk_notification_recipient`
- `fk_notification_outbox`
- `fk_notification_post`
- `fk_notification_answer`
- `fk_notification_delivery_notification`
- `fk_notification_delivery_device`

## 11. Unique-constraint inventory

- `uq_approved_question_source_proposal`
- `uq_question_assignment_cycle_user_key`
- `uq_question_assignment_cycle_question`
- `uq_question_assignment_cycle_order`
- `uq_direction_scheme_code_version`
- `uq_direction_segment_key`
- `uq_direction_segment_order`
- `uq_media_asset_id_owner`
- `uq_media_asset_storage_key`
- `uq_direction_post_idempotency`
- `uq_direction_post_id_sender`
- `uq_post_recipient_post_user`
- `uq_post_recipient_id_user`
- `uq_answer_idempotency`
- `uq_answer_id_author`
- `uq_outbox_event_dedup`
- `uq_notification_recipient_dedup`
- `uq_notification_delivery_device`

partial unique object는 위 Index inventory에 포함된다.

## 12. Check-constraint inventory

- `ck_region_code_not_self_parent`
- `ck_region_code_display_name`
- `ck_region_code_level`
- `ck_region_code_root`
- `ck_user_account_role`
- `ck_user_account_status`
- `ck_user_account_nickname`
- `ck_user_account_deleted_at`
- `ck_user_private_attribute_gender`
- `ck_user_private_attribute_age_band`
- `ck_active_user_presence_location`
- `ck_active_user_presence_accuracy`
- `ck_active_user_presence_expiry`
- `ck_recipient_receive_state_active_count`
- `ck_recipient_receive_state_recent_count`
- `ck_recipient_receive_state_last_received`
- `ck_question_proposal_status`
- `ck_question_proposal_text`
- `ck_question_proposal_submission`
- `ck_question_proposal_review_decision`
- `ck_question_proposal_review_reason`
- `ck_approved_question_source_type`
- `ck_approved_question_source`
- `ck_approved_question_status`
- `ck_approved_question_text`
- `ck_approved_question_answer_format`
- `ck_approved_question_active_range`
- `ck_approved_question_approval`
- `ck_question_assignment_cycle_status`
- `ck_question_assignment_cycle_range`
- `ck_question_assignment_display_order`
- `ck_question_assignment_viewed_at`
- `ck_question_assignment_used_at`
- `ck_direction_scheme_version`
- `ck_direction_scheme_type`
- `ck_direction_scheme_segment_count`
- `ck_direction_scheme_start_offset`
- `ck_direction_scheme_status`
- `ck_direction_segment_center`
- `ck_direction_segment_width`
- `ck_direction_segment_order`
- `ck_media_asset_status`
- `ck_media_asset_moderation`
- `ck_media_asset_size`
- `ck_media_asset_deleted_at`
- `ck_direction_post_status`
- `ck_direction_post_moderation`
- `ck_direction_post_body`
- `ck_direction_post_expiry`
- `ck_direction_post_published_at`
- `ck_direction_post_deleted_at`
- `ck_post_audience_center`
- `ck_post_audience_width`
- `ck_post_audience_distance`
- `ck_post_audience_origin`
- `ck_post_recipient_status`
- `ck_post_recipient_bearing`
- `ck_post_recipient_distance_band`
- `ck_post_recipient_timestamps`
- `ck_post_recipient_status_timestamps`
- `ck_answer_status`
- `ck_answer_moderation`
- `ck_answer_body`
- `ck_answer_bearing`
- `ck_answer_distance_band`
- `ck_answer_published_at`
- `ck_answer_deleted_at`
- `ck_media_attachment_exactly_one_target`
- `ck_media_attachment_order`
- `ck_user_block_not_self`
- `ck_user_block_release`
- `ck_report_exactly_one_target`
- `ck_report_reason`
- `ck_report_status`
- `ck_report_resolution`
- `ck_moderation_review_decision`
- `ck_moderation_review_action_type`
- `ck_push_device_platform`
- `ck_push_device_status`
- `ck_push_device_revoked_at`
- `ck_notification_preference_type`
- `ck_notification_preference_quiet_hours`
- `ck_outbox_event_aggregate_type`
- `ck_outbox_event_event_type`
- `ck_outbox_event_payload`
- `ck_outbox_event_status`
- `ck_outbox_event_attempt_count`
- `ck_outbox_event_processed_at`
- `ck_notification_type`
- `ck_notification_target`
- `ck_notification_status`
- `ck_notification_read_at`
- `ck_notification_delivery_status`
- `ck_notification_delivery_attempt_count`
- `ck_notification_delivery_sent_at`

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
