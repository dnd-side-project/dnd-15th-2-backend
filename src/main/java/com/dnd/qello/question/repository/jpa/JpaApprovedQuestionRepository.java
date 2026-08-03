package com.dnd.qello.question.repository.jpa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;

@Repository
@Transactional(readOnly = true)
public class JpaApprovedQuestionRepository implements ApprovedQuestionRepository {

	private final SpringDataApprovedQuestionRepository repository;

	public JpaApprovedQuestionRepository(SpringDataApprovedQuestionRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public ApprovedQuestion save(ApprovedQuestion question) {
		return QuestionJpaMapper.toDomain(repository.saveAndFlush(QuestionJpaMapper.toEntity(question)));
	}

	@Override
	public Optional<ApprovedQuestion> findById(long id) {
		return repository.findById(id).map(QuestionJpaMapper::toDomain);
	}

	@Override
	public List<ApprovedQuestion> findAssignableAt(Instant at) {
		return repository.findAssignableAt(ApprovedQuestionStatus.ACTIVE, at).stream()
			.map(QuestionJpaMapper::toDomain).toList();
	}
}
