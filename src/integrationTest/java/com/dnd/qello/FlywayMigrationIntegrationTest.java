package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Created at: 2026-08-03T17:45:39+09:00
 * Source scenario: TEST-PLAN-GH-36-FLYWAY-BASELINE-INT-001 through INT-004,
 * TEST-PLAN-GH-78-SCHEMA-REVISION-V7-INT-001, TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-003,
 * TEST-PLAN-GH-88-COUNTRY-ONBOARDING-INT-004,
 * TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-001
 */
@SpringBootTest
@ActiveProfiles({"test", "flyway-migration"})
class FlywayMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final Set<String> EXPECTED_TABLES = Set.of(
		"region_code",
		"user_account",
		"user_private_attribute",
		"active_user_presence",
		"recipient_receive_state",
		"question_proposal",
		"question_proposal_review",
		"approved_question",
		"question_assignment_cycle",
		"question_assignment",
		"direction_scheme",
		"direction_segment",
		"media_asset",
		"direction_post",
		"post_audience",
		"post_recipient",
		"answer",
		"post_reaction",
		"answer_reaction",
		"media_attachment",
		"user_block",
		"report",
		"moderation_review",
		"push_device",
		"notification_preference",
		"outbox_event",
		"notification",
		"notification_delivery",
		"operator_credential",
		"spring_session",
		"spring_session_attributes",
		"device_credential"
	);

	private static final Set<String> EXPECTED_INDEXES = Set.of(
		"uq_media_attachment_post_order",
		"uq_media_attachment_answer_order",
		"region_code_parent_idx",
		"active_user_presence_position_gix",
		"active_user_presence_expiry_idx",
		"approved_question_active_idx",
		"uq_direction_scheme_active",
		"user_account_region_idx",
		"active_user_presence_region_idx",
		"question_proposal_proposer_idx",
		"question_proposal_review_reviewer_idx",
		"approved_question_approver_idx",
		"media_asset_owner_idx",
		"direction_post_question_idx",
		"direction_post_region_idx",
		"post_audience_segment_idx",
		"post_recipient_region_idx",
		"answer_region_idx",
		"report_target_user_idx",
		"report_direction_post_idx",
		"report_answer_idx",
		"report_queue_idx",
		"moderation_review_report_idx",
		"moderation_review_reviewer_idx",
		"push_device_user_idx",
		"notification_preference_user_idx",
		"notification_outbox_idx",
		"notification_post_idx",
		"notification_answer_idx",
		"notification_delivery_device_idx",
		"question_proposal_review_queue_idx",
		"question_proposal_review_history_idx",
		"question_assignment_history_idx",
		"direction_post_sender_idx",
		"direction_post_expiry_idx",
		"post_recipient_inbox_idx",
		"post_recipient_capacity_idx",
		"recipient_receive_selection_idx",
		"answer_recipient_idx",
		"user_block_reverse_idx",
		"uq_open_report_user",
		"uq_open_report_post",
		"uq_open_report_answer",
		"uq_active_push_token",
		"outbox_event_dispatch_idx",
		"uq_outbox_event_direction_matching_round",
		"outbox_event_claim_idx",
		"notification_inbox_idx",
		"notification_delivery_dispatch_idx",
		"uq_answer_one_per_recipient",
		"post_reaction_reactor_idx",
		"answer_reaction_reactor_idx",
		"uq_user_account_id_role",
		"uq_operator_credential_login_id",
		"spring_session_ix1",
		"spring_session_ix2",
		"spring_session_ix3",
		"uq_device_credential_secret",
		"uq_active_device_installation",
		"device_credential_user_idx",
		"uq_region_code_code_level",
		"user_account_country_idx"
	);

	private static final Set<String> EXPECTED_FUNCTIONS = Set.of(
		"enforce_question_text_immutability",
		"enforce_direction_post_question_active",
		"enforce_post_recipient_not_sender",
		"enforce_post_recipient_capacity_release",
		"assert_post_has_content",
		"assert_answer_has_content",
		"enforce_post_has_content",
		"enforce_answer_has_content",
		"enforce_media_attachment_preserves_content",
		"enforce_media_status_preserves_content",
		"enforce_answer_reaction_reactor_can_view"
	);

	private static final Set<String> EXPECTED_TRIGGERS = Set.of(
		"tr_approved_question_text_immutable",
		"tr_question_proposal_text_immutable_after_submit",
		"ct_direction_post_question_active",
		"ct_post_recipient_not_sender",
		"ct_post_recipient_capacity_release",
		"ct_direction_post_has_content",
		"ct_answer_has_content",
		"ct_media_attachment_preserves_content",
		"ct_media_status_preserves_content",
		"ct_answer_reaction_reactor_can_view"
	);

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("빈 PostGIS 데이터베이스의 startup에서 V1부터 V16까지 migration을 적용한다")
	void appliesAllMigrationsOnApplicationStartup() {
		Integer successfulV1 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '1' AND success
			""", Integer.class);
		Integer successfulV2 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '2' AND success
			""", Integer.class);
		Integer successfulV3 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '3' AND success
			""", Integer.class);
		Integer successfulV4 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '4' AND success
			""", Integer.class);
		Integer successfulV5 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '5' AND success
			""", Integer.class);
		Integer successfulV6 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '6' AND success
			""", Integer.class);
		Integer successfulV7 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '7' AND success
			""", Integer.class);
		Integer successfulV8 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '8' AND success
			""", Integer.class);
		Integer successfulV9 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '9' AND success
			""", Integer.class);
		Integer successfulV10 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '10' AND success
			""", Integer.class);
		Integer successfulV11 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '11' AND success
			""", Integer.class);
		Integer successfulV12 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '12' AND success
			""", Integer.class);
		Integer successfulV13 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '13' AND success
			""", Integer.class);
		Integer successfulV14 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '14' AND success
			""", Integer.class);
		Integer successfulV15 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '15' AND success
			""", Integer.class);
		Integer successfulV16 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '16' AND success
			""", Integer.class);
		String postgisVersion = jdbcTemplate.queryForObject(
			"SELECT PostGIS_Version()", String.class);

		assertThat(successfulV1).isEqualTo(1);
		assertThat(successfulV2).isEqualTo(1);
		assertThat(successfulV3).isEqualTo(1);
		assertThat(successfulV4).isEqualTo(1);
		assertThat(successfulV5).isEqualTo(1);
		assertThat(successfulV6).isEqualTo(1);
		assertThat(successfulV7).isEqualTo(1);
		assertThat(successfulV8).isEqualTo(1);
		assertThat(successfulV9).isEqualTo(1);
		assertThat(successfulV10).isEqualTo(1);
		assertThat(successfulV11).isEqualTo(1);
		assertThat(successfulV12).isEqualTo(1);
		assertThat(successfulV13).isEqualTo(1);
		assertThat(successfulV14).isEqualTo(1);
		assertThat(successfulV15).isEqualTo(1);
		assertThat(successfulV16).isEqualTo(1);
		assertThat(flyway.info().applied()).hasSize(16);
		assertThat(postgisVersion).isNotBlank();
	}

	@Test
	@DisplayName("같은 데이터베이스에 Flyway를 다시 실행해도 추가 migration을 적용하지 않는다")
	void secondMigrateIsIdempotent() {
		MigrateResult secondResult = flyway.migrate();
		Integer successfulV1 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_schema_history
			WHERE version = '1' AND success
			""", Integer.class);

		assertThat(secondResult.migrationsExecuted).isZero();
		assertThat(successfulV1).isEqualTo(1);
	}

	@Test
	@DisplayName("적용된 schema의 catalog와 고정 방향 seed가 승인 manifest와 일치한다")
	void catalogMatchesApprovedManifest() {
		assertCatalogNames("""
			SELECT table_name
			FROM information_schema.tables
			WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
			""", EXPECTED_TABLES);
		assertCatalogNames("""
			SELECT indexname
			FROM pg_indexes
			WHERE schemaname = 'public'
			""", EXPECTED_INDEXES);
		assertCatalogNames("""
			SELECT p.proname
			FROM pg_proc p
			JOIN pg_namespace n ON n.oid = p.pronamespace
			WHERE n.nspname = 'public'
			""", EXPECTED_FUNCTIONS);
		assertCatalogNames("""
			SELECT t.tgname
			FROM pg_trigger t
			JOIN pg_class c ON c.oid = t.tgrelid
			JOIN pg_namespace n ON n.oid = c.relnamespace
			WHERE n.nspname = 'public' AND NOT t.tgisinternal
			""", EXPECTED_TRIGGERS);

		List<ConstraintDescriptor> constraints = jdbcTemplate.query("""
			SELECT c.relname, pc.contype::text
			FROM pg_constraint pc
			JOIN pg_class c ON c.oid = pc.conrelid
			JOIN pg_namespace n ON n.oid = c.relnamespace
			WHERE n.nspname = 'public'
			""", (resultSet, rowNumber) -> new ConstraintDescriptor(
			resultSet.getString(1), resultSet.getString(2)));

		assertThat(countConstraints(constraints, "f")).isEqualTo(52);
		assertThat(countConstraints(constraints, "u")).isEqualTo(21);
		// V7(#81, device_credential)이 4개, V8(#78)이 ck_post_recipient_inbound_bearing,
		// ck_post_recipient_distance_m, ck_post_recipient_answers_read_at,
		// ck_answer_distance_m, ck_answer_edit_count, ck_answer_edit_count_edited_at
		// 6개를 추가해 101에서 111이 됐고, V9(#88)이 국가 CHECK 2개를 추가했다.
		assertThat(countConstraints(constraints, "c")).isEqualTo(116);
		assertThat(EXPECTED_INDEXES).hasSize(62);
		assertThat(EXPECTED_FUNCTIONS).hasSize(11);
		assertThat(EXPECTED_TRIGGERS).hasSize(10);

		List<String> answerReactionPkColumns = jdbcTemplate.queryForList("""
			SELECT a.attname
			FROM pg_constraint pc
			JOIN pg_class c ON c.oid = pc.conrelid
			JOIN unnest(pc.conkey) WITH ORDINALITY AS key_column(attnum, position) ON true
			JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = key_column.attnum
			WHERE c.relname = 'answer_reaction' AND pc.contype = 'p'
			ORDER BY key_column.position
			""", String.class);

		assertThat(answerReactionPkColumns).containsExactly("answer_id", "reactor_id");

		Integer activeScheme = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM direction_scheme
			WHERE code = 'OCTANT' AND version = 1 AND status = 'ACTIVE'
			""", Integer.class);
		Integer segmentCount = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM direction_segment segment
			JOIN direction_scheme scheme ON scheme.id = segment.scheme_id
			WHERE scheme.code = 'OCTANT' AND scheme.version = 1
			""", Integer.class);

		assertThat(activeScheme).isEqualTo(1);
		assertThat(segmentCount).isEqualTo(8);
	}

	@Test
	@DisplayName("V12는 fingerprint·매칭 round·lease fencing 컬럼과 실제 제약을 생성한다")
	void v12AddsDirectionMatchingAndLeaseCatalog() {
		assertThat(columnExists("direction_post", "request_fingerprint")).isTrue();
		assertThat(columnExists("outbox_event", "match_round")).isTrue();
		assertThat(columnExists("outbox_event", "lease_owner")).isTrue();
		assertThat(columnExists("outbox_event", "lease_expires_at")).isTrue();
		assertThat(columnExists("outbox_event", "lease_generation")).isTrue();

		Set<String> constraintNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT conname
			FROM pg_constraint
			WHERE conrelid IN ('direction_post'::regclass, 'outbox_event'::regclass)
			""", String.class));
		assertThat(constraintNames).contains(
			"ck_outbox_event_match_round",
			"ck_outbox_event_lease_generation",
			"ck_outbox_event_lease_state");

		String matchingIndex = jdbcTemplate.queryForObject(
			"SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_outbox_event_direction_matching_round'",
			String.class);
		String claimIndex = jdbcTemplate.queryForObject(
			"SELECT indexdef FROM pg_indexes WHERE indexname = 'outbox_event_claim_idx'",
			String.class);
		assertThat(matchingIndex).contains("aggregate_id", "match_round", "event_type", "DIRECTION_POST",
			"RECIPIENT_MATCH_REQUESTED");
		assertThat(claimIndex).contains("lease_expires_at", "PROCESSING");
	}

	@Test
	@DisplayName("V13은 filter_job.deadline_at·deadline scan 인덱스와 outbox_event의 확장된 CHECK 제약을 생성한다")
	void v13AddsAnswerModerationDeadlineAndOutboxContractCatalog() {
		assertThat(columnExists("filter_job", "deadline_at")).isTrue();
		Integer nullableDeadlineAt = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.columns
			WHERE table_schema = 'public' AND table_name = 'filter_job' AND column_name = 'deadline_at'
			  AND is_nullable = 'NO'
			""", Integer.class);
		assertThat(nullableDeadlineAt).isEqualTo(1);

		String deadlineScanIndex = jdbcTemplate.queryForObject(
			"SELECT indexdef FROM pg_indexes WHERE indexname = 'filter_job_deadline_scan_idx'", String.class);
		assertThat(deadlineScanIndex).contains("deadline_at", "RESOLVED");

		String aggregateTypeConstraint = jdbcTemplate.queryForObject("""
			SELECT pg_get_constraintdef(oid)
			FROM pg_constraint
			WHERE conname = 'ck_outbox_event_aggregate_type' AND conrelid = 'outbox_event'::regclass
			""", String.class);
		String eventTypeConstraint = jdbcTemplate.queryForObject("""
			SELECT pg_get_constraintdef(oid)
			FROM pg_constraint
			WHERE conname = 'ck_outbox_event_event_type' AND conrelid = 'outbox_event'::regclass
			""", String.class);
		assertThat(aggregateTypeConstraint).contains("FILTER_JOB");
		assertThat(eventTypeConstraint).contains(
			"MODERATION_EXECUTION_REQUESTED", "MODERATION_VERDICT_READY", "MODERATION_DEADLINE_ELAPSED");
	}

	@Test
	@DisplayName("V14는 filter_job.logical_attempt_count와 filter_release_retry_gate 테이블·제약을 생성한다")
	void v14AddsAnswerModerationRetryBudgetAndReleaseGateCatalog() {
		assertThat(columnExists("filter_job", "logical_attempt_count")).isTrue();
		Integer nullableLogicalAttemptCount = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.columns
			WHERE table_schema = 'public' AND table_name = 'filter_job' AND column_name = 'logical_attempt_count'
			  AND is_nullable = 'NO'
			""", Integer.class);
		assertThat(nullableLogicalAttemptCount).isEqualTo(1);

		Integer retryGateTableExists = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.tables
			WHERE table_schema = 'public' AND table_name = 'filter_release_retry_gate'
			""", Integer.class);
		assertThat(retryGateTableExists).isEqualTo(1);

		Set<String> retryGateConstraintNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT conname
			FROM pg_constraint
			WHERE conrelid = 'filter_release_retry_gate'::regclass
			""", String.class));
		assertThat(retryGateConstraintNames).contains(
			"fk_filter_release_retry_gate_release",
			"ck_filter_release_retry_gate_state",
			"ck_filter_release_retry_gate_limit",
			"ck_filter_release_retry_gate_limit_positive",
			"ck_filter_release_retry_gate_counts");
	}

	@Test
	@DisplayName("V15는 snapshot_health·probe_result·emergency_migration_history 테이블과 제약을 생성한다")
	void v15AddsSnapshotHealthAndEmergencyMigrationCatalog() {
		Set<String> newTableNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT table_name
			FROM information_schema.tables
			WHERE table_schema = 'public'
			  AND table_name IN
			      ('snapshot_health', 'snapshot_health_probe_result', 'snapshot_emergency_migration_history')
			""", String.class));
		assertThat(newTableNames).containsExactlyInAnyOrder(
			"snapshot_health", "snapshot_health_probe_result", "snapshot_emergency_migration_history");

		Set<String> snapshotHealthConstraintNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT conname
			FROM pg_constraint
			WHERE conrelid = 'snapshot_health'::regclass
			""", String.class));
		assertThat(snapshotHealthConstraintNames).contains(
			"ck_snapshot_health_status", "ck_snapshot_health_target_only_failure_count",
			"ck_snapshot_health_confirmed");

		Set<String> probeResultConstraintNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT conname
			FROM pg_constraint
			WHERE conrelid = 'snapshot_health_probe_result'::regclass
			""", String.class));
		assertThat(probeResultConstraintNames).contains(
			"fk_snapshot_health_probe_result_snapshot", "ck_snapshot_health_probe_result_type",
			"ck_snapshot_health_probe_result_classification");

		Set<String> migrationHistoryConstraintNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT conname
			FROM pg_constraint
			WHERE conrelid = 'snapshot_emergency_migration_history'::regclass
			""", String.class));
		assertThat(migrationHistoryConstraintNames).contains(
			"fk_snapshot_emergency_migration_history_snapshot",
			"fk_snapshot_emergency_migration_history_source_release",
			"fk_snapshot_emergency_migration_history_target_release",
			"ck_snapshot_emergency_migration_history_job_count",
			"ck_snapshot_emergency_migration_history_different_release");
	}

	@Test
	@DisplayName("V16은 manual_review_case 확장 컬럼과 manual_review_priority_evaluation 테이블·제약을 생성한다")
	void v16AddsManualReviewPriorityAndAuthorityCatalog() {
		assertThat(columnExists("manual_review_case", "filter_job_id")).isTrue();
		assertThat(columnExists("manual_review_case", "band")).isTrue();
		assertThat(columnExists("manual_review_case", "status")).isTrue();

		Set<String> manualReviewCaseConstraintNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT conname
			FROM pg_constraint
			WHERE conrelid = 'manual_review_case'::regclass
			""", String.class));
		assertThat(manualReviewCaseConstraintNames).contains(
			"fk_manual_review_case_job", "ck_manual_review_case_status", "ck_manual_review_case_band",
			"ck_manual_review_case_report_signal_count", "ck_manual_review_case_resolved_verdict",
			"ck_manual_review_case_resolved_fields");

		Integer evaluationTableExists = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.tables
			WHERE table_schema = 'public' AND table_name = 'manual_review_priority_evaluation'
			""", Integer.class);
		assertThat(evaluationTableExists).isEqualTo(1);

		Set<String> evaluationConstraintNames = new HashSet<>(jdbcTemplate.queryForList("""
			SELECT conname
			FROM pg_constraint
			WHERE conrelid = 'manual_review_priority_evaluation'::regclass
			""", String.class));
		assertThat(evaluationConstraintNames).contains(
			"fk_manual_review_priority_evaluation_case", "ck_manual_review_priority_evaluation_band");
	}

	@Test
	@DisplayName("transactional V2가 실패하면 변경과 성공 이력을 남기지 않는다")
	void failedMigrationIsNotRecordedAsSuccessful() {
		Flyway failingFlyway = Flyway.configure()
			.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
			.schemas("flyway_failure")
			.defaultSchema("flyway_failure")
			.locations("classpath:db/failure")
			.cleanDisabled(true)
			.load();

		assertThatThrownBy(failingFlyway::migrate)
			.isInstanceOf(FlywayException.class);

		Integer successfulV2 = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM flyway_failure.flyway_schema_history
			WHERE version = '2' AND success
			""", Integer.class);
		Integer rolledBackColumn = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.columns
			WHERE table_schema = 'flyway_failure'
			  AND table_name = 'migration_probe'
			  AND column_name = 'transient_value'
			""", Integer.class);

		assertThat(successfulV2).isZero();
		assertThat(rolledBackColumn).isZero();
	}

	private void assertCatalogNames(String sql, Set<String> expectedNames) {
		Set<String> actualNames = new HashSet<>(jdbcTemplate.queryForList(sql, String.class));
		actualNames.retainAll(expectedNames);

		assertThat(actualNames).containsExactlyInAnyOrderElementsOf(expectedNames);
	}

	private boolean columnExists(String tableName, String columnName) {
		return jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.columns
			WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
			""", Integer.class, tableName, columnName) == 1;
	}

	private long countConstraints(
		List<ConstraintDescriptor> constraints,
		String constraintType
	) {
		return constraints.stream()
			.filter(constraint -> EXPECTED_TABLES.contains(constraint.tableName()))
			.filter(constraint -> constraintType.equals(constraint.type()))
			.count();
	}

	private record ConstraintDescriptor(String tableName, String type) {
	}

}
