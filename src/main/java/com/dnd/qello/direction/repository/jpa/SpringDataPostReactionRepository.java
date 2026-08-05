package com.dnd.qello.direction.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPostReactionRepository extends JpaRepository<PostReactionJpaEntity, PostReactionId> {

	long countByPostId(long postId);

	boolean existsByPostIdAndReactorId(long postId, long reactorId);

	void deleteByPostIdAndReactorId(long postId, long reactorId);
}
