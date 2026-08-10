/**
 * Created at: 2026-08-10T20:25:00+09:00
 * Source scenario: TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-001 through UNIT-003
 */
package com.dnd.qello.feed.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.feed.error.FeedErrorCode;
import com.dnd.qello.feed.error.FeedException;

class DistanceBandPolicyTest {

	private final FeedDistanceProperties properties = new FeedDistanceProperties(10_000L);
	private final DistanceBandPolicy policy = new DistanceBandPolicy(properties);

	@Test
	@DisplayName("10km 미만은 사용자 표시값 10km 이내로 저장한다")
	void storesNearDistanceLabelBelowFloor() {
		assertThat(policy.forDistance(9_999L)).isEqualTo(properties.nearDistanceLabel());
	}

	@Test
	@DisplayName("10km 이상은 정확 거리 표시용 내부 band로 저장한다")
	void storesExactDistanceMarkerAtAndAboveFloor() {
		assertThat(policy.forDistance(10_000L)).isEqualTo(DistanceBandPolicy.EXACT_DISTANCE_STORAGE_BAND);
		assertThat(policy.forDistance(10_001L)).isEqualTo(DistanceBandPolicy.EXACT_DISTANCE_STORAGE_BAND);
	}

	@Test
	@DisplayName("하한 설정값이 바뀌면 저장 band와 표시 문구가 같은 하한을 사용한다")
	void derivesLabelFromConfiguredFloor() {
		FeedDistanceProperties nineKilometerProperties = new FeedDistanceProperties(9_000L);
		DistanceBandPolicy nineKilometerPolicy = new DistanceBandPolicy(nineKilometerProperties);

		assertThat(nineKilometerPolicy.forDistance(8_000L)).isEqualTo("9km 이내");
		assertThat(nineKilometerPolicy.forDistance(9_000L))
			.isEqualTo(DistanceBandPolicy.EXACT_DISTANCE_STORAGE_BAND);
	}

	@Test
	@DisplayName("거리 하한은 0이 될 수 없다")
	void rejectsZeroDistanceFloor() {
		assertThatThrownBy(() -> new FeedDistanceProperties(0L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("음수 거리는 전역 feed 오류로 변환한다")
	void rejectsNegativeDistance() {
		assertThatThrownBy(() -> policy.forDistance(-1L))
			.isInstanceOf(FeedException.class)
			.hasFieldOrPropertyWithValue("errorCode", FeedErrorCode.INVALID_DISTANCE);
	}
}
