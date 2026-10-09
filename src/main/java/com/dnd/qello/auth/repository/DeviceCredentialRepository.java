package com.dnd.qello.auth.repository;

import java.time.Instant;
import java.util.Optional;

import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.SecretHash;

public interface DeviceCredentialRepository {

	/**
	 * 신규 자격증명만 저장한다.
	 */
	DeviceCredential save(DeviceCredential credential);

	/**
	 * last_used_at만 갱신한다.
	 */
	DeviceCredential updateLastUsedAt(DeviceCredential credential);

	Optional<DeviceCredential> findBySecretHash(SecretHash secretHash);

	Optional<DeviceCredential> findActiveByInstallationId(String installationId);

	/**
	 * 사용자의 ACTIVE 자격증명을 모두 REVOKED로 바꾸고 바꾼 개수를 돌려준다(#337). 이미 폐기된 자격증명은 건드리지 않는다.
	 */
	int revokeAllActiveByUserId(long userId, Instant revokedAt);

}
