package com.dnd.qello.answer.repository;

import java.util.Optional;

import com.dnd.qello.answer.domain.Answer;

public interface AnswerRepository {

	Answer save(Answer answer);

	Optional<Answer> findById(long id);

	Optional<Answer> findByAuthorAndIdempotencyKey(long authorId, String idempotencyKey);
}
