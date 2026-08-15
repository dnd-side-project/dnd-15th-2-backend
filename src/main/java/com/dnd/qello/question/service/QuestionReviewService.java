package com.dnd.qello.question.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestion;
import com.dnd.qello.question.domain.QuestionProposal;
import com.dnd.qello.question.domain.QuestionProposalReview;
import com.dnd.qello.question.error.QuestionErrorCode;
import com.dnd.qello.question.error.QuestionException;
import com.dnd.qello.question.repository.ApprovedQuestionRepository;
import com.dnd.qello.question.repository.QuestionProposalRepository;
import com.dnd.qello.question.repository.QuestionProposalReviewRepository;

/**
 * Proposal 상태, append-only review와 사용자 제안 승인 질문을 한 transaction으로 변경한다.
 */
@Service
public class QuestionReviewService {

	private final QuestionProposalRepository proposalRepository;
	private final QuestionProposalReviewRepository reviewRepository;
	private final ApprovedQuestionRepository approvedQuestionRepository;

	public QuestionReviewService(
		QuestionProposalRepository proposalRepository,
		QuestionProposalReviewRepository reviewRepository,
		ApprovedQuestionRepository approvedQuestionRepository
	) {
		this.proposalRepository = proposalRepository;
		this.reviewRepository = reviewRepository;
		this.approvedQuestionRepository = approvedQuestionRepository;
	}

	/**
	 * DRAFT 제안을 생성과 동시에 SUBMITTED로 전이한다. 사용자가 별도의 임시저장
	 * 단계 없이 한 번에 제안을 제출하는 API 경로에서 쓴다.
	 */
	@Transactional
	public QuestionProposal propose(long proposerId, String proposedText, Instant submittedAt) {
		QuestionProposal draft = proposalRepository.save(QuestionProposal.create(proposerId, proposedText));
		return proposalRepository.save(draft.submit(submittedAt));
	}

	@Transactional
	public QuestionProposal submit(long proposalId, Instant submittedAt) {
		QuestionProposal proposal = getProposal(proposalId);
		return proposalRepository.save(proposal.submit(submittedAt));
	}

	@Transactional
	public QuestionProposal startReview(long proposalId) {
		QuestionProposal proposal = getProposal(proposalId);
		return proposalRepository.save(proposal.startReview());
	}

	@Transactional
	public QuestionProposalReview reject(
		long proposalId, long reviewerId, String reason, Instant reviewedAt
	) {
		QuestionProposal proposal = getProposal(proposalId);
		QuestionProposal rejected = proposal.reject(reason);
		QuestionProposalReview review = QuestionProposalReview.reject(
			proposalId, reviewerId, reason, reviewedAt);
		QuestionProposalReview savedReview = reviewRepository.save(review);
		proposalRepository.save(rejected);
		return savedReview;
	}

	@Transactional
	public ApprovedQuestion approve(
		long proposalId,
		long reviewerId,
		AnswerFormat answerFormat,
		Instant activeFrom,
		Instant activeUntil,
		Instant approvedAt
	) {
		QuestionProposal proposal = getProposal(proposalId);
		QuestionProposal approved = proposal.approve(null);
		QuestionProposalReview review = QuestionProposalReview.approve(
			proposalId, reviewerId, null, approvedAt);
		ApprovedQuestion approvedQuestion = ApprovedQuestion.activeUserProposal(
			proposalId, proposal.getProposedText(), answerFormat,
			activeFrom, activeUntil, approvedAt, reviewerId);

		reviewRepository.save(review);
		proposalRepository.save(approved);
		return approvedQuestionRepository.save(approvedQuestion);
	}

	private QuestionProposal getProposal(long proposalId) {
		return proposalRepository.findById(proposalId)
			.orElseThrow(() -> new QuestionException(QuestionErrorCode.PROPOSAL_NOT_FOUND, "proposalId"));
	}
}
