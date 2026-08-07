package com.dnd.qello.auth.repository.jpa;

import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.SecretHash;

final class DeviceCredentialJpaMapper {

	private DeviceCredentialJpaMapper() {
	}

	static DeviceCredentialJpaEntity toNewEntity(DeviceCredential credential) {
		return new DeviceCredentialJpaEntity(
			credential.getUserId(),
			credential.getInstallationId(),
			credential.getSecretHash().value(),
			credential.getPlatform(),
			credential.getStatus(),
			credential.getLastUsedAt(),
			credential.getCreatedAt(),
			credential.getRevokedAt()
		);
	}

	static DeviceCredential toDomain(DeviceCredentialJpaEntity entity) {
		return DeviceCredential.restore(
			entity.getId(),
			entity.getUserId(),
			entity.getInstallationId(),
			new SecretHash(entity.getSecretHash()),
			entity.getPlatform(),
			entity.getStatus(),
			entity.getLastUsedAt(),
			entity.getCreatedAt(),
			entity.getRevokedAt()
		);
	}

	/**
	 * 관리 상태 엔티티를 변경하고 Dirty Checking에 위임한다.
	 */
	static void updateLastUsedAt(DeviceCredentialJpaEntity entity, DeviceCredential credential) {
		entity.updateLastUsedAt(credential.getLastUsedAt());
	}

}
