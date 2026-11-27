/**
 * Created at: 2026-11-24T17:42:01Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-001 through INT-004
 */
package com.dnd.qello;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CoordinateMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	@Test
	@DisplayName("INT-001: 빈 DB에서 V35를 한 번 적용하고 재실행과 checksum 검증이 성공한다")
	void migratesEmptyDatabaseExactlyOnce() {
		try (var database = CoordinateEncryptionTestFixture.database()) {
			var flyway = database.flyway(null);
			assertThat(flyway.migrate().migrationsExecuted).isEqualTo(35);
			flyway.validate();
			assertThat(flyway.migrate().migrationsExecuted).isZero();
			assertThat(database.jdbc().queryForObject("""
					SELECT count(*) FROM flyway_schema_history WHERE version = '35' AND success
					""", Integer.class)).isEqualTo(1);
			assertCiphertextCatalog(database.jdbc());
		}
	}

	@Test
	@DisplayName("INT-002: 정확 presence만 삭제하고 원점을 잃은 미완료 글만 MATCH_FAILED로 닫는다")
	void discardsOnlyApprovedPlaintextAndPreservesMetadata() {
		try (var database = CoordinateEncryptionTestFixture.database()) {
			database.flyway("34").migrate();
			JdbcTemplate jdbc = database.jdbc();
			List<Map<String, Object>> oldChecksums = jdbc.queryForList(
					"SELECT version, checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
			long cellOnly = seedCoordinatesAndPosts(jdbc);
			seedDeliveryHistory(jdbc, cellOnly);
			var recipientsBefore = jdbc.queryForList("SELECT * FROM post_recipient ORDER BY id");
			var slotsBefore = jdbc.queryForList("SELECT * FROM recipient_receive_state ORDER BY user_id");
			var outboxBefore = jdbc.queryForList("SELECT * FROM outbox_event ORDER BY id");
			assertThat(recipientsBefore).hasSize(1);
			assertThat(slotsBefore).hasSize(1);
			assertThat(outboxBefore).hasSize(1);
			List<Map<String, Object>> audienceBefore = jdbc.queryForList(
					"""
							SELECT post_id, direction_scheme_id, selected_segment_key, center_bearing_deg, angular_width_deg,
								min_distance_m, max_distance_m, origin_cell_id, snapshotted_at FROM post_audience ORDER BY post_id
							""");
			List<Map<String, Object>> postsBefore = jdbc.queryForList("""
					SELECT id, sender_id, body_text, moderation_status, expires_at, published_at, deleted_at
					FROM direction_post ORDER BY id
					""");
			database.flyway(null).migrate();

			assertDeliveryHistory(jdbc, recipientsBefore, slotsBefore, outboxBefore);
			assertThat(jdbc.queryForList("SELECT user_id FROM active_user_presence", Long.class))
					.containsExactly(cellOnly);
			assertThat(jdbc.queryForList("SELECT status FROM direction_post ORDER BY id", String.class))
					.containsExactly("MATCH_FAILED", "MATCH_FAILED", "MATCH_FAILED", "MATCH_FAILED", "ACTIVE",
							"MATCH_FAILED", "EXPIRED", "REJECTED", "HIDDEN", "DELETED", "MATCHING");
			assertThat(jdbc.queryForObject(
					"SELECT count(*) FROM post_audience WHERE origin_position_ciphertext IS NOT NULL", Integer.class))
					.isZero();
			assertThat(jdbc.queryForList(
					"""
							SELECT post_id, direction_scheme_id, selected_segment_key, center_bearing_deg, angular_width_deg,
								min_distance_m, max_distance_m, origin_cell_id, snapshotted_at FROM post_audience ORDER BY post_id
							"""))
					.isEqualTo(audienceBefore);
			assertThat(jdbc.queryForList("""
					SELECT id, sender_id, body_text, moderation_status, expires_at, published_at, deleted_at
					FROM direction_post ORDER BY id
					""")).isEqualTo(postsBefore);
			assertThat(jdbc.queryForList("""
					SELECT version, checksum FROM flyway_schema_history WHERE success AND version <> '35'
					ORDER BY installed_rank
					""")).isEqualTo(oldChecksums);
		}
	}

	@Test
	@DisplayName("INT-003: 상태 갱신 실패는 앞선 presence 삭제와 이력을 rollback하고 재시도는 한 번 성공한다")
	void rollsBackWholeMigrationWhenStateUpdateFails() {
		try (var database = CoordinateEncryptionTestFixture.database()) {
			database.flyway("34").migrate();
			JdbcTemplate jdbc = database.jdbc();
			long user = CoordinateEncryptionTestFixture.user(jdbc);
			CoordinateEncryptionTestFixture.oldPresence(jdbc, user, true, null);
			long post = CoordinateEncryptionTestFixture.post(jdbc, user, "MATCHING");
			CoordinateEncryptionTestFixture.oldAudience(jdbc, post, true, null);
			jdbc.execute("""
					CREATE FUNCTION coordinate_fail_update() RETURNS trigger LANGUAGE plpgsql AS $$
					BEGIN RAISE EXCEPTION 'synthetic migration failure'; END $$
					""");
			jdbc.execute("""
					CREATE TRIGGER coordinate_fail_update BEFORE UPDATE ON direction_post
					FOR EACH ROW EXECUTE FUNCTION coordinate_fail_update()
					""");

			assertThatThrownBy(() -> database.flyway(null).migrate()).isInstanceOf(FlywayException.class);
			assertThat(jdbc.queryForObject("SELECT count(*) FROM active_user_presence WHERE position IS NOT NULL",
					Integer.class)).isEqualTo(1);
			assertThat(jdbc.queryForObject("SELECT status FROM direction_post WHERE id = ?", String.class, post))
					.isEqualTo("MATCHING");
			assertThat(jdbc.queryForObject("""
					SELECT count(*) FROM information_schema.columns
					WHERE table_name = 'post_audience' AND column_name = 'origin_position_ciphertext'
					""", Integer.class)).isZero();
			assertThat(jdbc.queryForObject(
					"SELECT count(*) FROM flyway_schema_history WHERE version = '35'", Integer.class)).isZero();
			jdbc.execute("DROP TRIGGER coordinate_fail_update ON direction_post");
			jdbc.execute("DROP FUNCTION coordinate_fail_update()");
			assertThat(database.flyway(null).migrate().migrationsExecuted).isEqualTo(1);
			assertThat(database.flyway(null).migrate().migrationsExecuted).isZero();
		}
	}

	@Test
	@DisplayName("INT-004: 백업을 별도 DB에 복원해야만 폐기 좌표가 돌아오고 새 제약을 보존한다")
	void restoresSyntheticBackupOnlyToSeparateDatabase() throws Exception {
		try (var database = CoordinateEncryptionTestFixture.database();
				var restored = CoordinateEncryptionTestFixture.database()) {
			database.flyway("34").migrate();
			long user = CoordinateEncryptionTestFixture.user(database.jdbc());
			CoordinateEncryptionTestFixture.oldPresence(database.jdbc(), user, true, null);
			long post = CoordinateEncryptionTestFixture.post(database.jdbc(), user, "MATCHING");
			CoordinateEncryptionTestFixture.oldAudience(database.jdbc(), post, true, null);
			String backup = "/tmp/" + database.name() + ".dump";
			try {
				assertThat(postgres.execInContainer("pg_dump", "-U", postgres.getUsername(), "-d", database.name(),
						"-Fc", "-f", backup).getExitCode()).isZero();
				database.flyway(null).migrate();
				assertThat(postgres.execInContainer("pg_restore", "-U", postgres.getUsername(), "-d", restored.name(),
						"--no-owner", backup).getExitCode()).isZero();
				assertThat(restored.jdbc().queryForObject(
						"SELECT count(*) FROM active_user_presence WHERE position IS NOT NULL", Integer.class))
						.isEqualTo(1);
				assertThat(database.jdbc().queryForObject("SELECT count(*) FROM active_user_presence", Integer.class))
						.isZero();
				assertThat(database.jdbc().queryForObject(
						"SELECT origin_position_ciphertext FROM post_audience WHERE post_id = ?", byte[].class, post))
						.isNull();
				assertThatThrownBy(() -> CoordinateEncryptionTestFixture.oldPresence(
						database.jdbc(), user, true, null))
						.isInstanceOf(org.springframework.dao.DataAccessException.class);
				assertThatThrownBy(() -> database.jdbc().update("""
						INSERT INTO active_user_presence (user_id, coarse_region_code, location_at, expires_at)
						VALUES (?, 'KR', now(), now() + interval '1 hour')
						""", user)).isInstanceOf(DataIntegrityViolationException.class);
				assertThatThrownBy(() -> database.jdbc().update(
						"UPDATE post_audience SET max_distance_m = min_distance_m WHERE post_id = ?", post))
						.isInstanceOf(DataIntegrityViolationException.class);
				assertThatThrownBy(() -> database.jdbc().update(
						"UPDATE post_audience SET angular_width_deg = 0 WHERE post_id = ?", post))
						.isInstanceOf(DataIntegrityViolationException.class);
				assertThatThrownBy(() -> database.jdbc().update(
						"UPDATE post_audience SET post_id = -1 WHERE post_id = ?", post))
						.isInstanceOf(DataIntegrityViolationException.class);
			} finally {
				assertThat(postgres.execInContainer("rm", "-f", backup).getExitCode()).isZero();
			}
		}
	}

	private static void assertDeliveryHistory(JdbcTemplate jdbc, List<Map<String, Object>> recipients,
			List<Map<String, Object>> slots, List<Map<String, Object>> outbox) {
		assertThat(jdbc.queryForList("SELECT * FROM post_recipient ORDER BY id")).isEqualTo(recipients);
		assertThat(jdbc.queryForList("SELECT * FROM recipient_receive_state ORDER BY user_id")).isEqualTo(slots);
		assertThat(jdbc.queryForList("SELECT * FROM outbox_event ORDER BY id")).isEqualTo(outbox);
	}

	private static long seedCoordinatesAndPosts(JdbcTemplate jdbc) {
		long precise = CoordinateEncryptionTestFixture.user(jdbc);
		long both = CoordinateEncryptionTestFixture.user(jdbc);
		long cellOnly = CoordinateEncryptionTestFixture.user(jdbc);
		CoordinateEncryptionTestFixture.oldPresence(jdbc, precise, true, null);
		CoordinateEncryptionTestFixture.oldPresence(jdbc, both, true, "synthetic-cell");
		CoordinateEncryptionTestFixture.oldPresence(jdbc, cellOnly, false, "synthetic-cell");
		for (String status : List.of("SUBMITTED", "SAFETY_CHECKING", "MATCHING", "REVIEW_HELD",
				"ACTIVE", "MATCH_FAILED", "EXPIRED", "REJECTED", "HIDDEN", "DELETED")) {
			long postId = CoordinateEncryptionTestFixture.post(jdbc, precise, status);
			CoordinateEncryptionTestFixture.oldAudience(jdbc, postId, true, null);
		}
		long cellPost = CoordinateEncryptionTestFixture.post(jdbc, precise, "MATCHING");
		CoordinateEncryptionTestFixture.oldAudience(jdbc, cellPost, false, "synthetic-cell");
		return cellOnly;
	}

	private static void seedDeliveryHistory(JdbcTemplate jdbc, long cellOnly) {
		long activePost = jdbc.queryForObject("""
					SELECT p.id FROM direction_post p JOIN post_audience a ON a.post_id = p.id
				WHERE p.status = 'ACTIVE' AND a.origin_position IS NOT NULL
					""", Long.class);
		jdbc.update("""
					INSERT INTO post_recipient
					(post_id, recipient_id, distance_band, matched_bearing_deg, inbound_bearing_deg,
						distance_m, matched_region_code, matched_at)
				VALUES (?, ?, 'synthetic-band', 10, 190, 12345, 'KR', '2026-11-24T00:00:00Z')
				""", activePost, cellOnly);
		jdbc.update(
				"""
						INSERT INTO recipient_receive_state
							(user_id, active_unhandled_count, recent_received_count, recent_window_started_at, last_received_at)
						VALUES (?, 1, 3, '2026-11-24T00:00:00Z', '2026-11-24T00:00:00Z')
						""",
				cellOnly);
		jdbc.update(
				"""
							INSERT INTO outbox_event
							(aggregate_type, aggregate_id, event_type, dedup_key, match_round, payload, status, created_at, processed_at)
							VALUES ('DIRECTION_POST', ?, 'RECIPIENT_MATCH_REQUESTED', 'synthetic-preserved-history',
							1, jsonb_build_object('postId', ?, 'matchRound', 1, 'eventType', 'RECIPIENT_MATCH_REQUESTED'),
							'PROCESSED', '2026-11-24T00:00:00Z', '2026-11-24T00:00:00Z')
						""",
				activePost, activePost);
	}

	private static void assertCiphertextCatalog(JdbcTemplate jdbc) {
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name IN ('active_user_presence', 'post_audience')
					AND column_name IN ('position', 'origin_position')
				""", String.class)).isEmpty();
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND data_type = 'bytea'
					AND column_name IN ('position_ciphertext', 'origin_position_ciphertext')
				""", String.class)).containsExactlyInAnyOrder("position_ciphertext", "origin_position_ciphertext");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM pg_indexes WHERE indexname = 'active_user_presence_position_gix'
				""", Integer.class)).isZero();
	}
}
