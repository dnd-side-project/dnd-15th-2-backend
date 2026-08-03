package com.dnd.qello.answer.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.answer.domain.Answer;
import com.dnd.qello.answer.repository.AnswerRepository;

@Repository
@Transactional(readOnly = true)
public class JpaAnswerRepository implements AnswerRepository {

	private final SpringDataAnswerRepository repository;

	public JpaAnswerRepository(SpringDataAnswerRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public Answer save(Answer answer) {
		return AnswerJpaMapper.toDomain(repository.saveAndFlush(new AnswerJpaEntity(answer)));
	}

	@Override
	public Optional<Answer> findById(long id) {
		return repository.findById(id).map(AnswerJpaMapper::toDomain);
	}

	@Override
	public Optional<Answer> findByAuthorAndIdempotencyKey(long authorId, String idempotencyKey) {
		return repository.findByAuthorIdAndIdempotencyKey(authorId, idempotencyKey)
			.map(AnswerJpaMapper::toDomain);
	}
}
