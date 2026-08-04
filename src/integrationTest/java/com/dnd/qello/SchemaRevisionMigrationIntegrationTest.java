/**
 * Created at: 2026-08-05T04:08:29+09:00
 * Source scenario: TEST-PLAN-GH-54-SCHEMA-REVISION-BACKFILL-INT-001, TEST-PLAN-GH-54-SCHEMA-REVISION-BACKFILL-INT-002
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "flyway-migration"})
class SchemaRevisionMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String SCHEMA = "v2_backfill";
	private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static Flyway flywayFor(String... targets) {
		var configuration = Flyway.configure()
			.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
			.schemas(SCHEMA)
			.defaultSchema(SCHEMA)
			.locations("classpath:db/migration")
			.cleanDisabled(true);
		if (targets.length > 0) {
			configuration = configuration.target(targets[0]);
		}
		return configuration.load();
	}

	@BeforeAll
	static void applyV1Only() {
		flywayFor("1").migrate();
	}

	@Test
	@DisplayName("V2는 옛 형태 데이터를 새 제약이 받아들이는 형태로 백필한 뒤 제약을 건다")
	void backfillsLegacyRowsBeforeTighteningConstraints() {
		jdbcTemplate.execute("SET search_path TO " + SCHEMA);

		jdbcTemplate.update("INSERT INTO region_code (code, display_name, level) "
			+ "VALUES ('TEST-V2', 'V2 Test', 'COUNTRY')");
		Long senderId = insertAccount("v2-sender");
		Long recipientId = insertAccount("v2-recipient");
		Long questionId = jdbcTemplate.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'V2 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), senderId);
		Long postId = jdbcTemplate.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', 'v2-post', '글', 'TEST-V2', 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, Timestamp.from(NOW), Timestamp.from(NOW),
			Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));

		// 옛 형태 1: skip_requested_at이 없는 확정 넘김 행
		Long postRecipientId = jdbcTemplate.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg,
				 matched_region_code, matched_at, skipped_at, capacity_released_at)
			VALUES (?, ?, 'SKIPPED', 'NEAR', 10, 'TEST-V2', ?, ?, ?)
			RETURNING id
			""", Long.class, postId, recipientId, Timestamp.from(NOW),
			Timestamp.from(NOW.plusSeconds(60)), Timestamp.from(NOW.plusSeconds(60)));

		// 옛 형태 2: DAILY_QUESTION_ASSIGNED를 쓰는 알림 계열 행 3종
		// notification.outbox_event_id는 NOT NULL이고 FK가 DEFERRABLE이 아니므로
		// outbox_event를 먼저 넣어 id를 확보한 뒤 notification을 넣는다.
		Long outboxEventId = jdbcTemplate.queryForObject("""
			INSERT INTO outbox_event
				(aggregate_type, aggregate_id, event_type, dedup_key, payload, status, created_at)
			VALUES ('QUESTION_ASSIGNMENT', ?, 'DAILY_QUESTION_ASSIGNED', 'v2-legacy-outbox',
				'{}'::jsonb, 'PENDING', ?)
			RETURNING id
			""", Long.class, questionId, Timestamp.from(NOW));
		jdbcTemplate.update("""
			INSERT INTO notification_preference (notification_type, user_id, enabled)
			VALUES ('DAILY_QUESTION_ASSIGNED', ?, true)
			""", recipientId);
		jdbcTemplate.update("""
			INSERT INTO notification
				(recipient_id, outbox_event_id, notification_type, dedup_key, status, created_at)
			VALUES (?, ?, 'DAILY_QUESTION_ASSIGNED', 'v2-legacy-notification', 'UNREAD', ?)
			""", recipientId, outboxEventId, Timestamp.from(NOW));

		flywayFor().migrate();

		assertThat(jdbcTemplate.queryForObject(
			"SELECT skip_requested_at = skipped_at FROM post_recipient WHERE id = ?",
			Boolean.class, postRecipientId)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM notification_preference WHERE notification_type = 'QUESTION_RECOMMENDED'",
			Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM notification WHERE notification_type = 'QUESTION_RECOMMENDED'",
			Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE event_type = 'QUESTION_RECOMMENDED'",
			Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM notification WHERE notification_type = 'DAILY_QUESTION_ASSIGNED'",
			Integer.class)).isZero();
	}

	@Test
	@DisplayName("CHECK 정의가 새 값 집합을 담고 옛 값을 더 이상 허용하지 않는다")
	void checkDefinitionsCarryTheNewValueSets() {
		assertThat(constraintDefinition("ck_notification_type"))
			.contains("QUESTION_RECOMMENDED")
			.contains("ANSWER_REACTED")
			.doesNotContain("DAILY_QUESTION_ASSIGNED");
		assertThat(constraintDefinition("ck_notification_preference_type"))
			.contains("QUESTION_RECOMMENDED")
			.doesNotContain("DAILY_QUESTION_ASSIGNED");
		assertThat(constraintDefinition("ck_outbox_event_event_type"))
			.contains("QUESTION_RECOMMENDED")
			.contains("SKIP_CONFIRMATION_DUE")
			.doesNotContain("DAILY_QUESTION_ASSIGNED");
		assertThat(constraintDefinition("ck_post_recipient_status"))
			.contains("SKIP_PENDING");
		assertThat(constraintDefinition("ck_recipient_receive_state_active_count"))
			.contains("50");
	}

	private Long insertAccount(String nickname) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', 'TEST-V2', 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, nickname);
	}

	private String constraintDefinition(String constraintName) {
		List<String> definitions = jdbcTemplate.queryForList("""
			SELECT pg_get_constraintdef(pc.oid)
			FROM pg_constraint pc
			JOIN pg_namespace n ON n.oid = pc.connamespace
			WHERE n.nspname = 'public' AND pc.conname = ?
			""", String.class, constraintName);
		assertThat(definitions).as(constraintName).hasSize(1);
		return definitions.get(0);
	}
}
