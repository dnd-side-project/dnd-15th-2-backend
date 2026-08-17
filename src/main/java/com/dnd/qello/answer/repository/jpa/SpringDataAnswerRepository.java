package com.dnd.qello.answer.repository.jpa;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dnd.qello.answer.domain.AnswerStatus;

interface SpringDataAnswerRepository extends JpaRepository<AnswerJpaEntity, Long> {

	Optional<AnswerJpaEntity> findByAuthorIdAndIdempotencyKey(long authorId, String idempotencyKey);

	Optional<AnswerJpaEntity> findByIdAndAuthorId(long id, long authorId);

	boolean existsByPostRecipientIdAndStatusIn(long postRecipientId, Collection<AnswerStatus> statuses);
}
