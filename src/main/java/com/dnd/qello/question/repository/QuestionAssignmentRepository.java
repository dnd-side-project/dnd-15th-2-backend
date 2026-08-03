package com.dnd.qello.question.repository;

import java.util.List;

import com.dnd.qello.question.domain.QuestionAssignment;

public interface QuestionAssignmentRepository {

	QuestionAssignment save(QuestionAssignment assignment);

	List<QuestionAssignment> findAllByCycleId(long cycleId);
}
