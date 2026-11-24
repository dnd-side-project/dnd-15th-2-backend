package com.dnd.qello.direction.consent;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataLocationConsentRepository extends JpaRepository<LocationConsentEntity, Long> {

	Optional<LocationConsentEntity> findFirstByUserIdOrderByIdDesc(long userId);
}
