package com.dnd.qello.question.repository.jpa;

import java.time.Instant;

import com.dnd.qello.question.domain.QuestionAssignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_assignment")
public class QuestionAssignmentJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cycle_id", nullable = false)
	private Long cycleId;

	@Column(name = "approved_question_id", nullable = false)
	private Long approvedQuestionId;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Column(name = "assigned_at", nullable = false)
	private Instant assignedAt;

	@Column(name = "first_viewed_at")
	private Instant firstViewedAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected QuestionAssignmentJpaEntity() {
	}

	QuestionAssignmentJpaEntity(
		Long id, Long cycleId, Long approvedQuestionId, int displayOrder,
		Instant assignedAt, Instant firstViewedAt, Instant usedAt
	) {
		this.id = id;
		this.cycleId = cycleId;
		this.approvedQuestionId = approvedQuestionId;
		this.displayOrder = displayOrder;
		this.assignedAt = assignedAt;
		this.firstViewedAt = firstViewedAt;
		this.usedAt = usedAt;
	}

	Long getId() { return id; }
	Long getCycleId() { return cycleId; }
	Long getApprovedQuestionId() { return approvedQuestionId; }
	int getDisplayOrder() { return displayOrder; }
	Instant getAssignedAt() { return assignedAt; }
	Instant getFirstViewedAt() { return firstViewedAt; }
	Instant getUsedAt() { return usedAt; }
}
