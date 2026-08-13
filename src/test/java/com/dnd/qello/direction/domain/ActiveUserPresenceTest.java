/**
 * Created at: 2026-08-14T00:51:11+09:00
 * Source scenario: TEST-PLAN-GH-121-ACTIVE-USER-PRESENCE-API-UNIT-001, UNIT-002, UNIT-011
 */
package com.dnd.qello.direction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

class ActiveUserPresenceTest {

	private static final Instant OBSERVED_AT = Instant.parse("2026-08-13T15:00:00Z");

	@Test
	@DisplayName("수신을 거부해도 만료 전 정확 위치는 발신 원점으로 사용할 수 있다")
	void separatesCurrentLocationFromReceiveEligibility() {
		ActiveUserPresence presence = presence(false, OBSERVED_AT.plusSeconds(3_600));

		assertThat(presence.hasCurrentLocationAt(OBSERVED_AT)).isTrue();
		assertThat(presence.isReceiveEligibleAt(OBSERVED_AT)).isFalse();
	}

	@Test
	@DisplayName("만료 경계에서는 현재 위치와 수신 자격이 모두 종료된다")
	void excludesPresenceAtExpirationBoundary() {
		ActiveUserPresence presence = presence(true, OBSERVED_AT.plusSeconds(3_600));

		assertThat(presence.hasCurrentLocationAt(OBSERVED_AT.plusSeconds(3_600))).isFalse();
		assertThat(presence.isReceiveEligibleAt(OBSERVED_AT.plusSeconds(3_600))).isFalse();
	}

	@Test
	@DisplayName("좌표 범위 오류는 실제 좌표를 reason에 포함하지 않는다")
	void rejectsCoordinateWithoutLeakingValue() {
		BigDecimal sentinel = new BigDecimal("91.123456");

		assertThatThrownBy(() -> ActiveUserPresence.create(1L, sentinel, BigDecimal.valueOf(127), null,
			"TEST-REGION", BigDecimal.ONE, true, OBSERVED_AT, OBSERVED_AT.plusSeconds(1)))
			.isInstanceOf(DirectionException.class)
			.hasFieldOrPropertyWithValue("errorCode", DirectionErrorCode.INVALID_COORDINATE)
			.satisfies(exception -> assertThat(((DirectionException)exception).getReason())
				.doesNotContain(sentinel.toPlainString()));
	}

	private ActiveUserPresence presence(boolean receiveAllowed, Instant expiresAt) {
		return ActiveUserPresence.create(1L, BigDecimal.valueOf(37.5), BigDecimal.valueOf(127), null,
			"TEST-REGION", BigDecimal.ONE, receiveAllowed, OBSERVED_AT, expiresAt);
	}
}
