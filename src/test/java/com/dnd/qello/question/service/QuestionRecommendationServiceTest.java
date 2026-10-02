/**
 * Created at: 2026-10-02T16:41:59+09:00
 * Source scenario: TEST-PLAN-GH-301-QUESTION-RECOMMENDATION-LISTING-UNIT-001, UNIT-002
 */
package com.dnd.qello.question.service;

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
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.ApprovedQuestionSourceType;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionRecommendationServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-02T07:00:00Z");
	private static final long USER_ID = 11L;

	@Mock
	private AccountRepository accountRepository;
	@Mock
	private ApprovedQuestionRepository approvedQuestionRepository;

	private QuestionRecommendationService service;

	@BeforeEach
	void setUp() {
		service = new QuestionRecommendationService(
				accountRepository, approvedQuestionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("ACTIVE USER 계정은 Clock 시각 기준 활성 질문을 저장소 순서 그대로 받는다")
	void returnsAssignableQuestionsAtClockInstant() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		ApprovedQuestion first = activeQuestion(101L, "첫 질문");
		ApprovedQuestion second = activeQuestion(102L, "둘째 질문");
		when(approvedQuestionRepository.findAssignableAt(NOW)).thenReturn(List.of(first, second));

		List<ApprovedQuestion> result = service.findRecommendations(USER_ID);

		assertThat(result).containsExactly(first, second);
		verify(approvedQuestionRepository).findAssignableAt(NOW);
	}

	@Test
	@DisplayName("활성 질문이 없으면 오류 없이 빈 목록을 반환한다")
	void returnsEmptyListWhenNoQuestionIsActive() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
		when(approvedQuestionRepository.findAssignableAt(NOW)).thenReturn(List.of());

		assertThat(service.findRecommendations(USER_ID)).isEmpty();
	}

	@Test
	@DisplayName("OPERATOR 계정은 거부되고 질문 저장소를 조회하지 않는다")
	void rejectsOperatorAccount() {
		Account operator = Account.createOperator("KR-11", "ko-KR", "Asia/Seoul", "운영자");
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.of(restoreWith(operator, operator.getStatus())));

		assertThatThrownBy(() -> service.findRecommendations(USER_ID))
				.isInstanceOf(QuestionException.class)
				.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
						.isEqualTo(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE));
		verify(approvedQuestionRepository, never()).findAssignableAt(any());
	}

	@Test
	@DisplayName("ACTIVE가 아닌 USER 계정은 거부되고 질문 저장소를 조회하지 않는다")
	void rejectsInactiveUserAccount() {
		when(accountRepository.findById(USER_ID))
			.thenReturn(Optional.of(restoreWith(activeUser(), AccountStatus.BLOCKED)));

		assertThatThrownBy(() -> service.findRecommendations(USER_ID))
			.isInstanceOf(QuestionException.class)
			.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
				.isEqualTo(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_ELIGIBLE));
		verify(approvedQuestionRepository, never()).findAssignableAt(any());
	}

	@Test
	@DisplayName("계정이 없으면 거부되고 질문 저장소를 조회하지 않는다")
	void rejectsMissingAccount() {
		when(accountRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findRecommendations(USER_ID))
			.isInstanceOf(QuestionException.class)
			.satisfies(exception -> assertThat(((QuestionException) exception).getErrorCode())
				.isEqualTo(QuestionErrorCode.PROPOSER_ACCOUNT_NOT_FOUND));
		verify(approvedQuestionRepository, never()).findAssignableAt(any());
	}

	private static Account activeUser() {
		return Account.createUser("KR", "KR-11", "ko-KR", "Asia/Seoul", "테스터");
	}

	private static Account restoreWith(Account source, AccountStatus status) {
		return Account.restore(USER_ID, source.getRole(), status,
				source.getCountryCode(), source.getCoarseRegionCode(), source.getLocale(),
				source.getTimezone(), source.getNickname(), source.getDeletedAt());
	}

	private static ApprovedQuestion activeQuestion(long id, String text) {
		return ApprovedQuestion.restore(id, null, ApprovedQuestionSourceType.OPERATOR,
				ApprovedQuestionStatus.ACTIVE, text, AnswerFormat.BOTH,
				NOW.minusSeconds(3600), null, NOW.minusSeconds(3600), 1L, NOW.minusSeconds(3600));
	}
}
