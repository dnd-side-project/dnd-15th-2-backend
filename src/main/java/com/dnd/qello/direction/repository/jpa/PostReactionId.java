package com.dnd.qello.direction.repository.jpa;

import java.io.Serializable;
import java.util.Objects;

/**
 * post_reaction의 복합 PK. Hibernate가 @IdClass로 요구하는 형태다.
 */
public class PostReactionId implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long postId;
	private Long reactorId;

	public PostReactionId() { }

	PostReactionId(Long postId, Long reactorId) {
		this.postId = postId;
		this.reactorId = reactorId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof PostReactionId that)) return false;
		return Objects.equals(postId, that.postId) && Objects.equals(reactorId, that.reactorId);
	}

	@Override
	public int hashCode() { return Objects.hash(postId, reactorId); }

	@Override
	public String toString() {
		return "PostReactionId{postId=" + postId + ", reactorId=" + reactorId + "}";
	}
}
