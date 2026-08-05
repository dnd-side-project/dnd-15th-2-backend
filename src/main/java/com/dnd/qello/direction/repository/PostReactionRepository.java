package com.dnd.qello.direction.repository;

import com.dnd.qello.direction.domain.PostReaction;

public interface PostReactionRepository {

	/**
	 * 수신 자격 검증은 즉시(non-deferred) 복합 FK인 {@code fk_post_reaction_recipient}가 맡는다.
	 * 그래서 자격 없는 reactor로 호출하면 이 메서드 호출 자체(flush 시점)에서 실패가 드러난다.
	 * commit까지 기다려야 실패가 드러나는
	 * {@link com.dnd.qello.answer.repository.AnswerReactionRepository#react}와는
	 * 실패 시점이 다르다는 점에 유의한다.
	 * <p>
	 * {@code createdAt}은 DB의 기본값이 아니라 애플리케이션이 주입한 {@link java.time.Clock}으로
	 * 채운 값을 그대로 저장한다(이 repo의 {@code JpaAuditingConfiguration}이 채택한 것과 같은 원칙).
	 */
	PostReaction react(PostReaction reaction);

	void cancel(long postId, long reactorId);

	long countByPostId(long postId);

	boolean exists(long postId, long reactorId);
}
