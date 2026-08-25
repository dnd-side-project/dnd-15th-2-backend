package com.dnd.qello.notification.push.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Created at: 2026-08-24T19:13:08+09:00
 * Source scenario: TEST-PLAN-GH-179-PUSH-DELIVERY-UNIT-002 through UNIT-005
 */
class PushTokenProtectorTest {

	private static final String CURRENT_KEY_ID = "current-key";
	private static final String PREVIOUS_KEY_ID = "previous-key";
	private static final String UNKNOWN_KEY_ID = "unknown-key";
	private static final String TOKEN_SENTINEL = "unit-protector-token-sentinel";
	private static final String OTHER_TOKEN_SENTINEL = "unit-protector-other-token-sentinel";
	private static final byte[] ENCRYPTION_KEY = keyMaterial((byte) 0x11);
	private static final byte[] PREVIOUS_ENCRYPTION_KEY = keyMaterial((byte) 0x22);
	private static final byte[] DIFFERENT_ENCRYPTION_KEY = keyMaterial((byte) 0x33);
	private static final byte[] FINGERPRINT_KEY = keyMaterial((byte) 0x44);
	private static final byte[] DIFFERENT_FINGERPRINT_KEY = keyMaterial((byte) 0x55);

	@Test
	@DisplayName("UNIT-002: 같은 token을 두 번 보호하면 nonce는 다르고 fingerprint는 같다")
	void usesFreshNonceAndStableFingerprintForRepeatedProtection() {
		PushTokenProtector protector = protectorWithCurrentKey();
		PushToken token = PushToken.of(TOKEN_SENTINEL);

		ProtectedPushToken first = protector.protect(token);
		ProtectedPushToken second = protector.protect(token);

		assertThat(first.envelope()).isNotEqualTo(second.envelope());
		assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
		assertThat(Base64.getEncoder().encodeToString(first.envelope())).doesNotContain(TOKEN_SENTINEL);
		assertThat(first.fingerprint()).doesNotContain(TOKEN_SENTINEL);
	}

	@Test
	@DisplayName("UNIT-003: token과 보호 key가 달라지면 fingerprint와 ciphertext가 달라진다")
	void changesFingerprintAndCiphertextWhenTokenOrKeysChange() {
		PushTokenProtector protector = protectorWithCurrentKey();
		PushTokenProtector differentKeyProtector = new AesGcmPushTokenProtector(
			keyRing(CURRENT_KEY_ID, DIFFERENT_ENCRYPTION_KEY, DIFFERENT_FINGERPRINT_KEY));

		ProtectedPushToken first = protector.protect(PushToken.of(TOKEN_SENTINEL));
		ProtectedPushToken differentToken = protector.protect(PushToken.of(OTHER_TOKEN_SENTINEL));
		ProtectedPushToken differentKeys = differentKeyProtector.protect(PushToken.of(TOKEN_SENTINEL));

		assertThat(first.fingerprint()).isNotEqualTo(differentToken.fingerprint());
		assertThat(first.fingerprint()).isNotEqualTo(differentKeys.fingerprint());
		assertThat(protector.fingerprint(PushToken.of(TOKEN_SENTINEL))).isEqualTo(first.fingerprint());
		assertThat(protector.fingerprint(PushToken.of(OTHER_TOKEN_SENTINEL)))
			.isEqualTo(differentToken.fingerprint());
		assertThat(differentKeyProtector.fingerprint(PushToken.of(TOKEN_SENTINEL)))
			.isEqualTo(differentKeys.fingerprint());
		assertThat(first.envelope()).isNotEqualTo(differentToken.envelope());
		assertThat(first.envelope()).isNotEqualTo(differentKeys.envelope());
	}

	@Test
	@DisplayName("UNIT-004: ciphertext와 tag 변조는 원문 없는 제한된 오류로 거절한다")
	void rejectsTamperedCiphertextWithoutEchoingToken() {
		PushTokenProtector protector = protectorWithCurrentKey();
		ProtectedPushToken protectedToken = protector.protect(PushToken.of(TOKEN_SENTINEL));
		byte[] tamperedEnvelope = protectedToken.envelope().clone();
		tamperedEnvelope[tamperedEnvelope.length - 1] ^= 0x01;

		assertThatThrownBy(() -> protector.decrypt(tamperedEnvelope))
			.isInstanceOf(PushTokenProtectionException.class)
			.hasMessage("[REDACTED]")
			.hasMessageNotContaining(TOKEN_SENTINEL);
	}

	@Test
	@DisplayName("UNIT-004: 미지원 version과 알 수 없는 key ID는 원문 없는 제한된 오류로 거절한다")
	void rejectsUnsupportedVersionAndUnknownKeyIdWithoutEchoingToken() {
		PushTokenProtector protector = protectorWithCurrentKey();
		byte[] envelope = protector.protect(PushToken.of(TOKEN_SENTINEL)).envelope();
		byte[] unsupportedVersion = envelope.clone();
		unsupportedVersion[0] = (byte) 99;
		byte[] unknownKeyId = replaceKeyId(envelope, UNKNOWN_KEY_ID);

		assertThatThrownBy(() -> protector.decrypt(unsupportedVersion))
			.isInstanceOf(PushTokenProtectionException.class)
			.hasMessage("[REDACTED]")
			.hasMessageNotContaining(TOKEN_SENTINEL);
		assertThatThrownBy(() -> protector.decrypt(unknownKeyId))
			.isInstanceOf(PushTokenProtectionException.class)
			.hasMessage("[REDACTED]")
			.hasMessageNotContaining(TOKEN_SENTINEL);
	}

	@Test
	@DisplayName("UNIT-005: current key로 쓰고 current와 previous read key로 읽으며 폐기 key는 거절한다")
	void writesWithCurrentKeyReadsPreviousKeyAndRejectsRetiredKey() {
		PushToken token = PushToken.of(TOKEN_SENTINEL);
		PushTokenProtector previousProtector = new AesGcmPushTokenProtector(
			keyRing(PREVIOUS_KEY_ID, PREVIOUS_ENCRYPTION_KEY, FINGERPRINT_KEY));
		PushTokenProtector rotatedProtector = new AesGcmPushTokenProtector(
			new PushTokenKeyRing(
				CURRENT_KEY_ID,
				Map.of(CURRENT_KEY_ID, ENCRYPTION_KEY, PREVIOUS_KEY_ID, PREVIOUS_ENCRYPTION_KEY),
				FINGERPRINT_KEY));
		PushTokenProtector retiredKeyProtector = new AesGcmPushTokenProtector(
			keyRing(CURRENT_KEY_ID, ENCRYPTION_KEY, FINGERPRINT_KEY));

		ProtectedPushToken previousEnvelope = previousProtector.protect(token);
		ProtectedPushToken currentEnvelope = rotatedProtector.protect(token);

		assertThat(envelopeKeyId(currentEnvelope.envelope())).isEqualTo(CURRENT_KEY_ID);
		assertThat(rotatedProtector.decrypt(currentEnvelope.envelope())).isEqualTo(token);
		assertThat(rotatedProtector.decrypt(previousEnvelope.envelope())).isEqualTo(token);
		assertThatThrownBy(() -> retiredKeyProtector.decrypt(previousEnvelope.envelope()))
			.isInstanceOf(PushTokenProtectionException.class)
			.hasMessage("[REDACTED]")
			.hasMessageNotContaining(TOKEN_SENTINEL);
	}

	private static PushTokenProtector protectorWithCurrentKey() {
		return new AesGcmPushTokenProtector(keyRing(CURRENT_KEY_ID, ENCRYPTION_KEY, FINGERPRINT_KEY));
	}

	private static PushTokenKeyRing keyRing(String currentKeyId, byte[] encryptionKey, byte[] fingerprintKey) {
		return new PushTokenKeyRing(currentKeyId, Map.of(currentKeyId, encryptionKey), fingerprintKey);
	}

	private static byte[] replaceKeyId(byte[] envelope, String keyId) {
		byte[] replacement = keyId.getBytes(StandardCharsets.UTF_8);
		int originalLength = Byte.toUnsignedInt(envelope[1]);
		if (replacement.length != originalLength) {
			throw new AssertionError("test key IDs must have the same length");
		}
		byte[] result = envelope.clone();
		System.arraycopy(replacement, 0, result, 2, replacement.length);
		return result;
	}

	private static String envelopeKeyId(byte[] envelope) {
		int keyIdLength = Byte.toUnsignedInt(envelope[1]);
		return new String(envelope, 2, keyIdLength, StandardCharsets.UTF_8);
	}

	private static byte[] keyMaterial(byte value) {
		byte[] key = new byte[32];
		Arrays.fill(key, value);
		return key;
	}
}
