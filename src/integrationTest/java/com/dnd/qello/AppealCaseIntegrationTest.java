/**
 * Created at: 2026-08-17T19:50:00+09:00
 * Source scenario: TEST-PLAN-GH-112-AUTHOR-APPEAL-AND-MANUAL-RESTORE-INT-001 through INT-016
 *                  (INT-014는 기존 FilteringPersistenceIntegrationTest가 담당한다)
 */
package com.dnd.qello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.auth.domain.LoginId;
import com.dnd.qello.auth.security.RawPassword;
import com.dnd.qello.auth.service.OperatorSeedService;
import com.dnd.qello.filtering.domain.AppealAcceptance;
import com.dnd.qello.filtering.domain.AppealAcceptanceReasonCode;
import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.AppealCaseStatus;
import com.dnd.qello.filtering.domain.AppealDecision;
import com.dnd.qello.filtering.domain.AppealWindow;
import com.dnd.qello.filtering.domain.FilterDecision;
import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterTarget;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.OperatorReason;
import com.dnd.qello.filtering.domain.FilterVerdict;
import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;
import com.dnd.qello.filtering.moderation.AppealCaseService;
import com.dnd.qello.filtering.repository.AppealCaseRepository;
import com.dnd.qello.filtering.repository.FilterDecisionRepository;
import com.dnd.qello.filtering.repository.FilterJobRepository;
import com.dnd.qello.filtering.service.FilterReleaseRegistryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

// #112: 이의제기 접수가 공개 상태를 건드리지 않는지, 동일 대상 중복·동시 접수가
// 하나로 수렴하는지, OVERTURN_HIDDEN이 공개 금지 사유를 재검증한 뒤에만 복원
// 콜백을 내는지, 접수 기간을 6개월보다 줄이는 경로가 없는지를 실제 PostgreSQL
// 위에서 검증한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppealCaseIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION = "APPEAL-TEST";
	private static final String MODEL_SNAPSHOT = "omni-moderation-2026-08-01";
	private static final String LOGIN_ID = "qello-admin";
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
	private AppealCaseRepository appealCaseRepository;
	@Autowired
	private AppealCaseService appealCaseService;
	@Autowired
	private FilterJobRepository filterJobRepository;
	@Autowired
	private FilterDecisionRepository filterDecisionRepository;
	@Autowired
	private FilterReleaseRegistryService releaseRegistryService;

	private Instant now;
	private long authorId;
	private long senderId;
	private long postRecipientId;
	private long questionId;
	private long releaseId;
	private long answerId;
	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		jdbc.update("DELETE FROM appeal_case");
		jdbc.update("DELETE FROM manual_review_priority_evaluation");
		jdbc.update("DELETE FROM notification_event");
		jdbc.update("DELETE FROM outbox_event");
		jdbc.update("DELETE FROM filter_decision");
		jdbc.update("DELETE FROM filter_job_status_history");
		jdbc.update("DELETE FROM manual_review_case");
		jdbc.update("DELETE FROM filter_release_retry_gate");
		jdbc.update("DELETE FROM filter_job");
		jdbc.update("DELETE FROM release_promotion_history");
		jdbc.update("DELETE FROM filter_release");
		jdbc.update("DELETE FROM answer");
		jdbc.update("DELETE FROM post_recipient");
		jdbc.update("DELETE FROM post_audience");
		jdbc.update("DELETE FROM direction_post");
		jdbc.update("DELETE FROM approved_question");
		jdbc.update("DELETE FROM operator_credential");
		jdbc.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION);
		jdbc.update("DELETE FROM region_code WHERE code = ?", REGION);
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY')
			ON CONFLICT (code, level) DO NOTHING
			""");
		jdbc.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES (?, 'KR', 'Appeal Test', 'REGION')
			""", REGION);
		authorId = account("appeal-author");
		senderId = account("appeal-sender");
		questionId = question();
		postRecipientId = postRecipient(directionPost(questionId, "appeal-post-1"));
		answerId = answerRepository.save(Answer.submit(postRecipientId, authorId, "appeal-answer-1", "답변 본문",
			REGION, BigDecimal.valueOf(90), "NEAR", now, 5000L)).getId();
		releaseId = promotedRelease();
		executor = Executors.newFixedThreadPool(4);
	}

	@AfterEach
	void tearDown() {
		executor.shutdownNow();
	}

	@Test
	@DisplayName("INT-001: V18이 appeal_case 결정 컬럼과 outbox_event의 신규 aggregate·event type을 제공한다")
	void appliesAppealCaseSchemaAndOutboxContract() {
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM information_schema.columns
			WHERE table_name = 'appeal_case'
			  AND column_name IN ('appellant_user_id', 'status', 'window_started_at', 'expires_at',
			                      'acceptance_reason_code', 'decision', 'decided_at',
			                      'decided_by_operator_user_id', 'restore_blocked_reason_code')
			""", Integer.class)).isEqualTo(9);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM pg_constraint
			WHERE conname IN ('ck_appeal_case_status', 'ck_appeal_case_decision', 'ck_appeal_case_decided_fields',
			                  'ck_appeal_case_restore_blocked_reason', 'ck_appeal_case_expires_after_window_start')
			""", Integer.class)).isEqualTo(5);
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM pg_indexes WHERE indexname IN ('appeal_case_appellant_idx', 'appeal_case_queue_idx')",
			Integer.class)).isEqualTo(2);

		// V18은 outbox_event의 두 CHECK를 drop 후 재생성한다. 재생성 목록에서 기존
		// 값이 하나라도 빠지면 그 기능의 outbox 발행이 전면 중단되므로, event type과
		// aggregate type 양쪽 모두 기존 값을 다시 삽입해 회귀를 막는다.
		List<String> eventTypes = List.of("RECIPIENT_MATCH_REQUESTED", "RECIPIENTS_CONFIRMED",
			"DIRECTION_POST_EXPIRED", "ANSWER_PUBLISHED", "ANSWER_REACTED", "SKIP_CONFIRMATION_DUE",
			"QUESTION_RECOMMENDED", "QUESTION_PROPOSAL_REVIEWED", "REPORT_RESOLVED",
			"MODERATION_EXECUTION_REQUESTED", "MODERATION_VERDICT_READY", "MODERATION_DEADLINE_ELAPSED",
			"MODERATION_APPEAL_RESOLVED");
		for (String eventType : eventTypes) {
			insertOutboxProbe("APPEAL_CASE", eventType, "schema-check-event:" + eventType);
		}
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE dedup_key LIKE 'schema-check-event:%'", Integer.class))
			.isEqualTo(eventTypes.size());

		List<String> aggregateTypes = List.of("DIRECTION_POST", "POST_RECIPIENT", "ANSWER",
			"QUESTION_ASSIGNMENT", "QUESTION_PROPOSAL", "REPORT", "FILTER_JOB", "APPEAL_CASE");
		for (String aggregateType : aggregateTypes) {
			// RECIPIENT_MATCH_REQUESTED는 DIRECTION_POST에서만 match_round 유일
			// 인덱스에 걸리므로, aggregate type 확인에는 그 조합을 피한다.
			insertOutboxProbe(aggregateType, "REPORT_RESOLVED", "schema-check-aggregate:" + aggregateType);
		}
		assertThat(jdbc.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE dedup_key LIKE 'schema-check-aggregate:%'", Integer.class))
			.isEqualTo(aggregateTypes.size());
	}

	private void insertOutboxProbe(String aggregateType, String eventType, String dedupKey) {
		jdbc.update("""
			INSERT INTO outbox_event
				(aggregate_type, aggregate_id, event_type, dedup_key, payload, status, attempt_count,
				 next_attempt_at, created_at)
			VALUES (?, 1, ?, ?, '{}', 'PENDING', 0, ?, ?)
			""", aggregateType, eventType, dedupKey, Timestamp.from(now), Timestamp.from(now));
	}

	@Test
	@DisplayName("INT-002: 접수는 appeal_case만 만들고 filter_job과 outbox_event를 건드리지 않는다")
	void filingDoesNotChangePublicationState() {
		FilterDecision decision = blockDecision("file-clean", now.minus(Duration.ofDays(10)));
		long jobId = decision.filterJobId();
		String jobStatusBefore = jobStatus(jobId);

		AppealCase filed = appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), authorId);

		assertThat(filed.status()).isEqualTo(AppealCaseStatus.OPEN);
		assertThat(filed.acceptanceReasonCode()).isEqualTo(AppealAcceptanceReasonCode.WITHIN_WINDOW);
		assertThat(filed.expiresAt()).isEqualTo(filed.windowStartedAt().plus(Duration.ofDays(184)));
		assertThat(jobStatus(jobId)).isEqualTo(jobStatusBefore);
		assertThat(countAppealCases()).isEqualTo(1);
		assertThat(countOutboxEvents()).isZero();
		// 이의제기 접수가 답변의 공개 상태를 바꾸지 않는다(INV-APL-003).
		assertThat(answerRepository.findById(answerId).orElseThrow().getStatus().name()).isEqualTo("SUBMITTED");
	}

	@Test
	@DisplayName("INT-003: 동일 대상·decision의 재접수는 중복 case로 거절된다")
	void rejectsDuplicateFiling() {
		FilterDecision decision = blockDecision("file-dup", now.minus(Duration.ofDays(10)));
		appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), authorId);

		assertThatThrownBy(() -> appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), authorId))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.DUPLICATE_CASE);
		assertThat(countAppealCases()).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-004: 동시 접수는 정확히 하나만 성공한다")
	void concurrentFilingKeepsUniqueness() throws Exception {
		FilterDecision decision = blockDecision("file-race", now.minus(Duration.ofDays(10)));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Boolean> filing = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			try {
				appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), authorId);
				return true;
			} catch (FilteringException | DataIntegrityViolationException expected) {
				return false;
			}
		};

		Future<Boolean> first = executor.submit(filing);
		Future<Boolean> second = executor.submit(filing);
		ready.await(5, TimeUnit.SECONDS);
		start.countDown();

		assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
			.containsExactlyInAnyOrder(true, false);
		assertThat(countAppealCases()).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-005: 작성자가 아니면 접수가 거절되고 행이 생기지 않는다")
	void rejectsFilingByNonAuthor() {
		FilterDecision decision = blockDecision("file-not-owner", now.minus(Duration.ofDays(10)));

		assertThatThrownBy(() -> appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), senderId))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.APPEAL_NOT_OWNED);
		assertThat(countAppealCases()).isZero();
	}

	@Test
	@DisplayName("INT-006: 판정 후 185일이 지나면 접수 기간 경과로 거절된다")
	void rejectsFilingAfterWindowElapsed() {
		FilterDecision decision = blockDecision("file-elapsed", now.minus(Duration.ofDays(185)));

		assertThatThrownBy(() -> appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), authorId))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.APPEAL_WINDOW_ELAPSED);
		assertThat(countAppealCases()).isZero();
	}

	@Test
	@DisplayName("INT-007: 판정 시각이 미래라 기산점을 믿을 수 없으면 거절하지 않고 접수한다")
	void acceptsFilingWhenWindowStartIsUnverifiable() {
		FilterDecision decision = blockDecision("file-unverifiable", now.plus(Duration.ofDays(1)));

		AppealCase filed = appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), authorId);

		assertThat(filed.acceptanceReasonCode()).isEqualTo(AppealAcceptanceReasonCode.WINDOW_UNVERIFIABLE);
		assertThat(jdbc.queryForObject(
			"SELECT acceptance_reason_code FROM appeal_case WHERE id = ?", String.class, filed.id()))
			.isEqualTo("WINDOW_UNVERIFIABLE");
	}

	@Test
	@DisplayName("INT-008: 공개 금지 사유가 없는 OVERTURN_HIDDEN은 복원 콜백을 발행한다")
	void overturnEmitsRestoreCallback() {
		AppealCase filed = fileAppeal("decide-overturn");

		AppealCase resolved = appealCaseService.decide(filed.id(), AppealDecision.OVERTURN_HIDDEN, 1L, new OperatorReason("TEST", "테스트 근거"));

		assertThat(resolved.status()).isEqualTo(AppealCaseStatus.RESOLVED);
		assertThat(resolved.restoreBlockedReasonCode()).isNull();
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM outbox_event
			WHERE aggregate_type = 'APPEAL_CASE' AND event_type = 'MODERATION_APPEAL_RESOLVED' AND aggregate_id = ?
			""", Integer.class, filed.id())).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-009: 계정이 차단된 상태의 OVERTURN_HIDDEN은 사유만 기록하고 복원 콜백을 내지 않는다")
	void overturnWithBlockedAccountSuppressesRestoreCallback() {
		AppealCase filed = fileAppeal("decide-blocked");
		jdbc.update("UPDATE user_account SET status = 'BLOCKED' WHERE id = ?", authorId);

		AppealCase resolved = appealCaseService.decide(filed.id(), AppealDecision.OVERTURN_HIDDEN, 1L, new OperatorReason("TEST", "테스트 근거"));

		assertThat(resolved.status()).isEqualTo(AppealCaseStatus.RESOLVED);
		assertThat(resolved.decision()).isEqualTo(AppealDecision.OVERTURN_HIDDEN);
		assertThat(resolved.restoreBlockedReasonCode()).isEqualTo("ACCOUNT_BLOCKED");
		assertThat(countOutboxEvents()).isZero();
	}

	@Test
	@DisplayName("INT-010: UPHOLD_HIDDEN은 복원 콜백을 발행하지 않는다")
	void upholdEmitsNoCallback() {
		AppealCase filed = fileAppeal("decide-uphold");

		AppealCase resolved = appealCaseService.decide(filed.id(), AppealDecision.UPHOLD_HIDDEN, 1L, new OperatorReason("TEST", "테스트 근거"));

		assertThat(resolved.decision()).isEqualTo(AppealDecision.UPHOLD_HIDDEN);
		assertThat(countOutboxEvents()).isZero();
	}

	@Test
	@DisplayName("INT-011: 동시 결정은 하나만 성공하고 복원 콜백도 한 번만 발행된다")
	void concurrentDecisionResolvesOnce() throws Exception {
		AppealCase filed = fileAppeal("decide-race");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<Boolean> deciding = () -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			try {
				appealCaseService.decide(filed.id(), AppealDecision.OVERTURN_HIDDEN, 1L, new OperatorReason("TEST", "테스트 근거"));
				return true;
			} catch (FilteringException expected) {
				return false;
			}
		};

		Future<Boolean> first = executor.submit(deciding);
		Future<Boolean> second = executor.submit(deciding);
		ready.await(5, TimeUnit.SECONDS);
		start.countDown();

		assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
			.containsExactlyInAnyOrder(true, false);
		assertThat(jdbc.queryForObject("""
			SELECT count(*) FROM outbox_event
			WHERE aggregate_type = 'APPEAL_CASE' AND event_type = 'MODERATION_APPEAL_RESOLVED' AND aggregate_id = ?
			""", Integer.class, filed.id())).isEqualTo(1);
	}

	@Test
	@DisplayName("INT-012: 접수 기간은 서비스에서도 DB에서도 앞당길 수 없다")
	void neverShortensAcceptanceWindow() {
		AppealCase filed = fileAppeal("extend-guard");
		Instant current = filed.expiresAt();

		assertThatThrownBy(() -> appealCaseService.extendExpiry(
			filed.id(), current.minus(Duration.ofDays(1)), 1L, new OperatorReason("TEST", "테스트 근거")))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.APPEAL_EXPIRY_NOT_EXTENDABLE);

		assertThatThrownBy(() -> jdbc.update("UPDATE appeal_case SET expires_at = ? WHERE id = ?",
			Timestamp.from(filed.windowStartedAt().plus(Duration.ofDays(183))), filed.id()))
			.isInstanceOf(DataIntegrityViolationException.class);

		AppealCase extended = appealCaseService.extendExpiry(
			filed.id(), current.plus(Duration.ofDays(30)), 1L, new OperatorReason("TEST", "테스트 근거"));
		assertThat(extended.expiresAt()).isEqualTo(current.plus(Duration.ofDays(30)));
	}

	@Test
	@DisplayName("INT-013: 만료된 appeal이 있어도 작성자는 새 답변을 제출할 수 있다")
	void expiredAppealDoesNotBlockNewSubmission() {
		FilterDecision decision = blockDecision("expired-appeal", now.minus(Duration.ofDays(200)));
		Instant windowStartedAt = now.minus(Duration.ofDays(200));
		appealCaseRepository.save(AppealCase.restore(null, FilterTargetType.ANSWER, answerId, decision.id(), authorId,
			AppealCaseStatus.OPEN, windowStartedAt, windowStartedAt.plus(Duration.ofDays(184)),
			AppealAcceptanceReasonCode.WITHIN_WINDOW, null, null, null, null, windowStartedAt));

		// 답변은 recipient slot당 하나이므로, "새 콘텐츠 제출"은 새 slot에서 확인한다.
		long nextSlot = postRecipient(directionPost(questionId, "appeal-post-2"));
		Answer resubmitted = answerRepository.save(Answer.submit(nextSlot, authorId, "appeal-answer-2",
			"만료 후 새 답변", REGION, BigDecimal.valueOf(90), "NEAR", now, 5000L));

		assertThat(resubmitted.getId()).isPositive();
		assertThat(answerRepository.findById(resubmitted.getId())).isPresent();
	}

	@Test
	@DisplayName("INT-016: 작성자는 자신의 appeal만 조회하고 검토자 큐는 전체 OPEN case를 본다")
	void findsOnlyOwnAppeals() {
		AppealCase mine = fileAppeal("list-mine");
		Instant windowStartedAt = now.minus(Duration.ofDays(3));
		appealCaseRepository.save(AppealCase.restore(null, FilterTargetType.ANSWER, answerId + 9999L,
			blockDecision("list-other", now.minus(Duration.ofDays(3))).id(), senderId, AppealCaseStatus.OPEN,
			windowStartedAt, windowStartedAt.plus(Duration.ofDays(184)),
			AppealAcceptanceReasonCode.WITHIN_WINDOW, null, null, null, null, windowStartedAt));

		assertThat(appealCaseService.findMine(authorId)).extracting(AppealCase::id).containsExactly(mine.id());
		assertThat(appealCaseService.findQueue(50)).hasSize(2);
	}

	@Test
	@DisplayName("INT-015: 검토자 endpoint는 운영자 세션이 있어야 호출할 수 있다")
	void reviewerEndpointsRequireOperatorSession() throws Exception {
		AppealCase filed = fileAppeal("endpoint-decide");
		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode csrfData = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");

		mockMvc.perform(post("/admin/filtering/appeal-cases/%d/decide".formatted(filed.id()))
				.header(csrfData.get("headerName").asText(), csrfData.get("token").asText())
				.cookie(issued.getResponse().getCookies())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"UPHOLD_HIDDEN\",\"reason\":{\"reasonCode\":\"TEST\",\"reasonText\":\"검토 결과 유지\"}}"))
			.andExpect(status().isUnauthorized());
		assertThat(appealCaseRepository.findById(filed.id()).orElseThrow().status())
			.isEqualTo(AppealCaseStatus.OPEN);

		OperatorSession session = login();
		mockMvc.perform(withCsrf(withSession(
				post("/admin/filtering/appeal-cases/%d/decide".formatted(filed.id())), session), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"UPHOLD_HIDDEN\",\"reason\":{\"reasonCode\":\"TEST\",\"reasonText\":\"검토 결과 유지\"}}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("RESOLVED"))
			.andExpect(jsonPath("$.data.decision").value("UPHOLD_HIDDEN"));
	}

	private AppealCase fileAppeal(String idempotencyKey) {
		FilterDecision decision = blockDecision(idempotencyKey, now.minus(Duration.ofDays(10)));
		return appealCaseService.file(FilterTargetType.ANSWER, answerId, decision.id(), authorId);
	}

	private FilterDecision blockDecision(String idempotencyKey, Instant decidedAt) {
		FilterJob job = filterJobRepository.save(FilterJob.create(
			FilterTarget.of(FilterTargetType.ANSWER, answerId), releaseId, idempotencyKey,
			now.plus(Duration.ofMinutes(10)), now));
		return filterDecisionRepository.save(
			FilterDecision.of(job.id(), 1, FilterVerdict.BLOCK, releaseId, MODEL_SNAPSHOT, decidedAt));
	}

	private String jobStatus(long jobId) {
		return jdbc.queryForObject("SELECT status FROM filter_job WHERE id = ?", String.class, jobId);
	}

	private int countAppealCases() {
		return jdbc.queryForObject("SELECT count(*) FROM appeal_case", Integer.class);
	}

	private int countOutboxEvents() {
		return jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class);
	}

	private long promotedRelease() {
		FilterRelease candidate = releaseRegistryService.createCandidate(
			"norm-v1", "ruleset-v1", "category-map-v1", MODEL_SNAPSHOT);
		releaseRegistryService.markOfflineEvaluated(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		releaseRegistryService.designateShadow(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		releaseRegistryService.designateCanary(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거"));
		return releaseRegistryService.promote(candidate.id(), 1L, new OperatorReason("TEST", "테스트 근거")).id();
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
			VALUES ('OPERATOR', 'ACTIVE', '이의제기 테스트 질문', 'TEXT', ?, ?, ?)
			RETURNING id
			""", Long.class, Timestamp.from(now.minusSeconds(10)), Timestamp.from(now), authorId);
	}

	private long directionPost(long questionId, String idempotencyKey) {
		return jdbc.queryForObject("""
			INSERT INTO direction_post
				(sender_id, approved_question_id, status, idempotency_key, body_text,
				 coarse_region_code, moderation_status, submitted_at, published_at, expires_at)
			VALUES (?, ?, 'ACTIVE', ?, '글', ?, 'PASSED', ?, ?, ?)
			RETURNING id
			""", Long.class, senderId, questionId, idempotencyKey, REGION, Timestamp.from(now), Timestamp.from(now),
			Timestamp.from(now.plus(1, ChronoUnit.HOURS)));
	}

	private long postRecipient(long postId) {
		return jdbc.queryForObject("""
			INSERT INTO post_recipient
				(post_id, recipient_id, status, distance_band, matched_bearing_deg, matched_region_code, matched_at,
				 inbound_bearing_deg, distance_m)
			VALUES (?, ?, 'AVAILABLE', 'NEAR', 10, ?, ?, 190, 5000)
			RETURNING id
			""", Long.class, postId, authorId, REGION, Timestamp.from(now));
	}

	private OperatorSession login() throws Exception {
		jdbc.update("DELETE FROM operator_credential");
		operatorSeedService.seedIfAbsent(
			LoginId.of(LOGIN_ID), new RawPassword(PASSWORD), "qello-operator", REGION, "ko-KR", "Asia/Seoul");

		MvcResult issued = mockMvc.perform(get("/admin/csrf")).andReturn();
		JsonNode data = objectMapper.readTree(issued.getResponse().getContentAsString()).get("data");
		String csrfHeaderName = data.get("headerName").asText();
		String csrfToken = data.get("token").asText();
		Cookie[] csrfCookies = issued.getResponse().getCookies();

		MvcResult loggedIn = mockMvc.perform(post("/admin/login")
				.header(csrfHeaderName, csrfToken)
				.cookie(csrfCookies)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(LOGIN_ID, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn();
		return new OperatorSession(csrfHeaderName, csrfToken, csrfCookies, loggedIn.getResponse().getCookie("SESSION"));
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withSession(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, OperatorSession session
	) {
		return request.cookie(session.sessionCookie());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withCsrf(
		org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, OperatorSession session
	) {
		return request.header(session.csrfHeaderName(), session.csrfToken()).cookie(session.csrfCookies());
	}

	private record OperatorSession(String csrfHeaderName, String csrfToken, Cookie[] csrfCookies,
		Cookie sessionCookie) {
	}
}
