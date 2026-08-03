package com.dnd.qello.question.repository.jpa;

import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.question.domain.QuestionProposalReview;
import com.dnd.qello.question.repository.QuestionProposalReviewRepository;

@Repository
@Transactional(readOnly = true)
public class JpaQuestionProposalReviewRepository implements QuestionProposalReviewRepository {

	private final SpringDataQuestionProposalReviewRepository repository;

	public JpaQuestionProposalReviewRepository(SpringDataQuestionProposalReviewRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public QuestionProposalReview save(QuestionProposalReview review) {
		return QuestionJpaMapper.toDomain(repository.saveAndFlush(QuestionJpaMapper.toEntity(review)));
	}

	@Override
	public List<QuestionProposalReview> findAllByProposalId(long proposalId) {
		return repository.findAllByProposalIdOrderByReviewedAtAsc(proposalId).stream()
			.map(QuestionJpaMapper::toDomain).toList();
	}
}
