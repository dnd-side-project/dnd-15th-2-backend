package com.dnd.qello.answer.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.AnswerReaction;
import com.dnd.qello.answer.repository.AnswerReactionRepository;

@Repository
public class JpaAnswerReactionRepository implements AnswerReactionRepository {

	private final SpringDataAnswerReactionRepository reactions;

	public JpaAnswerReactionRepository(SpringDataAnswerReactionRepository reactions) {
		this.reactions = reactions;
	}

	/**
	 * 작성자 검증은 ct_answer_reaction_reactor_is_sender가 맡는다. 지연 트리거이므로
	 * 위반은 이 메서드가 아니라 transaction commit 시점에 드러난다.
	 */
	@Override
	@Transactional
	public AnswerReaction react(AnswerReaction reaction) {
		return reactions.saveAndFlush(new AnswerReactionJpaEntity(reaction)).toDomain();
	}

	@Override
	@Transactional
	public void cancel(long answerId) {
		reactions.deleteById(answerId);
	}

	@Override
	public Optional<AnswerReaction> findByAnswerId(long answerId) {
		return reactions.findById(answerId).map(AnswerReactionJpaEntity::toDomain);
	}
}
