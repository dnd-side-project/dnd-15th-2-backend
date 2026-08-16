package com.dnd.qello;

/**
 * Created at: 2026-08-16T02:00:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-INT-011 through INT-015,
 * TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION-INT-003, INT-004,
 * INT-006 through INT-008
 *
 * GH-144 식별자는 정식 계획 없이 병합한 예외 승인분이며,
 * TEST-PLAN-GH-145-QUESTION-PROPOSAL-NOTIFICATION이 승계했다(계획 5.2 참고).
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.dnd.qello.question.repository.QuestionProposalReviewRepository;
import com.dnd.qello.question.service.QuestionProposalApplicationService;
import com.dnd.qello.question.service.QuestionReviewService;

@SpringBootTest
@ActiveProfiles({"test", "question-proposal-api"})
@Import(QuestionProposalApiIntegrationTest.TestClockConfiguration.class)
class QuestionProposalApiIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-QUESTION-144";
	private static final Instant FIRST = Instant.parse("2026-08-16T09:00:00Z");

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private QuestionProposalRepository proposalRepository;

	@Autowired
	private QuestionProposalReviewRepository reviewRepository;

	@Autowired
	private ApprovedQuestionRepository approvedQuestionRepository;

	@Autowired
	private QuestionReviewService reviewService;

	@Autowired
	private QuestionProposalApplicationService applicationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetDatabaseAndClock() {
		clock.setInstant(FIRST);
		// 이전 테스트가 중간에 실패해 trigger가 남았을 수 있으므로 방어적으로 제거한다.
		unblockQuestionProposalOutboxInsert();
		jdbcTemplate.update("DELETE FROM outbox_event WHERE aggregate_type = 'QUESTION_PROPOSAL'");
		jdbcTemplate.update("DELETE FROM approved_question");
		jdbcTemplate.update("DELETE FROM question_proposal_review");
		jdbcTemplate.update("DELETE FROM question_proposal");
		jdbcTemplate.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = 'KR'");
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY'), (?, 'KR', 'Question API Test Region', 'REGION')
			""", REGION_CODE);
	}

	@Test
	@DisplayName("propose는 새 행 하나만 생성하고 그 행이 바로 SUBMITTED 상태다")
	void proposeCreatesExactlyOneSubmittedRow() {
		long proposerId = createAccount("propose-user").getId();

		QuestionProposal submitted = reviewService.propose(proposerId, "제안 문구", FIRST);

		assertThat(submitted.getStatus()).isEqualTo(QuestionProposalStatus.SUBMITTED);
		assertThat(submitted.getSubmittedAt()).isEqualTo(FIRST);
		Integer rowCount = jdbcTemplate.queryForObject(
			"SELECT count(*) FROM question_proposal WHERE proposer_id = ?", Integer.class, proposerId);
		assertThat(rowCount).isEqualTo(1);
	}

	@Test
	@DisplayName("ACTIVE USER 계정은 application service로 제출하고 자신의 목록에서 최신순으로 조회한다")
	void applicationServiceSubmitsAndListsForActiveUser() {
		long proposerId = createAccount("list-user").getId();

		QuestionProposal first = applicationService.submit(proposerId, "첫 번째 제안");
		clock.setInstant(FIRST.plusSeconds(60));
		QuestionProposal second = applicationService.submit(proposerId, "두 번째 제안");

		assertThat(applicationService.findMine(proposerId))
			.extracting(QuestionProposal::getId)
			.containsExactly(second.getId(), first.getId());
	}

	@Test
	@DisplayName("OPERATOR 계정은 application service로 제출할 수 없다")
	void applicationServiceRejectsOperatorAccount() {
		Account operator = accountRepository.save(
			Account.createOperator(REGION_CODE, "ko-KR", "Asia/Seoul", "운영자"));

		assertThatThrownBy(() -> applicationService.submit(operator.getId(), "운영자 제안"))
			.isInstanceOf(QuestionException.class)
			.hasFieldOrPropertyWithValue("errorCode", QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM question_proposal WHERE proposer_id = ?", Integer.class, operator.getId()))
			.isEqualTo(0);
	}

	@Test
	@DisplayName("propose로 제출한 제안은 검수·승인을 거쳐 배정 가능한 승인 질문이 된다")
	void proposeFlowsThroughReviewToApproval() {
		long proposerId = createAccount("approve-flow-proposer").getId();
		long reviewerId = createAccount("approve-flow-reviewer").getId();
		Instant activeFrom = FIRST.plusSeconds(3600);
		Instant activeUntil = FIRST.plusSeconds(7200);

		QuestionProposal submitted = applicationService.submit(proposerId, "승인될 제안");
		reviewService.startReview(submitted.getId());
		ApprovedQuestion approved = reviewService.approve(
			submitted.getId(), reviewerId, AnswerFormat.TEXT, activeFrom, activeUntil, FIRST);

		assertThat(approvedQuestionRepository.findAssignableAt(activeFrom))
			.extracting(ApprovedQuestion::getId)
			.containsExactly(approved.getId());
		assertThat(proposalRepository.findById(submitted.getId()).orElseThrow().getStatus())
			.isEqualTo(QuestionProposalStatus.APPROVED);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT payload::text FROM outbox_event WHERE dedup_key = ?", String.class,
			"question-proposal-reviewed:" + submitted.getId()))
			.contains("\"decision\": \"APPROVED\"")
			.contains("\"proposerId\": " + proposerId);
	}

	@Test
	@DisplayName("propose로 제출한 제안은 검수 후 반려되면 사유가 append-only로 남는다")
	void proposeFlowsThroughReviewToRejection() {
		long proposerId = createAccount("reject-flow-proposer").getId();
		long reviewerId = createAccount("reject-flow-reviewer").getId();

		QuestionProposal submitted = applicationService.submit(proposerId, "반려될 제안");
		reviewService.startReview(submitted.getId());
		reviewService.reject(submitted.getId(), reviewerId, "정책에 맞지 않습니다", FIRST);

		assertThat(proposalRepository.findById(submitted.getId()).orElseThrow().getStatus())
			.isEqualTo(QuestionProposalStatus.REJECTED);
		assertThat(reviewRepository.findAllByProposalId(submitted.getId()))
			.hasSize(1)
			.first()
			.satisfies(review -> assertThat(review.getReason()).isEqualTo("정책에 맞지 않습니다"));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT payload::text FROM outbox_event WHERE dedup_key = ?", String.class,
			"question-proposal-reviewed:" + submitted.getId()))
			.contains("\"decision\": \"REJECTED\"")
			.contains("\"proposerId\": " + proposerId);
	}

	@Test
	@DisplayName("같은 dedupKey outbox 행이 선점돼 있으면 판정은 성공하고 이벤트는 1건으로 유지된다")
	void reviewKeepsSingleOutboxEventWhenDedupKeyAlreadyTaken() {
		long proposerId = createAccount("dedup-proposer").getId();
		long reviewerId = createAccount("dedup-reviewer").getId();
		QuestionProposal submitted = applicationService.submit(proposerId, "중복 dedupKey 대상 제안");
		reviewService.startReview(submitted.getId());
		String dedupKey = "question-proposal-reviewed:" + submitted.getId();
		jdbcTemplate.update("""
			INSERT INTO outbox_event (aggregate_type, aggregate_id, event_type, dedup_key, payload)
			VALUES ('QUESTION_PROPOSAL', ?, 'QUESTION_PROPOSAL_REVIEWED', ?, '{"preexisting": true}'::jsonb)
			""", submitted.getId(), dedupKey);

		reviewService.reject(submitted.getId(), reviewerId, "정책에 맞지 않습니다", FIRST);

		assertThat(proposalRepository.findById(submitted.getId()).orElseThrow().getStatus())
			.isEqualTo(QuestionProposalStatus.REJECTED);
		assertThat(reviewRepository.findAllByProposalId(submitted.getId())).hasSize(1);
		assertThat(countOutboxEvents(dedupKey)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT payload ->> 'preexisting' FROM outbox_event WHERE dedup_key = ?", String.class, dedupKey))
			.isEqualTo("true");
	}

	@Test
	@DisplayName("outbox 저장이 실패하면 판정과 이력 쓰기가 모두 롤백된다")
	void outboxFailureRollsBackReviewAndProposal() {
		long proposerId = createAccount("outbox-rollback-proposer").getId();
		long reviewerId = createAccount("outbox-rollback-reviewer").getId();
		QuestionProposal submitted = applicationService.submit(proposerId, "롤백 검증 제안");
		reviewService.startReview(submitted.getId());

		// publishReviewed()는 dedupKey 사전 조회에서 조기 반환하므로, 기존 행을 심는
		// 방식으로는 저장 실패를 만들 수 없다. 삽입 자체를 거부하는 trigger를 걸어
		// 사전 조회를 통과한 뒤의 저장 실패를 재현한다.
		blockQuestionProposalOutboxInsert();
		try {
			assertThatThrownBy(() -> reviewService.reject(
				submitted.getId(), reviewerId, "정책에 맞지 않습니다", FIRST))
				.isInstanceOf(DataIntegrityViolationException.class);
		} finally {
			unblockQuestionProposalOutboxInsert();
		}

		assertThat(proposalRepository.findById(submitted.getId()).orElseThrow().getStatus())
			.isEqualTo(QuestionProposalStatus.UNDER_REVIEW);
		assertThat(reviewRepository.findAllByProposalId(submitted.getId())).isEmpty();
		assertThat(countOutboxEvents("question-proposal-reviewed:" + submitted.getId())).isEqualTo(0);
	}

	@Test
	@DisplayName("내 제안 목록은 다른 사용자의 제안을 반환하지 않는다")
	void findMineDoesNotLeakOtherProposals() {
		long mine = createAccount("isolation-mine").getId();
		long other = createAccount("isolation-other").getId();
		QuestionProposal first = applicationService.submit(mine, "내 첫 제안");
		clock.setInstant(FIRST.plusSeconds(60));
		QuestionProposal second = applicationService.submit(mine, "내 두 번째 제안");
		clock.setInstant(FIRST.plusSeconds(120));
		QuestionProposal foreign = applicationService.submit(other, "남의 제안");

		assertThat(applicationService.findMine(mine))
			.extracting(QuestionProposal::getId)
			.containsExactly(second.getId(), first.getId())
			.doesNotContain(foreign.getId());
		assertThat(applicationService.findMine(mine))
			.extracting(QuestionProposal::getProposedText)
			.doesNotContain("남의 제안");
	}

	@Test
	@DisplayName("propose가 제출 단계에서 실패하면 DRAFT 행을 남기지 않고 롤백한다")
	void proposeLeavesNoOrphanDraftWhenSubmitFails() {
		long proposerId = createAccount("rollback-proposer").getId();

		// submittedAt이 없으면 첫 save(DRAFT) 이후 submit() 단계에서 도메인 검증이 실패한다.
		assertThatThrownBy(() -> reviewService.propose(proposerId, "고아가 되면 안 되는 제안", null))
			.isInstanceOf(QuestionException.class)
			.hasFieldOrPropertyWithValue("errorCode", QuestionErrorCode.REQUIRED_VALUE_MISSING);

		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM question_proposal WHERE proposer_id = ?", Integer.class, proposerId))
			.isEqualTo(0);
	}

	@Test
	@DisplayName("이미 반려된 제안을 다시 반려하면 거절되고 이력·이벤트가 늘지 않는다")
	void secondRejectionIsBlockedAndAddsNoHistory() {
		long proposerId = createAccount("double-reject-proposer").getId();
		long reviewerId = createAccount("double-reject-reviewer").getId();
		QuestionProposal submitted = applicationService.submit(proposerId, "두 번 반려될 제안");
		reviewService.startReview(submitted.getId());
		reviewService.reject(submitted.getId(), reviewerId, "첫 번째 사유", FIRST);

		assertThatThrownBy(() -> reviewService.reject(submitted.getId(), reviewerId, "두 번째 사유", FIRST))
			.isInstanceOf(QuestionException.class)
			.hasFieldOrPropertyWithValue("errorCode", QuestionErrorCode.INVALID_PROPOSAL_STATUS);

		assertThat(reviewRepository.findAllByProposalId(submitted.getId())).hasSize(1);
		assertThat(countOutboxEvents("question-proposal-reviewed:" + submitted.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("발행된 outbox payload는 JSONB object로 파싱되고 판정·제안자 키를 갖는다")
	void publishedPayloadIsQueryableJsonObject() {
		long proposerId = createAccount("payload-proposer").getId();
		long reviewerId = createAccount("payload-reviewer").getId();
		QuestionProposal submitted = applicationService.submit(proposerId, "payload 검증 제안");
		reviewService.startReview(submitted.getId());
		reviewService.approve(submitted.getId(), reviewerId, AnswerFormat.TEXT,
			FIRST.plusSeconds(3600), FIRST.plusSeconds(7200), FIRST);
		String dedupKey = "question-proposal-reviewed:" + submitted.getId();

		assertThat(jdbcTemplate.queryForObject(
			"SELECT jsonb_typeof(payload) FROM outbox_event WHERE dedup_key = ?", String.class, dedupKey))
			.isEqualTo("object");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT payload ->> 'decision' FROM outbox_event WHERE dedup_key = ?", String.class, dedupKey))
			.isEqualTo("APPROVED");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT payload ->> 'proposalId' FROM outbox_event WHERE dedup_key = ?", String.class, dedupKey))
			.isEqualTo(String.valueOf(submitted.getId()));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT payload ->> 'proposerId' FROM outbox_event WHERE dedup_key = ?", String.class, dedupKey))
			.isEqualTo(String.valueOf(proposerId));
	}

	private Integer countOutboxEvents(String dedupKey) {
		return jdbcTemplate.queryForObject(
			"SELECT count(*) FROM outbox_event WHERE dedup_key = ?", Integer.class, dedupKey);
	}

	// ERRCODE 23514(check_violation)로 올려 Spring이 DataIntegrityViolationException으로
	// 변환하게 한다. 실제 제약 위반과 같은 경로로 트랜잭션이 실패한다.
	private void blockQuestionProposalOutboxInsert() {
		jdbcTemplate.execute("""
			CREATE OR REPLACE FUNCTION test_block_question_proposal_outbox()
			RETURNS TRIGGER LANGUAGE plpgsql AS $$
			BEGIN
				RAISE EXCEPTION 'test-injected outbox insert failure'
					USING ERRCODE = '23514';
			END;
			$$
			""");
		jdbcTemplate.execute("""
			CREATE TRIGGER tr_test_block_question_proposal_outbox
			BEFORE INSERT ON outbox_event
			FOR EACH ROW WHEN (NEW.aggregate_type = 'QUESTION_PROPOSAL')
			EXECUTE FUNCTION test_block_question_proposal_outbox()
			""");
	}

	private void unblockQuestionProposalOutboxInsert() {
		jdbcTemplate.execute(
			"DROP TRIGGER IF EXISTS tr_test_block_question_proposal_outbox ON outbox_event");
		jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_block_question_proposal_outbox()");
	}

	private Account createAccount(String nickname) {
		return accountRepository.save(Account.createUser("KR", REGION_CODE, "ko-KR", "Asia/Seoul", nickname));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class TestClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(FIRST, ZoneOffset.UTC);
		}
	}

	static final class MutableClock extends Clock {

		private final AtomicReference<Instant> current;
		private final ZoneId zone;

		private MutableClock(Instant initial, ZoneId zone) {
			this.current = new AtomicReference<>(initial);
			this.zone = zone;
		}

		void setInstant(Instant instant) {
			current.set(instant);
		}

		@Override
		public ZoneId getZone() {
			return zone;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return new MutableClock(current.get(), zone);
		}

		@Override
		public Instant instant() {
			return current.get();
		}
	}
}
