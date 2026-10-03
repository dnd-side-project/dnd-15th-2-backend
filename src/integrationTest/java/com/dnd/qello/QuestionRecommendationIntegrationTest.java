/**
 * Created at: 2026-10-02T16:41:59+09:00
 * Source scenario: TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-INT-001, INT-002
 */
package com.dnd.qello;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.service.DirectionPostApplicationService;
import com.dnd.qello.direction.service.DirectionPresenceService;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.service.QuestionRecommendationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(QuestionRecommendationIntegrationTest.FixedClockConfiguration.class)
class QuestionRecommendationIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-QUESTION-RECOMMENDATION-301";
	private static final Instant NOW = Instant.parse("2026-10-02T07:00:00Z");

	@Autowired
	private QuestionRecommendationService recommendationService;
	@Autowired
	private DirectionPostApplicationService postApplicationService;
	@Autowired
	private DirectionPresenceService presenceService;
	@Autowired
	private JdbcTemplate jdbc;

	private long userId;
	private Fixture fixture;

	@BeforeEach
	void reset() {
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM recipient_receive_state");
		jdbc.update("DELETE FROM active_user_presence");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
				+ "VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
				+ "VALUES (?, 'KR', 'Question Recommendation 301', 'REGION')", REGION);

		userId = account("recommendation-user");
		long approverId = account("recommendation-approver");
		fixture = seedQuestionPool(approverId);
	}

	@Test
	@DisplayName("INT-001: 활성 기간 안의 ACTIVE 질문만 id 순으로 반환하고 질문 행을 바꾸지 않는다")
	void returnsOnlyQuestionsActiveAtNowInIdOrder() {
		Integer before = jdbc.queryForObject("SELECT count(*) FROM approved_question", Integer.class);

		List<ApprovedQuestion> result = recommendationService.findRecommendations(userId);

		assertThat(result).extracting(ApprovedQuestion::getId)
				.containsExactly(fixture.openEnded(), fixture.startsNow());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM approved_question", Integer.class)).isEqualTo(before);
	}

	@Test
	@DisplayName("INT-002: 추천 목록의 질문으로는 전송이 되고 목록에 없는 INACTIVE 질문은 QUESTION_NOT_ACTIVE로 거부된다")
	void recommendedQuestionIsAcceptedBySubmission() {
		presenceService.update(userId, new DirectionPresenceService.UpdateCommand(
				BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0), BigDecimal.ONE, true, NOW));
		long schemeId = jdbc.queryForObject(
				"SELECT id FROM direction_scheme WHERE code = 'OCTANT' AND status = 'ACTIVE'", Long.class);
		long recommendedId = recommendationService.findRecommendations(userId).getFirst().getId();

		var result = postApplicationService.submit(userId, "gh-301-recommended",
				new DirectionPostApplicationService.SubmitCommand(recommendedId, schemeId, "N", "gh-301 본문",
						List.of()));

		assertThat(result.post().getApprovedQuestionId()).isEqualTo(recommendedId);
		assertThatThrownBy(() -> postApplicationService.submit(userId, "gh-301-inactive",
				new DirectionPostApplicationService.SubmitCommand(fixture.inactive(), schemeId, "N", "gh-301 본문",
						List.of())))
				.isInstanceOf(DirectionException.class)
				.satisfies(exception -> assertThat(((DirectionException) exception).getErrorCode())
						.isEqualTo(DirectionErrorCode.QUESTION_NOT_ACTIVE));
	}

	private Fixture seedQuestionPool(long approverId) {
		Instant past = NOW.minusSeconds(7200);
		long openEnded = question("ACTIVE", past, null, approverId);
		long startsNow = question("ACTIVE", NOW, NOW.plusSeconds(3600), approverId);
		question("ACTIVE", NOW.plusSeconds(60), null, approverId);
		question("ACTIVE", past, NOW, approverId);
		question("ACTIVE", past, NOW.minusSeconds(60), approverId);
		question("PENDING_REVIEW", null, null, null);
		long inactive = question("INACTIVE", past, null, approverId);
		return new Fixture(openEnded, startsNow, inactive);
	}

	private long question(String status, Instant activeFrom, Instant activeUntil, Long approverId) {
		Instant approvedAt = approverId == null ? null : NOW.minusSeconds(7200);
		return jdbc.queryForObject(
				"""
						INSERT INTO approved_question
							(source_type, status, question_text, answer_format, active_from, active_until, approved_at, approved_by)
						VALUES ('OPERATOR', ?, 'gh-301 question', 'TEXT', ?, ?, ?, ?) RETURNING id
						""",
				Long.class, status, timestamp(activeFrom), timestamp(activeUntil), timestamp(approvedAt), approverId);
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
				INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
				VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?) RETURNING id
				""", Long.class, REGION, nickname);
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private record Fixture(long openEnded, long startsNow, long inactive) {
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(NOW, ZoneOffset.UTC);
		}
	}
}
