/**
 * Created at: 2026-11-23T13:41:12Z
 * Source scenario: TEST-PLAN-GH-381-LOCATION-CONSENT-UNIT-008
 */
package com.dnd.qello.direction.consent;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationConsentPropertiesTest {

	@ParameterizedTest
	@MethodSource("validConfigurations")
	@DisplayName("B1 off의 미게시 상태와 1~100자 게시 버전 및 dev·test 합성 버전을 허용한다")
	void acceptsValidConfiguration(String version, boolean enabled) {
		assertThatCode(() -> new LocationConsentProperties(version).validate(enabled)).doesNotThrowAnyException();
	}

	static Stream<Arguments> validConfigurations() {
		return Stream.of(
				Arguments.of(null, false),
				Arguments.of("v", true),
				Arguments.of("v".repeat(100), true),
				Arguments.of("dev-v1", true),
				Arguments.of("test-v1", true),
				Arguments.of("test-v2", true),
				Arguments.of("dev-v1", false));
	}

	@ParameterizedTest
	@MethodSource("invalidConfigurations")
	@DisplayName("B1 on의 누락과 on·off 모두의 형식 위반은 입력값을 노출하지 않고 실패한다")
	void rejectsInvalidConfigurationWithoutExposingValue(String version, boolean enabled) {
		var assertion = assertThatThrownBy(() -> new LocationConsentProperties(version).validate(enabled))
				.isInstanceOf(IllegalStateException.class);
		if (version != null && !version.isBlank()) {
			assertion.hasMessageNotContaining(version);
		}
	}

	static Stream<Arguments> invalidConfigurations() {
		return Stream.concat(Stream.of(Arguments.of(null, true)),
				Stream.of(true, false)
						.flatMap(enabled -> Stream.of("", " \t\n", " test-v1", "test-v1 ", "v".repeat(101))
								.map(version -> Arguments.of(version, enabled))));
	}
}
