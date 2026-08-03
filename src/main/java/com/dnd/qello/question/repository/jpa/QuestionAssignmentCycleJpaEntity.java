package com.dnd.qello.question.repository.jpa;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import com.dnd.qello.question.domain.QuestionAssignmentCycleStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_assignment_cycle")
public class QuestionAssignmentCycleJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "cycle_key", nullable = false, length = 100)
	private String cycleKey;

	@Column(name = "pool_version", nullable = false, length = 100)
	private String poolVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private QuestionAssignmentCycleStatus status;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected QuestionAssignmentCycleJpaEntity() {
	}

	QuestionAssignmentCycleJpaEntity(
		Long id, Long userId, String cycleKey, String poolVersion,
		QuestionAssignmentCycleStatus status, Instant startsAt, Instant endsAt,
		Instant createdAt
	) {
		this.id = id;
		this.userId = userId;
		this.cycleKey = cycleKey;
		this.poolVersion = poolVersion;
		this.status = status;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.createdAt = createdAt;
	}

	Long getId() { return id; }
	Long getUserId() { return userId; }
	String getCycleKey() { return cycleKey; }
	String getPoolVersion() { return poolVersion; }
	QuestionAssignmentCycleStatus getStatus() { return status; }
	Instant getStartsAt() { return startsAt; }
	Instant getEndsAt() { return endsAt; }
	Instant getCreatedAt() { return createdAt; }
}
