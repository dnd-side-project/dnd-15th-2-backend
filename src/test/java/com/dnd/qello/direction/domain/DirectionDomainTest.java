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
 * Source scenario: TEST-PLAN-GH-39-DIRECTION-POSTGIS-PERSISTENCE-UNIT-001 through UNIT-006
 */
class DirectionDomainTest {

	private static final Instant LOCATION_AT = Instant.parse("2026-08-03T10:00:00Z");

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
		PostRecipient available = PostRecipient.available(1L, 2L, "NEAR", BigDecimal.valueOf(10), "KR-SEOUL", LOCATION_AT);
		assertThat(available.getStatus()).isEqualTo(PostRecipientStatus.AVAILABLE);
		assertThatThrownBy(() -> PostRecipient.restore(1L, 1L, 2L, PostRecipientStatus.SKIPPED,
			"NEAR", BigDecimal.TEN, "KR-SEOUL", LOCATION_AT, null, null, null, null, null, null))
			.isInstanceOf(DirectionException.class);
	}

	@Test
	@DisplayName("recipient receive state는 활성 미처리 5개 상한을 domain에서도 표현한다")
	void exposesCapacityLimit() {
		RecipientReceiveState state = RecipientReceiveState.restore(2L, 5, 7, LOCATION_AT, LOCATION_AT, LOCATION_AT);
		assertThat(state.canReserve()).isFalse();
		assertThatThrownBy(() -> RecipientReceiveState.restore(2L, 6, 7, LOCATION_AT, LOCATION_AT, LOCATION_AT))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_VALUE_RANGE)
			.hasFieldOrPropertyWithValue("field", "activeUnhandledCount");
	}
}
