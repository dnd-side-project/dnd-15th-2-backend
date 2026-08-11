package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;

import com.dnd.qello.filtering.domain.FilterRelease;
import com.dnd.qello.filtering.domain.FilterReleaseStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "filter_release")
@DynamicUpdate
public class FilterReleaseJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "normalization_ref", nullable = false, length = 200)
	private String normalizationRef;
	@Column(name = "local_ruleset_ref", nullable = false, length = 200)
	private String localRulesetRef;
	@Column(name = "category_mapping_ref", nullable = false, length = 200)
	private String categoryMappingRef;
	@Column(name = "model_snapshot", nullable = false, length = 200)
	private String modelSnapshot;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private FilterReleaseStatus status;
	@Column(name = "promoted_at")
	private Instant promotedAt;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FilterReleaseJpaEntity() { }

	FilterReleaseJpaEntity(FilterRelease release) {
		this.id = release.id();
		this.normalizationRef = release.normalizationRef();
		this.localRulesetRef = release.localRulesetRef();
		this.categoryMappingRef = release.categoryMappingRef();
		this.modelSnapshot = release.modelSnapshot();
		this.status = release.status();
		this.promotedAt = release.promotedAt();
		this.createdAt = release.createdAt();
	}

	Long getId() { return id; }
	String getNormalizationRef() { return normalizationRef; }
	String getLocalRulesetRef() { return localRulesetRef; }
	String getCategoryMappingRef() { return categoryMappingRef; }
	String getModelSnapshot() { return modelSnapshot; }
	FilterReleaseStatus getStatus() { return status; }
	Instant getPromotedAt() { return promotedAt; }
	Instant getCreatedAt() { return createdAt; }
}
