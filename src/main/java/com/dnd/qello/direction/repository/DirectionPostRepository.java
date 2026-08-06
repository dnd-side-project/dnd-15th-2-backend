package com.dnd.qello.direction.repository;

import java.util.Optional;

import com.dnd.qello.direction.domain.DirectionPost;

public interface DirectionPostRepository {
	DirectionPost save(DirectionPost post);
	Optional<DirectionPost> findById(long id);
	Optional<DirectionPost> findBySenderAndIdempotencyKey(long senderId, String idempotencyKey);

	/** 소유권을 쿼리 조건에 포함한다. 남의 질문글이면 빈 결과다. */
	Optional<DirectionPost> findByIdAndSenderId(long id, long senderId);
}
