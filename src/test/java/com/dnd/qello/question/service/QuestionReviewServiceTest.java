/**
 * Created at: 2026-08-16T00:25:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-UNIT-005 through UNIT-007
 * (임시 식별자 — /harness-test-plan 승인 전까지 이 시나리오 번호만 사용)
 */
package com.dnd.qello.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.dnd.qello.question.domain.QuestionProposal;
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

	@Mock private QuestionProposalRepository proposalRepository;
	@Mock private QuestionProposalReviewRepository reviewRepository;
	@Mock private ApprovedQuestionRepository approvedQuestionRepository;

	private QuestionReviewService service;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		service = new QuestionReviewService(proposalRepository, reviewRepository, approvedQuestionRepository);
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
}
