/**
 * Created at: 2026-11-24T17:41:29Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-UNIT-008
 */
package com.dnd.qello.direction.config;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.dnd.qello.direction.security.AesGcmCoordinateProtector;
import com.dnd.qello.direction.security.Coordinate;
import com.dnd.qello.direction.security.CoordinateProtectionException;
import com.dnd.qello.direction.security.CoordinateProtector;
import com.dnd.qello.direction.security.CoordinatePurpose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class CoordinateProtectionPropertiesTest {

	private static final String CURRENT = encodedMaterial((byte) 0x43);
	private static final String PREVIOUS = encodedMaterial((byte) 0x54);

	@ParameterizedTest(name = "{index}")
	@MethodSource("invalidConfigurations")
	@DisplayName("UNIT-008: 누락·Base64·키 길이·편측 previous·동일 ID 오류를 구성 시 거부한다")
	void rejectsInvalidConfiguration(String currentId, String currentKey, String previousId, String previousKey) {
		CoordinateProtectionException failure = catchThrowableOfType(
				() -> new CoordinateProtectionProperties(currentId, currentKey, previousId, previousKey),
				CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.reason()).isEqualTo(CoordinateProtectionException.Reason.INVALID_CONFIGURATION);
		assertThat(failure.getCause()).isNull();
		assertThat(failure.toString().contains(CURRENT) || failure.toString().contains(PREVIOUS)).isFalse();
	}

	@Test
	@DisplayName("UNIT-008: 두 슬롯 설정은 이전 키 read와 current write에 실제로 사용된다")
	void acceptsBothSlotsAndUsesConfiguredKeys() {
		CoordinateProtector previous = new AesGcmCoordinateProtector(
				new CoordinateProtectionProperties("previous", PREVIOUS, null, null));
		CoordinateProtectionProperties properties = new CoordinateProtectionProperties(
				"current", CURRENT, "previous", PREVIOUS);
		CoordinateProtector rotated = new AesGcmCoordinateProtector(properties);
		Coordinate coordinate = new Coordinate(new BigDecimal("12.34567890123"), new BigDecimal("-45.67890123456"));
		byte[] old = previous.protect(coordinate, CoordinatePurpose.POST_AUDIENCE, 91);

		Coordinate restored = rotated.decrypt(old, CoordinatePurpose.POST_AUDIENCE, 91);
		byte[] fresh = rotated.protect(coordinate, CoordinatePurpose.POST_AUDIENCE, 91);

		assertThat(restored.latitude().doubleValue() == coordinate.latitude().doubleValue()).isTrue();
		assertThat(restored.longitude().doubleValue() == coordinate.longitude().doubleValue()).isTrue();
		CoordinateProtector currentOnly = new AesGcmCoordinateProtector(
				new CoordinateProtectionProperties("current", CURRENT, "", ""));
		Coordinate currentRestored = currentOnly.decrypt(fresh, CoordinatePurpose.POST_AUDIENCE, 91);
		assertThat(currentRestored.latitude().doubleValue() == coordinate.latitude().doubleValue()).isTrue();
		assertThat(currentRestored.longitude().doubleValue() == coordinate.longitude().doubleValue()).isTrue();
		assertThat(properties.toString().contains(CURRENT) || properties.toString().contains(PREVIOUS)).isFalse();
	}

	private static Stream<Arguments> invalidConfigurations() {
		return Stream.of(
				Arguments.of(null, null, null, null),
				Arguments.of(null, CURRENT, null, null),
				Arguments.of("", CURRENT, null, null),
				Arguments.of(" ", CURRENT, null, null),
				Arguments.of("current", null, null, null),
				Arguments.of("current", "", null, null),
				Arguments.of("current", "not-base64!", null, null),
				Arguments.of("current", Base64.getEncoder().encodeToString(new byte[31]), null, null),
				Arguments.of("current", Base64.getEncoder().encodeToString(new byte[33]), null, null),
				Arguments.of("current", CURRENT, "previous", null),
				Arguments.of("current", CURRENT, null, PREVIOUS),
				Arguments.of("current", CURRENT, "", PREVIOUS),
				Arguments.of("current", CURRENT, "previous", ""),
				Arguments.of("current", CURRENT, "current", PREVIOUS),
				Arguments.of("current", CURRENT, "previous", "not-base64!"),
				Arguments.of("current", CURRENT, "previous", Base64.getEncoder().encodeToString(new byte[16])),
				Arguments.of("x".repeat(256), CURRENT, null, null),
				Arguments.of("current", CURRENT, "x".repeat(256), PREVIOUS));
	}

	private static String encodedMaterial(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return Base64.getEncoder().encodeToString(key);
	}
}
