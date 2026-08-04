/**
 * Created at: 2026-08-05T04:08:29+09:00
 * Source scenario: TEST-PLAN-GH-54-SCHEMA-REVISION-BACKFILL-INT-001, TEST-PLAN-GH-54-SCHEMA-REVISION-BACKFILL-INT-002, TEST-PLAN-GH-54-SCHEMA-REVISION-BACKFILL-INT-003
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "flyway-migration"})
class SchemaRevisionMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String SCHEMA = "v2_backfill";
	private static final String CONFLICT_SCHEMA = "v2_backfill_conflict";
	private static final Instant NOW = Instant.parse("2026-08-04T09:00:00Z");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static Flyway flywayForSchema(String schema, String... targets) {
		var configuration = Flyway.configure()
			.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
			.schemas(schema)
			.defaultSchema(schema)
			.locations("classpath:db/migration")
			.cleanDisabled(true);
		if (targets.length > 0) {
			configuration = configuration.target(targets[0]);
		}
		return configuration.load();
	}

	private static Flyway flywayFor(String... targets) {
		return flywayForSchema(SCHEMA, targets);
	}

	@BeforeAll
	static void applyV1Only() {
		flywayFor("1").migrate();
	}

	// 세 번째 시나리오(post_recipient당 살아있는 답변 2건 이상)는 V2 마이그레이션이
	// "실패"해야 검증되는 시나리오다. backfillsLegacyRowsBeforeTighteningConstraints가
	// SCHEMA를 이미 V2까지 성공적으로 올려 버리므로 같은 schema를 재사용할 수 없다 —
	// 별도 schema에 V1만 적용해 독립적으로 격리한다.
	@BeforeAll
	static void applyV1OnlyToConflictSchema() {
		flywayForSchema(CONFLICT_SCHEMA, "1").migrate();
	}

	@Test
	@DisplayName("V2는 옛 형태 데이터를 새 제약이 받아들이는 형태로 백필한 뒤 제약을 건다")
	void backfillsLegacyRowsBeforeTighteningConstraints() {
		// Hikari 풀에서 jdbcTemplate 호출마다 다른 커넥션을 빌려오면 SET search_path가
		// 유실되어 이후 문장이 의도와 달리 public schema를 상대로 실행될 수 있다. 이
		// 시나리오의 설정과 검증 전체를 커넥션 하나에 고정해 실행한다.
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			JdbcTemplate scoped = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
			scoped.execute("SET search_path TO " + SCHEMA);

			scoped.update("INSERT INTO region_code (code, display_name, level) "
				+ "VALUES ('TEST-V2', 'V2 Test', 'COUNTRY')");
			Long senderId = insertAccount(scoped, "v2-sender");
			Long recipientId = insertAccount(scoped, "v2-recipient");
			Long questionId = scoped.queryForObject("""
				INSERT INTO approved_question
					(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
				VALUES ('OPERATOR', 'ACTIVE', 'V2 질문', 'TEXT', ?, ?, ?)
				RETURNING id
				""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), senderId);
			Long postId = scoped.queryForObject("""
				INSERT INTO direction_post
					(sender_id, approved_question_id, status, idempotency_key, body_text,
					 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
				VALUES (?, ?, 'ACTIVE', 'v2-post', '글', 'TEST-V2', 'PASSED', ?, ?, ?)
				RETURNING id
				""", Long.class, senderId, questionId, Timestamp.from(NOW), Timestamp.from(NOW),
				Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));

			// 옛 형태 1: skip_requested_at이 없는 확정 넘김 행
			Long postRecipientId = scoped.queryForObject("""
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
			Long outboxEventId = scoped.queryForObject("""
				INSERT INTO outbox_event
					(aggregate_type, aggregate_id, event_type, dedup_key, payload, status, created_at)
				VALUES ('QUESTION_ASSIGNMENT', ?, 'DAILY_QUESTION_ASSIGNED', 'v2-legacy-outbox',
					'{}'::jsonb, 'PENDING', ?)
				RETURNING id
				""", Long.class, questionId, Timestamp.from(NOW));
			scoped.update("""
				INSERT INTO notification_preference (notification_type, user_id, enabled)
				VALUES ('DAILY_QUESTION_ASSIGNED', ?, true)
				""", recipientId);
			scoped.update("""
				INSERT INTO notification
					(recipient_id, outbox_event_id, notification_type, dedup_key, status, created_at)
				VALUES (?, ?, 'DAILY_QUESTION_ASSIGNED', 'v2-legacy-notification', 'UNREAD', ?)
				""", recipientId, outboxEventId, Timestamp.from(NOW));

			flywayFor().migrate();

			assertThat(scoped.queryForObject(
				"SELECT skip_requested_at = skipped_at FROM post_recipient WHERE id = ?",
				Boolean.class, postRecipientId)).isTrue();
			assertThat(scoped.queryForObject(
				"SELECT count(*) FROM notification_preference WHERE notification_type = 'QUESTION_RECOMMENDED'",
				Integer.class)).isEqualTo(1);
			assertThat(scoped.queryForObject(
				"SELECT count(*) FROM notification WHERE notification_type = 'QUESTION_RECOMMENDED'",
				Integer.class)).isEqualTo(1);
			assertThat(scoped.queryForObject(
				"SELECT count(*) FROM outbox_event WHERE event_type = 'QUESTION_RECOMMENDED'",
				Integer.class)).isEqualTo(1);
			assertThat(scoped.queryForObject(
				"SELECT count(*) FROM notification WHERE notification_type = 'DAILY_QUESTION_ASSIGNED'",
				Integer.class)).isZero();

			return null;
		});
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

	@Test
	@DisplayName("post_recipient에 살아있는 답변이 둘 이상이면 V2는 조용히 지우지 않고 시끄럽게 실패한다")
	void v2FailsLoudlyWhenAPostRecipientAlreadyHasTwoLiveAnswers() {
		jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			JdbcTemplate scoped = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
			scoped.execute("SET search_path TO " + CONFLICT_SCHEMA);

			scoped.update("INSERT INTO region_code (code, display_name, level) "
				+ "VALUES ('TEST-V2', 'V2 Test', 'COUNTRY')");
			Long senderId = insertAccount(scoped, "v2-conflict-sender");
			Long recipientId = insertAccount(scoped, "v2-conflict-recipient");
			Long questionId = scoped.queryForObject("""
				INSERT INTO approved_question
					(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
				VALUES ('OPERATOR', 'ACTIVE', 'V2 충돌 질문', 'TEXT', ?, ?, ?)
				RETURNING id
				""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), senderId);
			Long postId = scoped.queryForObject("""
				INSERT INTO direction_post
					(sender_id, approved_question_id, status, idempotency_key, body_text,
					 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
				VALUES (?, ?, 'ACTIVE', 'v2-conflict-post', '글', 'TEST-V2', 'PASSED', ?, ?, ?)
				RETURNING id
				""", Long.class, senderId, questionId, Timestamp.from(NOW), Timestamp.from(NOW),
				Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
			Long postRecipientId = scoped.queryForObject("""
				INSERT INTO post_recipient
					(post_id, recipient_id, status, distance_band, matched_bearing_deg,
					 matched_region_code, matched_at)
				VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, 'TEST-V2', ?)
				RETURNING id
				""", Long.class, postId, recipientId, Timestamp.from(NOW));

			// uq_answer_one_per_recipient가 아직 없는 V1 상태이므로, 같은 post_recipient_id를
			// 가리키며 상태가 REJECTED·DELETED가 아닌(=살아있는) 답변 두 건을 넣어도 막히지
			// 않는다. V2가 이 인덱스를 만들려는 순간 실패해야 하는 상황을 재현한다.
			scoped.update("""
				INSERT INTO answer
					(post_recipient_id, author_id, status, idempotency_key, body_text,
					 coarse_region_code, bearing_from_sender_deg, distance_band,
					 moderation_status, published_at)
				VALUES (?, ?, 'PUBLISHED', 'v2-conflict-answer-1', '답변 1',
					'TEST-V2', 10, 'NEAR', 'PASSED', ?)
				""", postRecipientId, recipientId, Timestamp.from(NOW.plusSeconds(60)));
			scoped.update("""
				INSERT INTO answer
					(post_recipient_id, author_id, status, idempotency_key, body_text,
					 coarse_region_code, bearing_from_sender_deg, distance_band,
					 moderation_status, published_at)
				VALUES (?, ?, 'PUBLISHED', 'v2-conflict-answer-2', '답변 2',
					'TEST-V2', 10, 'NEAR', 'PASSED', ?)
				""", postRecipientId, recipientId, Timestamp.from(NOW.plusSeconds(120)));

			assertThatThrownBy(() -> flywayForSchema(CONFLICT_SCHEMA).migrate())
				.isInstanceOf(FlywayException.class);

			// V2는 데이터를 임의로 지우지 않고 실패를 그대로 드러내야 한다. version '2'가
			// success=true로 기록된 행이 남아 있으면 부분 성공한 상태이므로 실패다.
			Integer successfulV2Migrations = scoped.queryForObject("""
				SELECT count(*) FROM flyway_schema_history
				WHERE version = '2' AND success = true
				""", Integer.class);
			assertThat(successfulV2Migrations).isZero();

			return null;
		});
	}

	private Long insertAccount(JdbcTemplate template, String nickname) {
		return template.queryForObject("""
			INSERT INTO user_account (role, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'ACTIVE', 'TEST-V2', 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, nickname);
	}

	// public schema는 @SpringBootTest 컨텍스트가 기동하며 Spring이 관리하는 Flyway로 이미
	// V1+V2까지 전부 적용한 애플리케이션 schema다. 이 클래스의 다른 테스트가 쓰는
	// v2_backfill / v2_backfill_conflict schema와는 무관한 별개 대상이며, 아래
	// nspname = 'public' 고정은 그 완전히 마이그레이션된 public schema의 최종 CHECK 정의를
	// 보려는 의도이지 schema를 헷갈린 실수가 아니다.
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
