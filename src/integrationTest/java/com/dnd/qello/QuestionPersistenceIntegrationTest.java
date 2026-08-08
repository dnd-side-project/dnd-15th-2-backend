package com.dnd.qello;

/**
 * Created at: 2026-08-03T20:10:00+09:00
 * Source scenario: TEST-PLAN-GH-38-QUESTION-PERSISTENCE-INT-001 through INT-009
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.question.repository.QuestionAssignmentRepository;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.dnd.qello.question.repository.QuestionProposalReviewRepository;
import com.dnd.qello.question.service.QuestionAssignmentService;
import com.dnd.qello.question.service.QuestionReviewService;

@SpringBootTest
@ActiveProfiles({"test", "question-persistence"})
@Import(QuestionPersistenceIntegrationTest.TestClockConfiguration.class)
class QuestionPersistenceIntegrationTest extends PostgisContainerIntegrationTestSupport {

	private static final String REGION_CODE = "TEST-QUESTION-38";
	private static final Instant FIRST = Instant.parse("2026-08-03T09:00:00Z");
	private static final Instant ACTIVE_FROM = Instant.parse("2026-08-03T10:00:00Z");
	private static final Instant ACTIVE_UNTIL = Instant.parse("2026-08-03T11:00:00Z");

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private QuestionProposalRepository proposalRepository;

	@Autowired
	private QuestionProposalReviewRepository reviewRepository;

	@Autowired
	private ApprovedQuestionRepository approvedQuestionRepository;

	@Autowired
	private QuestionAssignmentRepository assignmentRepository;

	@Autowired
	private QuestionReviewService reviewService;

	@Autowired
	private QuestionAssignmentService assignmentService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void resetDatabaseAndClock() {
		clock.setInstant(FIRST);
		jdbcTemplate.update("DELETE FROM question_assignment");
		jdbcTemplate.update("DELETE FROM question_assignment_cycle");
		jdbcTemplate.update("DELETE FROM approved_question");
		jdbcTemplate.update("DELETE FROM question_proposal_review");
		jdbcTemplate.update("DELETE FROM question_proposal");
		jdbcTemplate.update("DELETE FROM user_account WHERE coarse_region_code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = ?", REGION_CODE);
		jdbcTemplate.update("DELETE FROM region_code WHERE code = 'KR'");
		jdbcTemplate.update("""
			INSERT INTO region_code (code, parent_code, display_name, level)
			VALUES ('KR', NULL, 'Korea', 'COUNTRY'), (?, 'KR', 'Question Test Region', 'REGION')
			""", REGION_CODE);
	}

	@Test
	@DisplayName("Flyway V1과 Hibernate validate가 5개 질문 Entity를 실제 PostgreSQL에서 수용한다")
	void startsWithValidatedQuestionMappings() {
		Integer questionTableCount = jdbcTemplate.queryForObject("""
			SELECT count(*)
			FROM information_schema.tables
			WHERE table_schema = 'public'
			  AND table_name IN (
				'question_proposal', 'question_proposal_review', 'approved_question',
				'question_assignment_cycle', 'question_assignment'
			  )
			""", Integer.class);

		assertThat(questionTableCount).isEqualTo(5);
	}

	@Test
	@DisplayName("DRAFT 문구는 수정되지만 제출 후 question_proposal trigger가 문구 수정을 거절한다")
	void enforcesProposalTextImmutabilityAfterSubmit() {
		Long proposerId = createAccount("proposer").getId();
		QuestionProposal draft = proposalRepository.save(
			QuestionProposal.create(proposerId, "초기 질문"));
		QuestionProposal revised = proposalRepository.save(draft.reviseDraft("수정된 질문"));
		QuestionProposal submitted = reviewService.submit(revised.getId(), FIRST);

		assertThat(submitted.getProposedText()).isEqualTo("수정된 질문");
		assertThatThrownBy(() -> jdbcTemplate.update(
			"UPDATE question_proposal SET proposed_text = ? WHERE id = ?",
			"제출 후 변경", submitted.getId()))
			.isInstanceOf(DataAccessException.class);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT proposed_text FROM question_proposal WHERE id = ?", String.class, submitted.getId()))
			.isEqualTo("수정된 질문");
	}

	@Test
	@DisplayName("반려 사유 없는 검수는 거절되고 유효한 반려는 review와 proposal을 함께 저장한다")
	void rejectsWithoutReasonAndAppendsValidReview() {
		Long proposerId = createAccount("reject-proposer").getId();
		Long reviewerId = createAccount("reviewer").getId();
		QuestionProposal underReview = reviewService.startReview(
			reviewService.submit(proposalRepository.save(
				QuestionProposal.create(proposerId, "반려 대상 질문")).getId(), FIRST).getId());

		assertThatThrownBy(() -> reviewService.reject(
			underReview.getId(), reviewerId, " ", ACTIVE_FROM))
			.isInstanceOf(com.dnd.qello.question.error.QuestionException.class)
			.hasFieldOrPropertyWithValue(
				"errorCode", com.dnd.qello.question.error.QuestionErrorCode.REQUIRED_VALUE_MISSING);
		assertThat(reviewRepository.findAllByProposalId(underReview.getId())).isEmpty();

		reviewService.reject(underReview.getId(), reviewerId, "정책에 맞지 않습니다", ACTIVE_FROM);
		assertThat(proposalRepository.findById(underReview.getId()).orElseThrow().getStatus())
			.isEqualTo(com.dnd.qello.question.domain.QuestionProposalStatus.REJECTED);
		assertThat(reviewRepository.findAllByProposalId(underReview.getId())).hasSize(1);
	}

	@Test
	@DisplayName("승인 transaction은 review, APPROVED proposal, ACTIVE question을 함께 반영한다")
	void approvesProposalAtomically() {
		Long proposerId = createAccount("approve-proposer").getId();
		Long reviewerId = createAccount("approver").getId();
		QuestionProposal proposal = proposalRepository.save(
			QuestionProposal.create(proposerId, "승인할 질문"));
		reviewService.startReview(reviewService.submit(proposal.getId(), FIRST).getId());

		ApprovedQuestion approved = reviewService.approve(
			proposal.getId(), reviewerId, AnswerFormat.TEXT, ACTIVE_FROM, ACTIVE_UNTIL, ACTIVE_FROM);

		assertThat(approved.getStatus()).isEqualTo(com.dnd.qello.question.domain.ApprovedQuestionStatus.ACTIVE);
		assertThat(approvedQuestionRepository.findAssignableAt(ACTIVE_FROM)).extracting(ApprovedQuestion::getId)
			.containsExactly(approved.getId());
		assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
			.isEqualTo(com.dnd.qello.question.domain.QuestionProposalStatus.APPROVED);
		assertThat(reviewRepository.findAllByProposalId(proposal.getId())).hasSize(1);
	}

	@Test
	@DisplayName("승인 질문 source unique 충돌은 review와 proposal 상태까지 transaction rollback한다")
	void rollsBackApprovalWhenApprovedQuestionInsertFails() {
		Long proposerId = createAccount("rollback-proposer").getId();
		Long reviewerId = createAccount("rollback-reviewer").getId();
		QuestionProposal proposal = proposalRepository.save(
			QuestionProposal.create(proposerId, "rollback 질문"));
		reviewService.startReview(reviewService.submit(proposal.getId(), FIRST).getId());
		approvedQuestionRepository.save(ApprovedQuestion.fromUserProposalPending(
			proposal.getId(), proposal.getProposedText(), AnswerFormat.TEXT));

		assertThatThrownBy(() -> reviewService.approve(
			proposal.getId(), reviewerId, AnswerFormat.TEXT, ACTIVE_FROM, ACTIVE_UNTIL, ACTIVE_FROM))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(proposalRepository.findById(proposal.getId()).orElseThrow().getStatus())
			.isEqualTo(com.dnd.qello.question.domain.QuestionProposalStatus.UNDER_REVIEW);
		assertThat(reviewRepository.findAllByProposalId(proposal.getId())).isEmpty();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM approved_question WHERE source_proposal_id = ?", Integer.class, proposal.getId()))
			.isEqualTo(1);
	}

	@Test
	@DisplayName("assignable query는 ACTIVE이며 activeFrom 이상이고 activeUntil 미만인 질문만 반환한다")
	void findsOnlyQuestionsInsideActiveRange() {
		Long operatorId = createAccount("operator").getId();
		ApprovedQuestion active = approvedQuestionRepository.save(ApprovedQuestion.activeOperatorQuestion(
			"활성 질문", AnswerFormat.BOTH, ACTIVE_FROM, ACTIVE_UNTIL, FIRST, operatorId));
		approvedQuestionRepository.save(ApprovedQuestion.fromUserProposalPending(
			createProposal(operatorId, "검수 대기 질문").getId(), "검수 대기 질문", AnswerFormat.TEXT));

		assertThat(approvedQuestionRepository.findAssignableAt(ACTIVE_FROM)).extracting(ApprovedQuestion::getId)
			.containsExactly(active.getId());
		assertThat(approvedQuestionRepository.findAssignableAt(ACTIVE_UNTIL)).isEmpty();
	}

	@Test
	@DisplayName("approved_question trigger는 승인된 질문 문구 변경을 거절한다")
	void enforcesApprovedQuestionTextImmutability() {
		Long operatorId = createAccount("immutable-question").getId();
		ApprovedQuestion question = approvedQuestionRepository.save(ApprovedQuestion.activeOperatorQuestion(
			"변경할 수 없는 질문", AnswerFormat.TEXT, ACTIVE_FROM, ACTIVE_UNTIL, FIRST, operatorId));

		assertThatThrownBy(() -> jdbcTemplate.update(
			"UPDATE approved_question SET question_text = ? WHERE id = ?",
			"변경된 질문", question.getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT question_text FROM approved_question WHERE id = ?", String.class, question.getId()))
			.isEqualTo("변경할 수 없는 질문");
	}

	@Test
	@DisplayName("cycle과 assignment는 서버 절대 시각을 보존하고 중복 key와 child를 거절한다")
	void persistsCycleAndRejectsDuplicates() {
		Long userId = createAccount("assignment-user").getId();
		ApprovedQuestion question = approvedQuestionRepository.save(ApprovedQuestion.activeOperatorQuestion(
			"배정 질문", AnswerFormat.TEXT, ACTIVE_FROM, ACTIVE_UNTIL, FIRST, userId));
		Instant assignedAt = ACTIVE_FROM.plusSeconds(60);
		QuestionAssignmentService.AssignmentBatch batch = assignmentService.assign(
			new QuestionAssignmentService.CycleCommand(
				userId, "cycle-1", "pool-v1", ACTIVE_FROM, ACTIVE_UNTIL,
				List.of(new QuestionAssignmentService.AssignmentCommand(question.getId(), 1, assignedAt))));

		assertThat(batch.cycle().getStartsAt()).isEqualTo(ACTIVE_FROM);
		assertThat(batch.cycle().getEndsAt()).isEqualTo(ACTIVE_UNTIL);
		assertThat(assignmentRepository.findAllByCycleId(batch.cycle().getId()))
			.extracting(QuestionAssignment::getApprovedQuestionId).containsExactly(question.getId());
		assertThatThrownBy(() -> assignmentService.assign(new QuestionAssignmentService.CycleCommand(
			userId, "cycle-1", "pool-v1", ACTIVE_FROM, ACTIVE_UNTIL, List.of())))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> assignmentService.assign(new QuestionAssignmentService.CycleCommand(
			userId, "cycle-2", "pool-v1", ACTIVE_FROM, ACTIVE_UNTIL,
			List.of(
				new QuestionAssignmentService.AssignmentCommand(question.getId(), 1, assignedAt),
				new QuestionAssignmentService.AssignmentCommand(question.getId(), 1, assignedAt)))))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT count(*) FROM question_assignment_cycle WHERE cycle_key = 'cycle-2'", Integer.class))
			.isZero();
	}

	@Test
	@DisplayName("배정 service는 비활성 질문을 거절하고 DB는 assignedAt 이전 viewed/used 시각을 거절한다")
	void enforcesAssignmentActivityAndTimestampConstraints() {
		Long userId = createAccount("time-user").getId();
		ApprovedQuestion question = approvedQuestionRepository.save(ApprovedQuestion.activeOperatorQuestion(
			"만료 질문", AnswerFormat.TEXT, ACTIVE_FROM, ACTIVE_UNTIL, FIRST, userId));
		Instant afterExpiry = ACTIVE_UNTIL;

		assertThatThrownBy(() -> assignmentService.assign(new QuestionAssignmentService.CycleCommand(
			userId, "expired-cycle", "pool-v1", ACTIVE_FROM, ACTIVE_UNTIL,
			List.of(new QuestionAssignmentService.AssignmentCommand(question.getId(), 1, afterExpiry)))))
			.isInstanceOf(com.dnd.qello.question.error.QuestionException.class)
			.hasFieldOrPropertyWithValue(
				"errorCode", com.dnd.qello.question.error.QuestionErrorCode.QUESTION_NOT_ASSIGNABLE);

		QuestionAssignmentService.AssignmentBatch batch = assignmentService.assign(
			new QuestionAssignmentService.CycleCommand(
				userId, "timestamp-cycle", "pool-v1", ACTIVE_FROM, ACTIVE_UNTIL,
				List.of(new QuestionAssignmentService.AssignmentCommand(
					question.getId(), 1, ACTIVE_FROM.plusSeconds(1)))));
		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE question_assignment
			SET first_viewed_at = assigned_at - interval '1 second'
			WHERE id = ?
			""", batch.assignments().getFirst().getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbcTemplate.update("""
			UPDATE question_assignment
			SET used_at = assigned_at - interval '1 second'
			WHERE id = ?
			""", batch.assignments().getFirst().getId()))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Account createAccount(String nickname) {
		return accountRepository.save(Account.createUser("KR",
			REGION_CODE, "ko-KR", "Asia/Seoul", nickname));
	}

	private QuestionProposal createProposal(Long proposerId, String text) {
		return proposalRepository.save(QuestionProposal.create(proposerId, text));
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
