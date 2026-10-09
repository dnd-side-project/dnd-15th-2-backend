/*
 * Created at: 2026-10-09T18:14:46+09:00
 * Source scenario: TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-017
 */
package com.dnd.qello.account.sweep;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.dnd.qello.account.service.AccountWithdrawalCompletionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountWithdrawalSweepWorkerTest {

	private static final Instant CLOCK_NOW = Instant.parse("2026-10-09T09:00:00Z");
	private static final Instant COMMAND_AT = Instant.parse("2026-10-09T08:00:00Z");

	private AccountWithdrawalCompletionService completionService;
	private AccountWithdrawalSweepWorker worker;

	@BeforeEach
	void setUp() {
		completionService = mock(AccountWithdrawalCompletionService.class);
		worker = new AccountWithdrawalSweepWorker(completionService, Clock.fixed(CLOCK_NOW, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-017: 한 계정이 실패해도 나머지를 처리하고 scanned는 completed·ineligible·failed의 합이다")
	void isolatesFailureAndCountsEveryCandidate() {
		when(completionService.findDueUserIds(COMMAND_AT, 10)).thenReturn(List.of(1L, 2L, 3L));
		when(completionService.complete(1L, COMMAND_AT)).thenReturn(true);
		when(completionService.complete(2L, COMMAND_AT)).thenThrow(new IllegalStateException("optimistic lock"));
		when(completionService.complete(3L, COMMAND_AT)).thenReturn(false);

		AccountWithdrawalSweepWorker.BatchResult result = worker.processBatch(
			new AccountWithdrawalSweepWorker.BatchCommand(10, COMMAND_AT));

		assertThat(result).isEqualTo(new AccountWithdrawalSweepWorker.BatchResult(3, 1, 1, 1));
		assertThat(result.scanned()).isEqualTo(result.completed() + result.ineligible() + result.failed());
		verify(completionService).complete(1L, COMMAND_AT);
		verify(completionService).complete(2L, COMMAND_AT);
		verify(completionService).complete(3L, COMMAND_AT);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-017: at이 null이면 후보 조회와 계정별 완료 모두 Clock 시각을 쓴다")
	void usesClockWhenCommandAtIsNull() {
		when(completionService.findDueUserIds(CLOCK_NOW, 5)).thenReturn(List.of(9L));
		when(completionService.complete(9L, CLOCK_NOW)).thenReturn(true);

		AccountWithdrawalSweepWorker.BatchResult result = worker.processBatch(
			new AccountWithdrawalSweepWorker.BatchCommand(5, null));

		assertThat(result).isEqualTo(new AccountWithdrawalSweepWorker.BatchResult(1, 1, 0, 0));
		verify(completionService).findDueUserIds(CLOCK_NOW, 5);
		verify(completionService).complete(9L, CLOCK_NOW);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-017: 후보가 없으면 모든 수가 0이다")
	void returnsZeroCountsWithoutCandidates() {
		when(completionService.findDueUserIds(COMMAND_AT, 10)).thenReturn(List.of());

		AccountWithdrawalSweepWorker.BatchResult result = worker.processBatch(
			new AccountWithdrawalSweepWorker.BatchCommand(10, COMMAND_AT));

		assertThat(result).isEqualTo(new AccountWithdrawalSweepWorker.BatchResult(0, 0, 0, 0));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-017: limit이 0 이하인 command는 생성 시 거절한다")
	void rejectsNonPositiveLimit(int limit) {
		assertThatThrownBy(() -> new AccountWithdrawalSweepWorker.BatchCommand(limit, COMMAND_AT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("TEST-PLAN-GH-337-ACCOUNT-WITHDRAWAL-UNIT-017: command가 없으면 거절한다")
	void rejectsMissingCommand() {
		assertThatThrownBy(() -> worker.processBatch(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
