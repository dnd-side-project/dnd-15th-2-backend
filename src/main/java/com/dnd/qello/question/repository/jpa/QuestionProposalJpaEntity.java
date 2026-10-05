package com.dnd.qello.question.repository.jpa;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.DynamicUpdate;

import com.dnd.qello.common.persistence.JpaAuditableEntity;
import com.dnd.qello.question.domain.QuestionProposalStatus;

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

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "notification_muted", nullable = false)
	private boolean notificationMuted;

	protected QuestionProposalJpaEntity() {
	}

	QuestionProposalJpaEntity(
			Long id, Long proposerId, QuestionProposalStatus status, String proposedText,
			String decisionReason, Instant submittedAt, Instant createdAt, Instant updatedAt,
			Instant deletedAt, boolean notificationMuted) {
		super(createdAt, updatedAt);
		this.id = id;
		this.proposerId = proposerId;
		this.status = status;
		this.proposedText = proposedText;
		this.decisionReason = decisionReason;
		this.submittedAt = submittedAt;
		this.deletedAt = deletedAt;
		this.notificationMuted = notificationMuted;
	}

	Long getId() {
		return id;
	}
	Long getProposerId() {
		return proposerId;
	}
	QuestionProposalStatus getStatus() {
		return status;
	}
	String getProposedText() {
		return proposedText;
	}
	String getDecisionReason() {
		return decisionReason;
	}
	Instant getSubmittedAt() {
		return submittedAt;
	}
	Instant getDeletedAt() {
		return deletedAt;
	}
	boolean isNotificationMuted() {
		return notificationMuted;
	}
}
