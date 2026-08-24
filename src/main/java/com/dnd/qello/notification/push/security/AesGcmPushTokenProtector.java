package com.dnd.qello.notification.push.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmPushTokenProtector implements PushTokenProtector {

	private static final byte VERSION = 1;
	private static final int NONCE_LENGTH_BYTES = 12;
	private static final int GCM_TAG_LENGTH_BITS = 128;
	private static final String REDACTED = "[REDACTED]";

	private final PushTokenKeyRing keyRing;
	private final SecureRandom secureRandom;

	public AesGcmPushTokenProtector(PushTokenKeyRing keyRing) {
		this(keyRing, new SecureRandom());
	}

	AesGcmPushTokenProtector(PushTokenKeyRing keyRing, SecureRandom secureRandom) {
		this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
		this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
	}

	@Override
	public ProtectedPushToken protect(PushToken token) {
		Objects.requireNonNull(token, "token");
		byte[] keyId = keyRing.currentKeyId().getBytes(StandardCharsets.UTF_8);
		byte[] header = header(keyId);
		byte[] nonce = new byte[NONCE_LENGTH_BYTES];
		secureRandom.nextBytes(nonce);
		byte[] ciphertext = encrypt(token.utf8Bytes(), header, nonce, keyRing.currentEncryptionKey());
		ByteBuffer envelope = ByteBuffer.allocate(header.length + nonce.length + ciphertext.length);
		envelope.put(header);
		envelope.put(nonce);
		envelope.put(ciphertext);
		return new ProtectedPushToken(envelope.array(), fingerprint(token));
	}

	@Override
	public PushToken decrypt(byte[] envelope) {
		if (envelope == null) {
			throw new PushTokenProtectionException();
		}
		try {
			ParsedEnvelope parsedEnvelope = parse(envelope);
			byte[] plaintext = decrypt(
				parsedEnvelope.ciphertext(),
				parsedEnvelope.header(),
				parsedEnvelope.nonce(),
				keyRing.encryptionKey(parsedEnvelope.keyId()));
			return PushToken.of(new String(plaintext, StandardCharsets.UTF_8));
		}
		catch (IllegalArgumentException | PushTokenProtectionException exception) {
			throw exception instanceof PushTokenProtectionException protectionException
				? protectionException
				: new PushTokenProtectionException();
		}
		catch (GeneralSecurityException exception) {
			throw new PushTokenProtectionException();
		}
	}

	@Override
	public String fingerprint(PushToken token) {
		Objects.requireNonNull(token, "token");
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(keyRing.fingerprintKey(), "HmacSHA256"));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(token.utf8Bytes()));
		}
		catch (GeneralSecurityException exception) {
			throw new PushTokenProtectionException();
		}
	}

	private byte[] encrypt(byte[] plaintext, byte[] header, byte[] nonce, SecretKeySpec secretKey) {
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
			cipher.updateAAD(header);
			return cipher.doFinal(plaintext);
		}
		catch (GeneralSecurityException exception) {
			throw new PushTokenProtectionException();
		}
	}

	private byte[] decrypt(byte[] ciphertext, byte[] header, byte[] nonce, SecretKeySpec secretKey)
		throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
		cipher.updateAAD(header);
		return cipher.doFinal(ciphertext);
	}

	private ParsedEnvelope parse(byte[] envelope) {
		if (envelope.length < 2 + NONCE_LENGTH_BYTES + 16) {
			throw new PushTokenProtectionException();
		}
		ByteBuffer buffer = ByteBuffer.wrap(envelope.clone());
		byte version = buffer.get();
		if (version != VERSION) {
			throw new PushTokenProtectionException();
		}
		int keyIdLength = Byte.toUnsignedInt(buffer.get());
		if (keyIdLength == 0 || buffer.remaining() < keyIdLength + NONCE_LENGTH_BYTES + 16) {
			throw new PushTokenProtectionException();
		}
		byte[] keyIdBytes = new byte[keyIdLength];
		buffer.get(keyIdBytes);
		byte[] nonce = new byte[NONCE_LENGTH_BYTES];
		buffer.get(nonce);
		byte[] ciphertext = new byte[buffer.remaining()];
		buffer.get(ciphertext);
		return new ParsedEnvelope(
			new String(keyIdBytes, StandardCharsets.UTF_8),
			header(keyIdBytes),
			nonce,
			ciphertext);
	}

	private static byte[] header(byte[] keyId) {
		ByteBuffer buffer = ByteBuffer.allocate(2 + keyId.length);
		buffer.put(VERSION);
		buffer.put((byte) keyId.length);
		buffer.put(keyId);
		return buffer.array();
	}

	private record ParsedEnvelope(String keyId, byte[] header, byte[] nonce, byte[] ciphertext) {
	}
}
