package com.dnd.qello.direction.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.direction.domain.PostRecipient;
import com.dnd.qello.direction.domain.PostRecipientStatus;

public interface PostRecipientRepository {
	PostRecipient save(PostRecipient recipient);
	List<PostRecipient> findAllByPostId(long postId);

	/**
	 * status가 여전히 previousStatus일 때만 answered로 전이하고 저장한다. 영향받은 행이
	 * 없으면(이미 다른 트랜잭션이 먼저 ANSWERED로 전이시킨 경우) empty를 반환한다 — 호출자는
	 * 이 empty를 신호로 슬롯 회수(receive state release)를 건너뛰어, 동시에 들어온 publish()
	 * 재시도가 슬롯을 중복 회수하지 않도록 한다.
	 */
	Optional<PostRecipient> transitionToAnswered(PostRecipient answered, PostRecipientStatus previousStatus);

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
