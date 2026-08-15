package com.dnd.qello.filtering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.filtering.error.FilteringErrorCode;
import com.dnd.qello.filtering.error.FilteringException;

/**
 * Created at: 2026-08-14T23:30:00+09:00
 * Source scenario: TEST-PLAN-GH-108-ANSWER-MODERATION-RETRY-UNIT-011 through UNIT-013
 */
class FilterReleaseRetryGateTest {

	private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
	private static final long RELEASE_ID = 5L;
	// degradeThreshold=3, minLimit=2, rampStep=2, recoveryStreak=2, healthyLimit=6
	private static final RetryGateConfig CONFIG = new RetryGateConfig(3, 2, 2, 2, 6);

	@Test
	@DisplayName("새 게이트는 HEALTHY로 시작하고 한도 없이 모든 claim을 허용한다")
	void startsHealthyAndAllowsUnlimitedClaims() {
		FilterReleaseRetryGate gate = FilterReleaseRetryGate.healthy(RELEASE_ID, NOW);

		assertThat(gate.state()).isEqualTo(FilterReleaseGateState.HEALTHY);
		assertThat(gate.currentLimit()).isNull();
		assertThat(gate.allowsClaim(Integer.MAX_VALUE - 1)).isTrue();
	}

	@Test
	@DisplayName("연속 실패가 임계값에 도달하면 HEALTHY에서 DEGRADED로 저하되고 최소 한도로 시작한다")
	void degradesAfterConsecutiveFailuresReachThreshold() {
		FilterReleaseRetryGate gate = FilterReleaseRetryGate.healthy(RELEASE_ID, NOW);

		FilterReleaseRetryGate afterFirst = gate.onFailure(NOW.plusSeconds(1), CONFIG);
		FilterReleaseRetryGate afterSecond = afterFirst.onFailure(NOW.plusSeconds(2), CONFIG);
		assertThat(afterSecond.state()).isEqualTo(FilterReleaseGateState.HEALTHY);

		FilterReleaseRetryGate degraded = afterSecond.onFailure(NOW.plusSeconds(3), CONFIG);

		assertThat(degraded.state()).isEqualTo(FilterReleaseGateState.DEGRADED);
		assertThat(degraded.currentLimit()).isEqualTo(2);
		assertThat(degraded.allowsClaim(0)).isTrue();
		assertThat(degraded.allowsClaim(1)).isTrue();
		assertThat(degraded.allowsClaim(2)).isFalse();
	}

	@Test
	@DisplayName("DEGRADED 상태에서 연속 성공이 회복 streak에 도달하면 한도가 rampStep만큼 늘어난다")
	void ramsUpLimitAfterRecoveryStreak() {
		FilterReleaseRetryGate degraded = FilterReleaseRetryGate.restore(
			RELEASE_ID, FilterReleaseGateState.DEGRADED, 2, 0, 0, NOW);

		FilterReleaseRetryGate afterFirstSuccess = degraded.onSuccess(NOW.plusSeconds(1), CONFIG);
		assertThat(afterFirstSuccess.state()).isEqualTo(FilterReleaseGateState.DEGRADED);
		assertThat(afterFirstSuccess.currentLimit()).isEqualTo(2);

		FilterReleaseRetryGate rampedUp = afterFirstSuccess.onSuccess(NOW.plusSeconds(2), CONFIG);

		assertThat(rampedUp.state()).isEqualTo(FilterReleaseGateState.DEGRADED);
		assertThat(rampedUp.currentLimit()).isEqualTo(4);
		assertThat(rampedUp.consecutiveSuccesses()).isZero();
	}

	@Test
	@DisplayName("한도가 healthyLimit 이상으로 회복되면 HEALTHY로 완전히 복귀한다")
	void returnsToHealthyOnceLimitReachesHealthyThreshold() {
		// currentLimit=4, consecutiveSuccesses=1(=recoveryStreak-1) — 성공 1회만 더 있으면
		// streak(2)에 도달해 currentLimit(4) + rampStep(2) = 6 = healthyLimit이 된다.
		FilterReleaseRetryGate nearlyRecovered = FilterReleaseRetryGate.restore(
			RELEASE_ID, FilterReleaseGateState.DEGRADED, 4, 0, 1, NOW);

		FilterReleaseRetryGate recovered = nearlyRecovered.onSuccess(NOW.plusSeconds(1), CONFIG);

		assertThat(recovered.state()).isEqualTo(FilterReleaseGateState.HEALTHY);
		assertThat(recovered.currentLimit()).isNull();
		assertThat(recovered.allowsClaim(1000)).isTrue();
	}

	@Test
	@DisplayName("DEGRADED 복구 도중 실패가 재발하면 회복 진행이 초기화되고 다시 최소 한도로 저하된다")
	void relapsesToMinLimitOnFailureDuringRecovery() {
		FilterReleaseRetryGate recovering = FilterReleaseRetryGate.restore(
			RELEASE_ID, FilterReleaseGateState.DEGRADED, 4, 0, 1, NOW);

		FilterReleaseRetryGate relapsed = recovering.onFailure(NOW.plusSeconds(1), CONFIG);

		assertThat(relapsed.state()).isEqualTo(FilterReleaseGateState.DEGRADED);
		assertThat(relapsed.currentLimit()).isEqualTo(2);
		assertThat(relapsed.consecutiveSuccesses()).isZero();
		assertThat(relapsed.consecutiveFailures()).isEqualTo(1);
	}

	@Test
	@DisplayName("HEALTHY 상태의 currentLimit이 존재하거나 DEGRADED 상태의 currentLimit이 없으면 복원 시점에 거절된다")
	void rejectsInconsistentRestoredState() {
		assertThatThrownBy(() -> FilterReleaseRetryGate.restore(
			RELEASE_ID, FilterReleaseGateState.HEALTHY, 2, 0, 0, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);

		assertThatThrownBy(() -> FilterReleaseRetryGate.restore(
			RELEASE_ID, FilterReleaseGateState.DEGRADED, null, 0, 0, NOW))
			.isInstanceOf(FilteringException.class)
			.hasFieldOrPropertyWithValue("errorCode", FilteringErrorCode.INVALID_VALUE_RANGE);
	}
}
