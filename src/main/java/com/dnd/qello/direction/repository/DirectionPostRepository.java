package com.dnd.qello.direction.repository;

import java.util.Optional;

import com.dnd.qello.direction.domain.DirectionPost;

public interface DirectionPostRepository {
	DirectionPost save(DirectionPost post);
	Optional<DirectionPost> findById(long id);
	Optional<DirectionPost> findBySenderAndIdempotencyKey(long senderId, String idempotencyKey);
}
