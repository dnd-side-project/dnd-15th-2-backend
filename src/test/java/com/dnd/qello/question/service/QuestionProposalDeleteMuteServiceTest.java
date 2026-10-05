/**
 * Created at: 2026-10-05T18:10:06+09:00
 * Source scenario: TEST-PLAN-GH-310-PROPOSAL-DELETE-MUTE-UNIT-008 through UNIT-010
 */
package com.dnd.qello.question.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.notification.repository.OutboxEventRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.dnd.qello.question.repository.QuestionProposalReviewRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionProposalDeleteMuteServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
	private static final Instant SUBMITTED_AT = Instant.parse("2026-10-01T00:00:00Z");
	private static final long OWNER_ID = 5L;
	private static final long OTHER_USER_ID = 6L;
	private static final long PROPOSAL_ID = 7L;
	private static final long REVIEWER_ID = 99L;

	@Mock
	private AccountRepository accountRepository;
	@Mock
	private QuestionProposalRepository proposalRepository;
	@Mock
	private QuestionProposalReviewRepository reviewRepository;
	@Mock
	private ApprovedQuestionRepository approvedQuestionRepository;
	@Mock
	private OutboxEventRepository outboxEventRepository;
	@Mock
	private QuestionReviewService mockedReviewService;

	private QuestionReviewService reviewService;

	@BeforeEach
	void setUp() {
		reviewService = new QuestionReviewService(
				proposalRepository, reviewRepository, approvedQuestionRepository, outboxEventRepository);
	}

	@Test
	@DisplayName("다른 사용자의 제안 삭제는 PROPOSAL_NOT_FOUND로 거부하고 저장하지 않는다")
	void deleteRejectsOtherUsersProposal() {
		when(proposalRepository.findByIdForUpdate(PROPOSAL_ID))
			.thenReturn(Optional.of(proposal(QuestionProposalStatus.SUBMITTED, null)));

		assertErrorCode(() -> reviewService.delete(PROPOSAL_ID, OTHER_USER_ID, NOW),
			QuestionErrorCode.PROPOSAL_NOT_FOUND);
		verify(proposalRepository, never()).save(any());
	}

	@Test
	@DisplayName("다른 사용자의 제안 알림 설정 변경은 PROPOSAL_NOT_FOUND로 거부하고 저장하지 않는다")
	void changeNotificationRejectsOtherUsersProposal() {
		when(proposalRepository.findByIdForUpdate(PROPOSAL_ID))
			.thenReturn(Optional.of(proposal(QuestionProposalStatus.SUBMITTED, null)));

		assertErrorCode(() -> reviewService.changeNotificationMuted(PROPOSAL_ID, OTHER_USER_ID, true),
			QuestionErrorCode.PROPOSAL_NOT_FOUND);
		verify(proposalRepository, never()).save(any());
	}

	@Test
	@DisplayName("본인 제안 삭제는 행 잠금으로 읽고 삭제 시각을 담아 저장한다")
	void deleteSavesOwnedProposalWithDeletedAt() {
		when(proposalRepository.findByIdForUpdate(PROPOSAL_ID))
			.thenReturn(Optional.of(proposal(QuestionProposalStatus.UNDER_REVIEW, null)));
		when(proposalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		reviewService.delete(PROPOSAL_ID, OWNER_ID, NOW);

		ArgumentCaptor<QuestionProposal> saved = ArgumentCaptor.forClass(QuestionProposal.class);
		verify(proposalRepository).save(saved.capture());
		assertThat(saved.getValue().getDeletedAt()).isEqualTo(NOW);
		assertThat(saved.getValue().getStatus()).isEqualTo(QuestionProposalStatus.UNDER_REVIEW);
	}

	@Test
	@DisplayName("이미 삭제한 본인 제안은 다시 저장하지 않고 그대로 성공한다")
	void deleteSkipsSaveWhenAlreadyDeleted() {
		QuestionProposal deleted = proposal(QuestionProposalStatus.APPROVED, SUBMITTED_AT);
		when(proposalRepository.findByIdForUpdate(PROPOSAL_ID)).thenReturn(Optional.of(deleted));

		QuestionProposal result = reviewService.delete(PROPOSAL_ID, OWNER_ID, NOW);

		assertThat(result.getDeletedAt()).isEqualTo(SUBMITTED_AT);
		verify(proposalRepository, never()).save(any());
	}

	@Test
	@DisplayName("ACTIVE USER가 아닌 계정의 삭제·알림 설정은 PROPOSER_ACCOUNT_NOT_ELIGIBLE로 거부하고 위임하지 않는다")
	void applicationServiceRejectsIneligibleAccount() {
		QuestionProposalApplicationService applicationService = new QuestionProposalApplicationService(
				accountRepository, proposalRepository, mockedReviewService, Clock.fixed(NOW, ZoneOffset.UTC));
		Account operator = Account.createOperator("KR-11", "ko-KR", "Asia/Seoul", "운영자");
		when(accountRepository.findById(OWNER_ID)).thenReturn(Optional.of(Account.restore(OWNER_ID,
				operator.getRole(), operator.getStatus(), operator.getCountryCode(), operator.getCoarseRegionCode(),
				operator.getLocale(), operator.getTimezone(), operator.getNickname(), operator.getDeletedAt())));

		assertErrorCode(() -> applicationService.delete(OWNER_ID, PROPOSAL_ID),
				QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE);
		assertErrorCode(() -> applicationService.changeNotificationMuted(OWNER_ID, PROPOSAL_ID, true),
				QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE);
		verify(mockedReviewService, never()).delete(anyLong(), anyLong(), any());
		verify(mockedReviewService, never()).changeNotificationMuted(anyLong(), anyLong(), anyBoolean());
	}

	@Test
	@DisplayName("ACTIVE USER의 삭제는 서버 시각으로 QuestionReviewService에 위임한다")
	void applicationServiceDelegatesDeleteWithClock() {
		QuestionProposalApplicationService applicationService = new QuestionProposalApplicationService(
				accountRepository, proposalRepository, mockedReviewService, Clock.fixed(NOW, ZoneOffset.UTC));
		when(accountRepository.findById(OWNER_ID))
				.thenReturn(Optional.of(Account.createUser("KR", "KR-11", "ko-KR", "Asia/Seoul", "테스터")));

		applicationService.delete(OWNER_ID, PROPOSAL_ID);

		verify(mockedReviewService).delete(PROPOSAL_ID, OWNER_ID, NOW);
	}

	@Test
	@DisplayName("삭제한 UNDER_REVIEW 제안의 승인·반려는 409이고 review·승인 질문·outbox를 저장하지 않는다")
	void reviewRejectsDeletedProposal() {
		when(proposalRepository.findByIdForUpdate(PROPOSAL_ID))
			.thenReturn(Optional.of(proposal(QuestionProposalStatus.UNDER_REVIEW, NOW)));

		assertErrorCode(() -> reviewService.approve(PROPOSAL_ID, REVIEWER_ID, AnswerFormat.TEXT, null, null, NOW),
			QuestionErrorCode.INVALID_PROPOSAL_STATUS);
		assertErrorCode(() -> reviewService.reject(PROPOSAL_ID, REVIEWER_ID, "부적절한 문구", NOW),
			QuestionErrorCode.INVALID_PROPOSAL_STATUS);
		verify(reviewRepository, never()).save(any());
		verify(approvedQuestionRepository, never()).save(any());
		verify(outboxEventRepository, never()).save(any());
		verify(proposalRepository, never()).save(any());
	}

	private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
			QuestionErrorCode expected) {
		assertThatThrownBy(call)
				.isInstanceOf(QuestionException.class)
				.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode()).isEqualTo(expected));
	}

	private static QuestionProposal proposal(QuestionProposalStatus status, Instant deletedAt) {
		String reason = status == QuestionProposalStatus.REJECTED ? "반려 사유" : null;
		return QuestionProposal.restore(PROPOSAL_ID, OWNER_ID, status, "제안 문구", reason,
				SUBMITTED_AT, SUBMITTED_AT, SUBMITTED_AT, deletedAt, false);
	}
}
