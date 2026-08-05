package com.dnd.qello.answer.repository;

import java.util.Optional;

import com.dnd.qello.answer.domain.AnswerReaction;

public interface AnswerReactionRepository {

	AnswerReaction react(AnswerReaction reaction);

	void cancel(long answerId);

	Optional<AnswerReaction> findByAnswerId(long answerId);
}
