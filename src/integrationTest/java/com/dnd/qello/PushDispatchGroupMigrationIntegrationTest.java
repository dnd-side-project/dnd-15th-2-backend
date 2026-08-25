/**
 * Created at: 2026-08-25T13:06:48+09:00
 * Source scenario: TEST-PLAN-GH-180-PUSH-BUNDLING-BUDGET-INT-001
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class PushDispatchGroupMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH180-PUSH-SCHEMA";
	private static final String PREFIX = "gh180-push-schema-";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private long recipientId;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Push schema test region', 'REGION')
			ON CONFLICT (code, level) DO NOTHING
			""", REGION);
		recipientId = jdbcTemplate.queryForObject("""
			INSERT INTO user_account
				(role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, PREFIX + System.nanoTime());
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("DELETE FROM push_dispatch_group WHERE recipient_id = ?", recipientId);
		jdbcTemplate.update("DELETE FROM push_daily_budget WHERE user_id = ?", recipientId);
		jdbcTemplate.update("DELETE FROM notification WHERE recipient_id = ?", recipientId);
		jdbcTemplate.update("DELETE FROM outbox_event WHERE dedup_key LIKE ?", PREFIX + "%");
		jdbcTemplate.update("DELETE FROM user_account WHERE id = ?", recipientId);
	}

	@Test
	@DisplayName("INT-001은 허용하지 않는 push dispatch group 상태를 거절한다")
	void rejectsInvalidGroupStatus() {
		assertThatThrownBy(() -> insertGroup("invalid-status", "UNKNOWN", 0, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("INT-001은 음수 group attempt count를 거절한다")
	void rejectsNegativeGroupAttemptCount() {
		assertThatThrownBy(() -> insertGroup("negative-attempt", "PENDING", -1, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("INT-001은 general 소비량이 total 소비량을 넘는 budget을 거절한다")
	void rejectsBudgetGeneralCountGreaterThanTotal() {
		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO push_daily_budget (user_id, budget_date, consumed_total, consumed_general)
			VALUES (?, DATE '2026-08-25', 1, 2)
			""", recipientId)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("INT-001은 하나의 notification을 둘 이상의 dispatch group member로 중복 편입하지 않는다")
	void rejectsDuplicateGroupMemberNotification() {
		long notificationId = insertNotification("member-notification");
		long firstGroupId = insertGroup("first-member-group", "PENDING", 0, null);
		long secondGroupId = insertGroup("second-member-group", "PENDING", 0, null);
		jdbcTemplate.update("""
			INSERT INTO push_dispatch_group_member (group_id, notification_id)
			VALUES (?, ?)
			""", firstGroupId, notificationId);

		assertThatThrownBy(() -> jdbcTemplate.update("""
			INSERT INTO push_dispatch_group_member (group_id, notification_id)
			VALUES (?, ?)
			""", secondGroupId, notificationId)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("INT-001은 terminal group에 completed_at이 없으면 거절한다")
	void rejectsTerminalGroupWithoutCompletedAt() {
		assertThatThrownBy(() -> insertGroup("terminal-without-completion", "COMPLETED", 1, null))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long insertGroup(String suffix, String status, int attemptCount, String completedAt) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO push_dispatch_group (
				recipient_id, notification_type, aggregation_key, status, window_started_at,
				collect_until, policy_expires_at, attempt_count, next_attempt_at, completed_at)
			VALUES (?, 'ANSWER_RECEIVED', ?, ?, TIMESTAMPTZ '2026-08-25T04:00:00Z',
				TIMESTAMPTZ '2026-08-25T04:00:00Z', TIMESTAMPTZ '2026-08-25T12:00:00Z', ?,
				TIMESTAMPTZ '2026-08-25T04:00:00Z', CAST(? AS timestamptz))
			RETURNING id
			""", Long.class, recipientId, PREFIX + suffix + '-' + System.nanoTime(), status, attemptCount, completedAt);
	}

	private long insertNotification(String suffix) {
		long outboxEventId = jdbcTemplate.queryForObject("""
			INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, dedup_key, payload)
			VALUES ('ANSWER', 180, 'ANSWER_PUBLISHED', ?, '{}'::jsonb)
			RETURNING id
			""", Long.class, PREFIX + suffix + '-' + System.nanoTime());
		return jdbcTemplate.queryForObject("""
			INSERT INTO notification (recipient_id, outbox_event_id, notification_type, dedup_key)
			VALUES (?, ?, 'ANSWER_RECEIVED', ?)
			RETURNING id
			""", Long.class, recipientId, outboxEventId, PREFIX + suffix + '-' + System.nanoTime());
	}
}
