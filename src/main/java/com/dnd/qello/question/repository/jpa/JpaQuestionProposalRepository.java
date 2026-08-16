package com.dnd.qello.question.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalStatus;
import com.dnd.qello.question.repository.QuestionProposalRepository;

@Repository
@Transactional(readOnly = true)
public class JpaQuestionProposalRepository implements QuestionProposalRepository {

	private final SpringDataQuestionProposalRepository repository;

	public JpaQuestionProposalRepository(SpringDataQuestionProposalRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public QuestionProposal save(QuestionProposal proposal) {
		QuestionProposalJpaEntity entity = QuestionJpaMapper.toEntity(proposal);
		if (proposal.getId() != null && proposal.getStatus()
			== QuestionProposalStatus.DRAFT) {
			boolean textChanged = repository.findById(proposal.getId())
				.map(existing -> !existing.getProposedText().equals(proposal.getProposedText()))
				.orElse(false);
			if (textChanged) {
				// V1's shared trigger references approved_question.question_text even for
				// question_proposal rows. Replace only an unsubmitted draft so that this
				// pre-existing schema defect cannot make submitted text mutable.
				repository.deleteById(proposal.getId());
				repository.flush();
				entity = new QuestionProposalJpaEntity(
					null, proposal.getProposerId(), proposal.getStatus(), proposal.getProposedText(),
					proposal.getDecisionReason(), proposal.getSubmittedAt(), null, null);
			}
		}
		return QuestionJpaMapper.toDomain(repository.saveAndFlush(entity));
	}

	@Override
	public Optional<QuestionProposal> findById(long id) {
		return repository.findById(id).map(QuestionJpaMapper::toDomain);
	}

	@Override
	public List<QuestionProposal> findAllByProposerIdOrderByCreatedAtDesc(long proposerId) {
		return repository.findAllByProposerIdOrderByCreatedAtDesc(proposerId).stream()
			.map(QuestionJpaMapper::toDomain)
			.toList();
	}
}
