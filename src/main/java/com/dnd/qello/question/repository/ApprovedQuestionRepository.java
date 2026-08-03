package com.dnd.qello.question.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.dnd.qello.question.domain.ApprovedQuestion;

public interface ApprovedQuestionRepository {

	ApprovedQuestion save(ApprovedQuestion question);

	Optional<ApprovedQuestion> findById(long id);

	List<ApprovedQuestion> findAssignableAt(Instant at);
}
