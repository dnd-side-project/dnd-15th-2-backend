package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Created at: 2026-08-03T17:45:39+09:00
 * Source scenario: TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-001, TEST-PLAN-GH-36-FLYWAY-BASELINE-UNIT-002,
 * TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-001, TEST-PLAN-GH-88-COUNTRY-ONBOARDING-UNIT-004,
 * TEST-PLAN-GH-115-DIRECTION-MATCHING-CONTRACT-INT-001
 */
class FlywayMigrationContractTest {

	private static final String ACCEPTED_DDL_SHA_256 =
		"cc93ba87aa5999bdd48589b63fa4da4e383270626fb36ecb7adac482ed3d95a7";
	private static final String ACCEPTED_V2_SHA_256 =
		"c8daf71f9ce75fb75b6c30ecb49a4b2d912b887a34d8f602617c2ca8a27f4e04";
	private static final String ACCEPTED_V8_SHA_256 =
		"082ed77d45bf48deaf31b9705baa979fd24633e78d3c3490a40421663eb852cb";

	@Test
	@DisplayName("V1은 승인된 독립 DDL과 동일하고 V2는 승인된 delta와 동일하다")
	void migrationsMatchAcceptedContent() throws Exception {
		ClassPathResource v1 = new ClassPathResource(
			"db/migration/V1__create_direction_communication_schema.sql");
		ClassPathResource v2 = new ClassPathResource(
			"db/migration/V2__add_reactions_and_skip_pending.sql");
		Properties scriptConfiguration = PropertiesLoaderUtils.loadProperties(
			new ClassPathResource(
				"db/migration/V1__create_direction_communication_schema.sql.conf"));

		assertThat(sha256(v1)).isEqualTo(ACCEPTED_DDL_SHA_256);
		assertThat(sha256(v2)).isEqualTo(ACCEPTED_V2_SHA_256);
		assertThat(scriptConfiguration)
			.containsEntry("executeInTransaction", "false");
		// 문자열 정렬이라 "V10"이 "V1_"보다 앞선다('0' < '_'). Flyway 자체는 버전을
		// 수치로 비교해 순서에 영향이 없지만, 이 목록은 실제 디렉터리 정렬 순서와
		// 일치해야 한다.
		assertThat(sqlMigrationNames()).containsExactly(
			"V10__create_filtering_schema.sql",
			"V11__add_filter_release_registry.sql",
			"V12__add_direction_matching_outbox_contract.sql",
			"V13__add_answer_moderation_job_deadline_and_outbox_contract.sql",
			"V14__add_answer_moderation_retry_budget_and_release_gate.sql",
			"V15__add_snapshot_health_and_emergency_migration.sql",
			"V16__add_manual_review_priority_and_authority.sql",
			"V17__add_notification_event_for_slack_manual_review.sql",
			"V18__add_appeal_case_decision_and_window.sql",
			"V19__add_report_case_and_evidence_snapshot.sql",
			"V1__create_direction_communication_schema.sql",
			"V20__add_operator_action_audit.sql",
			"V21__add_user_account_nickname_uniqueness.sql",
			"V22__add_user_account_profile_image.sql",
			"V23__add_report_reporter_answer_suppression_index.sql",
			"V24__add_notification_inbox_read_state.sql",
			"V25__add_report_case_sla_and_manual_review_link.sql",
			"V25__split_notification_user_setting.sql",
			"V2__add_reactions_and_skip_pending.sql",
			"V3__add_user_account_password_hash.sql",
			"V4__add_user_account_optimistic_lock.sql",
			"V5__add_operator_credential.sql",
			"V6__add_spring_session_tables.sql",
			"V7__add_device_credential.sql",
			"V8__widen_answer_visibility_to_recipients.sql",
			"V9__require_country_before_user_account_creation.sql");
	}

	@Test
	@DisplayName("V8은 승인된 delta와 동일하다")
	void v8MatchesAcceptedContent() throws Exception {
		ClassPathResource v8 = new ClassPathResource(
			"db/migration/V8__widen_answer_visibility_to_recipients.sql");

		assertThat(sha256(v8)).isEqualTo(ACCEPTED_V8_SHA_256);
	}

	@Test
	@DisplayName("V2 이후 delta는 script configuration 없이 기본 transaction 안에서 실행된다")
	void deltaMigrationsRunInsideTheDefaultTransaction() throws IOException {
		assertThat(scriptConfigurationNames()).containsExactly(
			"V1__create_direction_communication_schema.sql.conf");
	}

	@Test
	@DisplayName("Flyway는 migration 이름을 검증하고 clean과 자동 baseline을 금지한다")
	void flywaySafetySettingsAreEnabled() throws IOException {
		Properties properties = PropertiesLoaderUtils.loadProperties(
			new ClassPathResource("application.properties"));

		assertThat(properties)
			.containsEntry("spring.flyway.enabled", "true")
			.containsEntry("spring.flyway.locations", "classpath:db/migration")
			.containsEntry("spring.flyway.clean-disabled", "true")
			.containsEntry("spring.flyway.baseline-on-migrate", "false")
			.containsEntry("spring.flyway.validate-migration-naming", "true")
			.containsEntry("spring.session.jdbc.initialize-schema", "never");
	}

	private String sha256(ClassPathResource resource)
		throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = resource.getInputStream()) {
			return HexFormat.of().formatHex(digest.digest(input.readAllBytes()));
		}
	}

	private List<String> sqlMigrationNames() throws IOException {
		Path migrationDirectory = Path.of("src/main/resources/db/migration");
		try (var paths = Files.list(migrationDirectory)) {
			return paths
				.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".sql"))
				.sorted()
				.toList();
		}
	}

	private List<String> scriptConfigurationNames() throws IOException {
		Path migrationDirectory = Path.of("src/main/resources/db/migration");
		try (var paths = Files.list(migrationDirectory)) {
			return paths
				.map(path -> path.getFileName().toString())
				.filter(name -> name.endsWith(".conf"))
				.sorted()
				.toList();
		}
	}

}
