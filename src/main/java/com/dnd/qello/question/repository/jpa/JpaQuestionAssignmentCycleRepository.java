package com.dnd.qello.question.repository.jpa;

import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.question.domain.QuestionAssignmentCycle;
import com.dnd.qello.question.repository.QuestionAssignmentCycleRepository;

@Repository
@Transactional(readOnly = true)
public class JpaQuestionAssignmentCycleRepository implements QuestionAssignmentCycleRepository {

	private final SpringDataQuestionAssignmentCycleRepository repository;

	public JpaQuestionAssignmentCycleRepository(SpringDataQuestionAssignmentCycleRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public QuestionAssignmentCycle save(QuestionAssignmentCycle cycle) {
		return QuestionJpaMapper.toDomain(repository.saveAndFlush(QuestionJpaMapper.toEntity(cycle)));
	}

	@Override
	public Optional<QuestionAssignmentCycle> findById(long id) {
		return repository.findById(id).map(QuestionJpaMapper::toDomain);
	}
}
