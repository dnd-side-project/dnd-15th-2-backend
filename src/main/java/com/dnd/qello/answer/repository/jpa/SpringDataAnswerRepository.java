package com.dnd.qello.answer.repository.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAnswerRepository extends JpaRepository<AnswerJpaEntity, Long> {

	Optional<AnswerJpaEntity> findByAuthorIdAndIdempotencyKey(long authorId, String idempotencyKey);
}
