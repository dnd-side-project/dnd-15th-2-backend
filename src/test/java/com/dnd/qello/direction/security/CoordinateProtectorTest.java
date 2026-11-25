/**
 * Created at: 2026-11-24T17:41:29Z
 * Source scenario: TEST-PLAN-GH-388-COORDINATE-ENCRYPTION-UNIT-001 through UNIT-007
 */
package com.dnd.qello.direction.security;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class CoordinateProtectorTest {

	private static final String CURRENT_ID = "current";
	private static final String PREVIOUS_ID = "former1";
	private static final byte[] CURRENT_KEY = material((byte) 0x21);
	private static final byte[] PREVIOUS_KEY = material((byte) 0x32);
	private static final long ROW_ID = 401L;

	@ParameterizedTest
	@EnumSource(CoordinatePurpose.class)
	@DisplayName("UNIT-001: 독립 JCE가 header·용도·행에 결합된 AES-256-GCM payload를 복원한다")
	void independentlyDecryptsAuthenticatedEnvelope(CoordinatePurpose purpose) throws Exception {
		Coordinate input = coordinate();
		CoordinateProtector protector = protector(CURRENT_ID, Map.of(CURRENT_ID, CURRENT_KEY));

		byte[] envelope = protector.protect(input, purpose, ROW_ID);

		assertThat(envelope[0]).isEqualTo((byte) 1);
		int idLength = Byte.toUnsignedInt(envelope[1]);
		assertThat(keyId(envelope)).isEqualTo(CURRENT_ID);
		assertThat(envelope.length).isEqualTo(2 + idLength + 12 + 16 + 16);
		byte[] header = Arrays.copyOf(envelope, 2 + idLength);
		byte[] nonce = Arrays.copyOfRange(envelope, header.length, header.length + 12);
		Cipher oracle = Cipher.getInstance("AES/GCM/NoPadding");
		oracle.init(Cipher.DECRYPT_MODE, new SecretKeySpec(CURRENT_KEY, "AES"), new GCMParameterSpec(128, nonce));
		oracle.updateAAD(header);
		String binding = switch (purpose) {
			case ACTIVE_USER_PRESENCE -> "active_user_presence.position";
			case POST_AUDIENCE -> "post_audience.origin_position";
		};
		oracle.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
		oracle.updateAAD(ByteBuffer.allocate(8).putLong(ROW_ID).array());
		ByteBuffer plaintext = ByteBuffer.wrap(oracle.doFinal(envelope, header.length + 12, 32));
		assertThat(plaintext.getDouble() == input.latitude().doubleValue()).isTrue();
		assertThat(plaintext.getDouble() == input.longitude().doubleValue()).isTrue();
		assertThat(plaintext.hasRemaining()).isFalse();
	}

	@Test
	@DisplayName("UNIT-001: 반복 저장은 새로운 nonce를 쓰고 각 암호문은 정상 복원된다")
	void usesFreshNonceForEveryWrite() {
		CoordinateProtector protector = protector(CURRENT_ID, Map.of(CURRENT_ID, CURRENT_KEY));

		byte[] first = protector.protect(coordinate(), CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID);
		byte[] second = protector.protect(coordinate(), CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID);

		int start = 2 + CURRENT_ID.length();
		assertThat(Arrays.equals(Arrays.copyOfRange(first, start, start + 12),
				Arrays.copyOfRange(second, start, start + 12))).isFalse();
		assertCoordinate(protector.decrypt(first, CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID));
		assertCoordinate(protector.decrypt(second, CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID));
	}

	@Test
	@DisplayName("UNIT-002: 회전 후 current로 쓰며 이전 세대와 현재 세대를 모두 읽는다")
	void readsBothGenerationsAndWritesOnlyCurrent() {
		CoordinateProtector previous = protector(PREVIOUS_ID, Map.of(PREVIOUS_ID, PREVIOUS_KEY));
		CoordinateProtector rotated = protector(CURRENT_ID,
				Map.of(CURRENT_ID, CURRENT_KEY, PREVIOUS_ID, PREVIOUS_KEY));
		byte[] old = previous.protect(coordinate(), CoordinatePurpose.POST_AUDIENCE, ROW_ID);

		byte[] fresh = rotated.protect(coordinate(), CoordinatePurpose.POST_AUDIENCE, ROW_ID);

		assertThat(keyId(fresh)).isEqualTo(CURRENT_ID);
		assertCoordinate(rotated.decrypt(old, CoordinatePurpose.POST_AUDIENCE, ROW_ID));
		assertCoordinate(rotated.decrypt(fresh, CoordinatePurpose.POST_AUDIENCE, ROW_ID));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("UNIT-003: 이전 키 누락 또는 동일 ID의 잘못된 material은 복호화를 거부한다")
	void rejectsMissingOrWrongKey(boolean wrongMaterial) {
		byte[] envelope = protector(PREVIOUS_ID, Map.of(PREVIOUS_ID, PREVIOUS_KEY))
				.protect(coordinate(), CoordinatePurpose.POST_AUDIENCE, ROW_ID);
		Map<String, byte[]> keys = wrongMaterial
				? Map.of(CURRENT_ID, CURRENT_KEY, PREVIOUS_ID, CURRENT_KEY)
				: Map.of(CURRENT_ID, CURRENT_KEY);

		CoordinateProtectionException failure = catchThrowableOfType(
				() -> protector(CURRENT_ID, keys).decrypt(envelope, CoordinatePurpose.POST_AUDIENCE, ROW_ID),
				CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.reason()).isEqualTo(wrongMaterial
				? CoordinateProtectionException.Reason.AUTHENTICATION_FAILED
				: CoordinateProtectionException.Reason.UNKNOWN_KEY);
		assertThat(failure.getCause()).isNull();
	}

	@ParameterizedTest
	@EnumSource(CoordinatePurpose.class)
	@DisplayName("UNIT-004: 같은 용도의 다른 행에는 암호문을 재사용할 수 없다")
	void rejectsCrossRowReplacement(CoordinatePurpose purpose) {
		CoordinateProtector protector = protector(CURRENT_ID, Map.of(CURRENT_ID, CURRENT_KEY));
		byte[] envelope = protector.protect(coordinate(), purpose, ROW_ID);

		CoordinateProtectionException failure = catchThrowableOfType(
				() -> protector.decrypt(envelope, purpose, ROW_ID + 1), CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.reason()).isEqualTo(CoordinateProtectionException.Reason.AUTHENTICATION_FAILED);
	}

	@ParameterizedTest
	@EnumSource(CoordinatePurpose.class)
	@DisplayName("UNIT-005: 행 번호가 같아도 다른 table·column 용도로 복호화할 수 없다")
	void rejectsCrossPurposeReplacement(CoordinatePurpose purpose) {
		CoordinateProtector protector = protector(CURRENT_ID, Map.of(CURRENT_ID, CURRENT_KEY));
		byte[] envelope = protector.protect(coordinate(), purpose, ROW_ID);
		CoordinatePurpose other = purpose == CoordinatePurpose.ACTIVE_USER_PRESENCE
				? CoordinatePurpose.POST_AUDIENCE
				: CoordinatePurpose.ACTIVE_USER_PRESENCE;

		CoordinateProtectionException failure = catchThrowableOfType(
				() -> protector.decrypt(envelope, other, ROW_ID), CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.reason()).isEqualTo(CoordinateProtectionException.Reason.AUTHENTICATION_FAILED);
	}

	@ParameterizedTest(name = "{index}")
	@MethodSource("byteOffsets")
	@DisplayName("UNIT-006: header·nonce·ciphertext·tag의 어느 바이트도 무시하지 않는다")
	void rejectsEveryTamperedByte(int offset) {
		CoordinateProtector protector = protector(CURRENT_ID, Map.of(CURRENT_ID, CURRENT_KEY));
		byte[] envelope = protector.protect(coordinate(), CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID);
		envelope[offset] ^= 1;

		CoordinateProtectionException failure = catchThrowableOfType(
				() -> protector.decrypt(envelope, CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID),
				CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.getCause()).isNull();
	}

	@Test
	@DisplayName("UNIT-006: 동일 material의 알려진 다른 key ID로 header를 바꿔도 인증이 실패한다")
	void authenticatesKeyIdInsteadOfOnlySelectingKey() {
		CoordinateProtector protector = protector(CURRENT_ID,
				Map.of(CURRENT_ID, CURRENT_KEY, PREVIOUS_ID, CURRENT_KEY));
		byte[] envelope = protector.protect(coordinate(), CoordinatePurpose.POST_AUDIENCE, ROW_ID);
		System.arraycopy(PREVIOUS_ID.getBytes(StandardCharsets.UTF_8), 0, envelope, 2, PREVIOUS_ID.length());

		CoordinateProtectionException failure = catchThrowableOfType(
				() -> protector.decrypt(envelope, CoordinatePurpose.POST_AUDIENCE, ROW_ID),
				CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.reason()).isEqualTo(CoordinateProtectionException.Reason.AUTHENTICATION_FAILED);
	}

	@ParameterizedTest(name = "{index}")
	@MethodSource("malformedEnvelopes")
	@DisplayName("UNIT-007: NULL·잘림·추가 바이트·미지원 version·빈 ID를 제한된 오류로 거부한다")
	void rejectsMalformedEnvelope(byte[] malformed) {
		CoordinateProtector protector = protector(CURRENT_ID, Map.of(CURRENT_ID, CURRENT_KEY));

		CoordinateProtectionException failure = catchThrowableOfType(
				() -> protector.decrypt(malformed, CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID),
				CoordinateProtectionException.class);

		assertThat(failure).isNotNull();
		assertThat(failure.reason()).isEqualTo(CoordinateProtectionException.Reason.INVALID_ENVELOPE);
		assertThat(failure.getCause()).isNull();
	}

	private static IntStream byteOffsets() {
		return IntStream.range(0, 2 + CURRENT_ID.length() + 12 + 32);
	}

	private static Stream<byte[]> malformedEnvelopes() {
		byte[] envelope = protector(CURRENT_ID, Map.of(CURRENT_ID, CURRENT_KEY))
				.protect(coordinate(), CoordinatePurpose.ACTIVE_USER_PRESENCE, ROW_ID);
		byte[] unsupported = envelope.clone();
		unsupported[0] = 2;
		byte[] emptyId = envelope.clone();
		emptyId[1] = 0;
		return Stream.concat(
				IntStream.range(0, envelope.length).mapToObj(length -> Arrays.copyOf(envelope, length)),
				Stream.of(null, unsupported, emptyId, Arrays.copyOf(envelope, envelope.length + 1)));
	}

	private static CoordinateProtector protector(String currentId, Map<String, byte[]> keys) {
		return new AesGcmCoordinateProtector(new CoordinateKeyRing(currentId, keys));
	}

	private static Coordinate coordinate() {
		return new Coordinate(new BigDecimal("37.123456789012345"), new BigDecimal("-122.98765432109876"));
	}

	private static void assertCoordinate(Coordinate actual) {
		assertThat(actual.latitude().doubleValue() == coordinate().latitude().doubleValue()).isTrue();
		assertThat(actual.longitude().doubleValue() == coordinate().longitude().doubleValue()).isTrue();
	}

	private static String keyId(byte[] envelope) {
		return new String(envelope, 2, Byte.toUnsignedInt(envelope[1]), StandardCharsets.UTF_8);
	}

	private static byte[] material(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}
}
