package com.dnd.qello.direction.repository;

import com.dnd.qello.direction.domain.PostReaction;

public interface PostReactionRepository {

	PostReaction react(PostReaction reaction);

	void cancel(long postId, long reactorId);

	long countByPostId(long postId);

	boolean exists(long postId, long reactorId);
}
