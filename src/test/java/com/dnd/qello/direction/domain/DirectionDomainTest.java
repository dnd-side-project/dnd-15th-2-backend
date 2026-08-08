package com.dnd.qello.direction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/**
 * Created at: 2026-08-03T20:30:00+09:00
 * Source scenario: TEST-PLAN-GH-39-DIRECTION-POSTGIS-PERSISTENCE-UNIT-001 through UNIT-006,
 * TEST-PLAN-GH-78-SCHEMA-REVISION-V7-UNIT-002 through UNIT-004
 */
class DirectionDomainTest {

	private static final Instant LOCATION_AT = Instant.parse("2026-08-03T10:00:00Z");
	private static final BigDecimal INBOUND_BEARING = BigDecimal.valueOf(190);
	private static final long DISTANCE_M = 5_000L;

	@Test
	@DisplayName("8개 sector는 0도와 360도를 정규화하고 half-open 경계를 지킨다")
	void mapsEightSegmentsWithoutBoundaryOverlap() {
		DirectionScheme scheme = DirectionScheme.createEqual("MVP-8", 1, 8, BigDecimal.ZERO);
		List<DirectionSegment> segments = java.util.stream.IntStream.range(0, 8)
			.mapToObj(index -> DirectionSegment.create(1L, "S" + index, "segment-" + index,
				BigDecimal.valueOf(index * 45L + 22.5), BigDecimal.valueOf(45), index))
			.toList();
		scheme.validateCoverage(segments);

		assertThat(scheme.selectSegment(0, segments).getSegmentKey()).isEqualTo("S0");
		assertThat(scheme.selectSegment(360, segments).getSegmentKey()).isEqualTo("S0");
		assertThat(segments.get(0).contains(22.5)).isTrue();
		assertThat(segments.get(0).contains(67.5)).isFalse();
		assertThatThrownBy(() -> DirectionScheme.createEqual("bad", 1, 8, BigDecimal.valueOf(360)))
			.isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("sector coverage에 gap 또는 중복이 있으면 거절한다")
	void rejectsInvalidCoverage() {
		DirectionScheme scheme = DirectionScheme.createEqual("MVP-8", 1, 8, BigDecimal.ZERO);
		List<DirectionSegment> invalid = List.of(
			DirectionSegment.create(1L, "S0", "zero", BigDecimal.valueOf(22.5), BigDecimal.valueOf(45), 0),
			DirectionSegment.create(1L, "S1", "one", BigDecimal.valueOf(67.5), BigDecimal.valueOf(45), 1),
			DirectionSegment.create(1L, "S2", "two", BigDecimal.valueOf(112.5), BigDecimal.valueOf(45), 2),
			DirectionSegment.create(1L, "S3", "three", BigDecimal.valueOf(157.5), BigDecimal.valueOf(45), 3),
			DirectionSegment.create(1L, "S4", "four", BigDecimal.valueOf(202.5), BigDecimal.valueOf(45), 4),
			DirectionSegment.create(1L, "S5", "five", BigDecimal.valueOf(247.5), BigDecimal.valueOf(45), 5),
			DirectionSegment.create(1L, "S6", "six", BigDecimal.valueOf(292.5), BigDecimal.valueOf(45), 6),
			DirectionSegment.create(1L, "S7", "seven", BigDecimal.valueOf(337.5), BigDecimal.valueOf(44), 7));

		assertThatThrownBy(() -> scheme.validateCoverage(invalid))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_SCHEME_CONFIGURATION);
	}

	@Test
	@DisplayName("presence는 서버가 전달한 절대 만료 시각과 현재성 규칙을 보존한다")
	void validatesPresenceExpiryAndCurrentWindow() {
		ActiveUserPresence presence = ActiveUserPresence.create(1L, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0),
			 null, "KR-SEOUL", BigDecimal.ONE, true, LOCATION_AT, LOCATION_AT.plusSeconds(3600));

		assertThat(presence.isCurrentAt(LOCATION_AT)).isTrue();
		assertThat(presence.isCurrentAt(LOCATION_AT.plusSeconds(3600))).isFalse();
		assertThatThrownBy(() -> ActiveUserPresence.create(1L, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0),
			null, "KR-SEOUL", null, true, LOCATION_AT, LOCATION_AT)).isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("post recipient은 matchedAt 이전 시각과 capacity 해제를 허용하지 않는다")
	void validatesRecipientTimestampsAndCapacity() {
		PostRecipient available = PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT,
			INBOUND_BEARING, DISTANCE_M);
		assertThat(available.getStatus()).isEqualTo(PostRecipientStatus.AVAILABLE);
		assertThatThrownBy(() -> PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.SKIPPED,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, null, null, null, null, null, null, null,
			INBOUND_BEARING, DISTANCE_M, null))
			.isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("inboundBearingDegrees는 [0, 360) 범위를 벗어나면 거절된다")
	void rejectsInboundBearingOutOfRange() {
		assertThatThrownBy(() -> PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT,
			BigDecimal.valueOf(-1), DISTANCE_M))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_BEARING);
		assertThatThrownBy(() -> PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT,
			BigDecimal.valueOf(360), DISTANCE_M))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_BEARING);

		PostRecipient boundary = PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT,
			BigDecimal.valueOf(359.999), DISTANCE_M);
		assertThat(boundary.getInboundBearingDegrees()).isEqualByComparingTo(BigDecimal.valueOf(359.999));
	}

	@Test
	@DisplayName("distanceM은 음수면 거절된다")
	void rejectsNegativeDistanceM() {
		assertThatThrownBy(() -> PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT,
			INBOUND_BEARING, -1L))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE);
	}

	@Test
	@DisplayName("answersReadAt은 matchedAt보다 이르면 거절된다")
	void rejectsAnswersReadAtBeforeMatchedAt() {
		assertThatThrownBy(() -> PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.AVAILABLE,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, null, null, null, null, null, null, null,
			INBOUND_BEARING, DISTANCE_M, LOCATION_AT.minusSeconds(1)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER)
			.hasFieldOrPropertyWithValue("field", "answersReadAt");

		PostRecipient withAnswersRead = PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.AVAILABLE,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, null, null, null, null, null, null, null,
			INBOUND_BEARING, DISTANCE_M, LOCATION_AT.plusSeconds(60));
		assertThat(withAnswersRead.getAnswersReadAt()).isEqualTo(LOCATION_AT.plusSeconds(60));
	}

	@Test
	@DisplayName("넘김은 SKIP_PENDING을 거치며 유예 중에는 용량을 붙잡는다")
	void holdsCapacityWhileSkipIsPending() {
		PostRecipient available = PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT,
			INBOUND_BEARING, DISTANCE_M);
		PostRecipient pending = available.requestSkip(LOCATION_AT.plusSeconds(10));

		assertThat(pending.getStatus()).isEqualTo(PostRecipientStatus.SKIP_PENDING);
		assertThat(pending.getSkipRequestedAt()).isEqualTo(LOCATION_AT.plusSeconds(10));
		assertThat(pending.getSkippedAt()).isNull();
		assertThat(pending.getCapacityReleasedAt()).isNull();

		PostRecipient confirmed = pending.confirmSkip(LOCATION_AT.plusSeconds(15));

		assertThat(confirmed.getStatus()).isEqualTo(PostRecipientStatus.SKIPPED);
		assertThat(confirmed.getSkippedAt()).isEqualTo(LOCATION_AT.plusSeconds(15));
		assertThat(confirmed.getCapacityReleasedAt()).isEqualTo(LOCATION_AT.plusSeconds(15));
	}

	@Test
	@DisplayName("되돌린 넘김은 timestamp 유무로 이전 상태를 도출한다")
	void revertsSkipToTheDerivedPreviousStatus() {
		PostRecipient available = PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT,
			INBOUND_BEARING, DISTANCE_M);
		assertThat(available.requestSkip(LOCATION_AT).revertSkip().getStatus())
			.isEqualTo(PostRecipientStatus.AVAILABLE);

		PostRecipient discovered = PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.DISCOVERED,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, LOCATION_AT, null, null, null, null, null, null,
			INBOUND_BEARING, DISTANCE_M, null);
		assertThat(discovered.requestSkip(LOCATION_AT).revertSkip().getStatus())
			.isEqualTo(PostRecipientStatus.DISCOVERED);

		PostRecipient opened = PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.OPENED,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, LOCATION_AT, LOCATION_AT, null, null, null, null, null,
			INBOUND_BEARING, DISTANCE_M, null);
		PostRecipient reverted = opened.requestSkip(LOCATION_AT).revertSkip();

		assertThat(reverted.getStatus()).isEqualTo(PostRecipientStatus.OPENED);
		assertThat(reverted.getSkipRequestedAt()).isNull();
	}

	@Test
	@DisplayName("SKIP_PENDING은 넘김 요청만 있고 확정이 없는 상태와 동치다")
	void skipPendingRequiresRequestWithoutConfirmation() {
		assertThatThrownBy(() -> PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.SKIP_PENDING,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, null, null, null, null, null, null, null,
			INBOUND_BEARING, DISTANCE_M, null))
			.isInstanceOf(DirectionException.class);
		assertThatThrownBy(() -> PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.SKIPPED,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, null, null, null,
			LOCATION_AT, LOCATION_AT, null, null, INBOUND_BEARING, DISTANCE_M, null))
			.isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("수신 상한은 호출자가 넘기고 domain은 DB 안전 상한만 강제한다")
	void separatesOperationalLimitFromSafetyCeiling() {
		RecipientReceiveState state = RecipientReceiveState.restore(2L, 5, 7, LOCATION_AT, LOCATION_AT, LOCATION_AT);

		assertThat(state.canReserve(5)).isFalse();
		assertThat(state.canReserve(10)).isTrue();
		assertThat(RecipientReceiveState.SAFETY_CEILING).isEqualTo(50);
		assertThatThrownBy(() -> RecipientReceiveState.restore(2L, 51, 7, LOCATION_AT, LOCATION_AT, LOCATION_AT))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE)
			.hasFieldOrPropertyWithValue("field", "activeUnhandledCount");
	}

	@Test
	@DisplayName("답변 읽음 기준선은 발송 시각보다 빠를 수 없다")
	void rejectsAnswersReadBeforeSubmission() {
		DirectionPost post = DirectionPost.restore(1L, 2L, 3L, DirectionPostStatus.ACTIVE, "key", "글",
			"KR-SEOUL", DirectionPostModerationStatus.PASSED, LOCATION_AT, LOCATION_AT,
			LOCATION_AT.plusSeconds(3600), LOCATION_AT.plusSeconds(60), null);

		assertThat(post.getAnswersReadAt()).isEqualTo(LOCATION_AT.plusSeconds(60));
		assertThatThrownBy(() -> DirectionPost.restore(1L, 2L, 3L, DirectionPostStatus.ACTIVE, "key", "글",
			"KR-SEOUL", DirectionPostModerationStatus.PASSED, LOCATION_AT, LOCATION_AT,
			LOCATION_AT.plusSeconds(3600), LOCATION_AT.minusSeconds(1), null))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER)
			.hasFieldOrPropertyWithValue("field", "answersReadAt");
	}

	private static final Instant MATCHED = Instant.parse("2026-08-06T12:00:00Z");

	private PostRecipient availableRecipient() {
		return PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(45), "TEST-REGION", MATCHED,
			BigDecimal.valueOf(225), DISTANCE_M);
	}

	@Test
	@DisplayName("open은 AVAILABLE에서 discoveredAt과 openedAt을 함께 채운다")
	void openFillsDiscoveredAndOpenedTimestamps() {
		PostRecipient opened = availableRecipient().open(MATCHED.plusSeconds(10));

		assertThat(opened.getStatus()).isEqualTo(PostRecipientStatus.OPENED);
		assertThat(opened.getDiscoveredAt()).isEqualTo(MATCHED.plusSeconds(10));
		assertThat(opened.getOpenedAt()).isEqualTo(MATCHED.plusSeconds(10));
		assertThat(opened.getCapacityReleasedAt()).isNull();
	}

	@Test
	@DisplayName("open은 멱등이며 최초 열람 시각을 유지한다")
	void openKeepsFirstOpenedAt() {
		PostRecipient opened = availableRecipient().open(MATCHED.plusSeconds(10));
		PostRecipient reopened = opened.open(MATCHED.plusSeconds(99));

		assertThat(reopened.getOpenedAt()).isEqualTo(MATCHED.plusSeconds(10));
	}

	@Test
	@DisplayName("open은 discoveredAt보다 이른 시각을 거부한다")
	void openRejectsTimeBeforeDiscovered() {
		PostRecipient discovered = availableRecipient().discover(MATCHED.plusSeconds(10));

		assertThatThrownBy(() -> discovered.open(MATCHED.plusSeconds(9)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER);
	}

	@Test
	@DisplayName("discover는 AVAILABLE만 DISCOVERED로 바꾸고 이후 상태는 그대로 둔다")
	void discoverOnlyAdvancesFromAvailable() {
		PostRecipient discovered = availableRecipient().discover(MATCHED.plusSeconds(5));
		assertThat(discovered.getStatus()).isEqualTo(PostRecipientStatus.DISCOVERED);
		assertThat(discovered.getDiscoveredAt()).isEqualTo(MATCHED.plusSeconds(5));

		PostRecipient opened = discovered.open(MATCHED.plusSeconds(6));
		assertThat(opened.discover(MATCHED.plusSeconds(7)).getStatus()).isEqualTo(PostRecipientStatus.OPENED);
	}

	@Test
	@DisplayName("answered는 capacityReleasedAt을 함께 설정한다")
	void answeredReleasesCapacity() {
		PostRecipient answered = availableRecipient()
			.open(MATCHED.plusSeconds(10))
			.answered(MATCHED.plusSeconds(20));

		assertThat(answered.getStatus()).isEqualTo(PostRecipientStatus.ANSWERED);
		assertThat(answered.getCapacityReleasedAt()).isEqualTo(MATCHED.plusSeconds(20));
	}

	@Test
	@DisplayName("answered는 openedAt보다 이른 시각을 거부한다")
	void answeredRejectsTimeBeforeOpened() {
		PostRecipient opened = availableRecipient().open(MATCHED.plusSeconds(10));

		assertThatThrownBy(() -> opened.answered(MATCHED.plusSeconds(9)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER);
	}

	@Test
	@DisplayName("넘김이 확정된 수신 항목은 다시 열람하거나 답변할 수 없다")
	void terminalRecipientRejectsOpenAndAnswer() {
		PostRecipient skipped = availableRecipient()
			.requestSkip(MATCHED.plusSeconds(10))
			.confirmSkip(MATCHED.plusSeconds(20));

		assertThatThrownBy(() -> skipped.open(MATCHED.plusSeconds(30)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_RECIPIENT_STATE);
		assertThatThrownBy(() -> skipped.answered(MATCHED.plusSeconds(30)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_RECIPIENT_STATE);
	}

	@Test
	@DisplayName("markAnswersRead는 답변 열람 시각을 기록한다")
	void marksAnswersRead() {
		Instant submitted = Instant.parse("2026-08-06T12:00:00Z");
		DirectionPost post = DirectionPost.submit(1L, 2L, "key", "글", "TEST-REGION",
			submitted, submitted.plusSeconds(3600));

		DirectionPost read = post.markAnswersRead(submitted.plusSeconds(60));

		assertThat(read.getAnswersReadAt()).isEqualTo(submitted.plusSeconds(60));
		assertThat(read.getStatus()).isEqualTo(post.getStatus());
	}

	@Test
	@DisplayName("markAnswersRead는 발송 시각보다 이른 시각을 거부한다")
	void markAnswersReadRejectsBeforeSubmission() {
		Instant submitted = Instant.parse("2026-08-06T12:00:00Z");
		DirectionPost post = DirectionPost.submit(1L, 2L, "key", "글", "TEST-REGION",
			submitted, submitted.plusSeconds(3600));

		assertThatThrownBy(() -> post.markAnswersRead(submitted.minusSeconds(1)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_TIME_ORDER);
	}
}
