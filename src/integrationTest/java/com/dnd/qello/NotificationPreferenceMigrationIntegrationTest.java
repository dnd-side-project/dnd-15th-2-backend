/**
 * Created at: 2026-08-21T17:25:00+09:00
 * Source scenario: TEST-PLAN-GH-178-NOTIFICATION-PREFERENCES-INT-001 through INT-003
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "flyway-migration"})
class NotificationPreferenceMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();
	private static final Map<String, Boolean> ENABLED_BY_TYPE = enabledByType();

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String schemaName;
	private String regionCode;

	@BeforeEach
	void setUp() {
		int sequence = SCHEMA_SEQUENCE.incrementAndGet();
		schemaName = "notification_preference_v25_" + sequence;
		regionCode = "NPV25R" + sequence;
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
	}

	@Test
	@DisplayName("V25는 미배포 quiet 값을 버리고 종류별 enabled 값은 그대로 보존한다")
	void preservesEnabledAndDropsLegacyQuietValues() {
		migrateTo("24");
		long userId = insertUser("migration-user");

		insertPreference(userId, "ANSWER_RECEIVED", false, "22:00", "07:00");
		insertPreference(userId, "ANSWER_REACTED", true, "21:00", "06:00");
		insertPreference(userId, "DIRECTION_POST_RECEIVED", false, "23:00", "08:00");
		insertPreference(userId, "REPORT_RESOLVED", true, "20:00", "05:00");
		insertPreference(userId, "QUESTION_PROPOSAL_REVIEWED", false, "19:00", "04:00");
		insertPreference(userId, "QUESTION_RECOMMENDED", true, "18:00", "03:00");

		MigrateResult result = migrateToLatest();

		assertThat(result.migrationsExecuted).isEqualTo(1);
		for (Map.Entry<String, Boolean> entry : ENABLED_BY_TYPE.entrySet()) {
			assertThat(enabled(userId, entry.getKey())).isEqualTo(entry.getValue());
		}
		assertThat(tableExists("notification_user_setting")).isTrue();
		assertThat(columnExists("notification_preference", "quiet_start")).isFalse();
		assertThat(columnExists("notification_preference", "quiet_end")).isFalse();
		assertThat(constraintExists("ck_notification_preference_quiet_hours")).isFalse();
		assertThat(count("notification_user_setting")).isZero();
	}

	@Test
	@DisplayName("V25는 사용자 설정 FK와 quiet 삼중값 CHECK를 만들고 재실행할 migration이 없다")
	void createsUserSettingContractAndIsFullyApplied() {
		migrateToLatest();
		long validUserId = insertUser("valid-user");
		long sameTimeUserId = insertUser("same-time-user");
		long partialQuietUserId = insertUser("partial-quiet-user");

		assertThat(tableExists("notification_user_setting")).isTrue();
		assertThat(constraintExists("notification_user_setting_pkey")).isTrue();
		assertThat(constraintExists("pk_notification_preference")).isTrue();
		assertThat(constraintDefinition("notification_user_setting_pkey"))
			.contains("PRIMARY KEY (user_id)");
		assertThat(constraintDefinition("pk_notification_preference"))
			.contains("PRIMARY KEY (notification_type, user_id)");
		assertThat(columnExists("notification_user_setting", "push_enabled")).isTrue();
		assertThat(columnExists("notification_user_setting", "quiet_start")).isTrue();
		assertThat(columnExists("notification_user_setting", "quiet_end")).isTrue();
		assertThat(columnExists("notification_user_setting", "quiet_zone_id")).isTrue();
		assertThat(constraintDefinition("fk_notification_user_setting_user"))
			.contains("FOREIGN KEY (user_id)")
			.contains("user_account(id)")
			.contains("ON DELETE CASCADE");
		assertThat(constraintDefinition("ck_notification_user_setting_quiet_hours"))
			.contains("num_nonnulls")
			.contains("quiet_zone_id");
		assertThat(constraintDefinition("ck_notification_user_setting_distinct_quiet_hours"))
			.contains("quiet_start")
			.contains("quiet_end");
		assertThat(columnExists("notification_preference", "quiet_start")).isFalse();
		assertThat(columnExists("notification_preference", "quiet_end")).isFalse();

		insertUserSetting(validUserId, true, "22:00", "07:00", "Asia/Seoul");
		assertThat(count("notification_user_setting")).isEqualTo(1);

		assertThatThrownBy(() -> insertUserSetting(sameTimeUserId, true, "22:00", "22:00", "Asia/Seoul"))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertUserSetting(partialQuietUserId, true, "22:00", "07:00", null))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(migrateToLatest().migrationsExecuted).isZero();
	}

	private long insertUser(String nickname) {
		jdbcTemplate.update("""
			INSERT INTO %s.region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""".formatted(schemaName));
		jdbcTemplate.update("""
			INSERT INTO %s.region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Migration Region', 'REGION')
			ON CONFLICT (code, level) DO NOTHING
			""".formatted(schemaName), regionCode);
		return jdbcTemplate.queryForObject("""
			INSERT INTO %s.user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""".formatted(schemaName), Long.class, regionCode, nickname);
	}

	private void insertPreference(long userId, String notificationType, boolean enabled, String quietStart, String quietEnd) {
		jdbcTemplate.update("""
			INSERT INTO %s.notification_preference
				(notification_type, user_id, enabled, quiet_start, quiet_end)
			VALUES (?, ?, ?, CAST(? AS time), CAST(? AS time))
			""".formatted(schemaName), notificationType, userId, enabled, quietStart, quietEnd);
	}

	private void insertUserSetting(long userId, boolean pushEnabled, String quietStart, String quietEnd, String quietZoneId) {
		jdbcTemplate.update("""
			INSERT INTO %s.notification_user_setting
				(user_id, push_enabled, quiet_start, quiet_end, quiet_zone_id)
			VALUES (?, ?, CAST(? AS time), CAST(? AS time), ?)
			""".formatted(schemaName), userId, pushEnabled, quietStart, quietEnd, quietZoneId);
	}

	private boolean enabled(long userId, String notificationType) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
			SELECT enabled
			FROM %s.notification_preference
			WHERE user_id = ? AND notification_type = ?
			""".formatted(schemaName), Boolean.class, userId, notificationType));
	}

	private long count(String tableName) {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM " + schemaName + "." + tableName,
			Long.class);
	}

	private boolean tableExists(String tableName) {
		return jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.tables
			WHERE table_schema = ? AND table_name = ?
			""", Integer.class, schemaName, tableName) == 1;
	}

	private boolean columnExists(String tableName, String columnName) {
		return jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.columns
			WHERE table_schema = ? AND table_name = ? AND column_name = ?
			""", Integer.class, schemaName, tableName, columnName) == 1;
	}

	private boolean constraintExists(String constraintName) {
		return jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM pg_constraint pc
			JOIN pg_namespace n ON n.oid = pc.connamespace
			WHERE n.nspname = ? AND pc.conname = ?
			""", Integer.class, schemaName, constraintName) == 1;
	}

	private String constraintDefinition(String constraintName) {
		List<String> definitions = jdbcTemplate.queryForList("""
			SELECT pg_get_constraintdef(pc.oid)
			FROM pg_constraint pc
			JOIN pg_namespace n ON n.oid = pc.connamespace
			WHERE n.nspname = ? AND pc.conname = ?
			""", String.class, schemaName, constraintName);
		assertThat(definitions).as(constraintName).hasSize(1);
		return definitions.get(0);
	}

	private MigrateResult migrateTo(String version) {
		return flywayForSchema(schemaName, version).migrate();
	}

	private MigrateResult migrateToLatest() {
		return flywayForSchema(schemaName).migrate();
	}

	private static Flyway flywayForSchema(String schemaName, String... targets) {
		var configuration = Flyway.configure()
			.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
			.schemas(schemaName)
			.defaultSchema(schemaName)
			.locations("classpath:db/migration")
			.cleanDisabled(true);
		if (targets.length > 0) {
			configuration = configuration.target(targets[0]);
		}
		return configuration.load();
	}

	private static Map<String, Boolean> enabledByType() {
		Map<String, Boolean> values = new LinkedHashMap<>();
		values.put("ANSWER_RECEIVED", false);
		values.put("ANSWER_REACTED", true);
		values.put("DIRECTION_POST_RECEIVED", false);
		values.put("REPORT_RESOLVED", true);
		values.put("QUESTION_PROPOSAL_REVIEWED", false);
		values.put("QUESTION_RECOMMENDED", true);
		return values;
	}

}
