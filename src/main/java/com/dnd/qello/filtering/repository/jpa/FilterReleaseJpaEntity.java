package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterRelease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "filter_release")
public class FilterReleaseJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FilterReleaseJpaEntity() { }

	FilterReleaseJpaEntity(FilterRelease release) {
		this.id = release.id();
		this.createdAt = release.createdAt();
	}

	Long getId() { return id; }
	Instant getCreatedAt() { return createdAt; }
}
