/**
 * Created at: 2026-08-10T20:25:00+09:00
 * Source scenario: TEST-PLAN-GH-95-DISTANCE-BAND-PER-RECIPIENT-UNIT-001 through UNIT-003
 */
package com.dnd.qello.feed.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DistanceBandPolicyTest {

	private final DistanceBandPolicy policy = new DistanceBandPolicy(new FeedDistanceProperties(10_000L));

	@Test
	@DisplayName("10km 미만은 사용자 표시값 10km 이내로 저장한다")
	void storesNearDistanceLabelBelowFloor() {
		assertThat(policy.forDistance(9_999L)).isEqualTo(DistanceBandPolicy.NEAR_DISTANCE_LABEL);
	}

	@Test
	@DisplayName("10km 이상은 정확 거리 표시용 내부 band로 저장한다")
	void storesExactDistanceMarkerAtAndAboveFloor() {
		assertThat(policy.forDistance(10_000L)).isEqualTo(DistanceBandPolicy.EXACT_DISTANCE_STORAGE_BAND);
		assertThat(policy.forDistance(10_001L)).isEqualTo(DistanceBandPolicy.EXACT_DISTANCE_STORAGE_BAND);
	}

	@Test
	@DisplayName("음수 거리는 band로 변환하지 않는다")
	void rejectsNegativeDistance() {
		assertThatThrownBy(() -> policy.forDistance(-1L))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
