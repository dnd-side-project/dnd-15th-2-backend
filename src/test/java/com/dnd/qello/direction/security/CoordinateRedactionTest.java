/**
 * Created at: 2026-11-24T17:41:29Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-UNIT-014
 */
package com.dnd.qello.direction.security;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.dnd.qello.direction.config.CoordinateProtectionProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class CoordinateRedactionTest {

	@Test
	@DisplayName("UNIT-014: 좌표·설정·key ring의 문자열은 key와 좌표 sentinel을 노출하지 않는다")
	void redactsValueRepresentations() {
		String latitude = "23.12345678987654";
		String longitude = "-109.9876543212345";
		byte[] key = material((byte) 0x65);
		String encodedKey = Base64.getEncoder().encodeToString(key);
		Coordinate coordinate = new Coordinate(new BigDecimal(latitude), new BigDecimal(longitude));
		CoordinateProtectionProperties properties = new CoordinateProtectionProperties("current", encodedKey, null,
				null);
		CoordinateKeyRing keyRing = properties.keyRing();
		CoordinateProtector protector = new AesGcmCoordinateProtector(keyRing);

		byte[] envelope = protector.protect(coordinate, CoordinatePurpose.ACTIVE_USER_PRESENCE, 802);
		Coordinate restored = protector.decrypt(envelope, CoordinatePurpose.ACTIVE_USER_PRESENCE, 802);
		String rendered = coordinate + " " + restored + " " + properties + " " + keyRing + " " + protector;

		assertThat(rendered.contains(latitude) || rendered.contains(longitude) || rendered.contains(encodedKey)
				|| rendered.contains(Arrays.toString(key))).isFalse();
	}

	@Test
	@DisplayName("UNIT-014: 변조와 키 누락 오류는 종류를 구분하면서 stack trace에 민감값과 cause를 남기지 않는다")
	void redactsFailureChainsWithoutLosingTypedReason() {
		String latitude = "23.12345678987654";
		String longitude = "-109.9876543212345";
		byte[] key = material((byte) 0x65);
		String encodedKey = Base64.getEncoder().encodeToString(key);
		CoordinateProtector protector = new AesGcmCoordinateProtector(
				new CoordinateKeyRing("current", Map.of("current", key)));
		Coordinate coordinate = new Coordinate(new BigDecimal(latitude), new BigDecimal(longitude));
		byte[] envelope = protector.protect(coordinate, CoordinatePurpose.ACTIVE_USER_PRESENCE, 802);
		byte[] tampered = envelope.clone();
		tampered[tampered.length - 1] ^= 1;
		CoordinateProtector missing = new AesGcmCoordinateProtector(
				new CoordinateKeyRing("other", Map.of("other", material((byte) 0x76))));

		CoordinateProtectionException authentication = catchThrowableOfType(
				() -> protector.decrypt(tampered, CoordinatePurpose.ACTIVE_USER_PRESENCE, 802),
				CoordinateProtectionException.class);
		CoordinateProtectionException unknown = catchThrowableOfType(
				() -> missing.decrypt(envelope, CoordinatePurpose.ACTIVE_USER_PRESENCE, 802),
				CoordinateProtectionException.class);

		assertThat(authentication).isNotNull();
		assertThat(unknown).isNotNull();
		assertThat(authentication.reason()).isEqualTo(CoordinateProtectionException.Reason.AUTHENTICATION_FAILED);
		assertThat(unknown.reason()).isEqualTo(CoordinateProtectionException.Reason.UNKNOWN_KEY);
		for (CoordinateProtectionException failure : new CoordinateProtectionException[]{authentication, unknown}) {
			StringWriter output = new StringWriter();
			failure.printStackTrace(new PrintWriter(output));
			assertThat(output.toString().contains(latitude) || output.toString().contains(longitude)
					|| output.toString().contains(encodedKey) || output.toString().contains(Arrays.toString(key)))
					.isFalse();
			assertThat(failure.getCause()).isNull();
		}
	}

	private static byte[] material(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}
}
