package com.dnd.qello.question.repository;

import java.util.List;

import com.dnd.qello.question.domain.QuestionProposalReview;

public interface QuestionProposalReviewRepository {

	QuestionProposalReview save(QuestionProposalReview review);

	List<QuestionProposalReview> findAllByProposalId(long proposalId);
}
