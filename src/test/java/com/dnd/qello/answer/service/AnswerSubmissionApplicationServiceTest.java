/**
 * Created at: 2026-08-17T16:10:00+09:00
 * Source scenario: TEST-PLAN-GH-125-ANSWER-SUBMISSION-PUBLICATION-API-UNIT-001, UNIT-003
 */
package com.dnd.qello.answer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.error.AnswerErrorCode;
import com.dnd.qello.answer.error.AnswerException;

import java.math.BigDecimal;

class AnswerSubmissionApplicationServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
	private static final long AUTHOR_ID = 11L;

	private final AccountRepository accountRepository = mock(AccountRepository.class);
	private final AnswerSubmissionService submissionService = mock(AnswerSubmissionService.class);

	@Test
	@DisplayName("UNIT-003: ACTIVE USER 계정만 제출을 진행하고 서버 Clock으로 submittedAt을 채운다")
	void proceedsForActiveUserAccount() {
		when(accountRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(activeUser()));
		Answer stored = Answer.submit(1L, AUTHOR_ID, "key", "본문", "TEST", BigDecimal.valueOf(90), "NEAR", NOW, 5000L);
		when(submissionService.submit(eq(AUTHOR_ID), eq("key"), any())).thenReturn(stored);
		AnswerSubmissionApplicationService applicationService = applicationService();

		Answer result = applicationService.submit(AUTHOR_ID, "key", 1L, "본문", List.of());

		assertThat(result).isEqualTo(stored);
	}

	@Test
	@DisplayName("UNIT-003: 존재하지 않는 계정은 ACCOUNT_NOT_FOUND로 거절하고 하위 제출을 호출하지 않는다")
	void rejectsMissingAccount() {
		when(accountRepository.findById(AUTHOR_ID)).thenReturn(Optional.empty());
		AnswerSubmissionApplicationService applicationService = applicationService();

		assertThatThrownBy(() -> applicationService.submit(AUTHOR_ID, "key", 1L, "본문", List.of()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.ACCOUNT_NOT_FOUND);
		verify(submissionService, never()).submit(anyLong(), any(), any());
	}

	@ParameterizedTest(name = "{0} 상태의 USER 계정은 ACCOUNT_NOT_ELIGIBLE로 거절된다")
	@DisplayName("UNIT-003: BLOCKED/DELETED 계정은 답변을 제출할 수 없다")
	@EnumSource(value = AccountStatus.class, names = {"BLOCKED", "DELETED"})
	void rejectsInactiveAccount(AccountStatus status) {
		when(accountRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(userWith(status)));
		AnswerSubmissionApplicationService applicationService = applicationService();

		assertThatThrownBy(() -> applicationService.submit(AUTHOR_ID, "key", 1L, "본문", List.of()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.ACCOUNT_NOT_ELIGIBLE);
		verify(submissionService, never()).submit(anyLong(), any(), any());
	}

	@Test
	@DisplayName("UNIT-003: OPERATOR 계정은 답변 제출 요청 body의 author 경로로 사용할 수 없다")
	void rejectsOperatorAccount() {
		Account operator = Account.restore(
			AUTHOR_ID, AccountRole.OPERATOR, AccountStatus.ACTIVE, "KR", "TEST", "ko-KR", "Asia/Seoul", "op", null);
		when(accountRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(operator));
		AnswerSubmissionApplicationService applicationService = applicationService();

		assertThatThrownBy(() -> applicationService.submit(AUTHOR_ID, "key", 1L, "본문", List.of()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.ACCOUNT_NOT_ELIGIBLE);
	}

	@Test
	@DisplayName("UNIT-001: Idempotency-Key가 없거나 공백이면 거절하고 계정 조회조차 하지 않는다")
	void rejectsBlankIdempotencyKey() {
		AnswerSubmissionApplicationService applicationService = applicationService();

		assertThatThrownBy(() -> applicationService.submit(AUTHOR_ID, null, 1L, "본문", List.of()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_TEXT);
		assertThatThrownBy(() -> applicationService.submit(AUTHOR_ID, "   ", 1L, "본문", List.of()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_TEXT);
		verify(accountRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("UNIT-001: 200자를 넘는 Idempotency-Key는 거절한다")
	void rejectsOversizedIdempotencyKey() {
		AnswerSubmissionApplicationService applicationService = applicationService();
		String tooLong = "k".repeat(201);

		assertThatThrownBy(() -> applicationService.submit(AUTHOR_ID, tooLong, 1L, "본문", List.of()))
			.isInstanceOf(AnswerException.class)
			.hasFieldOrPropertyWithValue("errorCode", AnswerErrorCode.INVALID_TEXT);
	}

	private static Account activeUser() {
		return userWith(AccountStatus.ACTIVE);
	}

	private static Account userWith(AccountStatus status) {
		Instant deletedAt = status == AccountStatus.DELETED ? NOW : null;
		return Account.restore(
			AUTHOR_ID, AccountRole.USER, status, "KR", "TEST", "ko-KR", "Asia/Seoul", "nickname", deletedAt);
	}

	private AnswerSubmissionApplicationService applicationService() {
		return new AnswerSubmissionApplicationService(
			accountRepository, submissionService, Clock.fixed(NOW, ZoneOffset.UTC));
	}
}
