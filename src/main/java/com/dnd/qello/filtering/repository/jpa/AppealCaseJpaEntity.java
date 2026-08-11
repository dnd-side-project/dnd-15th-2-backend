package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import com.dnd.qello.filtering.domain.AppealCase;
import com.dnd.qello.filtering.domain.FilterTargetType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appeal_case")
public class AppealCaseJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 20)
	private FilterTargetType targetType;
	@Column(name = "target_id", nullable = false)
	private long targetId;
	@Column(name = "filter_decision_id", nullable = false)
	private long filterDecisionId;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AppealCaseJpaEntity() { }

	AppealCaseJpaEntity(AppealCase appealCase) {
		this.id = appealCase.id();
		this.targetType = appealCase.targetType();
		this.targetId = appealCase.targetId();
		this.filterDecisionId = appealCase.filterDecisionId();
		this.createdAt = appealCase.createdAt();
	}

	Long getId() { return id; }
	FilterTargetType getTargetType() { return targetType; }
	long getTargetId() { return targetId; }
	long getFilterDecisionId() { return filterDecisionId; }
	Instant getCreatedAt() { return createdAt; }
}
