package com.dnd.qello.filtering.repository.jpa;

import java.time.Instant;

import com.dnd.qello.filtering.domain.FilterJobStatus;
import com.dnd.qello.filtering.domain.FilterJobStatusHistoryEntry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "filter_job_status_history")
public class FilterJobStatusHistoryJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "filter_job_id", nullable = false)
	private long filterJobId;
	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", length = 30)
	private FilterJobStatus fromStatus;
	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 30)
	private FilterJobStatus toStatus;
	@Column(length = 200)
	private String reason;
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	protected FilterJobStatusHistoryJpaEntity() { }

	FilterJobStatusHistoryJpaEntity(FilterJobStatusHistoryEntry entry) {
		this.id = entry.id();
		this.filterJobId = entry.filterJobId();
		this.fromStatus = entry.fromStatus();
		this.toStatus = entry.toStatus();
		this.reason = entry.reason();
		this.occurredAt = entry.occurredAt();
	}

	Long getId() { return id; }
	long getFilterJobId() { return filterJobId; }
	FilterJobStatus getFromStatus() { return fromStatus; }
	FilterJobStatus getToStatus() { return toStatus; }
	String getReason() { return reason; }
	Instant getOccurredAt() { return occurredAt; }
}
