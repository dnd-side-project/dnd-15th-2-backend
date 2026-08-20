package com.dnd.qello.question.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.question.domain.QuestionAssignment;

public interface QuestionAssignmentRepository {

	QuestionAssignment save(QuestionAssignment assignment);

	Optional<QuestionAssignment> findById(long id);

	List<QuestionAssignment> findAllByCycleId(long cycleId);
}
