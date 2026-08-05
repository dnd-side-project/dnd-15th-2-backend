package com.dnd.qello.direction.domain;

import java.time.Instant;

import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;

/**
 * 질문글에 남긴 공감. 답변은 부담스럽지만 반응은 하고 싶은 수신자의 저비용 표현 수단이다.
 * 누를 수 있는 사람은 수신 자격이 있는 사용자뿐이며, 그 규칙은 DB의 복합 FK가 강제한다.
 * 취소는 행 삭제이므로 상태 컬럼을 두지 않는다.
 */
public final class PostReaction {

	private final long postId;
	private final long reactorId;
	private final Instant createdAt;

	private PostReaction(long postId, long reactorId, Instant createdAt) {
		if (postId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "postId", "postId는 양수여야 합니다");
		}
		if (reactorId <= 0) {
			throw new DirectionException(DirectionErrorCode.INVALID_ID, "reactorId", "reactorId는 양수여야 합니다");
		}
		this.postId = postId;
		this.reactorId = reactorId;
		if (createdAt == null) {
			throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "createdAt", "createdAt은 필수입니다");
		}
		this.createdAt = createdAt;
	}

	public static PostReaction create(long postId, long reactorId, Instant createdAt) {
		return new PostReaction(postId, reactorId, createdAt);
	}

	public long getPostId() { return postId; }
	public long getReactorId() { return reactorId; }
	public Instant getCreatedAt() { return createdAt; }
}
