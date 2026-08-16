/**
 * Created at: 2026-08-16T00:20:00+09:00
 * Source scenario: TEST-PLAN-GH-144-QUESTION-PROPOSAL-API-UNIT-001 through UNIT-004
 * (임시 식별자 — /harness-test-plan 승인 전까지 이 시나리오 번호만 사용)
 */
package com.dnd.qello.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.QuestionProposalRepository;

@ExtendWith(MockitoExtension.class)
class QuestionProposalApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-16T00:00:00Z");
	private static final long USER_ID = 11L;

	@Mock private AccountRepository accountRepository;
	@Mock private QuestionProposalRepository proposalRepository;
	@Mock private QuestionReviewService reviewService;

	private QuestionProposalApplicationService service;

	@BeforeEach
	void setUp() {
		service = new QuestionProposalApplicationService(
			accountRepository, proposalRepository, reviewService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("ACTIVE USER 계정은 제안을 제출하면 QuestionReviewService.propose로 위임된다")
	void submitDelegatesToReviewServiceForActiveUser() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		QuestionProposal submitted = QuestionProposal.restore(
			1L, USER_ID, com.dnd.qello.question.domain.QuestionProposalStatus.SUBMITTED,
			"제안 문구", null, NOW, NOW, NOW);
		when(reviewService.propose(USER_ID, "제안 문구", NOW)).thenReturn(submitted);

		QuestionProposal result = service.submit(USER_ID, "제안 문구");

		assertThat(result).isEqualTo(submitted);
		verify(reviewService).propose(USER_ID, "제안 문구", NOW);
	}

	@Test
	@DisplayName("존재하지 않는 계정은 PROPOSER_ACCOUNT_NOT_FOUND로 거부된다")
	void submitRejectsUnknownAccount() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.submit(USER_ID, "제안 문구"))
			.isInstanceOf(QuestionException.class)
			.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
				.isEqualTo(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_FOUND));
		verify(reviewService, never()).propose(anyLong(), anyString(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("OPERATOR 계정은 PROPOSER_ACCOUNT_NOT_ELIGIBLE로 거부된다")
	void submitRejectsNonUserRole() {
		Account operator = Account.createOperator("KR-11", "ko-KR", "Asia/Seoul", "운영자");
		Account restored = Account.restore(USER_ID, operator.getRole(), operator.getStatus(),
			operator.getCountryCode(), operator.getCoarseRegionCode(), operator.getLocale(),
			operator.getTimezone(), operator.getNickname(), operator.getDeletedAt());
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(restored));

		assertThatThrownBy(() -> service.submit(USER_ID, "제안 문구"))
			.isInstanceOf(QuestionException.class)
			.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
				.isEqualTo(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE));
	}

	@Test
	@DisplayName("내 제안 목록은 제안자 id로 필터링해 저장소에서 그대로 반환한다")
	void findMineReturnsRepositoryResult() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		QuestionProposal proposal = QuestionProposal.restore(
			2L, USER_ID, com.dnd.qello.question.domain.QuestionProposalStatus.DRAFT,
			"초안", null, null, NOW, NOW);
		when(proposalRepository.findAllByProposerIdOrderByCreatedAtDesc(USER_ID))
			.thenReturn(List.of(proposal));

		List<QuestionProposal> result = service.findMine(USER_ID);

		assertThat(result).containsExactly(proposal);
		verify(proposalRepository).findAllByProposerIdOrderByCreatedAtDesc(eq(USER_ID));
	}

	private static Account activeUser() {
		return Account.createUser("KR", "KR-11", "ko-KR", "Asia/Seoul", "테스터");
	}
}
