package com.dnd.qello.answer.repository;

import java.util.Optional;

import com.dnd.qello.answer.domain.Answer;

public interface AnswerRepository {

	Answer save(Answer answer);

	Optional<Answer> findById(long id);

	Optional<Answer> findByAuthorAndIdempotencyKey(long authorId, String idempotencyKey);

	/** 소유권을 쿼리 조건에 포함한다. 남의 답변이면 빈 결과이며 예외를 던지지 않는다. */
	Optional<Answer> findByIdAndAuthorId(long id, long authorId);
}
