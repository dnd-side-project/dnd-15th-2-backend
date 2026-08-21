/**
 * Created at: 2026-08-21T00:00:00+09:00
 * Source scenario: TEST-PLAN-GH-156-REPORT-SEVERITY-OPERATOR-REVIEW-INT-010 through INT-019
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.dnd.qello.auth.token.AccessTokenIssuer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OperatorReportCase156TestClockConfiguration.class)
class OperatorReportCaseIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "TEST-GH156-API";
	private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");
	private static final String LOGIN_ID = "qello-admin-gh156";
	private static final String PASSWORD = "example-operator-password";

	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private OperatorSeedService operatorSeedService;
	@Autowired
	private AnswerRepository answerRepository;
	@Autowired
	private AccessTokenIssuer accessTokenIssuer;

	private long authorId;
	private long senderId;
	private long postId;
	private ExecutorService executor;

	@BeforeEach
	void resetSchemaFixtures() {
		jdbc.update("TRUNCATE report_case_event");
		jdbc.update("TRUNCATE report_content_snapshot");
		jdbc.update("DELETE FROM notification_delivery");
		jdbc.update("DELETE FROM notification");
		jdbc.update("DELETE FROM moderation_review");
		jdbc.update("DELETE FROM report");
		jdbc.update("DELETE FROM report_case");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM media_attachment");
		jdbc.update("DELETE FROM media_asset");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM operator_credential");
		jdbc.update("DELETE FROM user_account WHERE nickname = ?", LOGIN_ID);
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES ('KR', NULL, 'Korea', 'COUNTRY') ON CONFLICT (code, level) DO NOTHING");
		jdbc.update("INSERT INTO region_code (code, parent_code, display_name, level) "
			+ "VALUES (?, 'KR', 'GH156 API Test', 'REGION')", REGION);

		authorId = account("author");
		senderId = account("sender");
		executor = Executors.newFixedThreadPool(4);
	}

	@AfterEach
	void shutdownExecutor() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("INT-018: 세션 없이 대기열을 조회하면 401이다")
	void queueRequiresOperatorSession() throws Exception {
		mockMvc.perform(get("/api/v1/operator/report-cases"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("INT-019: 앱 액세스 토큰(JWT bearer)으로는 운영자 API에 접근할 수 없다")
	void appAccessTokenCannotReachOperatorApi() throws Exception {
		String jwt = accessTokenIssuer.issue(authorId, AccountRole.USER, 1L).value();

		mockMvc.perform(get("/api/v1/operator/report-cases")
				.header("Authorization", "Bearer " + jwt))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("INT-010: sla_due_at이 지난 사건은 overdue=true, 남은 사건은 overdue=false다")
	void queueMarksOverdueCases() throws Exception {
		long overdueAnswerId = publishedAnswer("gh156-api-int010-overdue");
		openCase(overdueAnswerId, NOW.minus(Duration.ofHours(1)));
		long freshAnswerId = publishedAnswer("gh156-api-int010-fresh");
		openCase(freshAnswerId, NOW.plus(Duration.ofHours(1)));
		OperatorSession session = login();

		MvcResult result = mockMvc.perform(withSession(get("/api/v1/operator/report-cases"), session))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
			.get("data").get("items");
		assertThat(items).hasSize(2);
		for (JsonNode item : items) {
			boolean expectedOverdue = item.get("answerId").asLong() == overdueAnswerId;
			assertThat(item.get("overdue").asBoolean()).isEqualTo(expectedOverdue);
		}
	}

	@Test
	@DisplayName("INT-011: queue 필터가 URGENT만 반환한다")
	void queueFiltersByQueueParameter() throws Exception {
		long urgentAnswerId = publishedAnswer("gh156-api-int011-urgent");
		openCriticalCase(urgentAnswerId);
		long standardAnswerId = publishedAnswer("gh156-api-int011-standard");
		openCase(standardAnswerId, NOW.plus(Duration.ofDays(3)));
		OperatorSession session = login();

		MvcResult result = mockMvc.perform(
				withSession(get("/api/v1/operator/report-cases").param("queue", "URGENT"), session))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString())
			.get("data").get("items");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).get("answerId").asLong()).isEqualTo(urgentAnswerId);
	}

	@Test
	@DisplayName("INT-012: 검토 시작은 OPEN 사건을 UNDER_REVIEW로 전이한다")
	void startReviewTransitionsCase() throws Exception {
		long answerId = publishedAnswer("gh156-api-int012");
		long caseId = openCase(answerId, NOW.plus(Duration.ofDays(3)));
		OperatorSession session = login();

		mockMvc.perform(withCsrf(post("/api/v1/operator/report-cases/" + caseId + "/review"), session)
				.cookie(session.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"));
	}

	@Test
	@DisplayName("INT-013: ACTIONED 판정은 사건을 종결하고 답변을 숨기고 internal_note를 기록한다")
	void decideActionedHidesAnswerAndRecordsInternalNote() throws Exception {
		long answerId = publishedAnswer("gh156-api-int013");
		long caseId = openCase(answerId, NOW.plus(Duration.ofDays(3)));
		OperatorSession session = login();

		mockMvc.perform(withCsrf(post("/api/v1/operator/report-cases/" + caseId + "/decision"), session)
				.cookie(session.sessionCookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"ACTIONED\",\"internalNote\":\"명백한 스팸\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RESOLVED"))
			.andExpect(jsonPath("$.data.decision").value("ACTIONED"));

		Answer hidden = answerRepository.findById(answerId).orElseThrow();
		assertThat(hidden.getStatus()).isEqualTo(AnswerStatus.HIDDEN);
		String internalNote = jdbc.queryForObject(
			"SELECT internal_note FROM moderation_review WHERE report_id = (SELECT id FROM report WHERE case_id = ?)",
			String.class, caseId);
		assertThat(internalNote).isEqualTo("명백한 스팸");
	}

	@Test
	@DisplayName("INT-014: NO_VIOLATION 판정은 사건을 종결하지만 콘텐츠는 숨기지 않는다")
	void decideNoViolationDoesNotHideAnswer() throws Exception {
		long answerId = publishedAnswer("gh156-api-int014");
		long caseId = openCase(answerId, NOW.plus(Duration.ofDays(3)));
		OperatorSession session = login();

		mockMvc.perform(withCsrf(post("/api/v1/operator/report-cases/" + caseId + "/decision"), session)
				.cookie(session.sessionCookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"NO_VIOLATION\",\"internalNote\":\"위반 아님\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RESOLVED"));

		Answer stillPublished = answerRepository.findById(answerId).orElseThrow();
		assertThat(stillPublished.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
	}

	@Test
	@DisplayName("INT-015: 추가 정보 요청은 사건을 종결하지 않고 MORE_INFO_REQUIRED로 표시한다")
	void requestMoreInfoKeepsCaseOpen() throws Exception {
		long answerId = publishedAnswer("gh156-api-int015");
		long caseId = openCase(answerId, NOW.plus(Duration.ofDays(3)));
		OperatorSession session = login();

		mockMvc.perform(withCsrf(post("/api/v1/operator/report-cases/" + caseId + "/more-info"), session)
				.cookie(session.sessionCookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"internalNote\":\"원본 미디어 확인 필요\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("OPEN"))
			.andExpect(jsonPath("$.data.decision").value("MORE_INFO_REQUIRED"));
	}

	@Test
	@DisplayName("INT-016: 복원 API는 ACTIONED로 숨긴 답변을 다시 PUBLISHED로 되돌린다")
	void restoreBringsAnswerBackToPublished() throws Exception {
		long answerId = publishedAnswer("gh156-api-int016");
		long caseId = openCase(answerId, NOW.plus(Duration.ofDays(3)));
		OperatorSession session = login();
		mockMvc.perform(withCsrf(post("/api/v1/operator/report-cases/" + caseId + "/decision"), session)
				.cookie(session.sessionCookie())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"ACTIONED\",\"internalNote\":\"숨김\"}"))
			.andExpect(status().isOk());

		mockMvc.perform(withCsrf(post("/api/v1/operator/report-cases/" + caseId + "/restore"), session)
				.cookie(session.sessionCookie()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"));

		Answer restored = answerRepository.findById(answerId).orElseThrow();
		assertThat(restored.getStatus()).isEqualTo(AnswerStatus.PUBLISHED);
	}

	@Test
	@DisplayName("INT-017: 같은 사건을 동시에 판정해도 판정은 한 번만 기록된다")
	void concurrentDecisionResolvesOnce() throws Exception {
		long answerId = publishedAnswer("gh156-api-int017");
		long caseId = openCase(answerId, NOW.plus(Duration.ofDays(3)));
		OperatorSession session = login();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Integer> deciding = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return mockMvc.perform(withCsrf(post("/api/v1/operator/report-cases/" + caseId + "/decision"), session)
					.cookie(session.sessionCookie())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"decision\":\"NO_VIOLATION\",\"internalNote\":\"동시 판정 테스트\"}"))
				.andReturn().getResponse().getStatus();
		};

		Future<Integer> first = executor.submit(deciding);
		Future<Integer> second = executor.submit(deciding);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		int firstStatus = first.get(10, TimeUnit.SECONDS);
		int secondStatus = second.get(10, TimeUnit.SECONDS);

		assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, 400);
		Integer reviewCount = jdbc.queryForObject(
			"SELECT count(*) FROM moderation_review WHERE report_id = (SELECT id FROM report WHERE case_id = ?)",
			Integer.class, caseId);
		assertThat(reviewCount).isEqualTo(1);
	}

	private long openCase(long answerId, Instant slaDueAt) {
		return openCase(answerId, "NORMAL", "STANDARD", slaDueAt);
	}

	private long openCriticalCase(long answerId) {
		return openCase(answerId, "CRITICAL", "URGENT", NOW.plus(Duration.ofHours(4)));
	}

	private long openCase(long answerId, String severity, String queue, Instant slaDueAt) {
		long caseId = jdbc.queryForObject("""
			INSERT INTO report_case (answer_id, status, severity, queue, created_at, sla_due_at)
			VALUES (?, 'OPEN', ?, ?, ?, ?)
			RETURNING id
			""", Long.class, answerId, severity, queue, Timestamp.from(NOW), Timestamp.from(slaDueAt));
		jdbc.update("""
			INSERT INTO report (reporter_id, answer_id, reason_code, status, created_at, case_id)
			VALUES (?, ?, 'SPAM_OR_ADVERTISING', 'RECEIVED', ?, ?)
			""", senderId, answerId, Timestamp.from(NOW), caseId);
		return caseId;
	}

	private long publishedAnswer(String idempotencyKey) {
		long questionId = question();
		postId = directionPost(questionId);
		long authorRecipientId = insertRecipient(postId, authorId, "AVAILABLE");
		return answerRepository.save(Answer.restore(null, authorRecipientId, authorId,
			AnswerStatus.PUBLISHED, idempotencyKey, "신고 대상 답변", REGION,
			BigDecimal.valueOf(90), "NEAR", AnswerModerationStatus.PASSED,
			NOW, NOW, null, 5000L, null, 0)).getId();
	}

	private OperatorSession login() throws Exception {
		operatorSeedService.seedIfAbsent(
			LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), LOGIN_ID, REGION, "ko-KR", "Asia/Seoul");

		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		String csrfHeaderName = data.get("headerName").asText();
		String csrfToken = data.get("token").asText();
		Cookie[] csrfCookies = issued.getResponse().getCookies();

		MvcResult loggedIn = mockMvc.perform(withCsrf(post("/admin/login"), csrfHeaderName, csrfToken, csrfCookies)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		Cookie sessionCookie = loggedIn.getResponse().getCookie("SESSION");
		return new OperatorSession(csrfHeaderName, csrfToken, csrfCookies, sessionCookie);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withSession(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, OperatorSession session
	) {
		return request.cookie(session.sessionCookie());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, OperatorSession session
	) {
		return withCsrf(request, session.csrfHeaderName(), session.csrfToken(), session.csrfCookies());
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String csrfHeaderName,
		String csrfToken, Cookie[] csrfCookies
	) {
		return request.header(csrfHeaderName, csrfToken).cookie(csrfCookies);
	}

	private long insertRecipient(long postIdValue, long recipientId, String status) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code,
				 matched_at, inbound_bearing_deg, distance_m)
			VALUES (?, ?, ?, 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postIdValue, recipientId, status, REGION, Timestamp.from(NOW));
	}

	private long account(String nickname) {
		return jdbc.queryForObject("""
			INSERT INTO user_account (role, country_code, status, coarse_region_code, locale, timezone, nickname)
			VALUES ('USER', 'KR', 'ACTIVE', ?, 'ko-KR', 'Asia/Seoul', ?)
			RETURNING id
			""", Long.class, REGION, nickname);
	}

	private long question() {
		return jdbc.queryForObject("""
			INSERT INTO approved_question
				(source_type, status, question_text, answer_format, active_from, approved_at, approved_by)
			VALUES ('OPERATOR', 'ACTIVE', 'GH156 API 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(NOW.minusSeconds(10)), Timestamp.from(NOW), authorId);
	}

	private long directionPost(long questionId) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '질문글 본문', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, "gh156-api-post-" + questionId, REGION, Timestamp.from(NOW),
			Timestamp.from(NOW), Timestamp.from(NOW.plus(1, ChronoUnit.HOURS)));
	}

	private record OperatorSession(String csrfHeaderName, String csrfToken, Cookie[] csrfCookies, Cookie sessionCookie) {
	}
}

@TestConfiguration
class OperatorReportCase156TestClockConfiguration {

	@Bean
	@Primary
	Clock operatorReportCase156FixedClock() {
		return Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneOffset.UTC);
	}
}
