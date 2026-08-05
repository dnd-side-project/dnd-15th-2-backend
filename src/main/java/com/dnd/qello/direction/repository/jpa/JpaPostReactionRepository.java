package com.dnd.qello.direction.repository.jpa;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.direction.domain.PostReaction;
import com.dnd.qello.direction.repository.PostReactionRepository;

@Repository
@Transactional(readOnly = true)
public class JpaPostReactionRepository implements PostReactionRepository {

	private final SpringDataPostReactionRepository reactions;

	public JpaPostReactionRepository(SpringDataPostReactionRepository reactions) {
		this.reactions = reactions;
	}

	@Override
	@Transactional
	public PostReaction react(PostReaction reaction) {
		return reactions.saveAndFlush(new PostReactionJpaEntity(reaction)).toDomain();
	}

	@Override
	@Transactional
	public void cancel(long postId, long reactorId) {
		reactions.deleteByPostIdAndReactorId(postId, reactorId);
	}

	@Override
	public long countByPostId(long postId) {
		return reactions.countByPostId(postId);
	}

	@Override
	public boolean exists(long postId, long reactorId) {
		return reactions.existsByPostIdAndReactorId(postId, reactorId);
	}
}
