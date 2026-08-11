package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import com.dnd.qello.filtering.domain.FilterDecision;
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
@Table(name = "filter_decision")
@DynamicUpdate
public class FilterDecisionJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "filter_job_id", nullable = false)
	private long filterJobId;
	@Column(name = "attempt_generation", nullable = false)
	private int attemptGeneration;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private FilterVerdict verdict;
	@Column(name = "requested_release_id", nullable = false)
	private long requestedReleaseId;
	@Column(name = "actual_model", length = 100)
	private String actualModel;
	@Column(name = "decided_at", nullable = false)
	private Instant decidedAt;

	protected FilterDecisionJpaEntity() { }

	FilterDecisionJpaEntity(FilterDecision decision) {
		this.id = decision.id();
		this.filterJobId = decision.filterJobId();
		this.attemptGeneration = decision.attemptGeneration();
		this.verdict = decision.verdict();
		this.requestedReleaseId = decision.requestedReleaseId();
		this.actualModel = decision.actualModel();
		this.decidedAt = decision.decidedAt();
	}

	Long getId() { return id; }
	long getFilterJobId() { return filterJobId; }
	int getAttemptGeneration() { return attemptGeneration; }
	FilterVerdict getVerdict() { return verdict; }
	long getRequestedReleaseId() { return requestedReleaseId; }
	String getActualModel() { return actualModel; }
	Instant getDecidedAt() { return decidedAt; }
}
