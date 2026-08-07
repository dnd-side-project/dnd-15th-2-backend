package com.dnd.qello.auth.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOperatorCredentialRepository
	extends JpaRepository<OperatorCredentialJpaEntity, Long> {

	Optional<OperatorCredentialJpaEntity> findByLoginId(String loginId);

}
