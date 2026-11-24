/**
 * Created at: 2026-11-23T13:41:12Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-001
 */
package com.dnd.qello.direction.consent;

import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocationConsentTest {

	@ParameterizedTest
	@MethodSource("consentStates")
	@DisplayName("현재 버전의 CONSENT 이벤트만 위치 이용을 허용한다")
	void permitsOnlyConsentToCurrentVersion(LocationConsentAction action, String version, boolean expected) {
		LocationConsent consent = new LocationConsent(1L, 7L, version, action,
				Instant.parse("2026-11-23T13:41:12Z"));

		assertThat(consent.isConsentedTo("test-v2")).isEqualTo(expected);
	}

	static Stream<Arguments> consentStates() {
		return Stream.of(
				Arguments.of(LocationConsentAction.CONSENT, "test-v2", true),
				Arguments.of(LocationConsentAction.CONSENT, "test-v1", false),
				Arguments.of(LocationConsentAction.WITHDRAW, "test-v2", false),
				Arguments.of(LocationConsentAction.WITHDRAW, "test-v1", false));
	}
}
