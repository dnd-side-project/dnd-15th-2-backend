package com.dnd.qello.question.repository;

import java.util.List;
import java.util.Optional;

import com.dnd.qello.question.domain.QuestionProposal;

public interface QuestionProposalRepository {

	QuestionProposal save(QuestionProposal proposal);

	Optional<QuestionProposal> findById(long id);

	List<QuestionProposal> findAllByProposerIdOrderByCreatedAtDesc(long proposerId);
}
