package com.dnd.qello.answer.repository.jpa;

import java.time.Instant;

import com.dnd.qello.answer.domain.AnswerReaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "answer_reaction")
public class AnswerReactionJpaEntity {

	@Id
	@Column(name = "answer_id", nullable = false)
	private Long answerId;

	@Column(name = "reactor_id", nullable = false)
	private Long reactorId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AnswerReactionJpaEntity() { }

	AnswerReactionJpaEntity(AnswerReaction reaction) {
		this.answerId = reaction.getAnswerId();
		this.reactorId = reaction.getReactorId();
		this.createdAt = reaction.getCreatedAt();
	}

	AnswerReaction toDomain() {
		return AnswerReaction.create(answerId, reactorId, createdAt);
	}
}
