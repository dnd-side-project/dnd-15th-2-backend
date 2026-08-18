/**
 * Created at: 2026-08-17T20:49:31+09:00
 * Source scenario: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP-UNIT-001 through UNIT-007
 * Source scenario: TEST-PLAN-GH-126-EXPIRATION-SKIP-SWEEP-UNIT-011 through UNIT-013
 */
package com.dnd.qello.direction.sweep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.service.ReceiveSlotReleaseService;

class RecipientExpirationSweepWorkerTest {

	private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

	@Test
	@DisplayName("batch 명령의 limit과 시각을 그대로 후보 조회에 전달한다")
	void passesLimitAndTimeToCandidateLookup() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		when(service.findExpirable(NOW, 7)).thenReturn(List.of());
		RecipientExpirationSweepWorker worker = worker(service);

		worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(7, NOW));

		verify(service).findExpirable(NOW, 7);
	}

	@Test
	@DisplayName("후보마다 postRecipientId로 expire()를 정확히 한 번씩 호출한다")
	void callsExpireOncePerCandidate() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		List<PostRecipient> candidates = List.of(available(1L), available(2L), available(3L));
		when(service.findExpirable(eq(NOW), any(Integer.class))).thenReturn(candidates);
		when(service.expire(anyLong(), eq(NOW))).thenReturn(Optional.of(available(1L)));
		RecipientExpirationSweepWorker worker = worker(service);

		worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		verify(service).expire(1L, NOW);
		verify(service).expire(2L, NOW);
		verify(service).expire(3L, NOW);
	}

	@Test
	@DisplayName("expire()가 빈 결과를 돌려준 행은 ineligible로 집계하고 슬롯을 해제하지 않는다")
	void countsEmptyExpireAsIneligible() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		List<PostRecipient> candidates = List.of(available(1L), available(2L), available(3L));
		when(service.findExpirable(eq(NOW), any(Integer.class))).thenReturn(candidates);
		when(service.expire(1L, NOW)).thenReturn(Optional.of(available(1L)));
		when(service.expire(2L, NOW)).thenReturn(Optional.empty());
		when(service.expire(3L, NOW)).thenReturn(Optional.of(available(3L)));
		RecipientExpirationSweepWorker worker = worker(service);

		SweepBatchResult result = worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.released()).isEqualTo(2);
		assertThat(result.ineligible()).isEqualTo(1);
		assertThat(result.failed()).isZero();
	}

	@Test
	@DisplayName("한 행의 expire() 실패가 나머지 행 처리를 막지 않는다")
	void isolatesFailurePerRow() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		List<PostRecipient> candidates = List.of(available(1L), available(2L), available(3L));
		when(service.findExpirable(eq(NOW), any(Integer.class))).thenReturn(candidates);
		when(service.expire(1L, NOW)).thenReturn(Optional.of(available(1L)));
		when(service.expire(2L, NOW)).thenThrow(new RuntimeException("boom"));
		when(service.expire(3L, NOW)).thenReturn(Optional.of(available(3L)));
		RecipientExpirationSweepWorker worker = worker(service);

		SweepBatchResult result = worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		verify(service).expire(3L, NOW);
		assertThat(result.failed()).isEqualTo(1);
		assertThat(result.released()).isEqualTo(2);
	}

	@Test
	@DisplayName("후보가 없으면 scanned가 0이고 expire()를 호출하지 않는다")
	void noCandidatesMeansNoExpireCalls() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		when(service.findExpirable(eq(NOW), any(Integer.class))).thenReturn(List.of());
		RecipientExpirationSweepWorker worker = worker(service);

		SweepBatchResult result = worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.scanned()).isZero();
		verify(service, never()).expire(anyLong(), any());
	}

	@Test
	@DisplayName("limit이 0 이하인 명령은 생성 시점에 거절되고 후보 조회가 호출되지 않는다")
	void rejectsNonPositiveLimit() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		RecipientExpirationSweepWorker worker = worker(service);

		assertThatThrownBy(() -> worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(0, NOW)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);

		verifyNoInteractions(service);
	}

	@Test
	@DisplayName("at이 null이면 Clock의 현재 시각으로 후보를 조회한다")
	void usesClockWhenAtIsNull() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		when(service.findExpirable(eq(NOW), any(Integer.class))).thenReturn(List.of());
		RecipientExpirationSweepWorker worker = worker(service);

		worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, null));

		verify(service).findExpirable(NOW, 10);
	}

	@Test
	@DisplayName("scanned은 released·ineligible·failed의 합과 같다")
	void scannedEqualsSumOfOutcomes() {
		ReceiveSlotReleaseService service = mock(ReceiveSlotReleaseService.class);
		List<PostRecipient> candidates = List.of(available(1L), available(2L), available(3L), available(4L));
		when(service.findExpirable(eq(NOW), any(Integer.class))).thenReturn(candidates);
		when(service.expire(1L, NOW)).thenReturn(Optional.of(available(1L)));
		when(service.expire(2L, NOW)).thenReturn(Optional.empty());
		when(service.expire(3L, NOW)).thenThrow(new RuntimeException("boom"));
		when(service.expire(4L, NOW)).thenReturn(Optional.of(available(4L)));
		RecipientExpirationSweepWorker worker = worker(service);

		SweepBatchResult result = worker.processBatch(new RecipientExpirationSweepWorker.BatchCommand(10, NOW));

		assertThat(result.scanned()).isEqualTo(result.released() + result.ineligible() + result.failed());
	}

	@Test
	@DisplayName("배치 결과에는 좌표·본문·사용자 식별자 필드가 없다")
	void resultHasNoSensitiveFields() {
		List<String> fieldNames = Arrays.stream(SweepBatchResult.class.getDeclaredFields())
			.map(Field::getName)
			.toList();

		assertThat(fieldNames).containsExactlyInAnyOrder("scanned", "released", "ineligible", "failed");
	}

	@Test
	@DisplayName("음수 카운터로 결과를 생성하면 거절된다")
	void rejectsNegativeCounters() {
		assertThatThrownBy(() -> new SweepBatchResult(-1, 0, 0, 0))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
	}

	private RecipientExpirationSweepWorker worker(ReceiveSlotReleaseService service) {
		return new RecipientExpirationSweepWorker(service, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static PostRecipient available(long id) {
		return PostRecipient.restore(id, 1L, 2L, PostRecipientStatus.AVAILABLE,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", NOW.minusSeconds(3600), null, null, null, null,
			null, null, null, BigDecimal.valueOf(45), 1_000L, null);
	}
}
