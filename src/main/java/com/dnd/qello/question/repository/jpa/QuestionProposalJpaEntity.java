package com.dnd.qello.question.repository.jpa;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import com.dnd.qello.common.persistence.JpaAuditableEntity;
import com.dnd.qello.question.domain.QuestionProposalStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_proposal")
@DynamicUpdate
public class QuestionProposalJpaEntity extends JpaAuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "proposer_id", nullable = false)
	private Long proposerId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private QuestionProposalStatus status;

	@Column(name = "proposed_text", nullable = false, columnDefinition = "TEXT")
	private String proposedText;

	@Column(name = "decision_reason", columnDefinition = "TEXT")
	private String decisionReason;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	protected QuestionProposalJpaEntity() {
	}

	QuestionProposalJpaEntity(
		Long id, Long proposerId, QuestionProposalStatus status, String proposedText,
		String decisionReason, Instant submittedAt, Instant createdAt, Instant updatedAt
	) {
		super(createdAt, updatedAt);
		this.id = id;
		this.proposerId = proposerId;
		this.status = status;
		this.proposedText = proposedText;
		this.decisionReason = decisionReason;
		this.submittedAt = submittedAt;
	}

	Long getId() { return id; }
	Long getProposerId() { return proposerId; }
	QuestionProposalStatus getStatus() { return status; }
	String getProposedText() { return proposedText; }
	String getDecisionReason() { return decisionReason; }
	Instant getSubmittedAt() { return submittedAt; }
}
