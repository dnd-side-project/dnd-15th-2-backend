package com.dnd.qello.auth.repository;

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

}
