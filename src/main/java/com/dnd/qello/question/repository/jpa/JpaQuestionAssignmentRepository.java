package com.dnd.qello.question.repository.jpa;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.question.domain.QuestionAssignment;
import com.dnd.qello.question.repository.QuestionAssignmentRepository;

@Repository
@Transactional(readOnly = true)
public class JpaQuestionAssignmentRepository implements QuestionAssignmentRepository {

	private final SpringDataQuestionAssignmentRepository repository;

	public JpaQuestionAssignmentRepository(SpringDataQuestionAssignmentRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public QuestionAssignment save(QuestionAssignment assignment) {
		return QuestionJpaMapper.toDomain(repository.saveAndFlush(QuestionJpaMapper.toEntity(assignment)));
	}

	@Override
	public List<QuestionAssignment> findAllByCycleId(long cycleId) {
		return repository.findAllByCycleIdOrderByDisplayOrderAsc(cycleId).stream()
			.map(QuestionJpaMapper::toDomain).toList();
	}
}
