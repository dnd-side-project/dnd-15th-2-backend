package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterTargetType;
import com.dnd.qello.filtering.domain.ManualReviewCase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "manual_review_case")
public class ManualReviewCaseJpaEntity {

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
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ManualReviewCaseJpaEntity() { }

	ManualReviewCaseJpaEntity(ManualReviewCase reviewCase) {
		this.id = reviewCase.id();
		this.targetType = reviewCase.target().targetType();
		this.targetId = reviewCase.target().targetId();
		this.targetVersion = reviewCase.target().targetVersion();
		this.filterReleaseId = reviewCase.filterReleaseId();
		this.createdAt = reviewCase.createdAt();
	}

	Long getId() { return id; }
	FilterTargetType getTargetType() { return targetType; }
	long getTargetId() { return targetId; }
	long getTargetVersion() { return targetVersion; }
	long getFilterReleaseId() { return filterReleaseId; }
	Instant getCreatedAt() { return createdAt; }
}
