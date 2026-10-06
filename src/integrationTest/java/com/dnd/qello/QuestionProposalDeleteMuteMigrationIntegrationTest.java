/**
 * Created at: 2026-10-05T18:10:06+09:00
 * Source scenario: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-INT-007
 */
package com.dnd.qello;

import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"test", "flyway-migration"})
class QuestionProposalDeleteMuteMigrationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String schemaName;
	private String regionCode;

	@BeforeEach
	void setUp() {
		int sequence = SCHEMA_SEQUENCE.incrementAndGet();
		schemaName = "question_proposal_v30_" + sequence;
		regionCode = "QPV30R" + sequence;
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
	}

	@Test
	@DisplayName("V30 적용 전 제안 행은 삭제 시각 NULL·알림 켜짐으로 남고 도메인으로 restore된다")
	void existingProposalGetsDefaultsAfterV30() {
		flyway("29").migrate();
		long userId = insertUser();
		long proposalId = jdbcTemplate.queryForObject("""
				INSERT INTO %s.question_proposal (proposer_id, status, proposed_text, submitted_at)
				VALUES (?, 'SUBMITTED', '마이그레이션 전 제안', now())
				RETURNING id
				""".formatted(schemaName), Long.class, userId);

		// V30만 적용해 본다. 뒤에 migration이 추가돼도 이 테스트가 세는 개수가 바뀌지 않는다(#315).
		MigrateResult result = flyway("30").migrate();

		assertThat(result.migrationsExecuted).isEqualTo(1);
		Map<String, Object> row = jdbcTemplate.queryForMap("""
				SELECT deleted_at, notification_muted FROM %s.question_proposal WHERE id = ?
				""".formatted(schemaName), proposalId);
		assertThat(row.get("deleted_at")).isNull();
		assertThat(row.get("notification_muted")).isEqualTo(false);
		QuestionProposal restored = QuestionProposal.restore(proposalId, userId, QuestionProposalStatus.SUBMITTED,
				"마이그레이션 전 제안", null, Instant.now(), null, null, null, (Boolean) row.get("notification_muted"));
		assertThat(restored.isDeleted()).isFalse();
		assertThat(restored.isPushMuted()).isFalse();
		assertThat(jdbcTemplate.queryForObject(
				"""
						SELECT count(*) FROM pg_indexes WHERE schemaname = ? AND indexname = 'question_proposal_proposer_active_idx'
						""",
				Long.class, schemaName)).isEqualTo(1L);
	}

	private long insertUser() {
		jdbcTemplate.update("""
				INSERT INTO %s.region_code (code, parent_code, display_name, level)
				VALUES ('KR', NULL, 'Korea', 'COUNTRY')
				ON CONFLICT DO NOTHING
				""".formatted(schemaName));
		jdbcTemplate.update("""
				INSERT INTO %s.region_code (code, parent_code, display_name, level)
				VALUES (?, 'KR', 'Migration Region', 'REGION')
				ON CONFLICT DO NOTHING
				""".formatted(schemaName), regionCode);
		return jdbcTemplate.queryForObject("""
				INSERT INTO %s.user_account
					(role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', 'gh310-migration-user')
				RETURNING id
				""".formatted(schemaName), Long.class, regionCode);
	}

	private Flyway flyway(String target) {
		var configuration = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.schemas(schemaName)
				.defaultSchema(schemaName)
				.locations("classpath:db/migration")
				.cleanDisabled(true);
		if (target != null) {
			configuration = configuration.target(target);
		}
		return configuration.load();
	}
}
