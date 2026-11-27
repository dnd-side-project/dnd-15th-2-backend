package com.dnd.qello.direction.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.dnd.qello.direction.config.CoordinateProtectionProperties;

@Component
public final class AesGcmCoordinateProtector implements CoordinateProtector {

	private static final byte VERSION = 1;
	private static final int NONCE_LENGTH_BYTES = 12;
	private static final int TAG_LENGTH_BITS = 128;
	private static final int CIPHERTEXT_LENGTH_BYTES = CoordinateCodec.PAYLOAD_LENGTH_BYTES + TAG_LENGTH_BITS / 8;

	private final CoordinateKeyRing keyRing;
	private final SecureRandom secureRandom;

	@Autowired
	public AesGcmCoordinateProtector(CoordinateProtectionProperties properties) {
		this(properties.keyRing());
	}

	public AesGcmCoordinateProtector(CoordinateKeyRing keyRing) {
		this.keyRing = Objects.requireNonNull(keyRing, "keyRing");
		this.secureRandom = new SecureRandom();
	}

	@Override
	public byte[] protect(Coordinate coordinate, CoordinatePurpose purpose, long rowId) {
		validateContext(purpose, rowId);
		byte[] plaintext = CoordinateCodec.encode(coordinate);
		byte[] keyId = keyRing.currentKeyId().getBytes(StandardCharsets.UTF_8);
		byte[] header = ByteBuffer.allocate(2 + keyId.length).put(VERSION).put((byte) keyId.length).put(keyId).array();
		byte[] nonce = new byte[NONCE_LENGTH_BYTES];
		secureRandom.nextBytes(nonce);
		try {
			Cipher cipher = cipher(Cipher.ENCRYPT_MODE, keyRing.currentKeyId(), nonce, header, purpose, rowId);
			byte[] ciphertext = cipher.doFinal(plaintext);
			return ByteBuffer.allocate(header.length + nonce.length + ciphertext.length)
					.put(header).put(nonce).put(ciphertext).array();
		} catch (GeneralSecurityException exception) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.ENCRYPTION_FAILED);
		} finally {
			Arrays.fill(plaintext, (byte) 0);
		}
	}

	@Override
	public Coordinate decrypt(byte[] envelope, CoordinatePurpose purpose, long rowId) {
		validateContext(purpose, rowId);
		validateEnvelope(envelope);
		int headerLength = 2 + Byte.toUnsignedInt(envelope[1]);
		String keyId = new String(envelope, 2, headerLength - 2, StandardCharsets.UTF_8);
		byte[] header = Arrays.copyOf(envelope, headerLength);
		byte[] nonce = Arrays.copyOfRange(envelope, headerLength, headerLength + NONCE_LENGTH_BYTES);
		byte[] plaintext = null;
		try {
			Cipher cipher = cipher(Cipher.DECRYPT_MODE, keyId, nonce, header, purpose, rowId);
			plaintext = cipher.doFinal(envelope, headerLength + NONCE_LENGTH_BYTES, CIPHERTEXT_LENGTH_BYTES);
			return CoordinateCodec.decode(plaintext);
		} catch (GeneralSecurityException exception) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.AUTHENTICATION_FAILED);
		} finally {
			if (plaintext != null) {
				Arrays.fill(plaintext, (byte) 0);
			}
		}
	}

	private Cipher cipher(int mode, String keyId, byte[] nonce, byte[] header, CoordinatePurpose purpose, long rowId)
			throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(mode, keyRing.key(keyId), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
		cipher.updateAAD(header);
		cipher.updateAAD(purpose.bindingBytes());
		cipher.updateAAD(ByteBuffer.allocate(Long.BYTES).putLong(rowId).array());
		return cipher;
	}

	private static void validateContext(CoordinatePurpose purpose, long rowId) {
		if (purpose == null || rowId <= 0) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_CONTEXT);
		}
	}

	private static void validateEnvelope(byte[] envelope) {
		if (envelope == null || envelope.length < 3 || envelope[0] != VERSION) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_ENVELOPE);
		}
		int keyIdLength = Byte.toUnsignedInt(envelope[1]);
		if (keyIdLength == 0 || envelope.length != 2 + keyIdLength + NONCE_LENGTH_BYTES + CIPHERTEXT_LENGTH_BYTES) {
			throw new CoordinateProtectionException(CoordinateProtectionException.Reason.INVALID_ENVELOPE);
		}
	}
}
