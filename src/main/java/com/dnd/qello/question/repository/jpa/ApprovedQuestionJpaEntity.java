package com.dnd.qello.question.repository.jpa;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.dnd.qello.question.domain.AnswerFormat;
import com.dnd.qello.question.domain.ApprovedQuestionSourceType;
import com.dnd.qello.question.domain.ApprovedQuestionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "approved_question")
public class ApprovedQuestionJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "source_proposal_id")
	private Long sourceProposalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 30)
	private ApprovedQuestionSourceType sourceType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private ApprovedQuestionStatus status;

	@Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
	private String questionText;

	@Enumerated(EnumType.STRING)
	@Column(name = "answer_format", nullable = false, length = 20)
	private AnswerFormat answerFormat;

	@Column(name = "active_from")
	private Instant activeFrom;

	@Column(name = "active_until")
	private Instant activeUntil;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "approved_by")
	private Long approvedBy;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ApprovedQuestionJpaEntity() {
	}

	ApprovedQuestionJpaEntity(
		Long id, Long sourceProposalId, ApprovedQuestionSourceType sourceType,
		ApprovedQuestionStatus status, String questionText, AnswerFormat answerFormat,
		Instant activeFrom, Instant activeUntil, Instant approvedAt, Long approvedBy,
		Instant createdAt
	) {
		this.id = id;
		this.sourceProposalId = sourceProposalId;
		this.sourceType = sourceType;
		this.status = status;
		this.questionText = questionText;
		this.answerFormat = answerFormat;
		this.activeFrom = activeFrom;
		this.activeUntil = activeUntil;
		this.approvedAt = approvedAt;
		this.approvedBy = approvedBy;
		this.createdAt = createdAt;
	}

	Long getId() { return id; }
	Long getSourceProposalId() { return sourceProposalId; }
	ApprovedQuestionSourceType getSourceType() { return sourceType; }
	ApprovedQuestionStatus getStatus() { return status; }
	String getQuestionText() { return questionText; }
	AnswerFormat getAnswerFormat() { return answerFormat; }
	Instant getActiveFrom() { return activeFrom; }
	Instant getActiveUntil() { return activeUntil; }
	Instant getApprovedAt() { return approvedAt; }
	Long getApprovedBy() { return approvedBy; }
	Instant getCreatedAt() { return createdAt; }
}
