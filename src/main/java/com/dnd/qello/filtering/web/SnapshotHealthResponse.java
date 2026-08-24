package com.dnd.qello.filtering.web;

import java.time.Instant;

import com.dnd.qello.filtering.domain.SnapshotHealth;
import com.dnd.qello.filtering.domain.SnapshotHealthStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모델 snapshot의 장애 상태와 운영자 확정 정보를 담은 응답입니다.")
public record SnapshotHealthResponse(
	@Schema(description = "상태를 확인한 모델 snapshot 식별자입니다.")
	String modelSnapshot,
	@Schema(description = "모델 snapshot의 상태입니다.")
	SnapshotHealthStatus status,
	@Schema(description = "target 전용 장애가 누적된 횟수입니다.")
	int targetOnlyFailureCount,
	@Schema(description = "target 전용 장애가 처음 발생한 시각입니다.")
	Instant firstTargetOnlyFailureAt,
	@Schema(description = "target 전용 장애가 마지막으로 발생한 시각입니다.")
	Instant lastTargetOnlyFailureAt,
	@Schema(description = "공식 장애 공지가 있었는지 나타냅니다.")
	boolean officialAnnouncement,
	@Schema(description = "영구 장애로 확정한 시각입니다. 미확정이면 값이 없습니다.")
	Instant confirmedAt,
	@Schema(description = "영구 장애로 확정한 운영자 식별자입니다. 미확정이면 값이 없습니다.")
	Long confirmedByOperatorUserId,
	@Schema(description = "상태가 마지막으로 변경된 시각입니다.")
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
