package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import com.dnd.qello.filtering.domain.FilterJob;
import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.FilterVerdict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "filter_job")
@DynamicUpdate
public class FilterJobJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private FilterTargetType targetType;
	@Column(name = "target_id", nullable = false)
	private long targetId;
	@Column(name = "target_version", nullable = false)
	private long targetVersion;
	@Column(name = "filter_release_id", nullable = false)
	private long filterReleaseId;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private FilterJobStatus status;
	@Column(name = "attempt_generation", nullable = false)
	private int attemptGeneration;
	@Column(name = "manually_resolved", nullable = false)
	private boolean manuallyResolved;
	@Enumerated(EnumType.STRING)
	@Column(name = "resolved_verdict", length = 10)
	private FilterVerdict resolvedVerdict;
	@Column(name = "idempotency_key", nullable = false, length = 200)
	private String idempotencyKey;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected FilterJobJpaEntity() { }

	FilterJobJpaEntity(FilterJob job) {
		this.id = job.id();
		this.targetType = job.target().targetType();
		this.targetId = job.target().targetId();
		this.targetVersion = job.target().targetVersion();
		this.filterReleaseId = job.filterReleaseId();
		this.status = job.status();
		this.attemptGeneration = job.attemptGeneration();
		this.manuallyResolved = job.manuallyResolved();
		this.resolvedVerdict = job.resolvedVerdict();
		this.idempotencyKey = job.idempotencyKey();
		this.createdAt = job.createdAt();
		this.updatedAt = job.updatedAt();
	}

	Long getId() { return id; }
	FilterTargetType getTargetType() { return targetType; }
	long getTargetId() { return targetId; }
	long getTargetVersion() { return targetVersion; }
	long getFilterReleaseId() { return filterReleaseId; }
	FilterJobStatus getStatus() { return status; }
	int getAttemptGeneration() { return attemptGeneration; }
	boolean isManuallyResolved() { return manuallyResolved; }
	FilterVerdict getResolvedVerdict() { return resolvedVerdict; }
	String getIdempotencyKey() { return idempotencyKey; }
	Instant getCreatedAt() { return createdAt; }
	Instant getUpdatedAt() { return updatedAt; }
}
