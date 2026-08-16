/**
 * Created at: 2026-08-16T00:25:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-UNIT-005 through UNIT-007
 * (임시 식별자 — /harness-test-plan 승인 전까지 이 시나리오 번호만 사용)
 */
package com.dnd.qello.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.ApprovedQuestionSourceType;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalReview;
import com.dnd.qello.question.domain.QuestionProposalReviewDecision;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.dnd.qello.question.repository.QuestionProposalReviewRepository;

@ExtendWith(MockitoExtension.class)
class QuestionReviewServiceTest {

	private static final Instant SUBMITTED_AT = Instant.parse("2026-08-16T00:00:00Z");
	private static final long PROPOSER_ID = 5L;
	private static final long PROPOSAL_ID = 7L;

	@Mock private QuestionProposalRepository proposalRepository;
	@Mock private QuestionProposalReviewRepository reviewRepository;
	@Mock private ApprovedQuestionRepository approvedQuestionRepository;
	@Mock private OutboxEventRepository outboxEventRepository;

	private QuestionReviewService service;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		service = new QuestionReviewService(
			proposalRepository, reviewRepository, approvedQuestionRepository, outboxEventRepository);
	}

	private QuestionProposal underReviewProposal() {
		return QuestionProposal.restore(PROPOSAL_ID, PROPOSER_ID, QuestionProposalStatus.UNDER_REVIEW,
			"제안 문구", null, SUBMITTED_AT, SUBMITTED_AT, SUBMITTED_AT);
	}

	@Test
	@DisplayName("propose는 DRAFT 생성과 SUBMITTED 전이를 한 번에 저장한다")
	void proposeCreatesAndSubmitsInOneCall() {
		QuestionProposal draft = QuestionProposal.create(PROPOSER_ID, "제안 문구");
		QuestionProposal savedDraft = QuestionProposal.restore(
			9L, PROPOSER_ID, QuestionProposalStatus.DRAFT, "제안 문구", null, null, null, null);
		QuestionProposal savedSubmitted = QuestionProposal.restore(
			9L, PROPOSER_ID, QuestionProposalStatus.SUBMITTED, "제안 문구", null, SUBMITTED_AT, SUBMITTED_AT, SUBMITTED_AT);
		when(proposalRepository.save(any(QuestionProposal.class))).thenReturn(savedDraft, savedSubmitted);

		QuestionProposal result = service.propose(PROPOSER_ID, "제안 문구", SUBMITTED_AT);

		assertThat(result.getStatus()).isEqualTo(QuestionProposalStatus.SUBMITTED);
		ArgumentCaptor<QuestionProposal> captor = ArgumentCaptor.forClass(QuestionProposal.class);
		verify(proposalRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo(QuestionProposalStatus.DRAFT);
		assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(QuestionProposalStatus.SUBMITTED);
	}

	@Test
	@DisplayName("존재하지 않는 proposalId로 검수를 시작하면 PROPOSAL_NOT_FOUND를 던진다")
	void startReviewOnMissingProposalThrowsNotFound() {
		when(proposalRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.startReview(404L))
			.isInstanceOf(QuestionException.class)
			.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
				.isEqualTo(QuestionErrorCode.PROPOSAL_NOT_FOUND));
	}

	@Test
	@DisplayName("존재하지 않는 proposalId를 반려하려 하면 PROPOSAL_NOT_FOUND를 던진다")
	void rejectOnMissingProposalThrowsNotFound() {
		when(proposalRepository.findById(404L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.reject(404L, 1L, "사유", SUBMITTED_AT))
			.isInstanceOf(QuestionException.class)
			.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
				.isEqualTo(QuestionErrorCode.PROPOSAL_NOT_FOUND));
	}

	@Test
	@DisplayName("반려하면 제안자에게 보낼 QUESTION_PROPOSAL_REVIEWED outbox event를 발행한다")
	void rejectPublishesOutboxEventForProposer() {
		when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(underReviewProposal()));
		when(outboxEventRepository.findByDedupKey(any())).thenReturn(Optional.empty());

		service.reject(PROPOSAL_ID, 1L, "정책에 맞지 않습니다", SUBMITTED_AT);

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());
		OutboxEvent event = captor.getValue();
		assertThat(event.aggregateType()).isEqualTo(OutboxAggregateType.QUESTION_PROPOSAL);
		assertThat(event.aggregateId()).isEqualTo(PROPOSAL_ID);
		assertThat(event.eventType()).isEqualTo(OutboxEventType.QUESTION_PROPOSAL_REVIEWED);
		assertThat(event.dedupKey()).isEqualTo("question-proposal-reviewed:" + PROPOSAL_ID);
		assertThat(event.payload()).contains("\"proposerId\":" + PROPOSER_ID).contains("\"decision\":\"REJECTED\"");
	}

	@Test
	@DisplayName("승인하면 제안자에게 보낼 QUESTION_PROPOSAL_REVIEWED outbox event를 발행한다")
	void approvePublishesOutboxEventForProposer() {
		when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(underReviewProposal()));
		when(outboxEventRepository.findByDedupKey(any())).thenReturn(Optional.empty());
		when(approvedQuestionRepository.save(any())).thenAnswer(invocation -> ApprovedQuestion.restore(
			1L, PROPOSAL_ID, ApprovedQuestionSourceType.USER_PROPOSAL, ApprovedQuestionStatus.ACTIVE,
			"제안 문구", AnswerFormat.TEXT, SUBMITTED_AT, SUBMITTED_AT.plusSeconds(3600), SUBMITTED_AT, 1L, SUBMITTED_AT));

		service.approve(PROPOSAL_ID, 1L, AnswerFormat.TEXT, SUBMITTED_AT, SUBMITTED_AT.plusSeconds(3600), SUBMITTED_AT);

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());
		assertThat(captor.getValue().payload()).contains("\"decision\":\"APPROVED\"");
	}

	@Test
	@DisplayName("같은 제안에 대한 outbox event가 이미 있으면 다시 발행하지 않는다")
	void rejectSkipsPublishingWhenDedupKeyAlreadyExists() {
		when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(underReviewProposal()));
		when(outboxEventRepository.findByDedupKey("question-proposal-reviewed:" + PROPOSAL_ID))
			.thenReturn(Optional.of(OutboxEvent.pending(OutboxAggregateType.QUESTION_PROPOSAL, PROPOSAL_ID,
				OutboxEventType.QUESTION_PROPOSAL_REVIEWED, "question-proposal-reviewed:" + PROPOSAL_ID,
				"{}", SUBMITTED_AT)));

		service.reject(PROPOSAL_ID, 1L, "정책에 맞지 않습니다", SUBMITTED_AT);

		verify(outboxEventRepository, never()).save(any());
	}
}
