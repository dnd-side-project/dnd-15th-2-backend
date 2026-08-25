package com.dnd.qello.notification.repository.jdbc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 네임스페이스 기반 Postgres advisory lock 키를 계산하는 공유 헬퍼. */
final class AdvisoryLockKeys {

	private AdvisoryLockKeys() {
	}

	static long of(String namespace, String value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256")
				.digest((namespace + ":" + value).getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.wrap(hash).getLong();
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
		}
	}
}
