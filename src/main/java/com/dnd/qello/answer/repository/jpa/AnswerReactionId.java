package com.dnd.qello.answer.repository.jpa;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * answer_reaction의 복합 PK(answer_id, reactor_id)에 대응하는 {@code @IdClass}.
 * "한 사람은 한 답변에 한 번만"을 이 키가 강제한다 — 답변당 한 건이던 옛 단일 PK와
 * 달리, 이제 열람 자격자 여러 명이 같은 답변에 각자 한 행씩 가질 수 있다.
 */
@NoArgsConstructor
@AllArgsConstructor
final class AnswerReactionId implements Serializable {

	private Long answerId;
	private Long reactorId;

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof AnswerReactionId that)) return false;
		return Objects.equals(answerId, that.answerId) && Objects.equals(reactorId, that.reactorId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(answerId, reactorId);
	}
}
