package com.dnd.qello.question.repository;

import java.util.Optional;

import com.dnd.qello.question.domain.QuestionAssignmentCycle;

public interface QuestionAssignmentCycleRepository {

	QuestionAssignmentCycle save(QuestionAssignmentCycle cycle);

	Optional<QuestionAssignmentCycle> findById(long id);
}
