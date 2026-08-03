package com.dnd.qello.question.repository.jpa;

import java.time.Instant;

import com.dnd.qello.question.domain.QuestionProposalReviewDecision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_proposal_review")
public class QuestionProposalReviewJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "proposal_id", nullable = false)
	private Long proposalId;

	@Column(name = "reviewer_id", nullable = false)
	private Long reviewerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false, length = 30)
	private QuestionProposalReviewDecision decision;

	@Column(name = "reason", columnDefinition = "TEXT")
	private String reason;

	@Column(name = "reviewed_at", nullable = false)
	private Instant reviewedAt;

	protected QuestionProposalReviewJpaEntity() {
	}

	QuestionProposalReviewJpaEntity(
		Long id, Long proposalId, Long reviewerId,
		QuestionProposalReviewDecision decision, String reason, Instant reviewedAt
	) {
		this.id = id;
		this.proposalId = proposalId;
		this.reviewerId = reviewerId;
		this.decision = decision;
		this.reason = reason;
		this.reviewedAt = reviewedAt;
	}

	Long getId() { return id; }
	Long getProposalId() { return proposalId; }
	Long getReviewerId() { return reviewerId; }
	QuestionProposalReviewDecision getDecision() { return decision; }
	String getReason() { return reason; }
	Instant getReviewedAt() { return reviewedAt; }
}
