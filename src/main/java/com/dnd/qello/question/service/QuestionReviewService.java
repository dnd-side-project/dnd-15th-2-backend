package com.dnd.qello.question.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dnd.qello.notification.domain.OutboxAggregateType;
import com.dnd.qello.notification.domain.OutboxEvent;
import com.dnd.qello.notification.domain.OutboxEventType;
import com.dnd.qello.notification.repository.OutboxEventRepository;
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
 *
 * <p>승인·반려 판정은 같은 transaction에서 {@code QUESTION_PROPOSAL_REVIEWED} outbox
 * event를 남긴다(producer 측만). 이 event를 실제 인앱 알림·push로 fan-out하는 worker
 * 배선은 다른 {@code *_REVIEWED}/{@code *_RECEIVED} 계열 event들과 마찬가지로 별도
 * production gate 이슈에서 다룬다.</p>
 */
@Service
public class QuestionReviewService {

	private final QuestionProposalRepository proposalRepository;
	private final QuestionProposalReviewRepository reviewRepository;
	private final ApprovedQuestionRepository approvedQuestionRepository;
	private final OutboxEventRepository outboxEventRepository;

	public QuestionReviewService(
		QuestionProposalRepository proposalRepository,
		QuestionProposalReviewRepository reviewRepository,
		ApprovedQuestionRepository approvedQuestionRepository,
		OutboxEventRepository outboxEventRepository
	) {
		this.proposalRepository = proposalRepository;
		this.reviewRepository = reviewRepository;
		this.approvedQuestionRepository = approvedQuestionRepository;
		this.outboxEventRepository = outboxEventRepository;
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
		publishReviewed(proposal.getProposerId(), proposalId, "REJECTED", reviewedAt);
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
		ApprovedQuestion saved = approvedQuestionRepository.save(approvedQuestion);
		publishReviewed(proposal.getProposerId(), proposalId, "APPROVED", approvedAt);
		return saved;
	}

	private QuestionProposal getProposal(long proposalId) {
		return proposalRepository.findById(proposalId)
			.orElseThrow(() -> new QuestionException(QuestionErrorCode.PROPOSAL_NOT_FOUND, "proposalId"));
	}

	// dedupKey를 proposalId에 고정한다. QuestionProposal은 UNDER_REVIEW에서 APPROVED나
	// REJECTED로 딱 한 번만 전이하므로(재검토 경로 없음, QuestionProposal#requireStatus),
	// 같은 제안에 대한 outbox event가 재복제될 수 없다.
	private void publishReviewed(Long proposerId, long proposalId, String decision, Instant at) {
		String dedupKey = "question-proposal-reviewed:" + proposalId;
		if (outboxEventRepository.findByDedupKey(dedupKey).isPresent()) {
			return;
		}
		String payload = String.format(
			"{\"proposalId\":%d,\"proposerId\":%d,\"decision\":\"%s\"}", proposalId, proposerId, decision);
		outboxEventRepository.save(OutboxEvent.pending(
			OutboxAggregateType.QUESTION_PROPOSAL, proposalId,
			OutboxEventType.QUESTION_PROPOSAL_REVIEWED, dedupKey, payload, at));
	}
}
