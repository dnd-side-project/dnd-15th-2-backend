/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-016
 */
package com.dnd.qello.answer.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.domain.AnswerModerationStatus;
import com.dnd.qello.answer.domain.AnswerStatus;
import com.dnd.qello.answer.repository.AnswerRepository;
import com.dnd.qello.filtering.domain.FilterTargetType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerPublicationBlockCheckerTest {

	private static final long ANSWER_ID = 501L;
	private static final long AUTHOR_ID = 77L;
	private static final Instant SUBMITTED_AT = Instant.parse("2026-10-01T00:00:00Z");
	// PublicationBlockChecker 계약상 사유 코드는 30자 이내다.
	private static final int REASON_MAX_LENGTH = 30;

	private AnswerRepository answerRepository;
	private AccountRepository accountRepository;
	private AnswerPublicationBlockChecker checker;

	@BeforeEach
	void setUp() {
		answerRepository = mock(AnswerRepository.class);
		accountRepository = mock(AccountRepository.class);
		checker = new AnswerPublicationBlockChecker(answerRepository, accountRepository);
		when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(Answer.restore(ANSWER_ID, 10L, AUTHOR_ID,
				AnswerStatus.PUBLISHED, "answer-key", "답변", "KR-11", BigDecimal.ZERO, "NEAR",
				AnswerModerationStatus.PASSED, SUBMITTED_AT, SUBMITTED_AT, null, 100L, null, 0)));
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("authorStatusReasons")
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-016: 작성자 상태별 공개 금지 사유는 ACTIVE만 없고 탈퇴 유예 중이면 ACCOUNT_WITHDRAWAL_PENDING이다(A6)")
	void returnsBlockReasonByAuthorStatus(AccountStatus authorStatus, String expectedReason) {
		Instant deletedAt = authorStatus == AccountStatus.DELETED ? SUBMITTED_AT.plusSeconds(60) : null;
		Instant withdrawalRequestedAt = authorStatus == AccountStatus.WITHDRAWAL_PENDING
				? SUBMITTED_AT.plusSeconds(60)
				: null;
		when(accountRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(Account.restore(AUTHOR_ID,
				AccountRole.USER, authorStatus, "KR", "KR-11", "ko-KR", "Asia/Seoul", "작성자", deletedAt,
				withdrawalRequestedAt)));

		Optional<String> reason = checker.findPublicationBlockReason(FilterTargetType.ANSWER, ANSWER_ID);

		assertThat(reason).isEqualTo(Optional.ofNullable(expectedReason));
		reason.ifPresent(value -> assertThat(value).hasSizeLessThanOrEqualTo(REASON_MAX_LENGTH));
	}

	private static Stream<Arguments> authorStatusReasons() {
		return Stream.of(
				Arguments.of(AccountStatus.ACTIVE, null),
				Arguments.of(AccountStatus.BLOCKED, "ACCOUNT_BLOCKED"),
				Arguments.of(AccountStatus.DELETED, "ACCOUNT_DELETED"),
				Arguments.of(AccountStatus.WITHDRAWAL_PENDING, "ACCOUNT_WITHDRAWAL_PENDING"));
	}

}
