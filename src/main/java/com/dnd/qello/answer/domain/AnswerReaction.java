package com.dnd.qello.answer.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 질문자가 받은 답변에 남긴 공감. 답변자가 받는 유일한 반응 신호다.
 * 답변당 한 건이며 그 규칙은 answer_id를 PK로 둔 schema가 강제한다.
 * 누를 수 있는 사람이 질문글 작성자뿐이라는 규칙은 DB의 지연 constraint trigger가
 * commit 시점에 판정한다. 만료된 질문글의 답변에도 공감할 수 있다.
 */
public final class AnswerReaction {

	private final long answerId;
	private final long reactorId;
	private final Instant createdAt;

	private AnswerReaction(long answerId, long reactorId, Instant createdAt) {
		if (answerId <= 0) throw new IllegalArgumentException("answerId는 양수여야 합니다");
		if (reactorId <= 0) throw new IllegalArgumentException("reactorId는 양수여야 합니다");
		this.answerId = answerId;
		this.reactorId = reactorId;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt은 필수입니다");
	}

	public static AnswerReaction create(long answerId, long reactorId, Instant createdAt) {
		return new AnswerReaction(answerId, reactorId, createdAt);
	}

	public long getAnswerId() { return answerId; }
	public long getReactorId() { return reactorId; }
	public Instant getCreatedAt() { return createdAt; }
}
