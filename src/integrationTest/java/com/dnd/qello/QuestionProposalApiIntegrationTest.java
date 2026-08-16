package com.dnd.qello;

/**
 * Created at: 2026-08-16T02:00:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-INT-011 through INT-015
 * (임시 식별자 — /harness-test-plan 승인 전까지 이 시나리오 번호만 사용)
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
