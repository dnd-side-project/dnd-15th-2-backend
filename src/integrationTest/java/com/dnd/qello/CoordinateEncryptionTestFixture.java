/**
 * Created at: 2026-11-24T17:42:01Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-INT-001 through INT-010
 */
package com.dnd.qello;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

final class CoordinateEncryptionTestFixture {

	static final Instant NOW = Instant.parse("2026-11-24T00:00:00Z");

	private CoordinateEncryptionTestFixture() {
	}

	static Database database() {
		var postgres = PostgisContainerIntegrationTestSupport.postgres;
		String name = "coordinate_" + UUID.randomUUID().toString().replace("-", "");
		var admin = new JdbcTemplate(new DriverManagerDataSource(
				postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
		admin.execute("CREATE DATABASE " + name);
		String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name);
		var source = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
		return new Database(name, source, new JdbcTemplate(source), admin);
	}

	static long user(JdbcTemplate jdbc) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', 'KR', 'ko-KR', 'UTC', ?) RETURNING id
				""", Long.class, "coordinate-" + UUID.randomUUID());
	}

	static long post(JdbcTemplate jdbc, long sender, String status) {
		long question = jdbc.queryForObject(
				"""
						INSERT INTO approved_question
							(source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
						VALUES ('OPERATOR', 'ACTIVE', 'coordinate fixture', 'TEXT', ?, ?, ?, ?) RETURNING id
						""",
				Long.class, Timestamp.from(NOW.minusSeconds(3600)),
				Timestamp.from(NOW.plusSeconds(86400)), Timestamp.from(NOW.minusSeconds(3600)), sender);
		return jdbc.queryForObject("""
				INSERT INTO direction_post
					(sender_id, approved_question_id, status, idempotency_key, body_text, coarse_region_code,
					 moderation_status, submitted_at, published_at, expires_at, deleted_at)
				VALUES (?, ?, ?, ?, 'coordinate fixture', 'KR', 'PASSED', ?, ?, ?, ?) RETURNING id
				""", Long.class, sender, question, status, UUID.randomUUID().toString(), Timestamp.from(NOW),
				"ACTIVE".equals(status) ? Timestamp.from(NOW) : null, Timestamp.from(NOW.plusSeconds(3600)),
				"DELETED".equals(status) ? Timestamp.from(NOW) : null);
	}

	static void oldAudience(JdbcTemplate jdbc, long postId, boolean precise, String cell) {
		jdbc.update("""
				INSERT INTO post_audience
					(post_id, direction_scheme_id, selected_segment_key, center_bearing_deg, angular_width_deg,
					 min_distance_m, max_distance_m, origin_position, origin_cell_id, snapshotted_at)
				SELECT ?, id, 'N', 0, 45, 0, 20100000,
					CASE WHEN ? THEN ST_SetSRID(ST_MakePoint(20, 10), 4326)::geography ELSE NULL END, ?, ?
				FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'
				""", postId, precise, cell, Timestamp.from(NOW));
	}

	static void oldPresence(JdbcTemplate jdbc, long userId, boolean precise, String cell) {
		jdbc.update(
				"""
						INSERT INTO active_user_presence
							(user_id, position, coarse_cell_id, coarse_region_code, accuracy_m, receive_allowed, location_at, expires_at)
						VALUES (?, CASE WHEN ? THEN ST_SetSRID(ST_MakePoint(20, 10), 4326)::geography ELSE NULL END,
							?, 'KR', 1, TRUE, ?, ?)
						""",
				userId, precise, cell, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(86400)));
	}

	record Database(String name, DriverManagerDataSource source, JdbcTemplate jdbc, JdbcTemplate admin)
			implements
				AutoCloseable {

		Flyway flyway(String target) {
			var configuration = Flyway.configure().dataSource(source).cleanDisabled(true);
			if (target != null) {
				configuration.target(target);
			}
			return configuration.load();
		}

		@Override
		public void close() {
			admin.execute("DROP DATABASE " + name + " WITH (FORCE)");
		}
	}
}
