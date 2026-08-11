package com.dnd.qello.filtering.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFilterJobRepository extends JpaRepository<FilterJobJpaEntity, Long> {

	Optional<FilterJobJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
