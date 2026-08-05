package com.dnd.qello.direction.repository.jpa;

import java.time.Instant;

import com.dnd.qello.direction.domain.PostReaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_reaction")
@IdClass(PostReactionId.class)
public class PostReactionJpaEntity {

	@Id
	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Id
	@Column(name = "reactor_id", nullable = false)
	private Long reactorId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PostReactionJpaEntity() { }

	PostReactionJpaEntity(PostReaction reaction) {
		this.postId = reaction.getPostId();
		this.reactorId = reaction.getReactorId();
		this.createdAt = reaction.getCreatedAt();
	}

	PostReaction toDomain() {
		return PostReaction.create(postId, reactorId, createdAt);
	}
}
