package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthStatus;

public record SnapshotHealthResponse(
	String modelSnapshot,
	SnapshotHealthStatus status,
	int targetOnlyFailureCount,
	Instant firstTargetOnlyFailureAt,
	Instant lastTargetOnlyFailureAt,
	boolean officialAnnouncement,
	Instant confirmedAt,
	Long confirmedByOperatorUserId,
	Instant updatedAt
) {

	public static SnapshotHealthResponse from(SnapshotHealth health) {
		return new SnapshotHealthResponse(
			health.modelSnapshot(),
			health.status(),
			health.targetOnlyFailureCount(),
			health.firstTargetOnlyFailureAt(),
			health.lastTargetOnlyFailureAt(),
			health.officialAnnouncement(),
			health.confirmedAt(),
			health.confirmedByOperatorUserId(),
			health.updatedAt()
		);
	}
}
