package com.dnd.qello.auth.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.auth.domain.CredentialStatus;
import com.dnd.qello.auth.domain.DeviceCredential;
import com.dnd.qello.auth.domain.SecretHash;
import com.dnd.qello.auth.error.AuthErrorCode;
import com.dnd.qello.auth.error.AuthException;
import com.dnd.qello.auth.repository.DeviceCredentialRepository;

// 수정 경로는 관리 엔티티를 조회해 Dirty Checking에 맡긴다. JpaOperatorCredentialRepository와 같은 방식이다.
@Repository
@Transactional(readOnly = true)
public class JpaDeviceCredentialRepository implements DeviceCredentialRepository {

	private final SpringDataDeviceCredentialRepository repository;

	public JpaDeviceCredentialRepository(SpringDataDeviceCredentialRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public DeviceCredential save(DeviceCredential credential) {
		DeviceCredentialJpaEntity entity = DeviceCredentialJpaMapper.toNewEntity(credential);
		// installation_id ACTIVE 유일성 위반을 flush 시점에 드러내 트랜잭션 경계 밖으로
		// 원인 불명 상태로 밀리지 않게 한다.
		DeviceCredentialJpaEntity saved = repository.saveAndFlush(entity);
		return DeviceCredentialJpaMapper.toDomain(saved);
	}

	@Override
	@Transactional
	public DeviceCredential updateLastUsedAt(DeviceCredential credential) {
		DeviceCredentialJpaEntity entity = findManaged(credential.getId());
		DeviceCredentialJpaMapper.updateLastUsedAt(entity, credential);
		return DeviceCredentialJpaMapper.toDomain(entity);
	}

	@Override
	public Optional<DeviceCredential> findBySecretHash(SecretHash secretHash) {
		return repository.findBySecretHash(secretHash.value())
			.map(DeviceCredentialJpaMapper::toDomain);
	}

	@Override
	public Optional<DeviceCredential> findActiveByInstallationId(String installationId) {
		return repository.findByInstallationIdAndStatus(installationId, CredentialStatus.ACTIVE)
			.map(DeviceCredentialJpaMapper::toDomain);
	}

	private DeviceCredentialJpaEntity findManaged(Long id) {
		return repository.findById(id)
			.orElseThrow(() -> new AuthException(
				AuthErrorCode.CREDENTIAL_NOT_FOUND, "id", "대상 자격증명이 존재하지 않습니다"));
	}

}
