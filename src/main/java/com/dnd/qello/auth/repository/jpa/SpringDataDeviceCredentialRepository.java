package com.dnd.qello.auth.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dnd.qello.auth.domain.CredentialStatus;

interface SpringDataDeviceCredentialRepository extends JpaRepository<DeviceCredentialJpaEntity, Long> {

	Optional<DeviceCredentialJpaEntity> findBySecretHash(String secretHash);

	Optional<DeviceCredentialJpaEntity> findByInstallationIdAndStatus(String installationId, CredentialStatus status);

}
