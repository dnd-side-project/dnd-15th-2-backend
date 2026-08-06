package com.dnd.qello.direction.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.direction.domain.PostRecipient;

public interface PostRecipientRepository {
	PostRecipient save(PostRecipient recipient);
	List<PostRecipient> findAllByPostId(long postId);

	/**
	 * 소유권 검증이 없는 조회다. 이미 다른 경로로 자격을 확인한 내부 서버 로직에서만
	 * 쓴다 — 예: AnswerReactionService가 answer_id → post_recipient_id로 내려갈 때.
	 * 사용자 입력으로 직접 이 메서드를 호출하지 않는다. 그런 경로는
	 * findByIdAndRecipientId를 쓴다.
	 */
	Optional<PostRecipient> findById(long id);

	/** 소유권을 쿼리 조건에 포함한다. 남의 항목이면 빈 결과이며 예외를 던지지 않는다. */
	Optional<PostRecipient> findByIdAndRecipientId(long id, long recipientId);

	Optional<PostRecipient> findByPostIdAndRecipientId(long postId, long recipientId);
}
