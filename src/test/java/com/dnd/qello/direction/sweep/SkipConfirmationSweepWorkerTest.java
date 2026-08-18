/**
 * Created at: 2026-08-17T20:49:31+09:00
 * Source scenario: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP-UNIT-008 through UNIT-010
 */
package com.dnd.qello.direction.sweep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;

class SkipConfirmationSweepWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

	@Test
	@DisplayName("batch 시각과 limit만 후보 조회에 전달한다 — 유예 계산은 서비스가 소유한다")
	void passesRawTimeAndLimitWithoutComputingDeadline() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		when(service.findConfirmableSkips(NOW, 5)).thenReturn(List.of());
		SkipConfirmationSweepWorker worker = worker(service);

		worker.processBatch(new SkipConfirmationSweepWorker.BatchCommand(5, NOW));

		verify(service).findConfirmableSkips(NOW, 5);
	}

	@Test
	@DisplayName("confirmSkip()이 빈 결과를 돌려준 행은 ineligible로 집계한다")
	void countsEmptyConfirmSkipAsIneligible() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		List<PostRecipient> candidates = List.of(skipPending(1L), skipPending(2L), skipPending(3L));
		when(service.findConfirmableSkips(eq(NOW), any(Integer.class))).thenReturn(candidates);
		when(service.confirmSkip(1L, NOW)).thenReturn(Optional.of(skipPending(1L)));
		when(service.confirmSkip(2L, NOW)).thenReturn(Optional.empty());
		when(service.confirmSkip(3L, NOW)).thenReturn(Optional.of(skipPending(3L)));
		SkipConfirmationSweepWorker worker = worker(service);

		SweepBatchResult result = worker.processBatch(new SkipConfirmationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.released()).isEqualTo(2);
		assertThat(result.ineligible()).isEqualTo(1);
	}

	@Test
	@DisplayName("한 행의 confirmSkip() 실패가 나머지 행 처리를 막지 않는다")
	void isolatesFailurePerRow() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		List<PostRecipient> candidates = List.of(skipPending(1L), skipPending(2L), skipPending(3L));
		when(service.findConfirmableSkips(eq(NOW), any(Integer.class))).thenReturn(candidates);
		when(service.confirmSkip(1L, NOW)).thenReturn(Optional.of(skipPending(1L)));
		when(service.confirmSkip(2L, NOW)).thenThrow(new RuntimeException("boom"));
		when(service.confirmSkip(3L, NOW)).thenReturn(Optional.of(skipPending(3L)));
		SkipConfirmationSweepWorker worker = worker(service);

		SweepBatchResult result = worker.processBatch(new SkipConfirmationSweepWorker.BatchCommand(10, NOW));

		verify(service).confirmSkip(3L, NOW);
		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.released()).isEqualTo(2);
	}

	private SkipConfirmationSweepWorker worker(ReceiveSlotReleaseService service) {
		return new SkipConfirmationSweepWorker(service, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static PostRecipient skipPending(long id) {
		return PostRecipient.restore(id, 1L, 2L, PostRecipientStatus.SKIP_PENDING,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", NOW.minusSeconds(3600), null, null,
			NOW.minusSeconds(30), null, null, null, null, BigDecimal.valueOf(45), 1_000L, null);
	}
}
