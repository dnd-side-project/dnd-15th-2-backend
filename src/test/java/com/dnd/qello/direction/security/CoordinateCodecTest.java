/**
 * Created at: 2026-11-24T17:41:29Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-UNIT-009
 */
package com.dnd.qello.direction.security;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class CoordinateCodecTest {

	@DisplayName("UNIT-009: 양·음·0·경계 좌표를 float8 정밀도로 보존하고 소수 자릿수를 자르지 않는다")
	@ParameterizedTest(name = "{index}")
	@CsvSource({
			"0,0",
			"90,180",
			"-90,-180",
			"37.12345678901234567890123456789,-122.98765432109876543210987654321",
			"-0.0000000000001234567890123456789,0.0000000000009876543210987654321",
			"89.99999999999999,-179.99999999999997"
	})
	void preservesExistingDoublePrecision(String latitude, String longitude) {
		Coordinate input = new Coordinate(new BigDecimal(latitude), new BigDecimal(longitude));

		byte[] encoded = CoordinateCodec.encode(input);
		Coordinate restored = CoordinateCodec.decode(encoded);

		assertThat(encoded.length).isEqualTo(16);
		ByteBuffer oracle = ByteBuffer.wrap(encoded);
		assertThat(
				Double.doubleToLongBits(oracle.getDouble()) == Double.doubleToLongBits(input.latitude().doubleValue()))
				.isTrue();
		assertThat(
				Double.doubleToLongBits(oracle.getDouble()) == Double.doubleToLongBits(input.longitude().doubleValue()))
				.isTrue();
		assertThat(restored.latitude().doubleValue() == input.latitude().doubleValue()).isTrue();
		assertThat(restored.longitude().doubleValue() == input.longitude().doubleValue()).isTrue();
	}

	@ParameterizedTest(name = "{index}")
	@MethodSource("invalidPayloads")
	@DisplayName("UNIT-009: 잘못된 payload 길이·비유한값·범위 초과는 제한된 오류로 거부한다")
	void rejectsInvalidPayload(byte[] payload) {
		CoordinateProtectionException failure = catchThrowableOfType(
				() -> CoordinateCodec.decode(payload), CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.reason()).isEqualTo(CoordinateProtectionException.Reason.INVALID_ENVELOPE);
		assertThat(failure.getCause()).isNull();
	}

	private static Stream<byte[]> invalidPayloads() {
		return Stream.of(null, new byte[0], new byte[15], new byte[17],
				payload(Double.NaN, 0), payload(0, Double.NaN),
				payload(Double.POSITIVE_INFINITY, 0), payload(0, Double.NEGATIVE_INFINITY),
				payload(90.1, 0), payload(-90.1, 0), payload(0, 180.1), payload(0, -180.1));
	}

	private static byte[] payload(double latitude, double longitude) {
		return ByteBuffer.allocate(16).putDouble(latitude).putDouble(longitude).array();
	}
}
